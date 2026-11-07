package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.SupermanAbilityPacket;
import com.flash.registry.ModSounds;
import com.flash.superman.SupermanAbilities;
import com.flash.superman.SupermanAbility;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Client side of Superman: ability keys, x-ray, the heat vision beams and the looping sounds. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanClient {

    private static final boolean[] KEY_DOWN = new boolean[SupermanAbility.values().length];
    private static boolean xray;
    private static long chargeStart = -1L;

    private SupermanClient() {
    }

    /** Key of an ability: the Flash ability key it shares, V for the punch or J for the grab. */
    public static KeyMapping key(SupermanAbility ability) {
        if (ability.specialKey() == SupermanAbility.KEY_V) {
            return FlashKeys.INCREASE_SPEED;
        }
        if (ability.specialKey() == SupermanAbility.KEY_J) {
            return FlashKeys.TOGGLE_SPEED;
        }
        return FlashAbilityKeys.mapping(ability.keySlot());
    }

    public static boolean xray() {
        return xray;
    }

    /** Charge of the punch (0..1) while V is held, or -1. */
    public static float punchCharge() {
        Minecraft mc = Minecraft.getInstance();
        if (chargeStart < 0L || mc.level == null) {
            return -1.0F;
        }
        return Math.min(1.0F, (mc.level.getGameTime() - chargeStart) / (float) SupermanAbilities.CHARGE_TICKS);
    }

    /** True when an ability is running for the local player (HUD highlight). */
    public static boolean active(SupermanAbility ability) {
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            return false;
        }
        return switch (ability) {
            case FLIGHT -> SupermanState.flag(p, SupermanPowers.F_FLIGHT);
            case SUPER_SPEED -> SupermanState.flag(p, SupermanPowers.F_SPEED);
            case TIME_SLOW -> SupermanState.flag(p, SupermanPowers.F_SLOW);
            case HEAT_VISION -> SupermanState.flag(p, SupermanPowers.F_HEAT);
            case FREEZE_BREATH -> SupermanState.flag(p, SupermanPowers.F_FREEZE);
            case SUPER_BREATH -> SupermanState.flag(p, SupermanPowers.F_BREATH);
            case CHARGED_PUNCH -> chargeStart >= 0L;
            case GRAB -> SupermanState.flag(p, SupermanPowers.F_HOLDING);
            case SUPER_COMBO -> SupermanState.flag(p, SupermanPowers.F_COMBO);
            case SOLAR_ULTIMATE -> SupermanState.flag(p, SupermanPowers.F_ULTIMATE);
            case XRAY -> xray;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Aimed flight with three stages
    // ------------------------------------------------------------------

    /** Flight speeds in blocks per tick: hover, walking, sprint, double sprint (scaled by the level). */
    private static final double[] STAGE_SPEED = {0.0D, 0.45D, 1.1D, 2.4D};
    /** The server rejects moves above ~10 blocks per tick: the fastest stage stays under it. */
    private static final double MAX_SPEED = 9.0D;
    private static int flightStage;
    private static boolean sprintWasDown;
    private static long lastSprintPress = -100L;
    private static boolean boostArmed;

    // ------------------------------------------------------------------
    // Charged takeoff: hold jump on the ground
    // ------------------------------------------------------------------

    /** Jump held this long starts the coil of the charged takeoff (the hop that just began is pulled back down). */
    private static final int TAP_TICKS = 3;
    private static final int LAUNCH_AT = TAP_TICKS + 20;
    /** Ticks of the coil (-1 = not coiling). */
    private static int jumpHeld = -1;
    /** Ticks the jump key has been held without a break. */
    private static int holdTicks;
    private static boolean jumpWasDown;
    private static boolean waitRelease;
    /** Horizontal push of the big leap, kept for its first ticks (the ground friction would eat it). */
    private static Vec3 leapVel = Vec3.ZERO;
    private static int leapTicks;
    private static int launchedFor = -1;

    /**
     * Jump stays the normal, instant one (double jump flies). Sneak + jump on the ground: a big leap like the
     * Hulk's, much weaker. Keep jump held: after the first hop, back on the ground, Superman coils into the crouch and
     * about a second later the ground cracks and he shoots ~10 blocks up, switching to flight at the top.
     */
    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer p = mc.player;
        if (p == null || event.getEntity() != p) {
            return;
        }
        var input = event.getInput();
        boolean down = input.jumping;
        boolean pressed = down && !jumpWasDown;
        jumpWasDown = down;
        holdTicks = down ? holdTicks + 1 : 0;
        boolean usable = SupermanState.isSuperman(p) && !p.getAbilities().flying && !p.isInWater()
                && !p.isPassenger() && mc.screen == null;
        if (waitRelease) {
            if (down) {
                input.jumping = false;
                return;
            }
            waitRelease = false;
        }
        if (!usable) {
            if (jumpHeld >= 0) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(2));
            }
            jumpHeld = -1;
            return;
        }
        // sneak + jump: the big leap
        if (pressed && input.shiftKeyDown && p.onGround() && jumpHeld < 0) {
            input.jumping = false;
            int lvl = Math.max(1, SupermanState.level());
            // toward where he looks: looking ahead goes far, looking up goes high
            Vec3 look = p.getLookAngle();
            Vec3 flat = look.multiply(1.0D, 0.0D, 1.0D);
            flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
            double strength = 1.6D + 0.06D * lvl;
            double horizontal = strength * Math.max(0.45D, Math.sqrt(look.x * look.x + look.z * look.z));
            double up = Math.max(0.9D + 0.04D * lvl, strength * look.y);
            leapVel = flat.scale(horizontal);
            leapTicks = 3;
            p.setDeltaMovement(leapVel.x, up, leapVel.z);
            p.hasImpulse = true;
            p.playSound(ModSounds.TAKEOFF.get(), 0.7F, 1.3F);
            return;
        }
        if (jumpHeld < 0) {
            // still held a moment after the press: no hop, Superman crouches and coils right away
            if (down && !input.shiftKeyDown && holdTicks == TAP_TICKS && (p.onGround() || p.getY() - p.yo > 0.0D)) {
                jumpHeld = 0;
                input.jumping = false;
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.3D, Math.min(v.y, -0.6D), v.z * 0.3D);
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(0));
            }
            return;
        }
        if (down) {
            // the coil counts once he is back on his feet
            if (p.onGround()) {
                jumpHeld++;
            }
            input.jumping = false;
            if (jumpHeld >= LAUNCH_AT - TAP_TICKS) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(1));
                jumpHeld = -1;
                waitRelease = true;
                launchedFor = 0;
            }
        } else {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(2));
            jumpHeld = -1;
        }
    }

    /** True while the local player coils for the charged takeoff. */
    public static boolean charging() {
        return jumpHeld >= 0;
    }

    /** Current stage of the local flight (0 hover, 1 slow, 2 fast, 3 fastest). */
    public static int flightStage() {
        return flightStage;
    }

    /**
     * The local player flies where he looks: forward follows the full 3D look (up, down, any angle), the
     * side keys strafe, jump / sneak rise and sink. Walking = stage 1, holding sprint = stage 2, sprint
     * pressed twice and held = stage 3. Set before the movement of the tick (the client moves the player).
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.START || event.player != mc.player || mc.level == null) {
            return;
        }
        net.minecraft.client.player.LocalPlayer p = mc.player;
        boolean sprint = mc.options.keySprint.isDown();
        long now = mc.level.getGameTime();
        if (sprint && !sprintWasDown) {
            boostArmed = now - lastSprintPress <= 7L;
            lastSprintPress = now;
        }
        if (!sprint) {
            boostArmed = false;
        }
        sprintWasDown = sprint;
        if (leapTicks > 0) {
            leapTicks--;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(leapVel.x, v.y, leapVel.z);
        }
        // after the charged takeoff: hover at the top of the jump
        if (launchedFor >= 0) {
            launchedFor++;
            if (launchedFor > 3 && !p.onGround() && p.getDeltaMovement().y <= 0.05D && p.getAbilities().mayfly) {
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.setDeltaMovement(p.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                launchedFor = -1;
            } else if (launchedFor > 60 || (launchedFor > 10 && p.onGround())) {
                launchedFor = -1;
            }
        }
        if (!SupermanState.isSuperman(p) || !p.getAbilities().flying || p.isPassenger()
                || !SupermanState.flag(p, SupermanPowers.F_FLIGHT)) {
            flightStage = 0;
            return;
        }
        float forward = p.input.forwardImpulse;
        float strafe = p.input.leftImpulse;
        int stage = forward > 0.0F ? (sprint ? (boostArmed ? 3 : 2) : 1) : 0;
        if (stage == 3 && flightStage < 3) {
            p.playSound(ModSounds.PUNCH_SONIC.get(), 0.7F, 1.3F);
        }
        flightStage = stage;
        // four times the old speeds, growing with the level: about 40 % at level 1, all of it at level 10
        double scale = 4.0D * (0.34D + 0.066D * Math.max(1, SupermanState.level()));
        Vec3 look = p.getLookAngle();
        Vec3 flatRight = new Vec3(-look.z, 0.0D, look.x);
        flatRight = flatRight.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : flatRight.normalize();
        Vec3 target = Vec3.ZERO;
        if (forward > 0.0F) {
            target = look.scale(Math.min(MAX_SPEED, STAGE_SPEED[stage] * scale));
        } else if (forward < 0.0F) {
            target = look.scale(-0.3D * scale);
        }
        // leftImpulse > 0 = left
        target = target.add(flatRight.scale(-strafe * 0.35D * Math.min(scale, 2.0D)));
        if (p.input.jumping) {
            target = target.add(0.0D, 0.45D * Math.min(scale, 2.0D), 0.0D);
        }
        if (p.input.shiftKeyDown) {
            target = target.add(0.0D, -0.45D * Math.min(scale, 2.0D), 0.0D);
        }
        Vec3 cur = p.getDeltaMovement();
        // speeds up a little slower than it brakes
        double k = target.lengthSqr() > cur.lengthSqr() ? 0.16D : 0.3D;
        p.setDeltaMovement(cur.add(target.subtract(cur).scale(k)));
        p.fallDistance = 0.0F;
    }

    // ------------------------------------------------------------------
    // Body following the flight direction
    // ------------------------------------------------------------------

    private static final java.util.Set<Integer> TILTED = new java.util.HashSet<>();
    private static final java.util.Set<String> TILT_POSES = java.util.Set.of("cruise", "boost", "prone_flight12",
            "bank_left", "bank_right");

    /** Smoothed flight motion of a player (as the Green Lantern mod's EstadoVoo). */
    private static final class Lean {
        Vec3 lastPos;
        long lastNanos;
        float lastYaw;
        Vec3 vel = Vec3.ZERO;
        float yawVel;
        float pitch;
        float roll;
    }

    private static final java.util.Map<java.util.UUID, Lean> LEANS = new java.util.HashMap<>();

    /**
     * While flying, the body follows the motion like in the Green Lantern mod: in the forward poses it tips toward
     * where it goes (up or down), and it banks into the turns (side speed and turning rate); slow, it bobs a little.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        String base = SupermanPose.base(p.getUUID());
        if (base == null || !SupermanState.isSuperman(p)) {
            LEANS.remove(p.getUUID());
            return;
        }
        float partial = event.getPartialTick();
        long now = System.nanoTime();
        Vec3 pos = new Vec3(net.minecraft.util.Mth.lerp(partial, p.xo, p.getX()),
                net.minecraft.util.Mth.lerp(partial, p.yo, p.getY()), net.minecraft.util.Mth.lerp(partial, p.zo, p.getZ()));
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        Lean l = LEANS.computeIfAbsent(p.getUUID(), k -> new Lean());
        if (l.lastPos == null) {
            l.lastPos = pos;
            l.lastNanos = now;
            l.lastYaw = bodyYaw;
        }
        double dt = Math.min(0.1D, (now - l.lastNanos) / 1.0E9D);
        if (dt > 1.0E-4D) {
            // velocity in blocks per tick, smoothed
            Vec3 inst = pos.subtract(l.lastPos).scale(1.0D / dt / 20.0D);
            l.vel = l.vel.add(inst.subtract(l.vel).scale(1.0D - Math.exp(-7.0D * dt)));
            float dyaw = net.minecraft.util.Mth.wrapDegrees(bodyYaw - l.lastYaw);
            l.yawVel += (float) ((dyaw / dt - l.yawVel) * (1.0D - Math.exp(-7.0D * dt)));
            l.lastPos = pos;
            l.lastNanos = now;
            l.lastYaw = bodyYaw;
        }
        double yr = Math.toRadians(bodyYaw);
        double forward = -Math.sin(yr) * l.vel.x + Math.cos(yr) * l.vel.z;
        double side = -Math.cos(yr) * l.vel.x - Math.sin(yr) * l.vel.z;
        double speed = l.vel.length();
        float moving = (float) net.minecraft.util.Mth.clamp(speed / 0.22D, 0.0D, 1.0D);
        float pitchTarget = 0.0F;
        if (TILT_POSES.contains(base) && forward > 0.02D) {
            // the prone pose tips toward the motion: nose down diving, up climbing
            pitchTarget = (float) net.minecraft.util.Mth.clamp(Math.toDegrees(Math.atan2(-l.vel.y, forward)), -60.0D,
                    60.0D) * moving;
        }
        float rollTarget = (float) net.minecraft.util.Mth.clamp(side * 140.0D, -32.0D, 32.0D) * moving
                + net.minecraft.util.Mth.clamp(l.yawVel * 0.06F, -22.0F, 22.0F) * moving;
        float k = (float) (1.0D - Math.exp(-6.0D * Math.max(dt, 1.0E-3D)));
        l.pitch += (pitchTarget - l.pitch) * k;
        l.roll += (rollTarget - l.roll) * k;
        float bob = (float) (1.0D - net.minecraft.util.Mth.clamp(speed / 0.15D, 0.0D, 1.0D))
                * (float) Math.sin(now / 1.0E9D * 2.2D) * 0.035F;
        float cy = p.getBbHeight() * 0.5F;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0.0F, cy + bob, 0.0F);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw));
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-l.roll));
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(l.pitch));
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bodyYaw));
        ps.translate(0.0F, -cy, 0.0F);
        TILTED.add(p.getId());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
        if (TILTED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // ------------------------------------------------------------------
    // Keys and sounds
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
            return;
        }
        boolean superman = SupermanState.isSuperman(mc.player);
        if (!superman) {
            xray = false;
            chargeStart = -1L;
        }
        boolean active = superman && mc.screen == null && !mc.player.isSpectator();
        SupermanAbility[] abilities = SupermanAbility.values();
        for (int i = 0; i < abilities.length; i++) {
            SupermanAbility a = abilities[i];
            KeyMapping key = key(a);
            boolean down = active && key != null && key.isDown();
            if (down == KEY_DOWN[i]) {
                continue;
            }
            KEY_DOWN[i] = down;
            boolean unlocked = SupermanState.level() >= a.level();
            if (a == SupermanAbility.XRAY) {
                if (down && unlocked) {
                    xray = !xray;
                    mc.player.displayClientMessage(Component.translatable(xray ? "message.flash.superman_xray_on"
                            : "message.flash.superman_xray_off"), true);
                } else if (down) {
                    mc.player.displayClientMessage(Component.translatable("message.flash.superman_locked", a.level()), true);
                }
                continue;
            }
            if (a == SupermanAbility.CHARGED_PUNCH) {
                chargeStart = down && unlocked && SupermanState.cooldown(a, mc.level.getGameTime()) <= 0
                        ? mc.level.getGameTime() : -1L;
            }
            boolean sendRelease = a.kind() == SupermanAbility.Kind.HOLD || a.kind() == SupermanAbility.Kind.CHARGE;
            if (down || sendRelease) {
                FlashNetwork.CHANNEL.sendToServer(new SupermanAbilityPacket(i, down));
            }
        }
        loopSounds(mc);
    }

    private static final Map<String, Loop> LOOPS = new HashMap<>();

    private static void loopSounds(Minecraft mc) {
        for (Player p : mc.level.players()) {
            if (!SupermanState.isSuperman(p)) {
                continue;
            }
            if (SupermanState.flag(p, SupermanPowers.F_LASER)) {
                loop(mc, p, SupermanPowers.F_LASER, ModSounds.HEAT_BLAST_LOOP.get());
            } else {
                loop(mc, p, SupermanPowers.F_HEAT, ModSounds.HEAT_LOOP.get());
            }
            loop(mc, p, SupermanPowers.F_FREEZE, ModSounds.COLD_BREATH_LOOP.get());
            loop(mc, p, SupermanPowers.F_BREATH, ModSounds.SUPER_BREATH_LOOP.get());
        }
        LOOPS.values().removeIf(Loop::isStopped);
    }

    private static void loop(Minecraft mc, Player p, int flag, SoundEvent sound) {
        if (!SupermanState.flag(p, flag)) {
            return;
        }
        String id = p.getUUID() + "/" + flag;
        Loop current = LOOPS.get(id);
        if (current == null || current.isStopped()) {
            Loop l = new Loop(sound, p, flag);
            LOOPS.put(id, l);
            mc.getSoundManager().play(l);
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Player player;
        private final int flag;

        Loop(SoundEvent sound, Player player, int flag) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.flag = flag;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.9F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            boolean replaced = this.flag == SupermanPowers.F_HEAT && SupermanState.flag(this.player, SupermanPowers.F_LASER);
            if (this.player.isRemoved() || !SupermanState.flag(this.player, this.flag) || replaced) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SupermanState.resetClient();
        SupermanPose.reset();
        SupermanLimbs.reset();
        LOOPS.clear();
        xray = false;
        chargeStart = -1L;
    }

    // ------------------------------------------------------------------
    // Rendering: heat vision beams and x-ray
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            renderBeams(event, mc, level);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && xray
                && SupermanState.isSuperman(mc.player)) {
            renderXray(event, mc, level);
        }
    }

    private static final Map<java.util.UUID, Vec3> BEAM_ENDS = new HashMap<>();

    /** Where the heat vision of a player hit this frame (null when not firing). */
    public static Vec3 beamEnd(java.util.UUID player) {
        return BEAM_ENDS.get(player);
    }

    /** One heat vision of this frame: both eye points, where it hits, laser mode, seen in first person. */
    public record Beam(Player player, Vec3 rightEye, Vec3 leftEye, Vec3 stop, boolean laser, boolean firstPerson) {
    }

    private static final List<Beam> BEAMS = new ArrayList<>();

    /** The heat vision beams of this frame (filled after the entities are drawn). */
    public static List<Beam> beams() {
        return BEAMS;
    }

    private static void renderBeams(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        BEAM_ENDS.clear();
        BEAMS.clear();
        List<Player> users = new ArrayList<>();
        for (Player p : level.players()) {
            if (SupermanState.flag(p, SupermanPowers.F_HEAT) && SupermanState.isSuperman(p)) {
                users.add(p);
            }
        }
        if (users.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();
        float flicker = 0.85F + 0.15F * (float) Math.sin((level.getGameTime() + partial) * 2.3F);
        for (Player p : users) {
            Vec3 eye = p.getEyePosition(partial);
            Vec3 look = p.getViewVector(partial);
            boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
            side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
            // first person: the beams start low and to the sides of the view so both stay visible
            Vec3 camUp = side.cross(look).normalize();
            Vec3 start = firstPerson ? eye.add(look.scale(0.5D)).subtract(camUp.scale(0.2D))
                    : eye.add(look.scale(0.22D)).add(0.0D, 0.02D, 0.0D);
            Vec3 end = eye.add(look.scale(64.0D));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, stop, box,
                    e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(stop));
            Vec3 normal = null;
            if (hit != null) {
                stop = hit.getLocation();
            } else if (block.getType() == HitResult.Type.BLOCK) {
                normal = Vec3.atLowerCornerOf(block.getDirection().getNormal());
                // burnt trail on the surface it sweeps
                SupermanScorch.paint(p.getUUID(), stop, normal, SupermanState.flag(p, SupermanPowers.F_LASER));
            }
            BEAM_ENDS.put(p.getUUID(), stop);
            boolean laser = SupermanState.flag(p, SupermanPowers.F_LASER);
            float thick = laser ? 1.8F : 1.0F;
            double spread = firstPerson ? 0.15D : 0.065D;
            BEAMS.add(new Beam(p, start.add(side.scale(-spread)), start.add(side.scale(spread)), stop, laser,
                    firstPerson));
            // the red ribbon is always drawn, also for the one firing in first person (the shader beam alone could
            // stay invisible to him)
            for (int s = -1; s <= 1; s += 2) {
                List<Vec3> points = new ArrayList<>();
                Vec3 from = start.add(side.scale(s * spread));
                int segments = 12;
                for (int i = 0; i <= segments; i++) {
                    points.add(from.add(stop.subtract(from).scale(i / (double) segments)));
                }
                FlashLightningRibbon.drawArc(consumer, matrix, cam, points, 0.11F * thick, 0.05F * thick,
                        0.018F * thick,
                        FlashLightningRibbon.RED_GLOW_COLOR, FlashLightningRibbon.RED_MID_COLOR,
                        FlashLightningRibbon.RED_CORE_COLOR, flicker);
            }
        }
        pose.popPose();
        buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
    }

    private static void renderXray(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        List<Entity> targets = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof LivingEntity && e != mc.player && e.isAlive()
                    && e.distanceToSqr(mc.player) < 64.0D * 64.0D) {
                targets.add(e);
            }
        }
        if (targets.isEmpty()) {
            pose.popPose();
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Entity e : targets) {
            box(buf, m, bounds(e, partial), color(e), 0.22F, true);
        }
        tess.end();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (Entity e : targets) {
            box(buf, m, bounds(e, partial), color(e), 0.9F, false);
        }
        tess.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static AABB bounds(Entity e, float partial) {
        Vec3 at = e.getPosition(partial);
        return e.getBoundingBox().move(at.subtract(e.position()));
    }

    private static int color(Entity e) {
        if (e instanceof Player) {
            return 0x4AA8FF;
        }
        if (e instanceof Enemy) {
            return 0xFF4040;
        }
        return 0x52E06A;
    }

    private static void box(BufferBuilder b, Matrix4f m, AABB a, int rgb, float alpha, boolean faces) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float bl = (rgb & 0xFF) / 255.0F;
        float x0 = (float) a.minX, y0 = (float) a.minY, z0 = (float) a.minZ;
        float x1 = (float) a.maxX, y1 = (float) a.maxY, z1 = (float) a.maxZ;
        if (faces) {
            float[][] q = {
                    {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                    {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                    {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                    {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                    {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                    {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
            for (float[] f : q) {
                for (int i = 0; i < 12; i += 3) {
                    b.vertex(m, f[i], f[i + 1], f[i + 2]).color(r, g, bl, alpha).endVertex();
                }
            }
            return;
        }
        float[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z0, x1, y1, z0}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (float[] e : edges) {
            b.vertex(m, e[0], e[1], e[2]).color(r, g, bl, alpha).endVertex();
            b.vertex(m, e[3], e[4], e[5]).color(r, g, bl, alpha).endVertex();
        }
    }
}

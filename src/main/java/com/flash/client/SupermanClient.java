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
            Vec3 start = firstPerson ? eye.add(look.scale(0.35D)).subtract(0.0D, 0.12D, 0.0D)
                    : eye.add(look.scale(0.22D)).add(0.0D, 0.02D, 0.0D);
            Vec3 end = eye.add(look.scale(64.0D));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, stop, box,
                    e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(stop));
            if (hit != null) {
                stop = hit.getLocation();
            }
            BEAM_ENDS.put(p.getUUID(), stop);
            boolean laser = SupermanState.flag(p, SupermanPowers.F_LASER);
            float thick = laser ? 1.8F : 1.0F;
            double spread = firstPerson ? 0.09D : 0.065D;
            BEAMS.add(new Beam(p, start.add(side.scale(-spread)), start.add(side.scale(spread)), stop, laser,
                    firstPerson));
            if (SupermanShaders.ready()) {
                // drawn by the pack shaders at the end of the level render
                continue;
            }
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

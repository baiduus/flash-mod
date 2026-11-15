package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroActionPacket;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.network.SpiderLinePacket;
import com.flash.spider.SpiderAbilities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Spider-Man on the client: who he is and his suit, the keys of his modes (R / Z), his locomotion (moved here, as
 * every player's own motion is) and what is drawn of him:
 * <ul>
 * <li><b>Web swing</b> (the flight key held): a line shot up ahead fastens to a block, and he swings under it like
 * a pendulum (gravity pulls him, the line holds him, W pumps the swing); let go, he is flung on with the
 * momentum.</li>
 * <li><b>Wall crawling</b>: walking into a wall he climbs it (W / space up, sneak down, still: he clings);
 * S + space jumps off it.</li>
 * <li>The web lines (Legends: Superheroes' web texture), the cocoons of the webbed (its web cocoon), and the
 * spider-sense over his head (its squiggles).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpiderClient {

    private static final ResourceLocation WEB = new ResourceLocation(FlashMod.MODID, "textures/spider/web_line.png");
    private static final ResourceLocation COCOON = new ResourceLocation(FlashMod.MODID,
            "textures/spider/web_cocoon.png");
    private static final ResourceLocation SENSE = new ResourceLocation(FlashMod.MODID,
            "textures/spider/spider_sense.png");

    /** Spider-Men around: entity -> suit. */
    private static final Map<Integer, Integer> SPIDERS = new HashMap<>();
    /** The swing lines of the others: entity -> where it is fastened. */
    private static final Map<Integer, Vec3> LINES = new HashMap<>();
    /** The webbed: entity -> game time it breaks free. */
    private static final Map<Integer, Long> WEBBED = new HashMap<>();
    /** The spider-sense showing: entity -> [game time it started, strength]. */
    private static final Map<Integer, long[]> SENSES = new HashMap<>();
    /** The flying kick: entity -> game time it ends (for the pose). */
    private static final Map<Integer, Long> KICKS = new HashMap<>();

    /** A web strand shot (a moment on screen). */
    private record Strand(int caster, Vec3 from, Vec3 to, int target, long born, int life, boolean fromHand) {
    }

    private static final List<Strand> STRANDS = new ArrayList<>();

    private SpiderClient() {
    }

    public static void reset() {
        SPIDERS.clear();
        LINES.clear();
        WEBBED.clear();
        SENSES.clear();
        KICKS.clear();
        STRANDS.clear();
        anchor = null;
        clinging = false;
    }

    public static void set(int entity, boolean has, int suit) {
        if (has) {
            SPIDERS.put(entity, suit);
        } else {
            SPIDERS.remove(entity);
            LINES.remove(entity);
        }
    }

    public static boolean isSpider(Entity e) {
        return SPIDERS.containsKey(e.getId());
    }

    public static int suit(Entity e) {
        return SPIDERS.getOrDefault(e.getId(), 0);
    }

    /** The local player on a web line now. */
    public static boolean swinging() {
        return anchor != null;
    }

    /** Where this player's swing line is fastened (null: none). */
    public static Vec3 line(Player p) {
        if (p == Minecraft.getInstance().player) {
            return anchor;
        }
        return LINES.get(p.getId());
    }

    /** Clinging to (or climbing) a wall: for the local player his own state, for the others a guess from where they are. */
    public static boolean clinging(Player p) {
        if (!isSpider(p) || p.onGround() || p.isPassenger() || line(p) != null) {
            return false;
        }
        if (p == Minecraft.getInstance().player) {
            return clinging;
        }
        double vy = p.getY() - p.yo;
        return vy > -0.05D && wallBeside(p);
    }

    public static boolean kicking(Player p) {
        Long until = KICKS.get(p.getId());
        return until != null && p.level().getGameTime() < until;
    }

    private static boolean wallBeside(Player p) {
        AABB box = p.getBoundingBox();
        return !p.level().noCollision(p, new AABB(box.minX - 0.12D, box.minY + 0.15D, box.minZ - 0.12D,
                box.maxX + 0.12D, box.maxY - 0.1D, box.maxZ + 0.12D));
    }

    // ------------------------------------------------------------------
    // The keys of his modes
    // ------------------------------------------------------------------

    private static boolean useWas;

    /** Every tick from YasAbilities with his mode now (null: not him). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "webs" -> {
                if (pressed) {
                    use("web_shot", SpiderAbilities.WEB_SHOT);
                }
                if (variant) {
                    use("web_net", SpiderAbilities.WEB_NET);
                }
            }
            case "pull" -> {
                if (pressed) {
                    use("yank", SpiderAbilities.YANK);
                }
                if (variant) {
                    use("zip", SpiderAbilities.ZIP);
                }
            }
            case "combat" -> {
                if (pressed) {
                    use("spider_kick", SpiderAbilities.KICK);
                }
                if (variant) {
                    use("sense", SpiderAbilities.SENSE);
                }
            }
            default -> {
            }
        }
    }

    private static void use(String cd, int action) {
        if (ScarletClient.coolingDown(cd)) {
            ScarletClient.denied(cd);
        } else {
            send(action, 0);
        }
    }

    public static void send(int action, int value) {
        FlashNetwork.CHANNEL.sendToServer(new HeroActionPacket(HeroSyncPacket.SPIDER, action, value));
    }

    /** The suit picked in the K menu (shown at once, kept by the server). */
    public static void pickSuit(int index) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && isSpider(p)) {
            SPIDERS.put(p.getId(), index);
            send(SpiderAbilities.SUIT, index);
        }
    }

    // ------------------------------------------------------------------
    // His locomotion: the swing and the walls
    // ------------------------------------------------------------------

    private static Vec3 anchor;
    private static double rope;
    private static int swingTicks;
    private static boolean swingWas;
    private static boolean clinging;
    private static boolean jumpWas;

    private static KeyMapping swingKey() {
        return FlashAbilityKeys.mapping(com.flash.ability.FlashAbility.WIND_HANDS);
    }

    private static void locomotion(Minecraft mc, LocalPlayer p) {
        boolean able = isSpider(p) && !p.isSpectator() && !p.isPassenger() && !p.getAbilities().flying
                && !p.isSleeping() && p.isAlive();
        if (p.getAbilities().flying && isSpider(p) && !p.isCreative()) {
            // he does not fly: the "may fly" is only for the long swings
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        KeyMapping key = swingKey();
        boolean down = able && key != null && key.isDown() && mc.screen == null;
        if (down && !swingWas && anchor == null) {
            attach(p);
        } else if (!down && anchor != null) {
            release(p, true);
        }
        swingWas = down;
        if (!able) {
            if (anchor != null) {
                release(p, false);
            }
            clinging = false;
            return;
        }
        if (anchor != null) {
            swing(p);
            clinging = false;
        } else {
            crawl(p);
        }
        jumpWas = p.input.jumping;
    }

    /** A block to fasten the line to: ahead and up (the higher the better for a swing). */
    private static Vec3 findAnchor(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        float[] raise = {0.0F, 18.0F, 34.0F, 50.0F, 64.0F};
        for (float r : raise) {
            float pp = Math.max(-80.0F, Math.min(pitch, 10.0F) - r);
            Vec3 dir = Vec3.directionFromRotation(pp, yaw);
            HitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(72.0D)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y > p.getY() + 2.5D) {
                return hit.getLocation();
            }
        }
        return null;
    }

    private static void attach(LocalPlayer p) {
        Vec3 at = findAnchor(p);
        if (at == null) {
            p.playSound(net.minecraft.sounds.SoundEvents.FISHING_BOBBER_THROW, 0.4F, 0.6F);
            return;
        }
        anchor = at;
        Vec3 chest = p.position().add(0.0D, 1.1D, 0.0D);
        rope = Math.max(4.0D, chest.distanceTo(at) * 0.94D);
        swingTicks = 0;
        if (p.onGround()) {
            p.setDeltaMovement(p.getDeltaMovement().add(0.0D, 0.55D, 0.0D));
        }
        p.playSound(net.minecraft.sounds.SoundEvents.FISHING_BOBBER_THROW, 0.8F, 1.5F);
        FlashNetwork.CHANNEL.sendToServer(new SpiderLinePacket(true, at));
    }

    private static void release(LocalPlayer p, boolean fling) {
        if (fling && !p.onGround()) {
            // let go at the bottom of the arc: flung on, a little up
            Vec3 look = p.getLookAngle();
            Vec3 v = p.getDeltaMovement().add(look.x * 0.25D, 0.32D, look.z * 0.25D);
            if (v.length() > 2.8D) {
                v = v.normalize().scale(2.8D);
            }
            p.setDeltaMovement(v);
        }
        anchor = null;
        FlashNetwork.CHANNEL.sendToServer(new SpiderLinePacket(false, Vec3.ZERO));
    }

    private static void swing(LocalPlayer p) {
        swingTicks++;
        Vec3 chest = p.position().add(0.0D, 1.1D, 0.0D);
        Vec3 d = anchor.subtract(chest);
        double dist = d.length();
        if (dist > 96.0D || swingTicks > 600 || (p.onGround() && swingTicks > 6)
                || p.level().getBlockState(BlockPos.containing(anchor.add(d.normalize().scale(0.05D)))).isAir()
                && p.level().getBlockState(BlockPos.containing(anchor)).isAir()) {
            release(p, false);
            return;
        }
        // the line reels in a little: it lifts him through the arc
        rope = Math.max(3.5D, rope - 0.035D);
        Vec3 v = p.getDeltaMovement();
        Vec3 r = d.scale(1.0D / dist);
        if (dist > rope) {
            double out = v.dot(r);
            if (out < 0.0D) {
                v = v.subtract(r.scale(out));
            }
            v = v.add(r.scale(Math.min(0.55D, (dist - rope) * 0.3D)));
        }
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);
        if (p.input.forwardImpulse > 0.0F) {
            v = v.add(flat.scale(0.05D));
        } else if (p.input.forwardImpulse < 0.0F) {
            v = v.scale(0.97D);
        }
        v = v.add(right.scale(-p.input.leftImpulse * 0.025D));
        if (v.length() > 2.6D) {
            v = v.normalize().scale(2.6D);
        }
        p.setDeltaMovement(v);
        p.fallDistance = 0.0F;
        if (swingTicks % 2 == 0 && v.lengthSqr() > 0.6D) {
            p.level().addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.4D, p.getZ(), -v.x * 0.05D, 0.0D,
                    -v.z * 0.05D);
        }
    }

    private static void crawl(LocalPlayer p) {
        if (p.isInWater() || p.isInLava() || p.onClimbable()) {
            clinging = false;
            return;
        }
        boolean wall = wallBeside(p);
        float forward = p.input.forwardImpulse;
        boolean jump = p.input.jumping;
        if (!clinging) {
            // walking into a wall: up it he goes
            if (wall && p.horizontalCollision && (forward > 0.0F || !p.onGround())) {
                clinging = true;
            } else {
                return;
            }
        }
        if (!wall || (p.onGround() && forward <= 0.0F && !jump)) {
            clinging = false;
            return;
        }
        Vec3 v = p.getDeltaMovement();
        if (forward < 0.0F && jump && !jumpWas) {
            // a jump off the wall, backwards
            Vec3 back = p.getLookAngle().multiply(-1.0D, 0.0D, -1.0D);
            back = back.lengthSqr() < 1.0E-4D ? Vec3.ZERO : back.normalize();
            p.setDeltaMovement(back.scale(0.7D).add(0.0D, 0.55D, 0.0D));
            clinging = false;
            return;
        }
        double vy;
        if (jump || (forward > 0.0F && p.horizontalCollision)) {
            vy = 0.26D;
        } else if (p.input.shiftKeyDown) {
            vy = -0.16D;
        } else {
            vy = 0.0D;
        }
        p.setDeltaMovement(v.x * 0.7D, vy, v.z * 0.7D);
        p.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            return;
        }
        if (mc.player != null && !mc.isPaused()) {
            locomotion(mc, mc.player);
        }
        long now = mc.level.getGameTime();
        WEBBED.values().removeIf(t -> t < now);
        KICKS.values().removeIf(t -> t < now);
        SENSES.values().removeIf(s -> now - s[0] > 30L);
        STRANDS.removeIf(s -> now - s.born > s.life);
        LINES.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player p) || !isSpider(p));
        // a few threads of web on the webbed
        if (now % 6 == 0) {
            for (int id : WEBBED.keySet()) {
                Entity e = mc.level.getEntity(id);
                if (e != null) {
                    mc.level.addParticle(ParticleTypes.WHITE_ASH, e.getX(), e.getY() + e.getBbHeight() * 0.5D,
                            e.getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // What the server sends
    // ------------------------------------------------------------------

    public static void fx(HeroFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        switch (p.type()) {
            case SpiderAbilities.FX_SHOT -> {
                STRANDS.add(new Strand(p.caster(), p.from(), p.to(), -1, now, 5, true));
                if (p.power() > 0.0F) {
                    splat(mc, p.to(), 8);
                }
            }
            case SpiderAbilities.FX_NET -> {
                // a fan of strands thrown ahead
                Vec3 dir = p.to();
                Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 up = side.cross(dir).normalize();
                for (int k = 0; k < 9; k++) {
                    double a = (k - 4) / 4.0D * 0.65D;
                    double b = ((k * 37) % 5 - 2) / 2.0D * 0.25D;
                    Vec3 d = dir.add(side.scale(Math.tan(a))).add(up.scale(b)).normalize();
                    STRANDS.add(new Strand(p.caster(), p.from(), p.from().add(d.scale(12.0D)), -1, now, 8, true));
                }
            }
            case SpiderAbilities.FX_YANK -> STRANDS.add(new Strand(p.caster(), p.from(), p.to(), p.target(), now, 9,
                    true));
            case SpiderAbilities.FX_ZIP -> STRANDS.add(new Strand(p.caster(), p.from(), p.to(), -1, now, 10, true));
            case SpiderAbilities.FX_LINE -> {
                if (mc.player == null || p.caster() != mc.player.getId()) {
                    LINES.put(p.caster(), p.from());
                }
            }
            case SpiderAbilities.FX_LINE_OFF -> LINES.remove(p.caster());
            case SpiderAbilities.FX_WEBBED -> {
                WEBBED.put(p.target(), now + (long) p.power());
                splat(mc, p.from().add(0.0D, 0.8D, 0.0D), 14);
            }
            case SpiderAbilities.FX_SENSE -> {
                SENSES.put(p.caster(), new long[] {now, (long) p.power()});
                if (p.power() >= 2.0F) {
                    for (int i = 0; i < 24; i++) {
                        double a = i / 24.0D * Math.PI * 2.0D;
                        mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.from().x + Math.cos(a) * 0.6D,
                                p.from().y + 2.1D, p.from().z + Math.sin(a) * 0.6D, Math.cos(a) * 0.12D, 0.05D,
                                Math.sin(a) * 0.12D);
                    }
                }
            }
            case SpiderAbilities.FX_KICK -> {
                KICKS.put(p.caster(), now + 12L);
                for (int i = 0; i < 10; i++) {
                    mc.level.addParticle(ParticleTypes.CLOUD, p.from().x, p.from().y + 0.4D, p.from().z,
                            -p.to().x * 0.2D + (Math.random() - 0.5D) * 0.1D, 0.02D,
                            -p.to().z * 0.2D + (Math.random() - 0.5D) * 0.1D);
                }
            }
            case SpiderAbilities.FX_BITE -> {
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0D * Math.PI * 2.0D;
                    double h = (i % 8) * 0.25D;
                    mc.level.addParticle(ParticleTypes.WITCH, p.from().x + Math.cos(a) * 0.7D, p.from().y + h,
                            p.from().z + Math.sin(a) * 0.7D, 0.0D, 0.04D, 0.0D);
                }
            }
            case SpiderAbilities.FX_DODGE -> {
                SENSES.put(p.caster(), new long[] {now, 1L});
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(ParticleTypes.POOF, p.from().x, p.from().y + 1.0D, p.from().z,
                            -p.to().x * 0.15D, 0.02D, -p.to().z * 0.15D);
                }
            }
            default -> {
            }
        }
    }

    private static void splat(Minecraft mc, Vec3 at, int n) {
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, (Math.random() - 0.5D) * 0.3D,
                    Math.random() * 0.2D, (Math.random() - 0.5D) * 0.3D);
            mc.level.addParticle(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Drawing: the lines, the cocoons, the sense
    // ------------------------------------------------------------------

    /** Where his web comes out: the raised right wrist (in first person, from the right of the view). */
    private static Vec3 wrist(Minecraft mc, Player p, float pt) {
        boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (me) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 look = new Vec3(cam.getLookVector());
            Vec3 left = new Vec3(cam.getLeftVector());
            Vec3 up = new Vec3(cam.getUpVector());
            return cam.getPosition().add(look.scale(0.6D)).add(left.scale(-0.32D)).add(up.scale(-0.22D));
        }
        Vec3 base = p.getPosition(pt);
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        double side = -0.36D;
        boolean raised = line(p) != null;
        double h = raised ? 2.05D : 1.35D;
        double fwd = raised ? 0.05D : 0.35D;
        return base.add(-Math.sin(yaw) * fwd + Math.cos(yaw) * side, h, Math.cos(yaw) * fwd + Math.sin(yaw) * side);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SPIDERS.isEmpty() && WEBBED.isEmpty() && STRANDS.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        long now = mc.level.getGameTime();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // the lines
        VertexConsumer web = buffers.getBuffer(RenderType.entityCutoutNoCull(WEB));
        for (Player p : mc.level.players()) {
            Vec3 at = line(p);
            if (at != null && isSpider(p)) {
                Vec3 from = wrist(mc, p, pt);
                strand(web, m, n, from, at, cam, 0.045F, light(mc, from));
            }
        }
        for (Strand s : STRANDS) {
            float age = now - s.born + pt;
            Entity caster = mc.level.getEntity(s.caster);
            Vec3 from = s.fromHand && caster instanceof Player cp ? wrist(mc, cp, pt) : s.from;
            Vec3 to = s.to;
            Entity t = s.target >= 0 ? mc.level.getEntity(s.target) : null;
            if (t != null) {
                to = t.getPosition(pt).add(0.0D, t.getBbHeight() * 0.6D, 0.0D);
            }
            // shot out: it reaches the end in two ticks, then hangs a moment
            float reach = Math.min(1.0F, age / 2.0F);
            Vec3 end = from.add(to.subtract(from).scale(reach));
            strand(web, m, n, from, end, cam, 0.04F, light(mc, from));
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(WEB));
        // the cocoons
        if (!WEBBED.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(COCOON));
            for (Map.Entry<Integer, Long> en : WEBBED.entrySet()) {
                Entity e = mc.level.getEntity(en.getKey());
                if (e == null || e.isRemoved()) {
                    continue;
                }
                Vec3 pos = e.getPosition(pt);
                AABB box = e.getBoundingBox().move(pos.subtract(e.position())).inflate(0.12D);
                cocoon(vc, m, n, box, light(mc, pos.add(0.0D, 0.5D, 0.0D)));
            }
            buffers.endBatch(RenderType.entityCutoutNoCull(COCOON));
        }
        // the spider-sense over their heads
        if (!SENSES.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SENSE));
            Vec3 left = new Vec3(camera.getLeftVector());
            Vec3 up = new Vec3(camera.getUpVector());
            for (Map.Entry<Integer, long[]> en : SENSES.entrySet()) {
                Entity e = mc.level.getEntity(en.getKey());
                if (e == null || e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
                    continue;
                }
                float age = now - en.getValue()[0] + pt;
                float big = en.getValue()[1] >= 2 ? 1.35F : 1.0F;
                float a = Math.min(1.0F, age / 3.0F) * Math.max(0.0F, 1.0F - (age - 18.0F) / 12.0F);
                float jitter = (((int) age) % 2 == 0) ? 0.03F : -0.03F;
                Vec3 c = e.getPosition(pt).add(jitter, e.getBbHeight() + 0.42D * big, 0.0D);
                billboard(vc, m, n, c, left, up, 0.5F * big, 0.42F * big, a);
            }
            buffers.endBatch(RenderType.entityTranslucent(SENSE));
        }
        ps.popPose();
    }

    private static int light(Minecraft mc, Vec3 at) {
        return LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
    }

    /** A strand of web from a to b, facing the camera, its texture repeated every block. */
    private static void strand(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 cam, float w,
                               int light) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3D) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 side = d.cross(mid.subtract(cam));
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize().scale(w);
        int seg = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i < seg; i++) {
            Vec3 p0 = a.add(d.scale(i / (double) seg));
            Vec3 p1 = a.add(d.scale((i + 1) / (double) seg));
            quad(vc, m, n, p0.subtract(side), p0.add(side), p1.add(side), p1.subtract(side), 0.0F, 0.0F, 4.0F / 16.0F,
                    15.0F / 16.0F, light, 1.0F);
        }
    }

    /** The web cocoon wrapped around a box. */
    private static void cocoon(VertexConsumer vc, Matrix4f m, Matrix3f n, AABB b, int light) {
        float u1 = 54.0F / 64.0F;
        float v1 = 63.0F / 64.0F;
        double x0 = b.minX;
        double y0 = b.minY;
        double z0 = b.minZ;
        double x1 = b.maxX;
        double y1 = b.maxY;
        double z1 = b.maxZ;
        quad(vc, m, n, new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x0, y1, z0), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x0, y0, z1), new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), 0, 0,
                u1, v1, light, 1.0F);
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, Vec3 left, Vec3 up, float hw,
                                  float hh, float a) {
        Vec3 l = left.scale(hw);
        Vec3 u = up.scale(hh);
        quad(vc, m, n, c.add(l).subtract(u), c.subtract(l).subtract(u), c.subtract(l).add(u), c.add(l).add(u), 0.0F,
                0.0F, 1.0F, 1.0F, 0xF000F0, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0,
                             float v0, float u1, float v1, int light, float alpha) {
        vert(vc, m, n, a, u0, v1, light, alpha);
        vert(vc, m, n, b, u1, v1, light, alpha);
        vert(vc, m, n, c, u1, v0, light, alpha);
        vert(vc, m, n, d, u0, v0, light, alpha);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int light,
                             float alpha) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** First person: the spider-sense at the top of the screen when it tingles. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || !event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id())
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        long[] s = SENSES.get(mc.player.getId());
        if (s == null) {
            return;
        }
        float age = mc.level.getGameTime() - s[0] + event.getPartialTick();
        float a = Math.min(1.0F, age / 3.0F) * Math.max(0.0F, 1.0F - (age - 18.0F) / 12.0F);
        if (a <= 0.01F) {
            return;
        }
        int w = s[1] >= 2 ? 96 : 72;
        int h = w * 216 / 256;
        int x = (event.getWindow().getGuiScaledWidth() - w) / 2 + ((((int) age) % 2 == 0) ? 1 : -1);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, a);
        event.getGuiGraphics().blit(SENSE, x, 6, 0, 0, w, h, w, h);
        event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** Remote lines whose player is gone. */
    public static void forget(int entity) {
        for (Iterator<Map.Entry<Integer, Vec3>> it = LINES.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getKey() == entity) {
                it.remove();
            }
        }
    }
}

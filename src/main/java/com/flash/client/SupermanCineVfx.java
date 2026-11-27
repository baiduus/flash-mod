package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.SupermanFxPacket;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Superman as in the films, drawn in light (VfxDraw): heat vision as two white-hot filaments in a scarlet corona
 * with plasma winding round them, pulses racing down and a molten splash where they hit; the freezing and the super
 * breath as rushing cones of frost or wind; sonic booms, vapour rings and a mach cone at top speed with a streak of
 * air behind him; shockwaves on the ground, the solar blast and the Ultimate's sun with its pillars of sunlight.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanCineVfx {

    private static final float[] HEAT = {1.0F, 0.1F, 0.03F};
    private static final float[] HEAT_HOT = {1.0F, 0.48F, 0.14F};
    private static final float[] HEAT_WHITE = {1.0F, 0.95F, 0.86F};
    private static final float[] SKY = {0.62F, 0.8F, 1.0F};
    private static final float[] SUN = {1.0F, 0.68F, 0.18F};
    private static final float[] SUN_HOT = {1.0F, 0.9F, 0.55F};
    private static final float[] FROST = {0.5F, 0.84F, 1.0F};

    /** Kinds of this class's own (after SupermanFxPacket's). */
    private static final int TAKEOFF = 100;
    private static final int VAPOR = 101;

    private static final class Fx {
        final int kind;
        final Vec3 pos;
        final Vec3 dir;
        final float scale;
        final long seed;
        final int life;
        float age;

        Fx(int kind, Vec3 pos, Vec3 dir, float scale) {
            this.kind = kind;
            this.pos = pos;
            this.dir = dir;
            this.scale = scale;
            this.seed = (long) (pos.x * 41.0D + pos.z * 17.0D) * 4099L ^ System.nanoTime();
            this.life = switch (kind) {
                case SupermanFxPacket.SOLAR_BLAST -> 40;
                case SupermanFxPacket.SHOCKWAVE -> 24;
                case TAKEOFF -> 20;
                case VAPOR -> 16;
                default -> 14;
            };
        }
    }

    private static final class Track {
        final Deque<Vec3> trail = new ArrayDeque<>();
        boolean fast;
        boolean flying;
        long ultStart = -1L;
        int vapor;
    }

    private static final List<Fx> EFFECTS = new ArrayList<>();
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static float clock;

    private SupermanCineVfx() {
    }

    public static void reset() {
        EFFECTS.clear();
        TRACKS.clear();
    }

    /** A one-shot effect of the server's (SupermanFx.spawn). */
    public static void spawn(int kind, Vec3 pos, Vec3 dir, float scale) {
        if (EFFECTS.size() < 300) {
            EFFECTS.add(new Fx(kind, pos, dir, scale));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (kind == SupermanFxPacket.SHOCKWAVE || kind == SupermanFxPacket.SOLAR_BLAST) {
            int n = kind == SupermanFxPacket.SOLAR_BLAST ? 60 : 30;
            for (int i = 0; i < n; i++) {
                double th = Math.random() * Math.PI * 2.0D;
                double sp = 0.3D + Math.random() * 0.5D;
                mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 0.2D, pos.z, Math.cos(th) * sp
                        * 0.4D, 0.03D + Math.random() * 0.05D, Math.sin(th) * sp * 0.4D);
            }
        }
    }

    private static boolean flying(Minecraft mc, Player p) {
        return p == mc.player ? p.getAbilities().flying
                : SupermanState.flag(p, SupermanPowers.F_FLIGHT) && !p.onGround();
    }

    private static float ease(float k) {
        float u = 1.0F - Math.min(1.0F, Math.max(0.0F, k));
        return 1.0F - u * u * u;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        clock += 1.0F;
        long now = mc.level.getGameTime();
        for (Iterator<Fx> it = EFFECTS.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.age += 1.0F;
            if (f.age > f.life) {
                it.remove();
            }
        }
        for (Player p : mc.level.players()) {
            if (!SupermanState.isSuperman(p)) {
                TRACKS.remove(p.getUUID());
                continue;
            }
            Track t = TRACKS.computeIfAbsent(p.getUUID(), id -> new Track());
            Vec3 v = p.position().subtract(p.xo, p.yo, p.zo);
            boolean fly = flying(mc, p);
            if (fly && !t.flying && p.position().y - groundY(mc, p) < 3.0D) {
                spawn(TAKEOFF, new Vec3(p.getX(), groundY(mc, p) + 0.05D, p.getZ()), new Vec3(0.0D, 1.0D, 0.0D), 1.0F);
            }
            t.flying = fly;
            boolean fast = fly && v.length() > 0.9D;
            if (fast && !t.fast) {
                // breaking into top speed: the sonic boom
                spawn(SupermanFxPacket.RING, p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D), v.normalize(), 3.0F);
            }
            t.fast = fast;
            if (fast && ++t.vapor % 14 == 0) {
                spawn(VAPOR, p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D), v.normalize(), 1.6F);
            }
            if (fast) {
                t.trail.addFirst(p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D));
            } else if (!t.trail.isEmpty()) {
                t.trail.removeLast();
            }
            while (t.trail.size() > 20) {
                t.trail.removeLast();
            }
            boolean ult = SupermanState.flag(p, SupermanPowers.F_ULTIMATE);
            if (ult && t.ultStart < 0L) {
                t.ultStart = now;
            } else if (!ult) {
                t.ultStart = -1L;
            }
            // the breath: frost and wind in the air
            boolean freeze = SupermanState.flag(p, SupermanPowers.F_FREEZE);
            boolean breath = SupermanState.flag(p, SupermanPowers.F_BREATH);
            if (freeze || breath) {
                Vec3 mouth = mouth(p, 1.0F);
                Vec3 look = p.getViewVector(1.0F);
                for (int i = 0; i < 3; i++) {
                    Vec3 d = look.scale(0.6D + Math.random() * 0.6D).add((Math.random() - 0.5D) * 0.25D,
                            (Math.random() - 0.5D) * 0.25D, (Math.random() - 0.5D) * 0.25D);
                    mc.level.addParticle(freeze ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD, mouth.x, mouth.y,
                            mouth.z, d.x, d.y, d.z);
                }
            }
            // where the heat vision burns: smoke and embers
            if (SupermanState.flag(p, SupermanPowers.F_HEAT)) {
                Vec3 hit = SupermanClient.beamEnd(p.getUUID());
                if (hit != null && now % 2L == 0L) {
                    mc.level.addParticle(ParticleTypes.SMOKE, hit.x, hit.y, hit.z, 0.0D, 0.06D, 0.0D);
                    mc.level.addParticle(ParticleTypes.SMALL_FLAME, hit.x, hit.y, hit.z, (Math.random() - 0.5D)
                            * 0.1D, 0.05D, (Math.random() - 0.5D) * 0.1D);
                }
            }
        }
    }

    private static double groundY(Minecraft mc, Player p) {
        net.minecraft.core.BlockPos.MutableBlockPos pos = p.blockPosition().mutable();
        for (int i = 0; i < 6; i++) {
            if (!mc.level.getBlockState(pos.below()).isAir()) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return p.getY() - 6.0D;
    }

    private static Vec3 mouth(Player p, float pt) {
        Vec3 look = p.getViewVector(pt);
        return p.getEyePosition(pt).add(look.scale(0.32D)).add(0.0D, -0.16D, 0.0D);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        float pt = event.getPartialTick();
        float t = clock + pt;
        long now = mc.level.getGameTime();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        BufferBuilder buf = VfxDraw.begin();
        try {
            for (Fx f : EFFECTS) {
                oneShot(buf, m, f, pt, t, cam, left, up);
            }
            for (SupermanClient.Beam b : SupermanClient.beams()) {
                heatVision(buf, m, mc, b, t, left, up);
            }
            for (Player p : mc.level.players()) {
                if (!SupermanState.isSuperman(p) || p.isInvisible()) {
                    continue;
                }
                Track tr = TRACKS.get(p.getUUID());
                boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                if (SupermanState.flag(p, SupermanPowers.F_FREEZE)) {
                    breath(buf, m, p, pt, t, left, up, true);
                } else if (SupermanState.flag(p, SupermanPowers.F_BREATH)) {
                    breath(buf, m, p, pt, t, left, up, false);
                }
                if (tr != null && tr.ultStart >= 0L) {
                    ultimate(buf, m, p, pt, t, (now - tr.ultStart + pt) / 100.0F, cam, left, up, me);
                }
                if (tr != null && tr.trail.size() > 1) {
                    flight(buf, m, p, pt, t, tr, left, up, me);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    private static void oneShot(BufferBuilder buf, Matrix4f m, Fx f, float pt, float t, Vec3 cam, Vec3 left,
                                Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / f.life);
        float fade = 1.0F - k;
        Vec3 vup = new Vec3(0.0D, 1.0D, 0.0D);
        switch (f.kind) {
            case SupermanFxPacket.RING -> {
                // a sonic boom: the shock ring, a second fainter one, the vapour cone and the flash
                double s = Math.max(0.8D, f.scale);
                VfxDraw.ring(buf, m, f.pos, f.dir, s * (0.4D + 1.6D * ease(k)), 0.06D * s, 0.0F, 0.9F, 0.94F, 1.0F,
                        0.3F * fade);
                VfxDraw.ring(buf, m, f.pos.subtract(f.dir.scale(0.4D * k)), f.dir, s * (0.3D + 1.2D * ease(k)),
                        0.25D * s, 0.0F, 0.85F, 0.9F, 0.95F, 0.12F * fade);
            }
            case VAPOR -> VfxDraw.ring(buf, m, f.pos.subtract(f.dir.scale(age * 0.3D)), f.dir,
                    f.scale * (0.6D + 0.9D * ease(k)), 0.18D, 0.0F, 0.9F, 0.95F, 1.0F, 0.14F * fade);
            case TAKEOFF -> {
                VfxDraw.shockwave(buf, m, f.pos, 0.6D + 4.5D * ease(k), 0.9D * fade, SKY[0], SKY[1], SKY[2], fade);
                VfxDraw.flare(buf, m, f.pos.add(0.0D, 0.3D, 0.0D), left, up, 0.9D, SKY[0], SKY[1], SKY[2],
                        Math.max(0.0F, 1.0F - age / 5.0F));
            }
            case SupermanFxPacket.SHOCKWAVE -> {
                double s = Math.max(1.5D, f.scale);
                VfxDraw.shockwave(buf, m, f.pos, 0.5D + s * 1.6D * ease(k), 1.2D * fade, 1.0F, 0.92F, 0.8F, fade);
                float k2 = Math.max(0.0F, (age - 3.0F) / (f.life - 3.0F));
                VfxDraw.shockwave(buf, m, f.pos, 0.3D + s * 1.1D * ease(k2), 0.6D * (1.0F - k2), SKY[0], SKY[1],
                        SKY[2], 0.6F * (1.0F - k2));
                VfxDraw.flare(buf, m, f.pos.add(0.0D, 0.4D, 0.0D), left, up, 0.6D + s * 0.25D, 1.0F, 0.9F, 0.75F,
                        Math.max(0.0F, 1.0F - age / 5.0F));
                VfxDraw.sparks(buf, m, f.pos.add(0.0D, 0.2D, 0.0D), f.seed, 20, 1.5D + s * 0.6D, age / 16.0F, 1.2D,
                        1.0F, 0.85F, 0.6F, 1.0F);
            }
            case SupermanFxPacket.SOLAR_BLAST -> {
                double s = Math.max(3.0D, f.scale);
                Vec3 c = f.pos;
                VfxDraw.flare(buf, m, c, left, up, 2.0D + s * 0.3D, SUN[0], SUN[1], SUN[2],
                        Math.max(0.0F, 1.0F - age / 10.0F));
                float kk = Math.min(1.0F, age / 18.0F);
                VfxDraw.sphere(buf, m, c, s * ease(kk), cam, SUN[0], SUN[1], SUN[2], 0.75F * (1.0F - kk), t, 2.0D);
                VfxDraw.sphere(buf, m, c, s * 0.55D * ease(kk), cam, SUN_HOT[0], SUN_HOT[1], SUN_HOT[2],
                        0.5F * (1.0F - kk), t, 3.0D);
                VfxDraw.burst(buf, m, c, left, up, (float) (s * 1.4D * ease(kk)), SUN[0], SUN[1], SUN[2], fade,
                        t * 0.02F);
                VfxDraw.shockwave(buf, m, c.subtract(0.0D, 1.0D, 0.0D), s * 1.5D * ease(k), 2.0D * fade, SUN[0],
                        SUN[1], SUN[2], fade);
                for (int i = 0; i < 3; i++) {
                    double ph = i * 1.1D;
                    VfxDraw.ring(buf, m, c, new Vec3(Math.sin(ph), 1.0D, Math.cos(ph)), s * (0.5D + 0.9D * ease(kk)),
                            0.12D, t * 0.1F, SUN_HOT[0], SUN_HOT[1], SUN_HOT[2], 0.6F * fade);
                }
                VfxDraw.sparks(buf, m, c, f.seed, 60, s * 1.4D, age / 26.0F, 1.0D, SUN_HOT[0], SUN_HOT[1], SUN_HOT[2],
                        1.0F);
            }
            default -> {
            }
        }
    }

    /** Heat vision: per eye a white-hot filament in a scarlet corona, plasma winding round it, pulses down it. */
    private static void heatVision(BufferBuilder buf, Matrix4f m, Minecraft mc, SupermanClient.Beam b, float t,
                                   Vec3 left, Vec3 up) {
        float th = b.laser() ? 1.9F : 1.0F;
        float flick = 0.88F + 0.12F * (float) Math.sin(t * 2.9D) * (float) Math.sin(t * 1.3D);
        for (Vec3 eye : new Vec3[] {b.rightEye(), b.leftEye()}) {
            Vec3 stop = b.stop();
            double len = eye.distanceTo(stop);
            VfxDraw.beam(buf, m, eye, stop, 0.32F * th, HEAT[0], HEAT[1], HEAT[2], 0.1F * flick);
            VfxDraw.energy(buf, m, eye, stop, 0.07F * th, HEAT[0], HEAT[1], HEAT[2], 0.95F * flick);
            VfxDraw.beam(buf, m, eye, stop, 0.018F * th, 1.0F, 1.0F, 1.0F, flick);
            Vec3[] hx = VfxDraw.helix(eye, stop, 0.055D * th, Math.max(2.0D, len / 1.1D), t * 0.9D,
                    Math.max(16, Math.min(160, (int) (len * 6.0D))));
            VfxDraw.path(buf, m, hx, 0.022F * th, HEAT_HOT[0], HEAT_HOT[1], HEAT_HOT[2], 0.55F);
            // pulses of heat racing to the target
            Vec3 d = stop.subtract(eye);
            for (int i = 0; i < 4; i++) {
                double k = (t * 0.09D + i / 4.0D) % 1.0D;
                VfxDraw.glow(buf, m, eye.add(d.scale(k)), left, up, 0.1D * th, HEAT_WHITE[0], HEAT_WHITE[1],
                        HEAT_WHITE[2], 0.7F);
            }
            if (b.laser()) {
                Vec3 dn = d.normalize();
                for (int i = 0; i < 3; i++) {
                    double k = (t * 0.06D + i / 3.0D) % 1.0D;
                    VfxDraw.ring(buf, m, eye.add(d.scale(k)), dn, 0.12D + 0.1D * k, 0.025D, 0.0F, HEAT_HOT[0],
                            HEAT_HOT[1], HEAT_HOT[2], 0.6F * (float) (1.0D - k));
                }
            }
            if (!b.firstPerson()) {
                VfxDraw.flare(buf, m, eye, left, up, 0.16D * th, HEAT[0], HEAT[1], HEAT[2], flick);
            }
        }
        // the molten splash where they hit
        Vec3 hit = b.stop();
        float pulse = 0.85F + 0.15F * (float) Math.sin(t * 1.7D);
        VfxDraw.flare(buf, m, hit, left, up, 0.55D * th * pulse, HEAT_HOT[0], HEAT_HOT[1], HEAT_HOT[2], 1.0F);
        VfxDraw.burst(buf, m, hit, left, up, 1.1F * th, HEAT[0], HEAT[1], HEAT[2], 0.7F, t * 0.15F);
        VfxDraw.glow(buf, m, hit, left, up, 1.3D * th, HEAT[0], HEAT[1], HEAT[2], 0.35F);
        long seed = b.player().getId() * 2654435761L;
        for (int g = 0; g < 2; g++) {
            long gen = (long) (t / 5.0F) - g;
            float k = (t - gen * 5.0F) / 10.0F;
            VfxDraw.sparks(buf, m, hit, seed + gen * 977L, 8, 1.2D * th, k, 0.9D, HEAT_HOT[0], HEAT_HOT[1],
                    HEAT_HOT[2], 1.0F);
        }
        // two beams meeting: the clash
        for (SupermanClient.Beam o : SupermanClient.beams()) {
            if (o.player() != b.player() && o.stop().distanceToSqr(hit) < 2.5D
                    && b.player().getId() < o.player().getId()) {
                Vec3 c = hit.add(o.stop()).scale(0.5D);
                VfxDraw.flare(buf, m, c, left, up, 1.6D, HEAT_WHITE[0], HEAT_WHITE[1], HEAT_WHITE[2], 1.0F);
                VfxDraw.burst(buf, m, c, left, up, 2.6F, HEAT[0], HEAT[1], HEAT[2], 1.0F, t * 0.3F);
            }
        }
    }

    /** The freezing breath (frost) or the super breath (wind): a rushing cone out of the mouth. */
    private static void breath(BufferBuilder buf, Matrix4f m, Player p, float pt, float t, Vec3 left, Vec3 up,
                               boolean freeze) {
        Vec3 o = mouth(p, pt);
        Vec3 look = p.getViewVector(pt).normalize();
        float[] c = freeze ? FROST : new float[] {0.86F, 0.92F, 1.0F};
        double len = 8.0D;
        double open = freeze ? 1.8D : 2.2D;
        VfxDraw.cone(buf, m, o, look, len, open, c[0], c[1], c[2], 0.12F);
        VfxDraw.cone(buf, m, o, look, len * 0.6D, open * 0.5D, 1.0F, 1.0F, 1.0F, 0.08F);
        for (int i = 0; i < 6; i++) {
            double k = (t * 0.11D + i / 6.0D) % 1.0D;
            VfxDraw.ring(buf, m, o.add(look.scale(k * len)), look, 0.15D + open * k, 0.06D + 0.1D * k, (float) t,
                    c[0], c[1], c[2], 0.45F * (float) Math.sin(Math.PI * k));
        }
        Vec3 ref = Math.abs(look.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = look.cross(ref).normalize();
        Vec3 v = look.cross(u).normalize();
        java.util.Random rnd = new java.util.Random(p.getId() * 31L);
        for (int i = 0; i < 30; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0D;
            double spread = rnd.nextDouble();
            double sp = 0.07D + rnd.nextDouble() * 0.06D;
            double k = (t * sp + rnd.nextDouble()) % 1.0D;
            Vec3 dir = look.add(u.scale(Math.cos(ang) * spread * open / len)).add(v.scale(Math.sin(ang) * spread
                    * open / len)).normalize();
            Vec3 a = o.add(dir.scale(k * len));
            Vec3 b = o.add(dir.scale(Math.min(len, k * len + 0.9D)));
            float al = 0.6F * (float) Math.sin(Math.PI * k);
            VfxDraw.beam(buf, m, a, b, 0.035F, c[0], c[1], c[2], al);
            if (freeze && i % 4 == 0) {
                VfxDraw.flare(buf, m, b, left, up, 0.09D, 0.85F, 0.95F, 1.0F, al);
            }
        }
    }

    /** The Ultimate: a sun growing round him, rings of solar fire, pillars of sunlight closing in. */
    private static void ultimate(BufferBuilder buf, Matrix4f m, Player p, float pt, float t, float k, Vec3 cam,
                                 Vec3 left, Vec3 up, boolean me) {
        k = Math.min(1.0F, Math.max(0.0F, k));
        Vec3 at = p.getPosition(pt);
        Vec3 c = at.add(0.0D, 1.0D, 0.0D);
        float pulse = 0.85F + 0.15F * (float) Math.sin(t * 0.4D);
        if (!me) {
            VfxDraw.sphere(buf, m, c, 0.7D + 1.9D * k, cam, SUN[0], SUN[1], SUN[2], (0.35F + 0.4F * k) * pulse, t,
                    1.6D);
            VfxDraw.flare(buf, m, c, left, up, 1.5D + 2.0D * k, SUN[0], SUN[1], SUN[2], 0.5F + 0.4F * k);
        }
        for (int i = 0; i < 3; i++) {
            double ph = i * 2.1D + t * 0.03D;
            VfxDraw.ring(buf, m, c, new Vec3(Math.sin(ph), 1.3D, Math.cos(ph * 0.8D)), 1.3D + 1.6D * k, 0.07D,
                    t * 0.12F, SUN_HOT[0], SUN_HOT[1], SUN_HOT[2], 0.6F * pulse);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0D + t * 0.02D;
            double r = 4.0D * (1.0D - k) + 0.4D;
            Vec3 base = at.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
            VfxDraw.pillar(buf, m, base, base.y, base.y + 60.0D, cam, 0.45F + 0.5F * k, SUN[0], SUN[1], SUN[2],
                    0.35F + 0.3F * k);
            VfxDraw.pillar(buf, m, base, base.y, base.y + 60.0D, cam, 0.12F + 0.1F * k, 1.0F, 1.0F, 0.9F,
                    0.5F + 0.3F * k);
        }
        VfxDraw.shockwave(buf, m, at, 1.5D + 2.5D * k, 0.6D * pulse, SUN[0], SUN[1], SUN[2], 0.5F);
    }

    /** Top speed: only wind behind him, no light: faint streaks of air and two thin wingtip vortices. */
    private static void flight(BufferBuilder buf, Matrix4f m, Player p, float pt, float t, Track tr, Vec3 left,
                               Vec3 up, boolean me) {
        List<Vec3> pts = new ArrayList<>(tr.trail);
        Vec3 head = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
        pts.set(0, head);
        int n = pts.size();
        java.util.Random rnd = new java.util.Random(p.getId() * 31L);
        // a few streaks of air peeling off the body, each a thin line along the path, a little apart
        Vec3[] offs = new Vec3[6];
        for (int i = 0; i < offs.length; i++) {
            offs[i] = new Vec3(rnd.nextGaussian() * 0.35D, rnd.nextGaussian() * 0.45D, rnd.nextGaussian() * 0.35D);
        }
        for (int i = 2; i < n; i++) {
            float a = (1.0F - (float) i / n) * 0.22F;
            Vec3 p0 = pts.get(i - 1);
            Vec3 p1 = pts.get(i);
            for (Vec3 o : offs) {
                double spread = 1.0D + i * 0.06D;
                VfxDraw.beam(buf, m, p0.add(o.scale(spread)), p1.add(o.scale(spread + 0.06D)), 0.012F, 0.92F, 0.95F,
                        1.0F, a);
            }
            Vec3 dir = p0.subtract(p1);
            if (dir.lengthSqr() > 1.0E-4D) {
                Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
                side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 vu = side.cross(dir.normalize());
                for (int s = -1; s <= 1; s += 2) {
                    double w0 = t * 0.6D + i * 0.7D;
                    double w1 = t * 0.6D + (i - 1) * 0.7D;
                    Vec3 q0 = p0.add(side.scale(0.5D * s)).add(side.scale(Math.cos(w1) * 0.08D))
                            .add(vu.scale(Math.sin(w1) * 0.08D));
                    Vec3 q1 = p1.add(side.scale(0.5D * s)).add(side.scale(Math.cos(w0) * 0.08D))
                            .add(vu.scale(Math.sin(w0) * 0.08D));
                    VfxDraw.beam(buf, m, q0, q1, 0.01F, 0.92F, 0.95F, 1.0F, a * 1.2F);
                }
            }
        }
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.ThorFxPacket;
import com.flash.thor.ThorPowers;
import com.flash.thor.ThorState;
import com.flash.thor.ThorSuits;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Thor as in the films, drawn in light (VfxDraw): real branching lightning that strobes and re-forks as it lives,
 * electric blue with a white core (the Cosmic King's violet), lens flares where it strikes; the Storm Striker's ring
 * of lightning running over the ground, the Stormbreaker's arc, the God of Thunder's impact with lightning crawling
 * along the ground and glowing cracks, the Bifrost's column of rainbow light, the Devourer King's dome crawling with
 * lightning, the God Blast; Thor's own aura of storm round his body and the hammer crackling in his hand.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ThorCineVfx {

    private static final float[][] BLUE = {{0.25F, 0.55F, 1.0F}, {0.62F, 0.85F, 1.0F}, {0.93F, 0.97F, 1.0F}};
    private static final float[][] VIOLET = {{0.55F, 0.18F, 1.0F}, {0.8F, 0.6F, 1.0F}, {0.97F, 0.92F, 1.0F}};

    private static final class Fx {
        final int kind;
        final Vec3 a;
        final Vec3 b;
        final float scale;
        final boolean cosmic;
        final long seed;
        final int life;
        float age;
        float born;

        Fx(int kind, Vec3 a, Vec3 b, float scale, boolean cosmic, int life) {
            this.kind = kind;
            this.a = a;
            this.b = b;
            this.scale = scale;
            this.cosmic = cosmic;
            this.seed = RANDOM.nextLong();
            this.life = life;
            this.born = clock;
        }
    }

    private static final Random RANDOM = new Random();
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static float clock;

    private ThorCineVfx() {
    }

    public static void reset() {
        ACTIVE.clear();
    }

    private static float[][] tone(boolean cosmic) {
        return cosmic ? VIOLET : BLUE;
    }

    private static boolean cosmic(Player p) {
        return ThorState.weapon(p) == ThorSuits.COSMIC;
    }

    /** An effect of the server's (ThorFx.handle passes them all here). */
    public static void handle(int kind, Vec3 a, Vec3 b, float scale, boolean cosmic) {
        if (kind == ThorFxPacket.TORNADO) {
            // the Mighty Tornado: one funnel, kept alive while the server keeps sending it
            for (Fx f : ACTIVE) {
                if (f.kind == ThorFxPacket.TORNADO && f.a.distanceToSqr(a) < 4.0D) {
                    f.age = 0.0F;
                    return;
                }
            }
            ACTIVE.add(new Fx(kind, a, b, scale, cosmic, 8));
            return;
        }
        int life = switch (kind) {
            case ThorFxPacket.BOLT -> 9;
            case ThorFxPacket.RING -> 18;
            case ThorFxPacket.WAVE -> 12;
            case ThorFxPacket.IMPACT -> 34;
            case ThorFxPacket.BIFROST -> Math.max(12, (int) b.x);
            case ThorFxPacket.DOME -> 24;
            case ThorFxPacket.BEAM -> 5;
            default -> 0;
        };
        if (life > 0 && ACTIVE.size() < 400) {
            ACTIVE.add(new Fx(kind, a, b, scale, cosmic, life));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (kind == ThorFxPacket.IMPACT) {
            // three strikes out of the sky round the impact
            for (int i = 0; i < 3; i++) {
                Vec3 g = a.add(RANDOM.nextGaussian() * 2.5D, 0.1D, RANDOM.nextGaussian() * 2.5D);
                handle(ThorFxPacket.BOLT, g.add(RANDOM.nextGaussian() * 3.0D, 30.0D, RANDOM.nextGaussian() * 3.0D), g,
                        2.0F, cosmic);
            }
            HulkVfx.quake(mc, a, 1.4F, 30.0D);
        } else if (kind == ThorFxPacket.BOLT && a.y - b.y > 10.0D) {
            HulkVfx.quake(mc, b, 0.5F, 18.0D);
        } else if (kind == ThorFxPacket.BEAM) {
            HulkVfx.quake(mc, b, 0.25F, 24.0D);
        }
    }

    // ------------------------------------------------------------------
    // Lightning
    // ------------------------------------------------------------------

    /** Lightning strobes: on, dim, on, as a real stroke does. */
    private static float strobe(long seed, float age) {
        int f = (int) age;
        long h = (seed + f * 0x9E3779B97F4A7C15L) >>> 40;
        return (h & 3L) == 0L ? 0.35F : 1.0F;
    }

    /** A branching bolt from a to b (the same seed, the same shape): glow, colour, white core, side forks. */
    private static void bolt(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, long seed, float w, float[][] c,
                             float alpha, int forks) {
        if (alpha <= 0.01F) {
            return;
        }
        Vec3[] pts = VfxDraw.jagged(a, b, seed, 1.0D, 5);
        VfxDraw.path(buf, m, pts, w * 4.0F, c[0][0], c[0][1], c[0][2], 0.22F * alpha);
        VfxDraw.path(buf, m, pts, w, c[1][0], c[1][1], c[1][2], 0.85F * alpha);
        VfxDraw.path(buf, m, pts, w * 0.32F, c[2][0], c[2][1], c[2][2], alpha);
        Random rnd = new Random(seed ^ 0x2545F4914F6CDD1DL);
        double len = a.distanceTo(b);
        for (int i = 0; i < forks; i++) {
            int at = 3 + rnd.nextInt(Math.max(1, pts.length - 6));
            Vec3 from = pts[at];
            Vec3 d = pts[Math.min(pts.length - 1, at + 2)].subtract(from).normalize()
                    .add(rnd.nextGaussian() * 0.7D, rnd.nextGaussian() * 0.5D, rnd.nextGaussian() * 0.7D).normalize();
            Vec3 to = from.add(d.scale(len * (0.15D + rnd.nextDouble() * 0.2D)));
            Vec3[] fp = VfxDraw.jagged(from, to, rnd.nextLong(), 1.2D, 3);
            VfxDraw.path(buf, m, fp, w * 2.0F, c[0][0], c[0][1], c[0][2], 0.15F * alpha);
            VfxDraw.path(buf, m, fp, w * 0.45F, c[1][0], c[1][1], c[1][2], 0.7F * alpha);
            VfxDraw.path(buf, m, fp, w * 0.15F, c[2][0], c[2][1], c[2][2], 0.9F * alpha);
        }
    }

    /** A bolt that crawls over the ground from c outwards (angle, length): kept low, re-forked every tick. */
    private static void groundBolt(BufferBuilder buf, Matrix4f m, Vec3 c, double ang, double len, long seed, float w,
                                   float[][] col, float alpha) {
        Vec3 to = c.add(Math.cos(ang) * len, 0.0D, Math.sin(ang) * len);
        Vec3[] pts = VfxDraw.jagged(c, to, seed, 1.1D, 4);
        for (int i = 0; i < pts.length; i++) {
            pts[i] = new Vec3(pts[i].x, c.y + Math.abs(pts[i].y - c.y) * 0.25D, pts[i].z);
        }
        VfxDraw.path(buf, m, pts, w * 3.5F, col[0][0], col[0][1], col[0][2], 0.25F * alpha);
        VfxDraw.path(buf, m, pts, w, col[1][0], col[1][1], col[1][2], 0.85F * alpha);
        VfxDraw.path(buf, m, pts, w * 0.3F, col[2][0], col[2][1], col[2][2], alpha);
    }

    /** About where the hand with the hammer is. */
    private static Vec3 hand(Player p, float partial) {
        Vec3 eye = p.getEyePosition(partial);
        Vec3 look = p.getViewVector(partial);
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        boolean first = p == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (ThorArmPose.raised(p) && !first) {
            // raised to the sky with the hammer (ThorArmPose)
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            flat = flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize();
            return eye.add(side.scale(0.38D)).add(flat.scale(0.05D)).add(0.0D, 0.75D, 0.0D);
        }
        return eye.add(look.scale(first ? 0.7D : 0.5D)).add(side.scale(0.35D)).subtract(0.0D, first ? 0.35D : 0.5D,
                0.0D);
    }

    private static float ease(float k) {
        float u = 1.0F - Math.min(1.0F, Math.max(0.0F, k));
        return 1.0F - u * u * u;
    }

    private static float[] rainbow(float h) {
        float r = Math.abs(h * 6.0F - 3.0F) - 1.0F;
        float g = 2.0F - Math.abs(h * 6.0F - 2.0F);
        float b = 2.0F - Math.abs(h * 6.0F - 4.0F);
        return new float[] {Math.max(0.0F, Math.min(1.0F, r)), Math.max(0.0F, Math.min(1.0F, g)),
                Math.max(0.0F, Math.min(1.0F, b))};
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        clock += 1.0F;
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.age += 1.0F;
            if (f.age > f.life) {
                it.remove();
            }
        }
        for (Player p : mc.level.players()) {
            if (!ThorState.isThor(p)) {
                continue;
            }
            boolean aura = ThorState.flag(p, ThorPowers.F_ULTIMATE) || ThorState.flag(p, ThorPowers.F_TWIRL)
                    || ThorState.flag(p, ThorPowers.F_FURY);
            if (aura && p.tickCount % 2 == 0) {
                // sparks of the storm rising round him
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + RANDOM.nextGaussian() * 0.8D,
                        p.getY() + RANDOM.nextDouble() * 2.2D, p.getZ() + RANDOM.nextGaussian() * 0.8D, 0.0D, 0.06D,
                        0.0D);
            }
        }
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
        List<Player> thors = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (ThorState.isThor(p) && !p.isInvisible()) {
                thors.add(p);
            }
        }
        if (ACTIVE.isEmpty() && thors.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        float pt = event.getPartialTick();
        float t = clock + pt;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        BufferBuilder buf = VfxDraw.begin();
        try {
            for (Fx f : ACTIVE) {
                draw(buf, m, f, pt, t, cam, left, up);
            }
            for (Player p : thors) {
                thor(buf, m, mc, p, pt, t, left, up);
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Fx f, float pt, float t, Vec3 cam, Vec3 left, Vec3 up) {
        float age = f.age + pt;
        float k = Math.min(1.0F, age / Math.max(1, f.life));
        float fade = 1.0F - k;
        float[][] c = tone(f.cosmic);
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        long tick = (long) age;
        switch (f.kind) {
            case ThorFxPacket.TORNADO -> {
                float on = Math.min(1.0F, (t - f.born) / 8.0F) * Math.min(1.0F, (f.life - age) / 4.0F);
                float grow = ease(Math.min(1.0F, (t - f.born) / 25.0F)) * 1.45F;
                FlashTornadoVfx.speedTornado(buf, m, f.a, cam, left, up, t, c[1], on, grow, 2.0D, f.seed);
            }
            case ThorFxPacket.BOLT -> {
                float s = Math.max(0.3F, f.scale);
                float a = fade * strobe(f.seed, age);
                // re-forked every other tick, as a real stroke flickers
                bolt(buf, m, f.a, f.b, f.seed + tick / 2, 0.05F * s, c, a, s >= 1.0F ? 4 : 1);
                if (s >= 1.0F) {
                    VfxDraw.flare(buf, m, f.b, left, up, 0.7D * s, c[1][0], c[1][1], c[1][2], a);
                    if (f.a.y - f.b.y > 10.0D) {
                        // out of the sky: the strike lights the ground and the clouds
                        VfxDraw.shockwave(buf, m, f.b, 0.5D + 3.0D * ease(k), 0.8D * fade, c[0][0], c[0][1], c[0][2],
                                fade);
                        VfxDraw.glow(buf, m, f.a, left, up, 8.0D, c[0][0], c[0][1], c[0][2], 0.15F * a);
                    }
                }
            }
            case ThorFxPacket.RING -> {
                // the Storm Striker: a ring of lightning running out over the ground
                double r = Math.max(0.5D, f.scale * ease(k));
                Vec3 g = f.a.add(0.0D, 0.15D, 0.0D);
                int n = 14;
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2.0D * i / n;
                    double a1 = Math.PI * 2.0D * (i + 1) / n;
                    Vec3 p0 = g.add(Math.cos(a0) * r, 0.0D, Math.sin(a0) * r);
                    Vec3 p1 = g.add(Math.cos(a1) * r, 0.0D, Math.sin(a1) * r);
                    bolt(buf, m, p0, p1, f.seed + i * 31L + tick, 0.05F, c, fade, 0);
                }
                VfxDraw.shockwave(buf, m, f.a, r, 1.6D * fade, c[0][0], c[0][1], c[0][2], 0.7F * fade);
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.5D, 0.0D), left, up, 1.2D, c[1][0], c[1][1], c[1][2],
                        Math.max(0.0F, 1.0F - age / 4.0F));
            }
            case ThorFxPacket.WAVE -> {
                // the Stormbreaker: an arc of lightning sweeping out in front
                Vec3 dir = f.b;
                double base = Math.atan2(dir.z, dir.x);
                double r = Math.max(0.5D, f.scale - 4.0D + 4.0D * ease(k));
                Vec3 prev = null;
                for (int i = -5; i <= 5; i++) {
                    double ang = base + Math.toRadians(9.0D * i);
                    Vec3 p = f.a.add(Math.cos(ang) * r, 0.7D, Math.sin(ang) * r);
                    if (prev != null) {
                        bolt(buf, m, prev, p, f.seed + i * 17L + tick, 0.06F, c, fade, 0);
                    }
                    VfxDraw.glow(buf, m, p, left, up, 1.0D, c[0][0], c[0][1], c[0][2], 0.25F * fade);
                    prev = p;
                }
                VfxDraw.cone(buf, m, f.a.add(0.0D, 0.7D, 0.0D), new Vec3(Math.cos(base), 0.0D, Math.sin(base)), r,
                        r * 0.7D, c[0][0], c[0][1], c[0][2], 0.1F * fade);
            }
            case ThorFxPacket.IMPACT -> {
                // the God of Thunder: the blow, lightning crawling out over the ground, glowing cracks
                double s = Math.max(2.0D, f.scale);
                Vec3 g = f.a.add(0.0D, 0.1D, 0.0D);
                VfxDraw.flare(buf, m, f.a.add(0.0D, 0.8D, 0.0D), left, up, 2.0D + s * 0.3D, c[1][0], c[1][1], c[1][2],
                        Math.max(0.0F, 1.0F - age / 7.0F));
                float kk = Math.min(1.0F, age / 16.0F);
                VfxDraw.sphere(buf, m, f.a.add(0.0D, 0.5D, 0.0D), s * 0.8D * ease(kk), cam, c[0][0], c[0][1],
                        c[0][2], 0.65F * (1.0F - kk), t, 2.2D);
                VfxDraw.shockwave(buf, m, f.a, s * 1.4D * ease(k), 1.8D * fade, c[1][0], c[1][1], c[1][2], fade);
                float k2 = Math.max(0.0F, (age - 4.0F) / 24.0F);
                if (k2 < 1.0F) {
                    VfxDraw.shockwave(buf, m, f.a, s * 0.9D * ease(k2), 0.7D * (1.0F - k2), c[0][0], c[0][1], c[0][2],
                            0.6F * (1.0F - k2));
                }
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 12; i++) {
                    double ang = Math.PI * 2.0D * i / 12.0D + rnd.nextDouble() * 0.4D;
                    double len = s * (0.8D + rnd.nextDouble() * 0.6D) * ease(Math.min(1.0F, age / 5.0F));
                    groundBolt(buf, m, g, ang, len, f.seed + i * 977L + tick, 0.06F, c,
                            Math.min(1.0F, fade * 1.6F) * strobe(f.seed + i, age));
                    VfxDraw.crack(buf, m, f.a, ang + 0.2D, len * 0.8D, f.seed + i, Math.min(1.0F, age / 6.0F), 0.07D,
                            c[0][0], c[0][1], c[0][2], fade);
                }
                VfxDraw.sparks(buf, m, f.a.add(0.0D, 0.4D, 0.0D), f.seed, 44, s * 1.2D, age / 22.0F, 1.5D, c[1][0],
                        c[1][1], c[1][2], 1.0F);
            }
            case ThorFxPacket.BIFROST -> {
                // the rainbow bridge: a column of light in every colour coming down
                float in = Math.min(1.0F, age / 5.0F);
                float out = Math.min(1.0F, (f.life - age) / 8.0F);
                float al = Math.max(0.0F, Math.min(in, out));
                double r = Math.max(1.0D, f.scale) * (0.6D + 0.4D * in);
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + 160.0D, cam, (float) r * 1.6F, 1.0F, 1.0F, 1.0F,
                        0.45F * al);
                for (int i = 0; i < 7; i++) {
                    float[] col = rainbow((i / 7.0F + t * 0.01F) % 1.0F);
                    double ang = t * 0.15D + i * Math.PI * 2.0D / 7.0D;
                    Vec3 o = f.a.add(Math.cos(ang) * r * 0.7D, 0.0D, Math.sin(ang) * r * 0.7D);
                    VfxDraw.pillar(buf, m, o, o.y, o.y + 160.0D, cam, (float) r * 0.6F, col[0], col[1], col[2],
                            0.4F * al);
                    VfxDraw.ring(buf, m, f.a.add(0.0D, 0.06D + i * 0.01D, 0.0D), yUp, r * (1.2D + i * 0.2D), 0.12D,
                            t * 0.05F, col[0], col[1], col[2], 0.7F * al);
                }
                // sparkles of the bridge rising
                Random rnd = new Random(f.seed);
                for (int i = 0; i < 24; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    double d = rnd.nextDouble() * r;
                    double y = (rnd.nextDouble() * 12.0D + age * 0.4D) % 12.0D;
                    float[] col = rainbow(rnd.nextFloat());
                    VfxDraw.glow(buf, m, f.a.add(Math.cos(ang) * d, y, Math.sin(ang) * d), left, up, 0.18D, col[0],
                            col[1], col[2], 0.8F * al);
                }
                VfxDraw.flare(buf, m, f.a.add(0.0D, 1.0D, 0.0D), left, up, 2.5D * r, 1.0F, 1.0F, 1.0F,
                        Math.max(0.0F, 1.0F - age / 8.0F));
                VfxDraw.shockwave(buf, m, f.a, r * (1.5D + 2.0D * ease(Math.min(1.0F, age / 12.0F))), 1.0D * al, 1.0F,
                        1.0F, 1.0F, 0.4F * al);
            }
            case ThorFxPacket.DOME -> {
                // the Devourer King: a dome of cosmic lightning crawling over itself
                float al = (float) Math.sin(k * Math.PI);
                double r = Math.max(2.0D, f.scale);
                float[][] v = VIOLET;
                VfxDraw.sphere(buf, m, f.a, r, cam, v[0][0], v[0][1], v[0][2], 0.45F * al, t, 1.6D);
                VfxDraw.shockwave(buf, m, f.a, r, 2.0D * al, v[0][0], v[0][1], v[0][2], 0.7F * al);
                Random rnd = new Random(f.seed + tick / 2);
                for (int i = 0; i < 10; i++) {
                    double a0 = rnd.nextDouble() * Math.PI * 2.0D;
                    double e0 = rnd.nextDouble() * Math.PI * 0.45D;
                    double a1 = a0 + (rnd.nextDouble() - 0.5D) * 1.4D;
                    double e1 = Math.max(0.0D, Math.min(Math.PI * 0.5D, e0 + (rnd.nextDouble() - 0.5D) * 0.9D));
                    Vec3 p0 = f.a.add(Math.cos(a0) * Math.cos(e0) * r, Math.sin(e0) * r, Math.sin(a0) * Math.cos(e0) * r);
                    Vec3 p1 = f.a.add(Math.cos(a1) * Math.cos(e1) * r, Math.sin(e1) * r, Math.sin(a1) * Math.cos(e1) * r);
                    bolt(buf, m, p0, p1, rnd.nextLong(), 0.07F, v, al, 1);
                }
                VfxDraw.pillar(buf, m, f.a, f.a.y, f.a.y + r * 1.5D, cam, 0.8F, v[1][0], v[1][1], v[1][2], 0.5F * al);
            }
            case ThorFxPacket.BEAM -> {
                // the God Blast: a river of lightning
                float al = 1.0F - k * 0.5F;
                float s = Math.max(0.5F, f.scale);
                VfxDraw.energy(buf, m, f.a, f.b, 1.0F * s, c[0][0], c[0][1], c[0][2], 0.6F * al);
                VfxDraw.beam(buf, m, f.a, f.b, 0.12F * s, 1.0F, 1.0F, 1.0F, al);
                for (int i = 0; i < 4; i++) {
                    bolt(buf, m, f.a, f.b, f.seed + i * 131L + (long) (t * 2.0F), 0.08F * s, c, al, 2);
                }
                double len = f.a.distanceTo(f.b);
                if (len > 1.0D) {
                    Vec3 dn = f.b.subtract(f.a).scale(1.0D / len);
                    for (int i = 0; i < 6; i++) {
                        double pos = (t * 2.5D + i * len / 6.0D) % len;
                        VfxDraw.ring(buf, m, f.a.add(dn.scale(pos)), dn, 1.5D * s, 0.1D, 0.0F, c[1][0], c[1][1],
                                c[1][2], 0.5F * al);
                    }
                }
                VfxDraw.flare(buf, m, f.a, left, up, 1.6D * s, c[1][0], c[1][1], c[1][2], al);
                VfxDraw.flare(buf, m, f.b, left, up, 2.4D * s, c[1][0], c[1][1], c[1][2], al);
                VfxDraw.sphere(buf, m, f.b, 1.6D * s, cam, c[0][0], c[0][1], c[0][2], 0.5F * al, t, 2.0D);
            }
            default -> {
            }
        }
    }

    /** Thor himself: the storm round his body, the hammer crackling, the Lightning Blast out of his hand. */
    private static void thor(BufferBuilder buf, Matrix4f m, Minecraft mc, Player p, float pt, float t, Vec3 left,
                             Vec3 up) {
        boolean cos = cosmic(p);
        float[][] c = tone(cos);
        boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        Vec3 pos = p.getPosition(pt);
        long tick = (long) t;
        Random rnd = new Random(p.getId() * 7919L + tick);
        boolean fury = ThorState.flag(p, ThorPowers.F_FURY);
        boolean aura = ThorState.flag(p, ThorPowers.F_ULTIMATE) || ThorState.flag(p, ThorPowers.F_TWIRL);
        boolean charging = ThorState.flag(p, ThorPowers.F_CHARGING);
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        if (fury || aura) {
            // lightning crawling all over the body, glowing eyes
            for (int i = 0; i < (aura ? 4 : 3); i++) {
                Vec3 p0 = pos.add(rnd.nextGaussian() * 0.35D, 0.2D + rnd.nextDouble() * 1.7D, rnd.nextGaussian() * 0.35D);
                Vec3 p1 = p0.add(rnd.nextGaussian() * 0.6D, rnd.nextGaussian() * 0.5D, rnd.nextGaussian() * 0.6D);
                bolt(buf, m, p0, p1, rnd.nextLong(), 0.035F, c, 0.9F, 0);
            }
            if (!me) {
                VfxDraw.glow(buf, m, pos.add(0.0D, 1.0D, 0.0D), left, up, 1.6D, c[0][0], c[0][1], c[0][2], 0.18F);
                Vec3 look = p.getViewVector(pt);
                Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.07D);
                Vec3 eye = p.getEyePosition(pt).add(look.scale(0.26D));
                VfxDraw.flare(buf, m, eye.add(side), left, up, 0.1D, c[1][0], c[1][1], c[1][2], 0.9F);
                VfxDraw.flare(buf, m, eye.subtract(side), left, up, 0.1D, c[1][0], c[1][1], c[1][2], 0.9F);
            }
        }
        if (aura) {
            // two rings of lightning turning round him, the storm gathering
            for (int ring = 0; ring < 2; ring++) {
                double h = ring == 0 ? 0.25D : 1.55D;
                double r = ring == 0 ? 1.35D : 1.05D;
                double spin = t * (ring == 0 ? 0.18D : -0.24D);
                Vec3 cc = pos.add(0.0D, h, 0.0D);
                for (int i = 0; i < 3; i++) {
                    double a0 = spin + i * Math.PI * 2.0D / 3.0D;
                    double a1 = a0 + 1.4D;
                    bolt(buf, m, cc.add(Math.cos(a0) * r, 0.0D, Math.sin(a0) * r), cc.add(Math.cos(a1) * r, 0.0D,
                            Math.sin(a1) * r), p.getId() * 31L + ring * 7L + i + tick / 2, 0.04F, c, 0.9F, 0);
                }
                VfxDraw.ring(buf, m, cc, yUp, r, 0.12D, (float) spin, c[0][0], c[0][1], c[0][2], 0.35F);
            }
            VfxDraw.shockwave(buf, m, pos, 1.8D + 0.2D * Math.sin(t * 0.3D), 0.6D, c[0][0], c[0][1], c[0][2], 0.4F);
        }
        if (charging) {
            // the sky's lightning pouring into the raised hammer
            Vec3 h = hand(p, pt);
            long strike = tick / 3;
            Random sky = new Random(p.getId() * 977L + strike);
            if (sky.nextInt(3) != 0) {
                Vec3 from = h.add(sky.nextGaussian() * 3.0D, 22.0D + sky.nextDouble() * 8.0D, sky.nextGaussian() * 3.0D);
                bolt(buf, m, from, h, sky.nextLong(), 0.07F, c, 1.0F - (t % 3.0F) / 3.0F, 3);
            }
        }
        if ((charging || fury || aura) && !me) {
            // the hammer crackling in his hand
            Vec3 h = hand(p, pt);
            VfxDraw.flare(buf, m, h, left, up, charging ? 0.6D : 0.4D, c[1][0], c[1][1], c[1][2], 0.8F);
            for (int i = 0; i < (charging ? 3 : 1); i++) {
                Vec3 to = h.add(rnd.nextGaussian() * 0.6D, rnd.nextDouble() * 0.8D, rnd.nextGaussian() * 0.6D);
                bolt(buf, m, h, to, rnd.nextLong(), 0.03F, c, 0.9F, 0);
            }
        }
        if (ThorState.flag(p, ThorPowers.F_BLAST)) {
            lightningBlast(buf, m, mc, p, pt, t, c, left, up);
        }
    }

    /** The Lightning Blast: a torrent of forking lightning from the hand to whatever it hits. */
    private static void lightningBlast(BufferBuilder buf, Matrix4f m, Minecraft mc, Player p, float pt, float t,
                                       float[][] c, Vec3 left, Vec3 up) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 look = p.getViewVector(pt);
        Vec3 end = eye.add(look.scale(32.0D));
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, stop,
                box, e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(stop));
        if (hit != null) {
            stop = hit.getLocation();
        } else if (block.getType() != HitResult.Type.MISS) {
            SupermanScorch.paint(p.getUUID(), stop, Vec3.atLowerCornerOf(block.getDirection().getNormal()), false);
        }
        Vec3 from = hand(p, pt);
        long tick = (long) t;
        VfxDraw.beam(buf, m, from, stop, 0.7F, c[0][0], c[0][1], c[0][2], 0.12F);
        for (int i = 0; i < 3; i++) {
            bolt(buf, m, from, stop, p.getId() * 131L + i * 17L + tick, i == 0 ? 0.09F : 0.05F, c, 1.0F, 3);
        }
        VfxDraw.flare(buf, m, from, left, up, 0.6D, c[1][0], c[1][1], c[1][2], 1.0F);
        VfxDraw.flare(buf, m, stop, left, up, 1.1D, c[1][0], c[1][1], c[1][2], 1.0F);
        VfxDraw.burst(buf, m, stop, left, up, 1.8F, c[0][0], c[0][1], c[0][2], 0.8F, t * 0.3F);
        for (int g = 0; g < 2; g++) {
            long gen = (long) (t / 4.0F) - g;
            float k = (t - gen * 4.0F) / 8.0F;
            VfxDraw.sparks(buf, m, stop, p.getId() * 31L + gen * 977L, 8, 1.5D, k, 0.6D, c[1][0], c[1][1], c[1][2],
                    1.0F);
        }
    }
}

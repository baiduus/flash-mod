package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashFx;
import com.flash.network.HeroFxPacket;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Flash's powers as in the films, drawn in light (VfxDraw) in his colours (FlashVfx.color: gold, the Negative
 * Force's red, the V-9's blue): impacts that tear the air with lightning and lens flares, the speed tornado as a
 * spinning funnel of lightning, the run round the circle of the mass punch, the dashes, phasing as a vibrating
 * blur of light and slowed time as a still dome with a clock turning on the ground.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashCineVfx {

    private static final class Fx {
        final int type;
        final int caster;
        Vec3 a;
        final Vec3 b;
        final float power;
        final long seed;
        final int life;
        final float born;
        float age;

        Fx(HeroFxPacket p, int life) {
            this.type = p.type();
            this.caster = p.caster();
            this.a = p.from();
            this.b = p.to();
            this.power = p.power();
            this.seed = (long) (p.from().x * 59.0D + p.from().z * 23.0D) * 6151L ^ System.nanoTime();
            this.life = life;
            this.born = clock;
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static float clock;

    private FlashCineVfx() {
    }

    public static void reset() {
        ACTIVE.clear();
    }

    private static boolean lasting(int type) {
        return type == FlashFx.TORNADO || type == FlashFx.MASS_CHARGE || type == FlashFx.PHASE
                || type == FlashFx.TIME_SLOW;
    }

    public static void fx(HeroFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (lasting(p.type())) {
            for (Fx f : ACTIVE) {
                if (f.type == p.type() && f.caster == p.caster()) {
                    f.age = 0.0F;
                    f.a = p.from();
                    return;
                }
            }
        }
        int life = switch (p.type()) {
            case FlashFx.IMPACT -> 30;
            case FlashFx.DASH -> 9;
            case FlashFx.HIT -> 8;
            case FlashFx.RELEASE -> 10;
            default -> lasting(p.type()) ? 8 : 0;
        };
        if (life > 0 && ACTIVE.size() < 400) {
            ACTIVE.add(new Fx(p, life));
        }
        if (p.type() == FlashFx.IMPACT) {
            Vec3 a = p.from();
            for (int i = 0; i < 30; i++) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, rnd(1.2D), rnd(1.2D), rnd(1.2D));
            }
            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(ParticleTypes.LARGE_SMOKE, a.x, a.y, a.z, rnd(0.2D), 0.05D, rnd(0.2D));
            }
        }
    }

    private static double rnd(double s) {
        return (Math.random() - 0.5D) * s;
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
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.age += 1.0F;
            if (f.age > f.life) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) {
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
                draw(buf, m, mc, f, pt, t, cam, left, up);
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    /** His colours: [main, hot, white]. */
    private static float[][] colours(Entity e) {
        float[] c = e instanceof Player p ? FlashVfx.color(FlashClientVisualLevels.isNegative(p),
                FlashClientVisualLevels.isV9(p)) : FlashVfx.color(false, false);
        float[] hot = {VfxDraw.mix(c[0], 0.45F), VfxDraw.mix(c[1], 0.45F), VfxDraw.mix(c[2], 0.45F)};
        float[] white = {VfxDraw.mix(c[0], 0.85F), VfxDraw.mix(c[1], 0.85F), VfxDraw.mix(c[2], 0.85F)};
        return new float[][] {c, hot, white};
    }

    /** A crackling bolt of his lightning (glow, colour, white core). */
    private static void bolt(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, long seed, double jag, float w,
                             float[][] c, float alpha) {
        Vec3[] pts = VfxDraw.jagged(a, b, seed, jag, 4);
        VfxDraw.path(buf, m, pts, w * 3.0F, c[0][0], c[0][1], c[0][2], 0.35F * alpha);
        VfxDraw.path(buf, m, pts, w, c[1][0], c[1][1], c[1][2], 0.8F * alpha);
        VfxDraw.path(buf, m, pts, w * 0.35F, c[2][0], c[2][1], c[2][2], alpha);
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, float t, Vec3 cam,
                             Vec3 left, Vec3 up) {
        Entity e = f.caster >= 0 ? mc.level.getEntity(f.caster) : null;
        float[][] c = colours(e);
        float[] main = c[0];
        float[] hot = c[1];
        float[] white = c[2];
        float age = f.age + pt;
        float k = Math.min(1.0F, age / Math.max(1, f.life));
        float fade = 1.0F - k;
        // the lasting ones: in over their first ticks, out when they stop being sent
        float on = Math.min(1.0F, (t - f.born) / 6.0F) * Math.min(1.0F, (f.life - age) / 4.0F);
        Vec3 vup = new Vec3(0.0D, 1.0D, 0.0D);
        boolean me = e != null && e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        switch (f.type) {
            case FlashFx.IMPACT -> {
                double s = Math.max(1.0D, f.power);
                float flash = Math.max(0.0F, 1.0F - age / 6.0F);
                VfxDraw.flare(buf, m, f.a, left, up, 1.1D * s, hot[0], hot[1], hot[2], flash);
                VfxDraw.burst(buf, m, f.a, left, up, (float) (1.6D * s * (0.5D + 0.5D * ease(k))), main[0], main[1],
                        main[2], fade, t * 0.05F);
                float kk = Math.min(1.0F, age / 14.0F);
                VfxDraw.sphere(buf, m, f.a, 0.3D + s * 1.1D * ease(kk), cam, main[0], main[1], main[2],
                        0.6F * (1.0F - kk), t, 2.2D);
                Vec3 way = f.b.lengthSqr() < 1.0E-6D ? vup : f.b.normalize();
                VfxDraw.ring(buf, m, f.a, way, 0.4D + s * 1.8D * ease(kk), 0.1D, 0.0F, white[0], white[1], white[2],
                        0.8F * (1.0F - kk));
                VfxDraw.shockwave(buf, m, f.a.subtract(0.0D, 0.4D, 0.0D), 0.5D + s * 2.2D * ease(k), 0.8D * fade,
                        main[0], main[1], main[2], 0.6F * fade);
                // lightning torn out of the air all round the impact
                java.util.Random rnd = new java.util.Random(f.seed + (long) (age / 2.0F));
                int bolts = 6 + (int) (s * 2.0D);
                for (int i = 0; i < bolts; i++) {
                    Vec3 d = new Vec3(rnd.nextDouble() - 0.5D, rnd.nextDouble() - 0.3D, rnd.nextDouble() - 0.5D)
                            .normalize().scale(s * (1.0D + rnd.nextDouble() * 1.4D) * (0.4D + 0.6D * ease(k)));
                    bolt(buf, m, f.a, f.a.add(d), rnd.nextLong(), 1.2D, 0.05F, c, fade);
                }
                VfxDraw.sparks(buf, m, f.a, f.seed, 36, 2.0D + s, age / 18.0F, 1.2D, hot[0], hot[1], hot[2], 1.0F);
            }
            case FlashFx.TORNADO -> {
                Vec3 base = f.a;
                double height = 9.5D;
                float spin = t * 0.55F;
                // spinning rings, wider up the funnel
                for (int i = 0; i < 11; i++) {
                    double h = i * height / 10.0D;
                    double r = 0.35D + h * 0.19D;
                    float a = on * (0.35F + 0.25F * (float) Math.sin(t * 0.3D + i));
                    VfxDraw.ring(buf, m, base.add(0.0D, h, 0.0D), vup, r, 0.05D + h * 0.01D, spin + i * 0.5F,
                            hot[0], hot[1], hot[2], a);
                }
                // bands of speed force winding up it
                for (int s = 0; s < 5; s++) {
                    List<Vec3> pts = new ArrayList<>();
                    for (int i = 0; i <= 26; i++) {
                        double h = i * height / 26.0D;
                        double r = 0.35D + h * 0.19D;
                        double ang = spin + s * Math.PI * 2.0D / 5.0D + h * 0.85D;
                        pts.add(base.add(Math.cos(ang) * r, h, Math.sin(ang) * r));
                    }
                    VfxDraw.path(buf, m, pts, 0.16F, main[0], main[1], main[2], 0.35F * on);
                    VfxDraw.path(buf, m, pts, 0.05F, white[0], white[1], white[2], 0.7F * on);
                }
                // lightning crackling inside
                java.util.Random rnd = new java.util.Random(f.seed + (long) (t / 2.0F));
                for (int i = 0; i < 3; i++) {
                    double h = rnd.nextDouble() * height;
                    double r = 0.35D + h * 0.19D;
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    bolt(buf, m, base.add(0.0D, 0.3D, 0.0D), base.add(Math.cos(ang) * r, h, Math.sin(ang) * r),
                            rnd.nextLong(), 1.0D, 0.04F, c, on);
                }
                VfxDraw.glow(buf, m, base.add(0.0D, 0.1D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D),
                        new Vec3(0.0D, 0.0D, 1.0D), 3.0D, main[0], main[1], main[2], 0.4F * on);
                VfxDraw.wall(buf, m, base, 1.7D, 1.4D, main[0], main[1], main[2], 0.35F * on);
                VfxDraw.glow(buf, m, base.add(0.0D, height * 0.5D, 0.0D), left, up, 4.0D, main[0], main[1], main[2],
                        0.08F * on);
            }
            case FlashFx.MASS_CHARGE -> {
                double r = Math.max(2.0D, f.power);
                Vec3 g = f.a.add(0.0D, 0.06D, 0.0D);
                float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.5D);
                VfxDraw.ring(buf, m, g, vup, r, 0.12D, t * 0.2F, white[0], white[1], white[2], 0.85F * on);
                VfxDraw.ring(buf, m, g, vup, r, 0.5D, 0.0F, main[0], main[1], main[2], 0.3F * on * pulse);
                VfxDraw.ring(buf, m, g, vup, r * 0.82D, 0.05D, -t * 0.12F, hot[0], hot[1], hot[2], 0.6F * on);
                VfxDraw.wall(buf, m, g, r, 1.8D * pulse, main[0], main[1], main[2], 0.4F * on);
                // dashes of light racing round the circle
                for (int i = 0; i < 18; i++) {
                    double a0 = t * 0.33D + i * Math.PI * 2.0D / 18.0D;
                    double a1 = a0 + 0.22D;
                    VfxDraw.bar(buf, m, g.x + Math.cos(a0) * r * 0.92D, g.y + 0.01D, g.z + Math.sin(a0) * r * 0.92D,
                            g.x + Math.cos(a1) * r * 0.92D, g.z + Math.sin(a1) * r * 0.92D, 0.06D, hot[0], hot[1],
                            hot[2], 0.8F * on);
                }
                // lightning arcing over the circle
                java.util.Random rnd = new java.util.Random(f.seed + (long) (t / 2.0F));
                for (int i = 0; i < 3; i++) {
                    double a0 = rnd.nextDouble() * Math.PI * 2.0D;
                    double a1 = a0 + 0.6D + rnd.nextDouble() * 0.8D;
                    Vec3 p0 = g.add(Math.cos(a0) * r, 0.2D, Math.sin(a0) * r);
                    Vec3 p1 = g.add(Math.cos(a1) * r, 0.2D, Math.sin(a1) * r);
                    bolt(buf, m, p0, p1, rnd.nextLong(), 1.3D, 0.045F, c, on);
                }
                if (e != null && !me) {
                    Vec3 body = e.getPosition(pt).add(0.0D, 1.0D, 0.0D);
                    VfxDraw.flare(buf, m, body, left, up, 0.9D, hot[0], hot[1], hot[2], 0.6F * on * pulse);
                }
            }
            case FlashFx.DASH -> {
                VfxDraw.energy(buf, m, f.a, f.b, 0.22F, main[0], main[1], main[2], 0.8F * fade);
                bolt(buf, m, f.a, f.b, f.seed, 0.8D, 0.05F, c, fade);
                VfxDraw.flare(buf, m, f.b, left, up, 0.8D, hot[0], hot[1], hot[2], fade * fade);
            }
            case FlashFx.HIT -> {
                Vec3 way = f.b.lengthSqr() < 1.0E-6D ? vup : f.b.normalize();
                float pw = Math.max(0.4F, f.power);
                VfxDraw.flare(buf, m, f.a, left, up, 0.6D * pw, hot[0], hot[1], hot[2], fade);
                VfxDraw.burst(buf, m, f.a, left, up, 1.2F * pw, main[0], main[1], main[2], fade, f.seed % 5L);
                VfxDraw.ring(buf, m, f.a, way, 0.2D + 1.2D * pw * ease(k), 0.06D, 0.0F, white[0], white[1], white[2],
                        0.8F * fade);
                VfxDraw.sparks(buf, m, f.a, f.seed, 10, 1.4D * pw, k, 0.4D, hot[0], hot[1], hot[2], 1.0F);
            }
            case FlashFx.RELEASE -> {
                Vec3 way = f.b.normalize();
                float pw = Math.max(1.0F, f.power);
                VfxDraw.flare(buf, m, f.a, left, up, 0.9D * pw, hot[0], hot[1], hot[2],
                        Math.max(0.0F, 1.0F - age / 5.0F));
                for (int i = 0; i < 3; i++) {
                    double d = age * 0.9D + i * 0.8D;
                    VfxDraw.ring(buf, m, f.a.add(way.scale(d)), way, 0.3D + d * 0.35D * pw, 0.06D, 0.0F, white[0],
                            white[1], white[2], 0.7F * fade);
                }
                VfxDraw.cone(buf, m, f.a, way, 2.0D * pw, 0.9D * pw, main[0], main[1], main[2], 0.25F * fade);
            }
            case FlashFx.PHASE -> {
                if (e == null) {
                    return;
                }
                Vec3 base = e.getPosition(pt);
                double h = e.getBbHeight();
                java.util.Random rnd = new java.util.Random((long) (t * 3.0F) + f.caster);
                // the body vibrating: blurred copies of its light shaking round it
                if (!me) {
                    for (int i = 0; i < 4; i++) {
                        Vec3 o = base.add((rnd.nextDouble() - 0.5D) * 0.5D, h * 0.5D + (rnd.nextDouble() - 0.5D)
                                * 0.3D, (rnd.nextDouble() - 0.5D) * 0.5D);
                        VfxDraw.glow(buf, m, o, left, up, h * 0.45D, main[0], main[1], main[2], 0.12F * on);
                    }
                }
                for (int i = 0; i < 5; i++) {
                    double y = h * (0.1D + 0.2D * i) + (rnd.nextDouble() - 0.5D) * 0.1D;
                    VfxDraw.ring(buf, m, base.add((rnd.nextDouble() - 0.5D) * 0.12D, y,
                                    (rnd.nextDouble() - 0.5D) * 0.12D), vup, 0.45D + rnd.nextDouble() * 0.12D, 0.03D,
                            (float) rnd.nextDouble() * 6.0F, white[0], white[1], white[2], 0.5F * on);
                }
                for (int i = 0; i < 2; i++) {
                    Vec3 p0 = base.add((rnd.nextDouble() - 0.5D) * 0.8D, rnd.nextDouble() * h,
                            (rnd.nextDouble() - 0.5D) * 0.8D);
                    Vec3 p1 = p0.add((rnd.nextDouble() - 0.5D) * 0.9D, (rnd.nextDouble() - 0.5D) * 0.9D,
                            (rnd.nextDouble() - 0.5D) * 0.9D);
                    bolt(buf, m, p0, p1, rnd.nextLong(), 1.3D, 0.025F, c, on);
                }
            }
            case FlashFx.TIME_SLOW -> {
                double r = Math.max(4.0D, f.power);
                Vec3 g = e != null ? e.getPosition(pt) : f.a;
                float grow = ease(Math.min(1.0F, (t - f.born) / 20.0F));
                double rr = r * grow;
                // the still dome of slowed time
                VfxDraw.sphere(buf, m, g, rr, cam, main[0], main[1], main[2], 0.22F * on, t * 0.15F, 3.0D);
                // the clock on the ground: its rim, twelve hours, two hands turning slowly
                Vec3 at = g.add(0.0D, 0.07D, 0.0D);
                VfxDraw.ring(buf, m, at, vup, rr, 0.1D, 0.0F, hot[0], hot[1], hot[2], 0.7F * on);
                VfxDraw.ring(buf, m, at, vup, rr * 0.9D, 0.04D, 0.0F, main[0], main[1], main[2], 0.5F * on);
                for (int i = 0; i < 12; i++) {
                    double ang = Math.PI * 2.0D * i / 12.0D;
                    double l = i % 3 == 0 ? 0.16D : 0.08D;
                    VfxDraw.bar(buf, m, at.x + Math.cos(ang) * rr * (0.9D - l), at.y, at.z + Math.sin(ang) * rr
                            * (0.9D - l), at.x + Math.cos(ang) * rr * 0.9D, at.z + Math.sin(ang) * rr * 0.9D,
                            0.09D, white[0], white[1], white[2], 0.8F * on);
                }
                double hand1 = t * 0.02D;
                double hand2 = t * 0.0025D;
                VfxDraw.bar(buf, m, at.x, at.y + 0.01D, at.z, at.x + Math.cos(hand1) * rr * 0.75D,
                        at.z + Math.sin(hand1) * rr * 0.75D, 0.08D, white[0], white[1], white[2], 0.7F * on);
                VfxDraw.bar(buf, m, at.x, at.y + 0.01D, at.z, at.x + Math.cos(hand2) * rr * 0.5D,
                        at.z + Math.sin(hand2) * rr * 0.5D, 0.14D, hot[0], hot[1], hot[2], 0.7F * on);
                // ripples rolling slowly out through it
                for (int i = 0; i < 2; i++) {
                    float kr = ((t * 0.012F) + i * 0.5F) % 1.0F;
                    VfxDraw.ring(buf, m, at, vup, rr * kr, 0.25D, 0.0F, main[0], main[1], main[2],
                            0.35F * on * (1.0F - kr));
                }
                // motes of light hanging still in the air
                java.util.Random rnd = new java.util.Random(f.caster * 977L);
                for (int i = 0; i < 26; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2.0D;
                    double d = Math.sqrt(rnd.nextDouble()) * rr * 0.95D;
                    double y = rnd.nextDouble() * Math.min(rr, 6.0D);
                    Vec3 p = g.add(Math.cos(ang + t * 0.002D) * d, y + Math.sin(t * 0.02D + i) * 0.15D,
                            Math.sin(ang + t * 0.002D) * d);
                    VfxDraw.glow(buf, m, p, left, up, 0.12D + rnd.nextDouble() * 0.1D, white[0], white[1], white[2],
                            0.6F * on);
                }
            }
            default -> {
            }
        }
    }
}

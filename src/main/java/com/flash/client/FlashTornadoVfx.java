package com.flash.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Flash's tornadoes, built of wind: curved blades of air whirling round a funnel (bright on their leading edge,
 * blurred behind like a camera's long exposure), streaks of dust caught in the spin and climbing it, the Speed
 * Force's lightning running over the outside, a glowing eye in the core and a mouth of turbulence at the open end.
 * The same funnel serves the two hand twisters (Wind Hands, lying along the look) and the speed tornado (standing,
 * swaying, its foot tearing up the ground).
 */
public final class FlashTornadoVfx {

    private static final float[] WIND = {0.82F, 0.92F, 1.0F};
    private static final float[] MIST = {0.6F, 0.72F, 0.85F};

    private FlashTornadoVfx() {
    }

    /** A funnel's frame: where it starts, along which axis, its two crosswise directions. */
    private record Frame(Vec3 o, Vec3 ax, Vec3 u, Vec3 v) {
        static Frame of(Vec3 origin, Vec3 axis) {
            Vec3 ax = axis.normalize();
            Vec3 ref = Math.abs(ax.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 u = ax.cross(ref).normalize();
            Vec3 v = ax.cross(u).normalize();
            return new Frame(origin, ax, u, v);
        }

        /** The point at distance d along the axis, angle th, radius r (sway: the axis bent sideways). */
        Vec3 at(double d, double th, double r, Vec3 sway) {
            return this.o.add(this.ax.scale(d)).add(sway).add(this.u.scale(Math.cos(th) * r))
                    .add(this.v.scale(Math.sin(th) * r));
        }
    }

    /** The shape of the funnel: radius at s (0 the narrow end .. 1 the open end). */
    private interface Shape {
        double r(double s);
    }

    /** The bend of the axis at s (the standing tornado sways like a rope; the twisters barely). */
    private interface Sway {
        Vec3 at(double s);
    }

    /**
     * The blades of air: n curved sheets wound round the funnel, each spanning span radians, leaning with the
     * twist, whirling with spin; bright on the leading edge, nothing on the trailing one.
     */
    private static void blades(BufferBuilder buf, Matrix4f m, Frame f, double len, Shape shape, Sway sway, int n,
                               double span, double twist, double spin, float[] col, float alpha, float fadeIn,
                               float fadeOut) {
        int steps = 26;
        for (int b = 0; b < n; b++) {
            double th0 = spin + b * Math.PI * 2.0D / n;
            double rOff = 1.0D + 0.08D * Math.sin(b * 2.3D);
            for (int i = 0; i < steps; i++) {
                double s0 = (double) i / steps;
                double s1 = (double) (i + 1) / steps;
                double a0 = th0 + s0 * twist;
                double a1 = th0 + s1 * twist;
                double r0 = shape.r(s0) * rOff;
                double r1 = shape.r(s1) * rOff;
                Vec3 w0 = sway.at(s0);
                Vec3 w1 = sway.at(s1);
                float e0 = edge(s0, fadeIn, fadeOut) * alpha;
                float e1 = edge(s1, fadeIn, fadeOut) * alpha;
                Vec3 lead0 = f.at(s0 * len, a0, r0, w0);
                Vec3 lead1 = f.at(s1 * len, a1, r1, w1);
                Vec3 tail0 = f.at(s0 * len, a0 - span, r0 * 0.96D, w0);
                Vec3 tail1 = f.at(s1 * len, a1 - span, r1 * 0.96D, w1);
                // the sheet, fading back from its leading edge
                VfxDraw.vert(buf, m, lead0, col[0], col[1], col[2], e0);
                VfxDraw.vert(buf, m, lead1, col[0], col[1], col[2], e1);
                VfxDraw.vert(buf, m, tail1, col[0], col[1], col[2], 0.0F);
                VfxDraw.vert(buf, m, tail0, col[0], col[1], col[2], 0.0F);
                // its bright edge: a thin hot line
                Vec3 in0 = f.at(s0 * len, a0 - span * 0.08D, r0, w0);
                Vec3 in1 = f.at(s1 * len, a1 - span * 0.08D, r1, w1);
                VfxDraw.vert(buf, m, lead0, 1.0F, 1.0F, 1.0F, e0 * 1.6F);
                VfxDraw.vert(buf, m, lead1, 1.0F, 1.0F, 1.0F, e1 * 1.6F);
                VfxDraw.vert(buf, m, in1, col[0], col[1], col[2], 0.0F);
                VfxDraw.vert(buf, m, in0, col[0], col[1], col[2], 0.0F);
            }
        }
    }

    /** Fades the ends of the funnel (in over the first fadeIn of it, out over the last fadeOut). */
    private static float edge(double s, float fadeIn, float fadeOut) {
        double a = fadeIn <= 0.0F ? 1.0D : Math.min(1.0D, s / fadeIn);
        double b = fadeOut <= 0.0F ? 1.0D : Math.min(1.0D, (1.0D - s) / fadeOut);
        return (float) Math.max(0.0D, a * b);
    }

    /** Streaks of dust whirled round and climbing the funnel: each an arc smeared along its way. */
    private static void streaks(BufferBuilder buf, Matrix4f m, Frame f, double len, Shape shape, Sway sway, int n,
                                double spin, double climb, float t, long seed, float[] col, float alpha, float w) {
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < n; i++) {
            double s0 = rnd.nextDouble();
            double th0 = rnd.nextDouble() * Math.PI * 2.0D;
            double rr = 0.75D + rnd.nextDouble() * 0.6D;
            double sp = spin * (0.7D + rnd.nextDouble() * 0.6D);
            double cl = climb * (0.6D + rnd.nextDouble() * 0.8D);
            double s = (s0 + t * cl) % 1.0D;
            double th = th0 + t * sp / Math.max(0.3D, shape.r(s));
            float a = alpha * (float) Math.sin(Math.PI * s);
            double arc = 0.35D + rnd.nextDouble() * 0.5D;
            Vec3 prev = null;
            for (int j = 0; j <= 4; j++) {
                double k = j / 4.0D;
                double ss = Math.max(0.0D, s - k * 0.02D);
                Vec3 p = f.at(ss * len, th - k * arc * Math.signum(sp), shape.r(ss) * rr, sway.at(ss));
                if (prev != null) {
                    VfxDraw.beam(buf, m, p, prev, w, col[0], col[1], col[2], a * (float) (1.0D - k));
                }
                prev = p;
            }
        }
    }

    /** The Speed Force's lightning crawling over the funnel's skin (new shapes every other tick). */
    private static void lightning(BufferBuilder buf, Matrix4f m, Frame f, double len, Shape shape, Sway sway,
                                  int n, float t, long seed, float[] c, float alpha) {
        java.util.Random rnd = new java.util.Random(seed + (long) (t / 2.0F) * 7919L);
        for (int i = 0; i < n; i++) {
            if (rnd.nextFloat() > 0.75F) {
                continue;
            }
            double s0 = 0.1D + rnd.nextDouble() * 0.75D;
            double s1 = Math.min(1.0D, s0 + 0.08D + rnd.nextDouble() * 0.2D);
            double th0 = rnd.nextDouble() * Math.PI * 2.0D;
            double th1 = th0 + 0.6D + rnd.nextDouble() * 1.2D;
            int seg = 8;
            Vec3[] pts = new Vec3[seg + 1];
            for (int j = 0; j <= seg; j++) {
                double k = (double) j / seg;
                double ss = s0 + (s1 - s0) * k;
                double jit = j == 0 || j == seg ? 0.0D : (rnd.nextDouble() - 0.5D) * 0.25D;
                pts[j] = f.at(ss * len + jit, th0 + (th1 - th0) * k + jit, shape.r(ss) * (1.03D + jit * 0.3D),
                        sway.at(ss));
            }
            VfxDraw.path(buf, m, pts, 0.14F, c[0], c[1], c[2], 0.35F * alpha);
            VfxDraw.path(buf, m, pts, 0.045F, VfxDraw.mix(c[0], 0.5F), VfxDraw.mix(c[1], 0.5F),
                    VfxDraw.mix(c[2], 0.5F), 0.9F * alpha);
            VfxDraw.path(buf, m, pts, 0.015F, 1.0F, 1.0F, 1.0F, alpha);
        }
    }

    // ------------------------------------------------------------------
    // Wind Hands: a twister out of each palm
    // ------------------------------------------------------------------

    /**
     * A twister lying along the look out of a hand (side: -1 left, 1 right, they turn against each other), in the
     * Flash's colour c for its lightning.
     */
    public static void hand(BufferBuilder buf, Matrix4f m, Vec3 palm, Vec3 look, Vec3 left, Vec3 up, float t,
                            int side, float[] c, float on, long seed) {
        if (on <= 0.01F) {
            return;
        }
        Frame f = Frame.of(palm, look);
        double len = 6.5D;
        float breathe = 1.0F + 0.06F * (float) Math.sin(t * 0.4D);
        Shape shape = s -> (0.07D + 1.45D * Math.pow(s, 1.5D)) * breathe;
        Sway sway = s -> f.u().scale(Math.sin(t * 0.21D + s * 4.0D) * 0.12D * s)
                .add(f.v().scale(Math.cos(t * 0.17D + s * 3.0D) * 0.12D * s));
        double spin = t * 0.75D * side;
        // the wind: two layers of blades turning at different speeds, a misty outer one
        blades(buf, m, f, len, s -> shape.r(s) * 1.18D, sway, 3, 1.9D * side, 3.2D * side, spin * 0.7D, MIST,
                0.10F * on, 0.15F, 0.35F);
        blades(buf, m, f, len, shape, sway, 5, 1.2D * side, 4.4D * side, spin, WIND, 0.22F * on, 0.08F, 0.25F);
        blades(buf, m, f, len, s -> shape.r(s) * 0.55D, sway, 3, 0.9D * side, 6.0D * side, spin * 1.5D, WIND,
                0.25F * on, 0.05F, 0.4F);
        streaks(buf, m, f, len, shape, sway, 26, 0.55D * side, 0.045D, t, seed, WIND, 0.55F * on, 0.025F);
        lightning(buf, m, f, len, shape, sway, 3, t, seed ^ 0x5DEECE66DL, c, on);
        // the eye in the palm and the turbulent mouth
        VfxDraw.flare(buf, m, palm, left, up, 0.22D, c[0], c[1], c[2], 0.85F * on);
        Vec3 mouth = f.at(len, 0.0D, 0.0D, sway.at(1.0D));
        VfxDraw.ring(buf, m, mouth, look, shape.r(1.0D) * 1.05D, 0.18D, (float) spin, WIND[0], WIND[1], WIND[2],
                0.18F * on);
        VfxDraw.ring(buf, m, f.at(len * 0.82D, 0.0D, 0.0D, sway.at(0.82D)), look, shape.r(0.82D), 0.05D, (float) spin,
                1.0F, 1.0F, 1.0F, 0.25F * on);
        // the core drawn in: rings running back into the palm
        for (int i = 0; i < 3; i++) {
            double k = 1.0D - ((t * 0.09D + i / 3.0D) % 1.0D);
            VfxDraw.ring(buf, m, f.at(k * len * 0.6D, 0.0D, 0.0D, sway.at(k * 0.6D)), look, shape.r(k * 0.6D) * 0.5D,
                    0.03D, 0.0F, c[0], c[1], c[2], 0.5F * on * (float) Math.sin(Math.PI * k));
        }
        VfxDraw.beam(buf, m, palm, f.at(len * 0.7D, 0.0D, 0.0D, sway.at(0.7D)), 0.12F, c[0], c[1], c[2], 0.25F * on);
    }

    // ------------------------------------------------------------------
    // The speed tornado: he runs round and round, a funnel rises
    // ------------------------------------------------------------------

    /** The standing tornado over base (the centre of his run), in the Flash's colour c. */
    public static void speedTornado(BufferBuilder buf, Matrix4f m, Vec3 base, Vec3 cam, Vec3 left, Vec3 up,
                                    float t, float[] c, float on, float grow, long seed) {
        speedTornado(buf, m, base, cam, left, up, t, c, on, grow, 1.0D, seed);
    }

    /** The same, wide times as wide (Thor's Mighty Tornado: wider and taller). */
    public static void speedTornado(BufferBuilder buf, Matrix4f m, Vec3 base, Vec3 cam, Vec3 left, Vec3 up,
                                    float t, float[] c, float on, float grow, double wide, long seed) {
        if (on <= 0.01F) {
            return;
        }
        double height = 10.0D * grow;
        if (height < 0.3D) {
            return;
        }
        Frame f = Frame.of(base, new Vec3(0.0D, 1.0D, 0.0D));
        Shape shape = s -> (0.55D + 1.6D * s + 2.3D * s * s) * wide;
        // the rope sway: the top wanders, the foot stays on the run
        Sway sway = s -> new Vec3(Math.sin(t * 0.06D + s * 2.2D), 0.0D, Math.cos(t * 0.05D + s * 1.8D))
                .scale(0.55D * s * s);
        double spin = t * 0.5D;
        blades(buf, m, f, height, s -> shape.r(s) * 1.15D, sway, 4, 2.0D, 3.0D, spin * 0.6D, MIST, 0.09F * on,
                0.05F, 0.2F);
        blades(buf, m, f, height, shape, sway, 6, 1.3D, 4.0D, spin, WIND, 0.2F * on, 0.03F, 0.15F);
        blades(buf, m, f, height, s -> shape.r(s) * 0.6D, sway, 4, 1.0D, 5.5D, spin * 1.35D, WIND, 0.18F * on,
                0.02F, 0.3F);
        // the Speed Force at its foot: his colour swirling up the first blocks
        blades(buf, m, f, height * 0.3D, s -> shape.r(s * 0.3D) * 1.05D, sway, 4, 1.4D, 2.5D, spin * 1.6D, c,
                0.3F * on, 0.0F, 0.8F);
        streaks(buf, m, f, height, shape, sway, 60, 0.5D, 0.02D, t, seed, WIND, 0.5F * on, 0.035F);
        lightning(buf, m, f, height, shape, sway, 5, t, seed ^ 0x2545F491L, c, on);
        // the eye glowing up the middle
        VfxDraw.pillar(buf, m, base, base.y, base.y + height * 0.8D, cam, 0.35F, c[0], c[1], c[2], 0.25F * on);
        // the top: a wide mouth of turbulence
        Vec3 top = base.add(sway.at(1.0D)).add(0.0D, height, 0.0D);
        VfxDraw.ring(buf, m, top, f.ax(), shape.r(1.0D) * 1.1D, 0.6D, (float) spin, MIST[0], MIST[1], MIST[2],
                0.16F * on);
        VfxDraw.ring(buf, m, top.subtract(0.0D, 0.6D, 0.0D), f.ax(), shape.r(0.94D), 0.08D, (float) spin, 1.0F, 1.0F,
                1.0F, 0.3F * on);
        // the foot: the ground torn up in a ring of dust, his colour glowing under it
        Vec3 g = base.add(0.0D, 0.05D, 0.0D);
        VfxDraw.glow(buf, m, g, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), 3.2D, c[0], c[1], c[2],
                0.4F * on);
        VfxDraw.wall(buf, m, g, 2.3D * wide, 0.9D, MIST[0], MIST[1], MIST[2], 0.3F * on);
        VfxDraw.ring(buf, m, g, f.ax(), 1.6D * wide, 0.25D, (float) (spin * 2.0D), c[0], c[1], c[2], 0.5F * on);
        for (int i = 0; i < 3; i++) {
            float k = (float) ((t * 0.04D + i / 3.0D) % 1.0D);
            VfxDraw.ring(buf, m, g, f.ax(), (1.0D + 3.0D * k) * wide, 0.2D, 0.0F, MIST[0], MIST[1], MIST[2],
                    0.35F * on * (1.0F - k));
        }
    }
}

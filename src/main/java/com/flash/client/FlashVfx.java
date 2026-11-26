package com.flash.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Flash's light (VfxDraw: the Lanterns' glows, rays and rings), drawn around his lightning: a soft glow along
 * the trail and a flare at the runner, a halo of Speed Force round the body, a glow along the lightning beam with
 * flares and rings running down it, the twisters of the hands glowing and the fists of the barrage flaring.
 * Yellow-orange; red for the Negative Force; blue under the V-9.
 */
public final class FlashVfx {

    private FlashVfx() {
    }

    public static float[] color(boolean negative, boolean v9) {
        return v9 ? new float[] {0.35F, 0.65F, 1.0F}
                : negative ? new float[] {1.0F, 0.15F, 0.08F} : new float[] {1.0F, 0.72F, 0.15F};
    }

    public static Vec3 left(Camera c) {
        return new Vec3(c.getLeftVector());
    }

    public static Vec3 up(Camera c) {
        return new Vec3(c.getUpVector());
    }

    /**
     * A segment of the trail: a wide heat haze, the coloured band, a hotter inner band and the white-hot core, as
     * the light a speedster leaves behind in the films.
     */
    public static void trail(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float fade, float[] c) {
        VfxDraw.beam(buf, m, a, b, 0.85F, c[0], c[1] * 0.85F, c[2], 0.06F * fade);
        VfxDraw.beam(buf, m, a, b, 0.42F, c[0], c[1], c[2], 0.15F * fade);
        VfxDraw.beam(buf, m, a, b, 0.17F, VfxDraw.mix(c[0], 0.4F), VfxDraw.mix(c[1], 0.4F), VfxDraw.mix(c[2], 0.4F),
                0.3F * fade * fade);
        VfxDraw.beam(buf, m, a, b, 0.05F, 1.0F, 1.0F, 1.0F, 0.35F * fade * fade * fade);
    }

    /** A glint of Speed Force left in the trail (seeded by where it is). */
    public static void glint(BufferBuilder buf, Matrix4f m, Vec3 at, Camera cam, float fade, float[] c, float t) {
        float tw = 0.6F + 0.4F * Mth.sin(t * 0.9F + (float) (at.x * 3.0D + at.z * 5.0D));
        VfxDraw.flare(buf, m, at, left(cam), up(cam), 0.18D, c[0], c[1], c[2], 0.7F * fade * tw);
    }

    /** The runner himself: a flare at the chest with its long streak, a crackling burst and a halo. */
    public static void runner(BufferBuilder buf, Matrix4f m, Player p, float pt, Camera cam, float[] c, float t) {
        Vec3 chest = p.getPosition(pt).add(0.0D, 1.0D, 0.0D);
        VfxDraw.flare(buf, m, chest, left(cam), up(cam), 0.55D, c[0], c[1], c[2], 0.55F);
        VfxDraw.burst(buf, m, chest, left(cam), up(cam), 1.5F, c[0], c[1], c[2], 0.3F, t * 0.3F);
        VfxDraw.glow(buf, m, chest, left(cam), up(cam), 1.6D, c[0], c[1], c[2], 0.12F);
    }

    /** The halo of Speed Force round the body: crossed orbits of light with sparks riding them. */
    public static void halo(BufferBuilder buf, Matrix4f m, Player p, float pt, Camera cam, float[] c, int level) {
        float t = p.tickCount + pt;
        Vec3 chest = p.getPosition(pt).add(0.0D, 1.0D, 0.0D);
        float k = Mth.clamp(level / 10.0F, 0.2F, 1.0F);
        float flick = 0.8F + 0.2F * Mth.sin(t * 1.7F) * Mth.sin(t * 0.9F);
        VfxDraw.glow(buf, m, chest, left(cam), up(cam), 1.3D, c[0], c[1], c[2], 0.14F * k * flick);
        for (int i = 0; i < 3; i++) {
            double ph = t * (0.11D + i * 0.03D) + i * 2.1D;
            Vec3 n = new Vec3(Math.sin(ph), 1.2D + i * 0.4D, Math.cos(ph * 0.7D));
            VfxDraw.ring(buf, m, p.getPosition(pt).add(0.0D, 0.9D, 0.0D), n, 0.62D + i * 0.06D, 0.022D, t * 0.4F,
                    c[0], c[1], c[2], 0.35F * k * flick);
            double sa = t * 0.5D + i * 2.0D;
            Vec3 spark = chest.add(Math.cos(sa) * 0.65D, Math.sin(sa * 1.3D) * 0.5D, Math.sin(sa) * 0.65D);
            VfxDraw.glow(buf, m, spark, left(cam), up(cam), 0.12D, 1.0F, 1.0F, 1.0F, 0.8F * k * flick);
        }
    }

    /**
     * The lightning beam: a band of energy, two spirals of Speed Force winding down it, rings racing along, a
     * flare at the hand and a burst, a shock ring and sparks where it lands.
     */
    public static void beam(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float width, float fade, float time,
                            Camera cam, float[] c) {
        Vec3 l = left(cam);
        Vec3 u = up(cam);
        VfxDraw.energy(buf, m, a, b, 0.24F * width, c[0], c[1], c[2], 0.5F * fade);
        double len = a.distanceTo(b);
        for (int s = 0; s < 2; s++) {
            Vec3[] hx = VfxDraw.helix(a, b, 0.25D * width + 0.12D, Math.max(2.0D, len / 2.5D), time * 0.6D
                    + s * Math.PI, 48);
            VfxDraw.path(buf, m, hx, 0.07F * width, c[0], c[1], c[2], 0.45F * fade);
            VfxDraw.path(buf, m, hx, 0.025F * width, 1.0F, 1.0F, 1.0F, 0.6F * fade);
        }
        if (len > 1.0D) {
            Vec3 dn = b.subtract(a).scale(1.0D / len);
            for (int i = 0; i < 4; i++) {
                double pos = (time * 1.8D + i * len / 4.0D) % len;
                VfxDraw.ring(buf, m, a.add(dn.scale(pos)), dn, 0.35D * width + 0.2D, 0.04D, 0.0F, c[0], c[1], c[2],
                        0.5F * fade);
            }
            VfxDraw.ring(buf, m, b, dn, (0.6D + 1.4D * (1.0F - fade)) * (width + 0.5D), 0.08D, 0.0F, 1.0F, 1.0F,
                    1.0F, 0.6F * fade);
        }
        VfxDraw.flare(buf, m, a, l, u, 0.45D * width + 0.35D, c[0], c[1], c[2], 0.8F * fade);
        VfxDraw.burst(buf, m, b, l, u, 1.3F * width + 0.7F, c[0], c[1], c[2], 0.8F * fade, -time * 0.3F);
        VfxDraw.flare(buf, m, b, l, u, 0.7D * width + 0.5D, c[0], c[1], c[2], fade);
        VfxDraw.sparks(buf, m, b, (long) (b.x * 13.0D + b.z * 7.0D), 18, 1.5D + width, 1.0F - fade, 0.8D, c[0],
                c[1], c[2], 1.0F);
    }

    /** A twister out of a hand (look: its axis): rings opening along it, a spiral of light, the glow of the palm. */
    public static void twister(BufferBuilder buf, Matrix4f m, Vec3 hand, Vec3 look, float t, float[] c) {
        for (int i = 0; i < 6; i++) {
            double s = ((t * 0.08D + i / 6.0D) % 1.0D);
            VfxDraw.ring(buf, m, hand.add(look.scale(0.3D + s * 2.6D)), look, 0.12D + s * 0.75D, 0.03D, t * 0.5F,
                    0.85F, 0.95F, 1.0F, 0.5F * (float) Math.sin(Math.PI * s));
        }
        Vec3[] hx = VfxDraw.helix(hand, hand.add(look.scale(3.0D)), 0.45D, 3.0D, t * 0.5D, 30);
        VfxDraw.path(buf, m, hx, 0.05F, 0.8F, 0.95F, 1.0F, 0.35F);
        VfxDraw.glow(buf, m, hand, new Vec3(look.z, 0.0D, -look.x).normalize(), new Vec3(0.0D, 1.0D, 0.0D), 0.4D,
                c[0], c[1], c[2], 0.45F);
    }

    /** A fist of the barrage flaring, a ring of shock off it. */
    public static void fist(BufferBuilder buf, Matrix4f m, Vec3 at, Camera cam, float t, float[] c) {
        VfxDraw.burst(buf, m, at, left(cam), up(cam), 0.55F, c[0], c[1], c[2], 0.6F, t * 0.9F);
        VfxDraw.flare(buf, m, at, left(cam), up(cam), 0.22D, c[0], c[1], c[2], 0.6F);
        float k = (t * 0.4F) % 1.0F;
        VfxDraw.ring(buf, m, at, new Vec3(cam.getLookVector()), 0.15D + 0.5D * k, 0.03D, 0.0F, 1.0F, 1.0F, 1.0F,
                0.5F * (1.0F - k));
    }
}

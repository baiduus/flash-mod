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

    /** A segment of the trail: a soft wide glow under the lightning. */
    public static void trail(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float fade, float[] c) {
        VfxDraw.beam(buf, m, a, b, 0.45F, c[0], c[1], c[2], 0.13F * fade);
        VfxDraw.beam(buf, m, a, b, 0.16F, c[0], c[1], c[2], 0.18F * fade);
    }

    /** The runner himself: a flare at the chest. */
    public static void runner(BufferBuilder buf, Matrix4f m, Player p, float pt, Camera cam, float[] c, float t) {
        Vec3 chest = p.getPosition(pt).add(0.0D, 1.0D, 0.0D);
        VfxDraw.burst(buf, m, chest, left(cam), up(cam), 1.4F, c[0], c[1], c[2], 0.35F, t * 0.3F);
    }

    /** The halo of Speed Force round the body. */
    public static void halo(BufferBuilder buf, Matrix4f m, Player p, float pt, Camera cam, float[] c, int level) {
        float t = p.tickCount + pt;
        Vec3 chest = p.getPosition(pt).add(0.0D, 1.0D, 0.0D);
        float k = Mth.clamp(level / 10.0F, 0.2F, 1.0F);
        float flick = 0.8F + 0.2F * Mth.sin(t * 1.7F) * Mth.sin(t * 0.9F);
        VfxDraw.glow(buf, m, chest, left(cam), up(cam), 1.2D, c[0], c[1], c[2], 0.12F * k * flick);
        VfxDraw.ring(buf, m, p.getPosition(pt).add(0.0D, 0.9D, 0.0D),
                new Vec3(Math.sin(t * 0.13D), 1.0D, Math.cos(t * 0.13D)), 0.65D, 0.025D, t * 0.4F, c[0], c[1], c[2],
                0.35F * k * flick);
    }

    /** The lightning beam: a glow along it, flares at both ends, rings running down it. */
    public static void beam(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float width, float fade, float time,
                            Camera cam, float[] c) {
        Vec3 l = left(cam);
        Vec3 u = up(cam);
        VfxDraw.energy(buf, m, a, b, 0.22F * width, c[0], c[1], c[2], 0.45F * fade);
        double len = a.distanceTo(b);
        if (len > 1.0D) {
            Vec3 dn = b.subtract(a).scale(1.0D / len);
            for (int i = 0; i < 3; i++) {
                double pos = (time * 1.5D + i * len / 3.0D) % len;
                VfxDraw.ring(buf, m, a.add(dn.scale(pos)), dn, 0.35D * width + 0.2D, 0.04D, 0.0F, c[0], c[1], c[2],
                        0.45F * fade);
            }
        }
        VfxDraw.burst(buf, m, a, l, u, 0.6F * width + 0.4F, c[0], c[1], c[2], 0.6F * fade, time * 0.4F);
        VfxDraw.burst(buf, m, b, l, u, 1.2F * width + 0.6F, c[0], c[1], c[2], 0.8F * fade, -time * 0.3F);
        VfxDraw.glow(buf, m, b, l, u, 0.9D * width + 0.5D, c[0], c[1], c[2], 0.35F * fade);
    }

    /** A twister out of a hand (look: its axis). */
    public static void twister(BufferBuilder buf, Matrix4f m, Vec3 hand, Vec3 look, float t, float[] c) {
        for (int i = 0; i < 4; i++) {
            double s = ((t * 0.08D + i / 4.0D) % 1.0D);
            VfxDraw.ring(buf, m, hand.add(look.scale(0.3D + s * 2.4D)), look, 0.12D + s * 0.7D, 0.03D, t * 0.5F,
                    0.85F, 0.95F, 1.0F, 0.5F * (float) Math.sin(Math.PI * s));
        }
        VfxDraw.glow(buf, m, hand, new Vec3(look.z, 0.0D, -look.x).normalize(), new Vec3(0.0D, 1.0D, 0.0D), 0.35D,
                c[0], c[1], c[2], 0.4F);
    }

    /** A fist of the barrage flaring. */
    public static void fist(BufferBuilder buf, Matrix4f m, Vec3 at, Camera cam, float t, float[] c) {
        VfxDraw.burst(buf, m, at, left(cam), up(cam), 0.5F, c[0], c[1], c[2], 0.6F, t * 0.9F);
    }
}

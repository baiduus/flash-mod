package com.flash.client;

import com.flash.network.YasFxPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The transcendence drawn in light (VfxDraw: the Green Lanterns' soft beams, rays and fresnel spheres, God Wand's
 * jagged lightning and screen shockwaves, the hex circles of this mod), on the same ten seconds as before:
 * <ul>
 * <li>rising: a golden hex circle opening on the ground under him, three rings of gold turning round his body
 * like a gyroscope, pale lightning crackling over him (more and more), motes of light winding up, a halo of
 * rays growing at his chest and a shell of light thickening round him;</li>
 * <li>at five seconds the white beam: a pillar of light from the sky through his body, flaring as it lands (and a
 * shockwave over the screen), pulses running down it;</li>
 * <li>done: a sphere of light bursting out, a ring running over the ground, rays, the beam fading upwards, the
 * screen struck by God Wand's impact frames;</li>
 * <li>doomed (not ready): violet lightning closing in on him, rings of violet shrinking round him, a violet glow
 * along the Guardian's black beam; the end: a violet implosion and a shockwave.</li>
 * </ul>
 * The black of the doom (the beam, the black lightning) stays drawn dark by YasClient.
 */
public final class TranscendVfx {

    private static final float[] GOLD = {1.0F, 0.78F, 0.28F};
    private static final float[] PALE = {1.0F, 0.96F, 0.65F};
    private static final float[] VIOLET = {0.55F, 0.05F, 0.9F};

    private record End(Vec3 at, boolean light, long start) {
    }

    private static final List<End> ENDS = new ArrayList<>();

    private TranscendVfx() {
    }

    /** The end of a sequence at that spot (light: transcended; else the doom). */
    public static void end(Vec3 at, boolean light, long now) {
        ENDS.add(new End(at, light, now));
        var mc = net.minecraft.client.Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(at) < 96.0D * 96.0D) {
            com.flash.client.godwand.GodWandPostFxManager.triggerImpact(at.x, at.y + 1.0D, at.z, light ? 5 : 2,
                    light ? 24 : 16, light ? 1.4F : 1.0F);
        }
    }

    public static boolean busy() {
        return !ENDS.isEmpty();
    }

    /** The white beam reaching him (a shockwave over the screen once). */
    public static void beamLands(Vec3 at) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) < 96.0D * 96.0D) {
            com.flash.client.godwand.GodWandPostFxManager.triggerImpact(at.x, at.y + 1.0D, at.z, 2, 16, 1.2F);
        }
    }

    public static BufferBuilder begin() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    public static void finish(BufferBuilder buf) {
        try {
            BufferUploader.drawWithShader(buf.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static Vec3 vec(Vector3f v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Rising (age in ticks of ticks): drawn around the body's feet at "at", the anchor on the ground below. */
    public static void ascend(BufferBuilder buf, Matrix4f m, Camera camera, Vec3 at, float age, int ticks, long id) {
        Vec3 cam = camera.getPosition();
        Vec3 left = vec(camera.getLeftVector());
        Vec3 up = vec(camera.getUpVector());
        float k = Mth.clamp(age / ticks, 0.0F, 1.0F);
        Vec3 chest = at.add(0.0D, 1.1D, 0.0D);
        Vec3 ground = new Vec3(at.x, at.y - Math.min(3.0D, 3.0D * Mth.clamp(age / 60.0F, 0.0F, 1.0F)), at.z);
        float open = Mth.clamp(age / 25.0F, 0.0F, 1.0F);
        // the golden hex circle under him
        VfxDraw.hexCircle(buf, m, ground, 1.5D + 2.5D * open, age, GOLD[0], GOLD[1], GOLD[2], 0.9F * open);
        VfxDraw.wall(buf, m, ground, 1.5D + 2.5D * open, 1.2D + 2.0D * k, GOLD[0], GOLD[1], GOLD[2], 0.25F * open);
        // three rings of gold turning round him
        for (int r = 0; r < 3; r++) {
            double tilt = age * (0.03D + r * 0.012D) + r * 2.1D;
            Vec3 n = new Vec3(Math.sin(tilt), Math.cos(tilt * 0.7D + r), Math.cos(tilt));
            VfxDraw.ring(buf, m, chest, n, 1.0D + r * 0.28D, 0.045D, age * 0.1F, GOLD[0], GOLD[1], GOLD[2],
                    0.75F * open);
        }
        // the pale lightning crackling over him, more and more
        Random rnd = new Random((long) (age / 2.0F) * 7919L + id);
        int bolts = 3 + (int) (k * 9.0F);
        for (int i = 0; i < bolts; i++) {
            double a0 = rnd.nextDouble() * Math.PI * 2.0D;
            double a1 = a0 + (rnd.nextDouble() - 0.5D) * 2.2D;
            double rad = 0.45D + rnd.nextDouble() * 0.5D + k * 0.5D;
            Vec3 from = at.add(Math.cos(a0) * rad, rnd.nextDouble() * 2.0D, Math.sin(a0) * rad);
            Vec3 to = at.add(Math.cos(a1) * rad * 0.8D, rnd.nextDouble() * 2.0D, Math.sin(a1) * rad * 0.8D);
            VfxDraw.path(buf, m, VfxDraw.jagged(from, to, rnd.nextLong(), 1.2D, 3), 0.05F + k * 0.03F, PALE[0],
                    PALE[1], PALE[2], 0.9F);
        }
        // motes of light winding up him
        for (int j = 0; j < 14; j++) {
            double ph = (age * 0.03D + j / 14.0D) % 1.0D;
            double ang = age * 0.2D + j * Math.PI * 2.0D / 14.0D;
            double rr = 0.9D - ph * 0.4D;
            Vec3 q = ground.add(Math.cos(ang) * rr, ph * (at.y - ground.y + 2.6D), Math.sin(ang) * rr);
            VfxDraw.glow(buf, m, q, left, up, 0.12D, 1.0F, 0.95F, 0.7F, 0.9F * (float) Math.sin(ph * Math.PI));
        }
        // a halo of rays and a shell of light thickening round him
        VfxDraw.burst(buf, m, chest, left, up, 1.2F + 2.2F * k, GOLD[0], GOLD[1], GOLD[2], 0.35F + 0.4F * k,
                age * 0.05F);
        VfxDraw.sphere(buf, m, chest, 1.25D, cam, PALE[0], PALE[1], PALE[2], 0.15F + 0.45F * k, age, 2.4D);
        VfxDraw.glow(buf, m, chest, left, up, 1.6D + k, GOLD[0], GOLD[1], GOLD[2], 0.25F + 0.25F * k);
        // the white beam from the sky, from halfway
        if (age >= ticks * 0.5F) {
            float in = Mth.clamp((age - ticks * 0.5F) / 8.0F, 0.0F, 1.0F);
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.9F);
            double top = at.y + 120.0D;
            double bottom = ground.y - 2.0D;
            VfxDraw.pillar(buf, m, at, bottom, top, cam, 2.6F * in, GOLD[0], GOLD[1], GOLD[2], 0.25F * in);
            VfxDraw.pillar(buf, m, at, bottom, top, cam, 1.0F * in * pulse, 1.0F, 1.0F, 0.9F, 0.65F * in);
            VfxDraw.pillar(buf, m, at, bottom, top, cam, 0.35F * in, 1.0F, 1.0F, 1.0F, 0.95F * in);
            // pulses running down it
            for (int p = 0; p < 3; p++) {
                double y = top - ((age * 3.0D + p * 40.0D) % (top - bottom));
                VfxDraw.ring(buf, m, new Vec3(at.x, y, at.z), new Vec3(0.0D, 1.0D, 0.0D), 1.1D, 0.12D, 0.0F, 1.0F,
                        1.0F, 0.85F, 0.6F * in);
            }
            float land = Mth.clamp(1.0F - (age - ticks * 0.5F) / 12.0F, 0.0F, 1.0F);
            if (land > 0.0F) {
                VfxDraw.burst(buf, m, chest, left, up, 6.0F * (1.2F - land), 1.0F, 1.0F, 0.9F, 0.9F * land,
                        age * 0.1F);
                VfxDraw.ring(buf, m, ground.add(0.0D, 0.06D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 8.0D * (1.0F - land)
                        + 1.0D, 0.3D, 0.0F, 1.0F, 1.0F, 0.85F, 0.8F * land);
            }
        }
    }

    /** Doomed: the violet parts (the black ones are drawn dark elsewhere). */
    public static void doom(BufferBuilder buf, Matrix4f m, Camera camera, Vec3 at, Vec3 chest, Vec3 eye, float age,
                            int ticks, long id) {
        Vec3 left = vec(camera.getLeftVector());
        Vec3 up = vec(camera.getUpVector());
        Vec3 cam = camera.getPosition();
        float k = Mth.clamp(age / ticks, 0.0F, 1.0F);
        float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        VfxDraw.energy(buf, m, eye, chest, 0.3F * in, VIOLET[0], VIOLET[1], VIOLET[2], 0.55F * in);
        Vec3[] crack = VfxDraw.jagged(eye, chest, id + (long) (age / 2.0F), 0.8D, 4);
        VfxDraw.path(buf, m, crack, 0.06F, VIOLET[0], VIOLET[1], VIOLET[2], 0.5F * in);
        // rings shrinking round him
        for (int r = 0; r < 3; r++) {
            float kk = ((age * 0.05F + r / 3.0F) % 1.0F);
            VfxDraw.ring(buf, m, chest, new Vec3(Math.sin(r * 2.0D), 1.0D, Math.cos(r * 2.0D)),
                    2.6D * (1.0D - kk) + 0.4D, 0.06D, 0.0F, VIOLET[0], VIOLET[1], VIOLET[2], 0.7F * kk * in);
        }
        Random rnd = new Random((long) (age / 2.0F) * 104729L + id);
        int bolts = 2 + (int) (k * 6.0F);
        for (int i = 0; i < bolts; i++) {
            double a0 = rnd.nextDouble() * Math.PI * 2.0D;
            double rad = 1.2D + rnd.nextDouble() * 1.5D;
            Vec3 from = at.add(Math.cos(a0) * rad, rnd.nextDouble() * 2.4D, Math.sin(a0) * rad);
            VfxDraw.path(buf, m, VfxDraw.jagged(from, chest, rnd.nextLong(), 1.0D, 3), 0.04F, VIOLET[0], VIOLET[1],
                    VIOLET[2], 0.7F);
        }
        VfxDraw.sphere(buf, m, chest, 1.4D - 0.7D * k, cam, VIOLET[0], VIOLET[1], VIOLET[2], 0.3F + 0.3F * k, age,
                2.0D);
        VfxDraw.burst(buf, m, chest, left, up, 1.5F, VIOLET[0], VIOLET[1], VIOLET[2], 0.4F * in, -age * 0.08F);
    }

    /** The ends: the light bursting out, or the violet implosion. */
    public static void ends(BufferBuilder buf, Matrix4f m, Camera camera, float time) {
        if (ENDS.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Vec3 left = vec(camera.getLeftVector());
        Vec3 up = vec(camera.getUpVector());
        Vec3 yUp = new Vec3(0.0D, 1.0D, 0.0D);
        for (Iterator<End> it = ENDS.iterator(); it.hasNext(); ) {
            End e = it.next();
            float age = time - e.start();
            int life = e.light() ? 40 : 24;
            if (age > life) {
                it.remove();
                continue;
            }
            float k = Mth.clamp(age / life, 0.0F, 1.0F);
            float grow = 1.0F - (1.0F - k) * (1.0F - k);
            float fade = (1.0F - k) * (1.0F - k);
            Vec3 c = e.at().add(0.0D, 1.0D, 0.0D);
            if (e.light()) {
                VfxDraw.sphere(buf, m, c, 0.6D + 9.0D * grow, cam, PALE[0], PALE[1], PALE[2], 0.8F * fade, age, 2.2D);
                VfxDraw.sphere(buf, m, c, 0.4D + 4.0D * grow, cam, 1.0F, 1.0F, 1.0F, 0.6F * fade, age, 1.2D);
                VfxDraw.ring(buf, m, e.at().add(0.0D, 0.06D, 0.0D), yUp, 1.0D + 16.0D * grow, 0.5D * (1.0F - k) + 0.05D,
                        0.0F, GOLD[0], GOLD[1], GOLD[2], 0.9F * fade);
                VfxDraw.burst(buf, m, c, left, up, 3.0F + 9.0F * grow, 1.0F, 1.0F, 0.9F, 0.9F * fade, age * 0.05F);
                VfxDraw.pillar(buf, m, e.at(), e.at().y + 120.0D * grow, e.at().y + 140.0D, cam, 1.2F * fade, 1.0F,
                        1.0F, 0.95F, 0.8F * fade);
            } else {
                double r = k < 0.4F ? 2.5D * (1.0D - k / 0.4D) + 0.2D : 0.2D + 5.0D * (k - 0.4D) / 0.6D;
                float al = k < 0.4F ? 0.7F : 0.8F * (1.0F - (k - 0.4F) / 0.6F);
                VfxDraw.sphere(buf, m, c, r, cam, VIOLET[0], VIOLET[1], VIOLET[2], al, age, 1.8D);
                if (k >= 0.4F) {
                    VfxDraw.ring(buf, m, e.at().add(0.0D, 0.06D, 0.0D), yUp, r * 1.6D, 0.25D, 0.0F, VIOLET[0],
                            VIOLET[1], VIOLET[2], al);
                    VfxDraw.burst(buf, m, c, left, up, (float) r * 1.5F, VIOLET[0], VIOLET[1], VIOLET[2], al,
                            age * 0.1F);
                }
            }
        }
    }

    public static void clear() {
        ENDS.clear();
    }
}

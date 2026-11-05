package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shared visual language of the Flash electricity.
 *
 * <p>Both the body aura and the running trail draw their geometry through this
 * class, so the texture, colour ramp, glow passes, widths and branch style are
 * guaranteed to be identical. Only the geometry generators differ: one builds
 * arcs around the body, the other builds fixed segments along the travelled
 * path.</p>
 */
public final class FlashLightningRibbon {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(FlashMod.MODID, "textures/effect/lightning_ribbon.png");
    public static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    public static final float[] GLOW_COLOR = {1.0F, 0.64F, 0.02F, 0.20F};
    public static final float[] MID_COLOR = {1.0F, 0.84F, 0.14F, 0.40F};
    public static final float[] CORE_COLOR = {1.0F, 0.98F, 0.72F, 0.92F};

    public static final float[] RED_GLOW_COLOR = {0.85F, 0.03F, 0.05F, 0.22F};
    public static final float[] RED_MID_COLOR = {1.0F, 0.12F, 0.10F, 0.42F};
    public static final float[] RED_CORE_COLOR = {1.0F, 0.72F, 0.66F, 0.92F};

    public static final float BODY_GLOW_WIDTH = 0.052F;
    public static final float BODY_MID_WIDTH = 0.022F;
    public static final float BODY_CORE_WIDTH = 0.0085F;
    public static final float BRANCH_GLOW_WIDTH = 0.027F;
    public static final float BRANCH_MID_WIDTH = 0.012F;
    public static final float BRANCH_CORE_WIDTH = 0.005F;

    /** Orange (base of a bolt) to yellow (tip) used by the body and trail lightning. */
    private static final float[] BOLT_ORANGE = {1.0F, 0.42F, 0.04F};
    private static final float[] BOLT_YELLOW = {1.0F, 0.88F, 0.16F};
    private static final float[] BOLT_WHITE = {1.0F, 0.97F, 0.70F};
    /** Negative Acceleration Force: dark red at the base, bright red at the tip. */
    private static final float[] BOLT_RED_DARK = {0.62F, 0.02F, 0.03F};
    private static final float[] BOLT_RED_BRIGHT = {1.0F, 0.14F, 0.10F};
    private static final float[] BOLT_RED_WHITE = {1.0F, 0.66F, 0.60F};

    private FlashLightningRibbon() {
    }

    /**
     * Draws a thin bolt whose colour runs from orange at the first point to
     * yellow at the last one (glow, body and a pale core).
     */
    public static void drawBolt(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                List<Vec3> points, float width, float alpha) {
        drawBolt(consumer, matrix, cameraPos, points, width, alpha, false);
    }

    /** Same bolt, red instead of orange / yellow when {@code negative} is set. */
    public static void drawBolt(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                List<Vec3> points, float width, float alpha, boolean negative) {
        if (alpha <= 0.001F || points.size() < 2) {
            return;
        }
        drawGradientPass(consumer, matrix, cameraPos, points, width * 2.4F, 0.0F, 0.22F * alpha, negative);
        drawGradientPass(consumer, matrix, cameraPos, points, width, 0.0F, 0.60F * alpha, negative);
        drawGradientPass(consumer, matrix, cameraPos, points, width * 0.38F, 0.65F, 0.95F * alpha, negative);
    }

    private static void drawGradientPass(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                         List<Vec3> points, float width, float whiten, float alpha,
                                         boolean negative) {
        float[] from = negative ? BOLT_RED_DARK : BOLT_ORANGE;
        float[] to = negative ? BOLT_RED_BRIGHT : BOLT_YELLOW;
        float[] white = negative ? BOLT_RED_WHITE : BOLT_WHITE;
        int n = points.size();
        for (int i = 1; i < n; i++) {
            Vec3 start = points.get(i - 1);
            Vec3 end = points.get(i);
            Vec3 middle = start.add(end).scale(0.5D);
            Vec3 side = end.subtract(start).cross(middle.subtract(cameraPos));
            if (side.lengthSqr() < 1.0E-8D) {
                continue;
            }
            side = side.normalize().scale(width);
            float t = (i - 0.5F) / (n - 1);
            float red = mix(mix(from[0], to[0], t), white[0], whiten);
            float green = mix(mix(from[1], to[1], t), white[1], whiten);
            float blue = mix(mix(from[2], to[2], t), white[2], whiten);

            vertex(consumer, matrix, start.add(side), red, green, blue, alpha, 0.0F, 0.0F);
            vertex(consumer, matrix, start.subtract(side), red, green, blue, alpha, 1.0F, 0.0F);
            vertex(consumer, matrix, end.subtract(side), red, green, blue, alpha, 1.0F, 1.0F);
            vertex(consumer, matrix, end.add(side), red, green, blue, alpha, 0.0F, 1.0F);
        }
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static void drawArc(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                               List<Vec3> points, float glowWidth, float midWidth,
                               float coreWidth, float alpha) {
        drawArc(consumer, matrix, cameraPos, points, glowWidth, midWidth,
                coreWidth, GLOW_COLOR, MID_COLOR, CORE_COLOR, alpha);
    }

    /**
     * Colour-parameterised variant used by the Acceleration Force portal for
     * its electric-blue discharges while keeping the exact same ribbon
     * geometry and texture as the approved yellow body lightning.
     */
    public static void drawArc(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                               List<Vec3> points, float glowWidth, float midWidth,
                               float coreWidth, float[] glowColor, float[] midColor,
                               float[] coreColor, float alpha) {
        if (alpha <= 0.001F || points.size() < 2) {
            return;
        }
        drawPass(consumer, matrix, cameraPos, points, glowWidth, glowColor, alpha);
        drawPass(consumer, matrix, cameraPos, points, midWidth, midColor, alpha);
        drawPass(consumer, matrix, cameraPos, points, coreWidth, coreColor, alpha);
    }

    public static List<Vec3> buildBranch(Vec3 start, RandomSource random,
                                         float time, double phase) {
        int count = 4 + random.nextInt(2);
        double angle = random.nextDouble() * Mth.TWO_PI;
        double length = 0.22D + random.nextDouble() * 0.30D;
        double dx = Math.cos(angle) * length;
        double dz = Math.sin(angle) * length;
        double dy = (random.nextDouble() - 0.25D) * 0.36D;
        double sideX = -Math.sin(angle);
        double sideZ = Math.cos(angle);
        double[] wobble = new double[count * 2];
        for (int i = 0; i < wobble.length; i++) {
            wobble[i] = random.nextDouble() - 0.5D;
        }

        List<Vec3> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = i / (double) (count - 1);
            double taper = Math.sin(Math.PI * t);
            double jitter = Math.sin(time * 0.7D + phase + t * 10.0D) * 0.05D;
            points.add(start.add(
                    dx * t + sideX * (jitter + wobble[i * 2] * 0.10D * taper),
                    dy * t + jitter * 0.6D + wobble[i * 2 + 1] * 0.08D * taper,
                    dz * t + sideZ * (jitter + wobble[i * 2] * 0.10D * taper)
            ));
        }
        return points;
    }

    private static void drawPass(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraPos,
                                 List<Vec3> points, float width, float[] color, float alpha) {
        float red = color[0];
        float green = color[1];
        float blue = color[2];
        float combinedAlpha = color[3] * alpha;
        for (int i = 1; i < points.size(); i++) {
            Vec3 start = points.get(i - 1);
            Vec3 end = points.get(i);
            Vec3 middle = start.add(end).scale(0.5D);
            Vec3 side = end.subtract(start).cross(middle.subtract(cameraPos));
            if (side.lengthSqr() < 1.0E-8D) {
                continue;
            }
            side = side.normalize().scale(width);

            Vec3 a1 = start.add(side);
            Vec3 a2 = start.subtract(side);
            Vec3 b1 = end.add(side);
            Vec3 b2 = end.subtract(side);

            vertex(consumer, matrix, a1, red, green, blue, combinedAlpha, 0.0F, 0.0F);
            vertex(consumer, matrix, a2, red, green, blue, combinedAlpha, 1.0F, 0.0F);
            vertex(consumer, matrix, b2, red, green, blue, combinedAlpha, 1.0F, 1.0F);
            vertex(consumer, matrix, b1, red, green, blue, combinedAlpha, 0.0F, 1.0F);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 point,
                               float red, float green, float blue, float alpha,
                               float u, float v) {
        consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}

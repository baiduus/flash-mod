package com.flash.client;

import com.flash.FlashMod;
import com.flash.block.AccelerationPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the circular Acceleration Force portal as several animated energy
 * layers: a dark blue vortex, three irregular gold rings, orbiting ribbons,
 * branching gold/blue discharges and a soft ground glow.
 *
 * <p>The portal is not made of blocks. Only the centre marker block exists;
 * this renderer draws a 5.6-block disc around it, floating above the terrain,
 * with no frame, pedestal or supporting structure.</p>
 */
public final class AccelerationPortalRenderer
        implements BlockEntityRenderer<AccelerationPortalBlockEntity> {

    private static final ResourceLocation VORTEX_TEXTURE = new ResourceLocation(
            FlashMod.MODID, "textures/effect/portal_vortex.png");
    private static final ResourceLocation RING_TEXTURE = new ResourceLocation(
            FlashMod.MODID, "textures/effect/portal_ring.png");
    private static final ResourceLocation GLOW_TEXTURE = new ResourceLocation(
            FlashMod.MODID, "textures/effect/portal_glow.png");

    private static final RenderType VORTEX_TYPE =
            RenderType.entityTranslucentEmissive(VORTEX_TEXTURE);
    private static final RenderType RING_TYPE =
            RenderType.entityTranslucentEmissive(RING_TEXTURE);
    private static final RenderType GLOW_TYPE =
            RenderType.entityTranslucentEmissive(GLOW_TEXTURE);

    private static final float[] GOLD_GLOW = {1.0F, 0.55F, 0.03F, 0.22F};
    private static final float[] GOLD_MID = {1.0F, 0.78F, 0.10F, 0.55F};
    private static final float[] GOLD_CORE = {1.0F, 0.97F, 0.65F, 0.95F};
    private static final float[] BLUE_GLOW = {0.12F, 0.42F, 1.0F, 0.20F};
    private static final float[] BLUE_MID = {0.28F, 0.65F, 1.0F, 0.65F};
    private static final float[] BLUE_CORE = {0.80F, 0.94F, 1.0F, 0.98F};

    public AccelerationPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AccelerationPortalBlockEntity portal, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (portal.owner() == null || portal.getLevel() == null) {
            return;
        }

        float ticks = portal.getLevel().getGameTime() + partialTick;
        float time = ticks * 0.05F;
        float age = portal.createdAt() > 0L
                ? ticks - portal.createdAt() : 100.0F;
        float open = Mth.clamp(age / 15.0F, 0.0F, 1.0F);
        open = open * open * (3.0F - 2.0F * open);
        if (open <= 0.01F) {
            return;
        }

        double radius = portal.radius();
        float scale = 0.35F + 0.65F * open;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-portal.yaw()));

        // Ground glow is drawn first and is not affected by the portal's own
        // opening scale, so it stays on the floor while the ring forms.
        drawGroundGlow(poseStack, bufferSource, portal, radius, open);

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        Matrix4f matrix = poseStack.last().pose();

        drawVortex(poseStack, bufferSource, radius, time, open);
        drawRings(poseStack, bufferSource, radius, time, open);
        drawOrbitals(bufferSource, matrix, radius, time, open);
        drawLightning(bufferSource, matrix, portal, radius, time, open, ticks);

        poseStack.popPose();
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch(VORTEX_TYPE);
            buffers.endBatch(RING_TYPE);
            buffers.endBatch(GLOW_TYPE);
            buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
        }
    }

    private static void drawGroundGlow(PoseStack poseStack, MultiBufferSource bufferSource,
                                       AccelerationPortalBlockEntity portal,
                                       double radius, float open) {
        poseStack.pushPose();
        poseStack.translate(0.0D, -portal.groundOffset() + 0.04D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(GLOW_TYPE);
        float r = (float) (radius * 1.45D);
        float alpha = 0.55F * open;
        quad(consumer, matrix, -r, -r, r, r, 0.0F,
                1.0F, 1.0F, 1.0F, alpha, 0.0F, 0.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private static void drawVortex(PoseStack poseStack, MultiBufferSource bufferSource,
                                   double radius, float time, float open) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-time * 9.0F));
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(VORTEX_TYPE);
        float r = (float) (radius * 0.84D);
        quad(consumer, matrix, -r, -r, r, r, 0.01F,
                1.0F, 1.0F, 1.0F, 0.92F * open,
                0.0F, 0.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private static void drawRings(PoseStack poseStack, MultiBufferSource bufferSource,
                                  double radius, float time, float open) {
        float r = (float) radius;
        float[][] layers = {
                {1.00F, 0.06F, 7.0F, 0.90F},
                {0.93F, 0.11F, -11.0F, 0.55F},
                {1.07F, 0.00F, 4.0F, 0.72F}
        };
        for (float[] layer : layers) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * layer[2]));
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer consumer = bufferSource.getBuffer(RING_TYPE);
            float size = r * layer[0];
            quad(consumer, matrix, -size, -size, size, size, layer[1],
                    1.0F, 1.0F, 1.0F, layer[3] * open,
                    0.0F, 0.0F, 1.0F, 1.0F);
            poseStack.popPose();
        }
    }

    private static void drawOrbitals(MultiBufferSource bufferSource, Matrix4f matrix,
                                     double radius, float time, float open) {
        VertexConsumer consumer = bufferSource.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        for (int band = 0; band < 3; band++) {
            double rx = radius * (1.02D + band * 0.05D);
            double ry = radius * (0.32D + band * 0.20D);
            double phase = time * (3.0D + band * 1.7D);
            double zBase = 0.14D + band * 0.06D;
            List<Vec3> points = new ArrayList<>(25);
            for (int i = 0; i <= 24; i++) {
                double angle = i * (Math.PI * 2.0D / 24.0D);
                double x = Math.cos(angle) * rx;
                double y = Math.sin(angle) * ry;
                double z = zBase + Math.sin(angle * 2.0D + phase) * 0.09D;
                double tilt = Math.toRadians(phase * 4.0D);
                points.add(new Vec3(
                        x * Math.cos(tilt) - y * Math.sin(tilt),
                        x * Math.sin(tilt) + y * Math.cos(tilt),
                        z
                ));
            }
            FlashLightningRibbon.drawArc(consumer, matrix, Vec3.ZERO, points,
                    0.085F, 0.032F, 0.011F,
                    GOLD_GLOW, GOLD_MID, GOLD_CORE, 0.85F * open);
        }
    }

    private static void drawLightning(MultiBufferSource bufferSource, Matrix4f matrix,
                                      AccelerationPortalBlockEntity portal, double radius,
                                      float time, float open, float ticks) {
        VertexConsumer consumer = bufferSource.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        RandomSource random = RandomSource.create(portal.seed()
                ^ (portal.getLevel().getGameTime() / 3L));

        int arcs = 12;
        for (int i = 0; i < arcs; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double startRadius = radius * (0.80D + random.nextDouble() * 0.16D);
            double endRadius = radius * (1.02D + random.nextDouble() * 0.52D);
            double spread = (random.nextDouble() - 0.5D) * 0.55D;

            Vec3 start = polar(angle, startRadius, 0.12D + (random.nextDouble() - 0.5D) * 0.25D);
            Vec3 end = polar(angle + spread, endRadius,
                    0.12D + (random.nextDouble() - 0.5D) * 0.45D);
            List<Vec3> points = new ArrayList<>(7);
            for (int step = 0; step <= 6; step++) {
                double t = step / 6.0D;
                Vec3 point = start.lerp(end, t);
                double jitter = Math.sin(t * Math.PI);
                points.add(point.add(
                        (random.nextDouble() - 0.5D) * 0.22D * jitter,
                        (random.nextDouble() - 0.5D) * 0.22D * jitter,
                        (random.nextDouble() - 0.5D) * 0.36D * jitter
                ));
            }

            boolean blue = i % 3 == 0;
            FlashLightningRibbon.drawArc(consumer, matrix, Vec3.ZERO, points,
                    0.052F, 0.020F, 0.007F,
                    blue ? BLUE_GLOW : GOLD_GLOW,
                    blue ? BLUE_MID : GOLD_MID,
                    blue ? BLUE_CORE : GOLD_CORE,
                    open);
        }

        // Short inner filaments suggesting energy flowing towards the vortex.
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double outer = radius * (0.55D + random.nextDouble() * 0.18D);
            double inner = radius * 0.16D;
            Vec3 start = polar(angle, outer, 0.05D);
            Vec3 end = polar(angle + (random.nextDouble() - 0.5D) * 0.5D,
                    inner, 0.02D);
            List<Vec3> points = new ArrayList<>(5);
            for (int step = 0; step <= 4; step++) {
                double t = step / 4.0D;
                Vec3 point = start.lerp(end, t);
                points.add(point.add(
                        (random.nextDouble() - 0.5D) * 0.10D * Math.sin(t * Math.PI),
                        (random.nextDouble() - 0.5D) * 0.10D * Math.sin(t * Math.PI),
                        (random.nextDouble() - 0.5D) * 0.12D * Math.sin(t * Math.PI)
                ));
            }
            FlashLightningRibbon.drawArc(consumer, matrix, Vec3.ZERO, points,
                    0.035F, 0.014F, 0.005F,
                    BLUE_GLOW, BLUE_MID, BLUE_CORE, 0.75F * open);
        }
    }

    private static Vec3 polar(double angle, double radius, double z) {
        return new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius, z);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x0, float y0, float x1, float y1, float z,
                             float red, float green, float blue, float alpha,
                             float u0, float v0, float u1, float v1) {
        vertex(consumer, matrix, x0, y0, z, red, green, blue, alpha, u0, v1);
        vertex(consumer, matrix, x1, y0, z, red, green, blue, alpha, u1, v1);
        vertex(consumer, matrix, x1, y1, z, red, green, blue, alpha, u1, v0);
        vertex(consumer, matrix, x0, y1, z, red, green, blue, alpha, u0, v0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float x, float y, float z,
                               float red, float green, float blue, float alpha,
                               float u, float v) {
        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}

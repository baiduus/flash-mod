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
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/**
 * Floating Acceleration Force portal, rebuilt from the reference image.
 *
 * <p>No blocks and no frame: only energy. From the inside out it draws a deep
 * navy vortex (three counter-rotating layers for depth), a thick main ring made
 * of nine overlapping irregular gold ribbons plus a thin full circle that keeps
 * the silhouette, three orbital ribbons that cross in front of and behind the
 * ring, blue and yellow branching discharges that appear, move and fade at
 * different rhythms, inward-flowing filaments and a concentric glow on the
 * ground. Nothing here is a collision; the crossing test lives on the
 * server.</p>
 */
public final class AccelerationPortalRenderer
        implements BlockEntityRenderer<AccelerationPortalBlockEntity> {

    private static final ResourceLocation VORTEX_TEXTURE = new ResourceLocation(
            FlashMod.MODID, "textures/effect/portal_vortex.png");
    private static final ResourceLocation GLOW_TEXTURE = new ResourceLocation(
            FlashMod.MODID, "textures/effect/portal_glow.png");

    private static final RenderType VORTEX_TYPE_POSITIVE =
            RenderType.entityTranslucentEmissive(VORTEX_TEXTURE);
    private static final RenderType GLOW_TYPE_POSITIVE =
            RenderType.entityTranslucentEmissive(GLOW_TEXTURE);
    private static final RenderType VORTEX_TYPE_NEGATIVE = RenderType.entityTranslucentEmissive(
            new ResourceLocation(FlashMod.MODID, "textures/effect/portal_vortex_negative.png"));
    private static final RenderType GLOW_TYPE_NEGATIVE = RenderType.entityTranslucentEmissive(
            new ResourceLocation(FlashMod.MODID, "textures/effect/portal_glow_negative.png"));

    private static final RenderType SWIRL_TYPE_POSITIVE = RenderType.entityTranslucentEmissive(
            new ResourceLocation(FlashMod.MODID, "textures/effect/portal_swirl.png"));
    private static final RenderType SWIRL_TYPE_NEGATIVE = RenderType.entityTranslucentEmissive(
            new ResourceLocation(FlashMod.MODID, "textures/effect/portal_swirl_negative.png"));

    /** Theme of the portal being drawn right now (render thread only). */
    private static boolean negativeTheme;

    private static final float[] POS_GOLD_GLOW = {1.0F, 0.55F, 0.03F, 0.24F};
    private static final float[] POS_GOLD_MID = {1.0F, 0.80F, 0.10F, 0.62F};
    private static final float[] POS_GOLD_CORE = {1.0F, 0.97F, 0.62F, 0.96F};
    private static final float[] POS_AMBER_GLOW = {1.0F, 0.42F, 0.02F, 0.22F};
    private static final float[] POS_AMBER_MID = {1.0F, 0.66F, 0.06F, 0.58F};
    private static final float[] POS_AMBER_CORE = {1.0F, 0.90F, 0.50F, 0.95F};
    private static final float[] POS_BLUE_GLOW = {0.10F, 0.38F, 1.0F, 0.24F};
    private static final float[] POS_BLUE_MID = {0.26F, 0.62F, 1.0F, 0.70F};
    private static final float[] POS_BLUE_CORE = {0.85F, 0.95F, 1.0F, 0.98F};
    // Negative Acceleration Force: the same portal in reds.
    private static final float[] NEG_GOLD_GLOW = {0.85F, 0.04F, 0.05F, 0.26F};
    private static final float[] NEG_GOLD_MID = {1.0F, 0.14F, 0.10F, 0.64F};
    private static final float[] NEG_GOLD_CORE = {1.0F, 0.72F, 0.66F, 0.96F};
    private static final float[] NEG_AMBER_GLOW = {0.60F, 0.02F, 0.04F, 0.22F};
    private static final float[] NEG_AMBER_MID = {0.85F, 0.07F, 0.08F, 0.58F};
    private static final float[] NEG_AMBER_CORE = {1.0F, 0.55F, 0.50F, 0.95F};
    private static final float[] NEG_BLUE_GLOW = {0.90F, 0.10F, 0.15F, 0.24F};
    private static final float[] NEG_BLUE_MID = {1.0F, 0.30F, 0.30F, 0.70F};
    private static final float[] NEG_BLUE_CORE = {1.0F, 0.92F, 0.92F, 0.98F};

    private static float[] pick(float[] positive, float[] negative) {
        return negativeTheme ? negative : positive;
    }

    public AccelerationPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AccelerationPortalBlockEntity portal, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (portal.owner() == null || portal.getLevel() == null) {
            return;
        }

        negativeTheme = portal.isNegative();
        float ticks = portal.getLevel().getGameTime() + partialTick;
        float t = ticks / 20.0F;
        float age = portal.createdAt() > 0L ? ticks - portal.createdAt() : 100.0F;
        float open = smooth(Mth.clamp(age / 18.0F, 0.0F, 1.0F));
        float remaining = AccelerationPortalBlockEntity.LIFETIME_TICKS - age;
        float close = smooth(Mth.clamp(remaining / 15.0F, 0.0F, 1.0F));
        float vis = open * close;
        if (vis <= 0.01F) {
            return;
        }

        float radius = (float) portal.radius();
        float pulse = 0.5F + 0.5F * Mth.sin(t * 2.1F);
        float scale = 0.30F + 0.70F * vis;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-portal.yaw()));

        // Camera position in the portal's local frame, so the ribbons can face it.
        Vec3 camWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f cam = new Vector3f(
                (float) (camWorld.x - (portal.getBlockPos().getX() + 0.5D)),
                (float) (camWorld.y - (portal.getBlockPos().getY() + 0.5D)),
                (float) (camWorld.z - (portal.getBlockPos().getZ() + 0.5D)));
        cam.rotateY((float) Math.toRadians(portal.yaw()));
        Vec3 camera = new Vec3(cam.x / scale, cam.y / scale, cam.z / scale);

        drawGroundGlow(poseStack, bufferSource, portal, radius, vis, pulse, t);
        drawHalo(poseStack, bufferSource, radius, vis, pulse, scale);

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        drawVortex(poseStack, bufferSource, radius, t, vis);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer ribbons = bufferSource.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        drawFilaments(ribbons, matrix, camera, portal.seed(), radius, t, vis);
        drawRing(ribbons, matrix, camera, portal.seed(), radius, t, vis, pulse);
        drawOrbitals(ribbons, matrix, camera, radius, t, vis);
        drawDischarges(ribbons, matrix, camera, portal.seed(), radius, ticks, vis);

        poseStack.popPose();
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch(GLOW_TYPE_POSITIVE);
            buffers.endBatch(VORTEX_TYPE_POSITIVE);
            buffers.endBatch(GLOW_TYPE_NEGATIVE);
            buffers.endBatch(SWIRL_TYPE_POSITIVE);
            buffers.endBatch(SWIRL_TYPE_NEGATIVE);
            buffers.endBatch(VORTEX_TYPE_NEGATIVE);
            buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
        }
    }

    private static RenderType vortexType() {
        return negativeTheme ? VORTEX_TYPE_NEGATIVE : VORTEX_TYPE_POSITIVE;
    }

    private static RenderType glowType() {
        return negativeTheme ? GLOW_TYPE_NEGATIVE : GLOW_TYPE_POSITIVE;
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

    private static void drawGroundGlow(PoseStack poseStack, MultiBufferSource bufferSource,
                                       AccelerationPortalBlockEntity portal, float radius,
                                       float vis, float pulse, float t) {
        poseStack.pushPose();
        poseStack.translate(0.0D, -portal.groundOffset() + 0.04D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(t * 18.0F));
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(glowType());
        float r = radius * 1.35F;
        quad(consumer, matrix, -r, -r, r, r, 0.0F, 1.0F, 1.0F, 1.0F,
                (0.50F + 0.12F * pulse) * vis);
        poseStack.popPose();
    }

    /** Large soft glow behind the whole gate, so the portal lights up its surroundings. */
    private static void drawHalo(PoseStack poseStack, MultiBufferSource bufferSource,
                                 float radius, float vis, float pulse, float scale) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        VertexConsumer consumer = bufferSource.getBuffer(glowType());
        float h = radius * (1.85F + 0.10F * pulse);
        quad(consumer, poseStack.last().pose(), -h, -h, h, h, -0.14F, 1.0F, 1.0F, 1.0F,
                (0.40F + 0.15F * pulse) * vis);
        poseStack.popPose();
    }

    /** Three vortex layers turning at different speeds and directions for depth. */
    private static void drawVortex(PoseStack poseStack, MultiBufferSource bufferSource,
                                   float radius, float t, float vis) {
        float[][] layers = {
                // size, z, degrees per second, alpha, r, g, b
                {0.90F, -0.07F, -16.0F, 0.96F, 1.0F, 1.0F, 1.0F},
                {0.74F, -0.03F, 26.0F, 0.55F, 0.80F, 0.90F, 1.0F},
                {0.52F, 0.01F, -42.0F, 0.42F, 0.70F, 0.85F, 1.0F}
        };
        VertexConsumer consumer = bufferSource.getBuffer(vortexType());
        VertexConsumer swirls = null;
        for (float[] layer : layers) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(t * layer[2]));
            Matrix4f matrix = poseStack.last().pose();
            float size = radius * layer[0];
            quad(consumer, matrix, -size, -size, size, size, layer[1],
                    negativeTheme ? 1.0F : layer[4], negativeTheme ? 1.0F : layer[5],
                    negativeTheme ? 1.0F : layer[6], layer[3] * vis);
            poseStack.popPose();
        }
        // glowing spiral arms over the vortex: depth and a clear sense of rotation
        swirls = bufferSource.getBuffer(negativeTheme ? SWIRL_TYPE_NEGATIVE : SWIRL_TYPE_POSITIVE);
        float[][] arms = {{0.96F, 0.02F, 24.0F, 0.80F}, {0.66F, 0.04F, -38.0F, 0.70F}};
        for (float[] arm : arms) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(t * arm[2]));
            float size = radius * arm[0];
            quad(swirls, poseStack.last().pose(), -size, -size, size, size, arm[1],
                    1.0F, 1.0F, 1.0F, arm[3] * vis);
            poseStack.popPose();
        }
    }

    /** Nine overlapping irregular gold ribbons plus a thin full circle for the silhouette. */
    private static void drawRing(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                 int seed, float radius, float t, float vis, float pulse) {
        for (int i = 0; i < 13; i++) {
            RandomSource random = RandomSource.create(seed * 31L + i * 7919L);
            double baseRadius = radius * (0.86D + random.nextDouble() * 0.17D);
            double span = Math.toRadians(190.0D + random.nextDouble() * 150.0D);
            double speed = (0.45D + random.nextDouble() * 1.1D) * (random.nextBoolean() ? 1.0D : -1.0D);
            double start = random.nextDouble() * Math.PI * 2.0D + t * speed;
            int waves = 2 + random.nextInt(4);
            double amplitude = radius * (0.012D + random.nextDouble() * 0.035D);
            double phase = random.nextDouble() * Math.PI * 2.0D;
            double zOffset = (random.nextDouble() - 0.5D) * 0.30D;
            double waveSpeed = 1.0D + random.nextDouble() * 1.6D;
            boolean amber = i % 3 == 1;

            List<Vec3> points = new ArrayList<>(37);
            for (int k = 0; k <= 36; k++) {
                double u = k / 36.0D;
                double theta = start + span * u;
                double r = baseRadius
                        + amplitude * Math.sin(waves * theta + phase + t * waveSpeed)
                        + radius * 0.018D * pulse * Math.sin(theta * 3.0D + i);
                double z = zOffset + 0.05D * Math.sin(theta * 2.0D + t * 1.3D + i);
                points.add(new Vec3(Math.cos(theta) * r, Math.sin(theta) * r, z));
            }
            float width = 0.8F + random.nextFloat() * 0.5F;
            float alpha = vis * (0.70F + 0.30F * Mth.sin(t * 1.7F + i * 1.9F));
            FlashLightningRibbon.drawArc(consumer, matrix, camera, points,
                    0.17F * width, 0.072F * width, 0.024F * width,
                    amber ? pick(POS_AMBER_GLOW, NEG_AMBER_GLOW) : pick(POS_GOLD_GLOW, NEG_GOLD_GLOW),
                    amber ? pick(POS_AMBER_MID, NEG_AMBER_MID) : pick(POS_GOLD_MID, NEG_GOLD_MID),
                    amber ? pick(POS_AMBER_CORE, NEG_AMBER_CORE) : pick(POS_GOLD_CORE, NEG_GOLD_CORE), alpha);
        }

        // Continuous thin circle: keeps the portal readable as a circle.
        List<Vec3> circle = new ArrayList<>(73);
        for (int k = 0; k <= 72; k++) {
            double theta = k * (Math.PI * 2.0D / 72.0D);
            double r = radius * (0.975D + 0.012D * Math.sin(theta * 5.0D + t * 2.0D));
            circle.add(new Vec3(Math.cos(theta) * r, Math.sin(theta) * r, 0.02D));
        }
        FlashLightningRibbon.drawArc(consumer, matrix, camera, circle,
                0.12F, 0.05F, 0.018F, pick(POS_GOLD_GLOW, NEG_GOLD_GLOW), pick(POS_GOLD_MID, NEG_GOLD_MID), pick(POS_GOLD_CORE, NEG_GOLD_CORE),
                vis * (0.80F + 0.20F * pulse));
    }

    /** Three thin ribbons orbiting at different tilts, crossing in front of and behind the ring. */
    private static void drawOrbitals(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                     float radius, float t, float vis) {
        double[] tilt = {62.0D, -48.0D, 28.0D};
        for (int band = 0; band < 3; band++) {
            double a = radius * (1.12D + band * 0.06D);
            double b = radius * (0.30D + band * 0.08D);
            double spin = t * (0.55D + band * 0.35D) * (band % 2 == 0 ? 1.0D : -1.0D) + band * 2.1D;
            double tiltX = Math.toRadians(tilt[band]) + 0.18D * Math.sin(t * 0.8D + band);
            double cosT = Math.cos(tiltX);
            double sinT = Math.sin(tiltX);
            double cosS = Math.cos(spin);
            double sinS = Math.sin(spin);

            List<Vec3> points = new ArrayList<>(49);
            for (int k = 0; k <= 48; k++) {
                double theta = k * (Math.PI * 2.0D / 48.0D);
                double x = Math.cos(theta) * a;
                double y = Math.sin(theta) * b * cosT;
                double z = Math.sin(theta) * b * sinT;
                points.add(new Vec3(x * cosS - y * sinS, x * sinS + y * cosS, z));
            }
            FlashLightningRibbon.drawArc(consumer, matrix, camera, points,
                    0.075F, 0.030F, 0.011F, pick(POS_GOLD_GLOW, NEG_GOLD_GLOW), pick(POS_GOLD_MID, NEG_GOLD_MID), pick(POS_GOLD_CORE, NEG_GOLD_CORE),
                    vis * 0.9F);
        }
    }

    /** Energy streaming towards the centre of the vortex. */
    private static void drawFilaments(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                      int seed, float radius, float t, float vis) {
        for (int i = 0; i < 18; i++) {
            RandomSource random = RandomSource.create(seed * 17L + i * 104729L);
            double baseAngle = random.nextDouble() * Math.PI * 2.0D;
            double flow = (t * (0.28D + random.nextDouble() * 0.22D) + random.nextDouble()) % 1.0D;
            double swirl = 1.2D + random.nextDouble() * 0.8D;
            boolean gold = i % 4 == 0;

            List<Vec3> points = new ArrayList<>(7);
            for (int k = 0; k <= 6; k++) {
                double u = k / 6.0D;
                double r = radius * (0.80D - 0.66D * Math.min(1.0D, flow * 0.5D + u * 0.5D));
                double theta = baseAngle + (1.0D - u) * swirl + t * 0.4D;
                points.add(new Vec3(Math.cos(theta) * r, Math.sin(theta) * r,
                        -0.03D + (random.nextDouble() - 0.5D) * 0.05D));
            }
            float alpha = vis * 0.8F * Mth.sin((float) (flow * Math.PI));
            FlashLightningRibbon.drawArc(consumer, matrix, camera, points,
                    0.040F, 0.016F, 0.006F,
                    gold ? pick(POS_GOLD_GLOW, NEG_GOLD_GLOW) : pick(POS_BLUE_GLOW, NEG_BLUE_GLOW), gold ? pick(POS_GOLD_MID, NEG_GOLD_MID) : pick(POS_BLUE_MID, NEG_BLUE_MID),
                    gold ? pick(POS_GOLD_CORE, NEG_GOLD_CORE) : pick(POS_BLUE_CORE, NEG_BLUE_CORE), alpha);
        }
    }

    /**
     * Branching discharges leaving the ring. Each one has its own period and
     * offset, so they appear, move and fade out of step and never repeat the
     * same picture in a short time.
     */
    private static void drawDischarges(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                       int seed, float radius, float ticks, float vis) {
        for (int j = 0; j < 22; j++) {
            boolean blue = j < 13;
            int period = blue ? 4 + (j * 3) % 6 : 7 + (j * 5) % 7;
            int offset = j * 3;
            long cycle = (long) Math.floor((ticks + offset) / period);
            float frac = ((ticks + offset) % period) / period;
            float alpha = vis * Mth.clamp(Mth.sin(frac * Mth.PI) * 1.7F, 0.0F, 1.0F);
            if (alpha <= 0.02F) {
                continue;
            }
            RandomSource random = RandomSource.create(
                    seed * 131L + j * 1013L + cycle * 7919L);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double startRadius = radius * (0.93D + random.nextDouble() * 0.08D);
            Vec3 start = new Vec3(Math.cos(angle) * startRadius, Math.sin(angle) * startRadius,
                    (random.nextDouble() - 0.5D) * 0.2D);
            double direction = angle + (random.nextDouble() - 0.5D) * 1.0D;
            double length = radius * (blue ? 0.45D + random.nextDouble() * 0.75D
                    : 0.95D + random.nextDouble() * 1.25D);
            bolt(consumer, matrix, camera, start, direction, length, blue ? 7 : 9,
                    random, blue, alpha, blue ? 1 : 2);
        }
    }

    private static void bolt(VertexConsumer consumer, Matrix4f matrix, Vec3 camera, Vec3 start,
                             double direction, double length, int segments, RandomSource random,
                             boolean blue, float alpha, int depth) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        points.add(start);
        double step = length / segments;
        Vec3 current = start;
        double dir = direction;
        double z = start.z;
        double[] branchAt = new double[segments + 1];
        for (int k = 1; k <= segments; k++) {
            dir += (random.nextDouble() - 0.5D) * 0.75D;
            double jitter = (random.nextDouble() - 0.5D) * step * 0.8D;
            z += (random.nextDouble() - 0.5D) * 0.14D;
            current = new Vec3(
                    current.x + Math.cos(dir) * step - Math.sin(dir) * jitter,
                    current.y + Math.sin(dir) * step + Math.cos(dir) * jitter,
                    z);
            points.add(current);
            branchAt[k] = dir;
        }

        float scale = depth >= 2 ? 1.0F : 0.7F;
        float widthMul = blue ? 1.0F : 1.15F;
        FlashLightningRibbon.drawArc(consumer, matrix, camera, points,
                0.062F * scale * widthMul, 0.023F * scale * widthMul, 0.0085F * scale * widthMul,
                blue ? pick(POS_BLUE_GLOW, NEG_BLUE_GLOW) : pick(POS_GOLD_GLOW, NEG_GOLD_GLOW), blue ? pick(POS_BLUE_MID, NEG_BLUE_MID) : pick(POS_GOLD_MID, NEG_GOLD_MID),
                blue ? pick(POS_BLUE_CORE, NEG_BLUE_CORE) : pick(POS_GOLD_CORE, NEG_GOLD_CORE), alpha);

        if (depth > 0) {
            int branches = 1 + random.nextInt(2);
            for (int b = 0; b < branches; b++) {
                int at = 2 + random.nextInt(Math.max(1, segments - 3));
                double side = (random.nextBoolean() ? 1.0D : -1.0D) * (0.5D + random.nextDouble() * 0.7D);
                bolt(consumer, matrix, camera, points.get(at), branchAt[at] + side,
                        length * (0.30D + random.nextDouble() * 0.25D),
                        Math.max(3, segments / 2), random, blue, alpha * 0.9F, depth - 1);
            }
        }
    }

    // ------------------------------------------------------------------
    // Geometry helpers
    // ------------------------------------------------------------------

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x0, float y0, float x1, float y1, float z,
                             float red, float green, float blue, float alpha) {
        vertex(consumer, matrix, x0, y0, z, red, green, blue, alpha, 0.0F, 1.0F);
        vertex(consumer, matrix, x1, y0, z, red, green, blue, alpha, 1.0F, 1.0F);
        vertex(consumer, matrix, x1, y1, z, red, green, blue, alpha, 1.0F, 0.0F);
        vertex(consumer, matrix, x0, y1, z, red, green, blue, alpha, 0.0F, 0.0F);
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

package com.flash.client;

import com.flash.FlashMod;
import com.flash.block.AccelerationPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
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
import org.joml.Vector3f;

/**
 * Acceleration Force portal, designed from scratch: a glowing gate of pure energy.
 *
 * <p>From the back to the front it draws a soft halo, the event horizon (a dim
 * core with three spiral layers turning in opposite directions), a thick
 * energy ring whose current flows around it, a thin outer ring, bolts that
 * crackle along the ring and spiral towards the centre, and bright sparks that
 * orbit the gate. The positive force is gold / white, the negative one is
 * crimson with a black core. Nothing here collides; the crossing test lives on
 * the server.</p>
 */
public final class AccelerationPortalRenderer
        implements BlockEntityRenderer<AccelerationPortalBlockEntity> {

    private static final RenderType SWIRL_POS = type("portal_swirl");
    private static final RenderType SWIRL_NEG = type("portal_swirl_negative");
    private static final RenderType CORE_POS = type("portal_core");
    private static final RenderType CORE_NEG = type("portal_core_negative");
    private static final RenderType RING_POS = type("portal_ring");
    private static final RenderType RING_NEG = type("portal_ring_negative");
    private static final RenderType GLOW_POS = type("portal_glow");
    private static final RenderType GLOW_NEG = type("portal_glow_negative");

    private static RenderType type(String name) {
        return RenderType.entityTranslucentEmissive(
                new ResourceLocation(FlashMod.MODID, "textures/effect/" + name + ".png"));
    }

    private static final int RING_SEGMENTS = 96;

    public AccelerationPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AccelerationPortalBlockEntity portal, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (portal.owner() == null || portal.getLevel() == null) {
            return;
        }
        boolean negative = portal.isNegative();
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
        float pulse = 0.5F + 0.5F * Mth.sin(t * 2.4F);
        float scale = 0.25F + 0.75F * vis;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-portal.yaw()));

        // Camera position in the portal's local frame, so the bolts can face it.
        Vec3 camWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f cam = new Vector3f(
                (float) (camWorld.x - (portal.getBlockPos().getX() + 0.5D)),
                (float) (camWorld.y - (portal.getBlockPos().getY() + 0.5D)),
                (float) (camWorld.z - (portal.getBlockPos().getZ() + 0.5D)));
        cam.rotateY((float) Math.toRadians(portal.yaw()));
        Vec3 camera = new Vec3(cam.x / scale, cam.y / scale, cam.z / scale);

        RenderType glow = negative ? GLOW_NEG : GLOW_POS;
        RenderType core = negative ? CORE_NEG : CORE_POS;
        RenderType swirl = negative ? SWIRL_NEG : SWIRL_POS;
        RenderType ring = negative ? RING_NEG : RING_POS;

        // ground reflection (outside the scaled part)
        poseStack.pushPose();
        poseStack.translate(0.0D, -portal.groundOffset() + 0.04D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(t * 16.0F));
        float gr = radius * 1.4F;
        quad(bufferSource.getBuffer(glow), poseStack.last().pose(), -gr, -gr, gr, gr, 0.0F,
                1.0F, 1.0F, 1.0F, (0.50F + 0.12F * pulse) * vis);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        Matrix4f m = poseStack.last().pose();

        // 1. halo behind the gate
        float halo = radius * (1.75F + 0.08F * pulse);
        quad(bufferSource.getBuffer(glow), m, -halo, -halo, halo, halo, -0.12F,
                1.0F, 1.0F, 1.0F, (0.55F + 0.15F * pulse) * vis);

        // 2. event horizon: core + three spiral layers
        float coreSize = radius * 0.98F;
        quad(bufferSource.getBuffer(core), m, -coreSize, -coreSize, coreSize, coreSize, -0.06F,
                1.0F, 1.0F, 1.0F, 0.95F * vis);
        float[][] layers = {
                // size, z, degrees per second, alpha
                {1.00F, -0.04F, -22.0F, 0.95F},
                {0.78F, -0.02F, 34.0F, 0.80F},
                {0.54F, 0.00F, -58.0F, 0.75F}
        };
        VertexConsumer swirlBuffer = bufferSource.getBuffer(swirl);
        for (float[] layer : layers) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(t * layer[2]));
            float size = radius * layer[0];
            quad(swirlBuffer, poseStack.last().pose(), -size, -size, size, size, layer[1],
                    1.0F, 1.0F, 1.0F, layer[3] * vis);
            poseStack.popPose();
        }

        // 3. thick ring with a current flowing around it, plus a thin outer ring
        VertexConsumer ringBuffer = bufferSource.getBuffer(ring);
        float flow = t * 0.35F;
        float ringAlpha = (0.85F + 0.15F * pulse) * vis;
        strip(ringBuffer, m, radius * 0.86F, radius * 1.02F, 0.01F, 8.0F, flow, ringAlpha);
        strip(ringBuffer, m, radius * 1.03F, radius * 1.09F, 0.02F, 14.0F, -flow * 1.7F, ringAlpha * 0.85F);
        strip(ringBuffer, m, radius * 0.80F, radius * 0.86F, 0.00F, 10.0F, flow * 2.2F, ringAlpha * 0.55F);

        // 4. bolts along the ring and spiralling inwards, then orbiting sparks
        VertexConsumer bolts = bufferSource.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        drawBolts(bolts, m, camera, portal.seed(), radius, ticks, vis, negative);
        VertexConsumer sparks = bufferSource.getBuffer(glow);
        drawSparks(sparks, m, radius, t, vis, pulse);

        poseStack.popPose();
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch(glow);
            buffers.endBatch(core);
            buffers.endBatch(swirl);
            buffers.endBatch(ring);
            buffers.endBatch(FlashLightningRibbon.RENDER_TYPE);
        }
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

    /** Textured band between two radii; u repeats {@code repeats} times around and scrolls with {@code scroll}. */
    private static void strip(VertexConsumer c, Matrix4f m, float rIn, float rOut, float z,
                              float repeats, float scroll, float alpha) {
        for (int k = 0; k < RING_SEGMENTS; k++) {
            float a0 = k * (Mth.TWO_PI / RING_SEGMENTS);
            float a1 = (k + 1) * (Mth.TWO_PI / RING_SEGMENTS);
            float u0 = scroll + repeats * k / RING_SEGMENTS;
            float u1 = scroll + repeats * (k + 1) / RING_SEGMENTS;
            vertex(c, m, Mth.cos(a0) * rIn, Mth.sin(a0) * rIn, z, 1.0F, 1.0F, 1.0F, alpha, u0, 1.0F);
            vertex(c, m, Mth.cos(a1) * rIn, Mth.sin(a1) * rIn, z, 1.0F, 1.0F, 1.0F, alpha, u1, 1.0F);
            vertex(c, m, Mth.cos(a1) * rOut, Mth.sin(a1) * rOut, z, 1.0F, 1.0F, 1.0F, alpha, u1, 0.0F);
            vertex(c, m, Mth.cos(a0) * rOut, Mth.sin(a0) * rOut, z, 1.0F, 1.0F, 1.0F, alpha, u0, 0.0F);
        }
    }

    private static void drawBolts(VertexConsumer c, Matrix4f m, Vec3 camera, int seed, float radius,
                                  float ticks, float vis, boolean negative) {
        // Crackle along the ring: three arcs that travel around it and re-roll every few ticks.
        for (int i = 0; i < 3; i++) {
            long cycle = (long) Math.floor((ticks + i * 2) / 3.0F);
            RandomSource random = RandomSource.create(seed * 31L + i * 7919L + cycle * 104729L);
            double start = ticks * (0.05D + 0.02D * i) * (i % 2 == 0 ? 1 : -1) + i * 2.1D;
            double span = 0.9D + random.nextDouble() * 0.9D;
            List<Vec3> points = new ArrayList<>(13);
            for (int k = 0; k <= 12; k++) {
                double theta = start + span * k / 12.0D;
                double r = radius * (0.94D + (random.nextDouble() - 0.5D) * 0.10D);
                points.add(new Vec3(Math.cos(theta) * r, Math.sin(theta) * r,
                        (random.nextDouble() - 0.5D) * 0.10D));
            }
            FlashLightningRibbon.drawBolt(c, m, camera, points, 0.050F, vis, negative);
        }
        // Bolts leaving the ring towards the centre, each with its own rhythm.
        for (int j = 0; j < 7; j++) {
            int period = 5 + (j * 3) % 6;
            int offset = j * 4;
            long cycle = (long) Math.floor((ticks + offset) / period);
            float frac = ((ticks + offset) % period) / period;
            float alpha = vis * Mth.clamp(Mth.sin(frac * Mth.PI) * 1.8F, 0.0F, 1.0F);
            if (alpha <= 0.03F) {
                continue;
            }
            RandomSource random = RandomSource.create(seed * 131L + j * 1013L + cycle * 7919L);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double swirl = (random.nextBoolean() ? 1.0D : -1.0D) * (0.8D + random.nextDouble() * 1.0D);
            int steps = 9;
            List<Vec3> points = new ArrayList<>(steps + 1);
            for (int k = 0; k <= steps; k++) {
                double u = k / (double) steps;
                double r = radius * (0.96D - 0.80D * u) + (random.nextDouble() - 0.5D) * 0.18D;
                double theta = angle + swirl * u;
                points.add(new Vec3(Math.cos(theta) * r, Math.sin(theta) * r,
                        -0.02D + (random.nextDouble() - 0.5D) * 0.12D));
            }
            FlashLightningRibbon.drawBolt(c, m, camera, points, 0.045F, alpha, negative);
        }
    }

    private static void drawSparks(VertexConsumer c, Matrix4f m, float radius, float t, float vis, float pulse) {
        int count = 14;
        for (int i = 0; i < count; i++) {
            float dir = i % 2 == 0 ? 1.0F : -1.0F;
            float angle = t * (0.55F + 0.12F * (i % 3)) * dir + i * Mth.TWO_PI / count;
            float r = radius * (1.13F + 0.05F * Mth.sin(t * 2.0F + i));
            float size = 0.10F + 0.07F * Mth.sin(t * 3.0F + i * 1.7F) * Mth.sin(t * 3.0F + i * 1.7F);
            float x = Mth.cos(angle) * r;
            float y = Mth.sin(angle) * r;
            quad(c, m, x - size, y - size, x + size, y + size, 0.03F,
                    1.0F, 1.0F, 1.0F, (0.65F + 0.35F * pulse) * vis);
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

package com.flash.client;

import com.flash.block.TachyonExtractorBlock;
import com.flash.block.TachyonExtractorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws what moves inside the Tachyon Extractor: the item turning (about 4
 * degrees per tick) and floating inside the chamber, and the progress bar on
 * the front panel. Everything is interpolated with the partial tick.
 */
public class TachyonExtractorRenderer implements BlockEntityRenderer<TachyonExtractorBlockEntity> {

    public TachyonExtractorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TachyonExtractorBlockEntity extractor, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = extractor.getBlockState();
        if (!state.hasProperty(TachyonExtractorBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(TachyonExtractorBlock.FACING);
        boolean working = state.getValue(TachyonExtractorBlock.WORKING);
        boolean powered = state.getValue(TachyonExtractorBlock.POWERED);
        float time = extractor.animTicks + partialTick;

        poseStack.pushPose();
        // Work in the model's own frame (door on the north face), then turn it to the real facing.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        ItemStack stack = extractor.displayStack();
        if (!stack.isEmpty()) {
            poseStack.pushPose();
            float bob = working ? Mth.sin(time * 0.12F) * (1.0F / 16.0F) : 0.0F;
            poseStack.translate(0.5D, 0.46D + bob * 0.6F, 0.5D);
            poseStack.scale(0.36F, 0.36F, 0.36F);
            float spin = working ? time * 4.0F : (extractor.isDone() ? time * 2.0F : 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            int light = powered ? LightTexture.FULL_BRIGHT : packedLight;
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, extractor.getLevel(), 0);
            poseStack.popPose();
        }

        if (working && extractor.isGammaWork()) {
            drawGammaLightning(extractor, poseStack, buffers, time);
        }
        drawGlass(poseStack, buffers, powered ? LightTexture.FULL_BRIGHT : packedLight);
        if (powered) {
            drawProgressBar(extractor, poseStack, buffers);
        }
        poseStack.popPose();
    }

    /**
     * The gamma work (green gamma to red and back): forked lightning jumping from the capsule to the chamber walls,
     * a few bolts leaping out through the glass, in deep blue, neon purple, magenta and bright white, a new shape
     * every two ticks and more of them as the 10 s go by.
     */
    private void drawGammaLightning(TachyonExtractorBlockEntity extractor, PoseStack poseStack,
                                    MultiBufferSource buffers, float time) {
        VertexConsumer c = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        float t = extractor.progressFraction();
        long seed = (long) (time / 2.0F) * 341873128712L + extractor.getBlockPos().asLong();
        java.util.Random r = new java.util.Random(seed);
        int bolts = 3 + (int) (t * 5);
        float cx = 0.5F;
        float cy = 0.46F;
        float cz = 0.5F;
        for (int i = 0; i < bolts; i++) {
            boolean out = i >= bolts - 1 - (int) (t * 2);
            double yaw = r.nextDouble() * Math.PI * 2.0D;
            double pitch = (r.nextDouble() - 0.5D) * 1.4D;
            float len = out ? 0.55F + r.nextFloat() * 0.45F : 0.3F + r.nextFloat() * 0.06F;
            float ex = cx + (float) (Math.cos(yaw) * Math.cos(pitch)) * len;
            float ey = cy + (float) Math.sin(pitch) * len;
            float ez = cz + (float) (Math.sin(yaw) * Math.cos(pitch)) * len;
            int rgb = TachyonExtractorBlockEntity.GAMMA_COLORS[r.nextInt(3)];
            bolt(c, m, r, cx, cy, cz, ex, ey, ez, rgb, out ? 0.028F : 0.02F);
            if (r.nextInt(3) == 0) {
                // a fork from the middle of the bolt
                float mx = (cx + ex) * 0.5F;
                float my = (cy + ey) * 0.5F;
                float mz = (cz + ez) * 0.5F;
                bolt(c, m, r, mx, my, mz, mx + (r.nextFloat() - 0.5F) * 0.3F, my + (r.nextFloat() - 0.5F) * 0.3F,
                        mz + (r.nextFloat() - 0.5F) * 0.3F, TachyonExtractorBlockEntity.GAMMA_COLORS[r.nextInt(3)],
                        0.014F);
            }
        }
        // the glowing heart around the capsule
        int rgb = TachyonExtractorBlockEntity.GAMMA_COLORS[(int) (time / 3.0F) % 3];
        float g = 0.08F + 0.04F * Mth.sin(time * 0.7F) + t * 0.05F;
        cross(c, m, cx, cy, cz, g, rgb, 70);
        cross(c, m, cx, cy, cz, g * 0.5F, 0xF4F0FF, 140);
    }

    /** A jagged bolt: glow in its colour and a white core, as crossed ribbons. */
    private static void bolt(VertexConsumer c, Matrix4f m, java.util.Random r, float x0, float y0, float z0,
                             float x1, float y1, float z1, int rgb, float width) {
        int segs = 6;
        float px = x0;
        float py = y0;
        float pz = z0;
        for (int s = 1; s <= segs; s++) {
            float k = s / (float) segs;
            float jitter = s == segs ? 0.0F : 0.06F;
            float nx = x0 + (x1 - x0) * k + (r.nextFloat() - 0.5F) * jitter;
            float ny = y0 + (y1 - y0) * k + (r.nextFloat() - 0.5F) * jitter;
            float nz = z0 + (z1 - z0) * k + (r.nextFloat() - 0.5F) * jitter;
            ribbon(c, m, px, py, pz, nx, ny, nz, width * 2.4F, rgb, 90);
            ribbon(c, m, px, py, pz, nx, ny, nz, width, 0xF4F0FF, 230);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static void ribbon(VertexConsumer c, Matrix4f m, float x0, float y0, float z0, float x1, float y1,
                               float z1, float w, int rgb, int a) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        // two ribbons, one widened along y and one along x/z, both faces
        float[][] offs = {{0.0F, w, 0.0F}, {w, 0.0F, w}};
        for (float[] o : offs) {
            for (int face = 0; face < 2; face++) {
                float sgn = face == 0 ? 1.0F : -1.0F;
                c.vertex(m, x0 - o[0] * sgn, y0 - o[1], z0 + o[2] * sgn).color(r, g, b, a).endVertex();
                c.vertex(m, x0 + o[0] * sgn, y0 + o[1], z0 - o[2] * sgn).color(r, g, b, a).endVertex();
                c.vertex(m, x1 + o[0] * sgn, y1 + o[1], z1 - o[2] * sgn).color(r, g, b, a).endVertex();
                c.vertex(m, x1 - o[0] * sgn, y1 - o[1], z1 + o[2] * sgn).color(r, g, b, a).endVertex();
            }
        }
    }

    private static void cross(VertexConsumer c, Matrix4f m, float x, float y, float z, float s, int rgb, int a) {
        ribbon(c, m, x - s, y, z, x + s, y, z, s, rgb, a);
        ribbon(c, m, x, y, z - s, x, y, z + s, s, rgb, a);
    }

    private static final net.minecraft.resources.ResourceLocation GLASS =
            new net.minecraft.resources.ResourceLocation("flash", "textures/block/processor/glass.png");

    /** The glass of the chamber, on its four sides (a render type that does not write depth, so the item inside
     * always shows). */
    private void drawGlass(PoseStack poseStack, MultiBufferSource buffers, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.beaconBeam(GLASS, true));
        PoseStack.Pose pose = poseStack.last();
        float a = 3.0F / 16.0F;
        float b = 13.0F / 16.0F;
        float y0 = 3.5F / 16.0F;
        float y1 = 12.6F / 16.0F;
        // north, south, west, east
        glassVertex(consumer, pose, a, y0, a, 0.0F, 1.0F, light);
        glassVertex(consumer, pose, b, y0, a, 1.0F, 1.0F, light);
        glassVertex(consumer, pose, b, y1, a, 1.0F, 0.0F, light);
        glassVertex(consumer, pose, a, y1, a, 0.0F, 0.0F, light);
        glassVertex(consumer, pose, b, y0, b, 0.0F, 1.0F, light);
        glassVertex(consumer, pose, a, y0, b, 1.0F, 1.0F, light);
        glassVertex(consumer, pose, a, y1, b, 1.0F, 0.0F, light);
        glassVertex(consumer, pose, b, y1, b, 0.0F, 0.0F, light);
        glassVertex(consumer, pose, a, y0, b, 0.0F, 1.0F, light);
        glassVertex(consumer, pose, a, y0, a, 1.0F, 1.0F, light);
        glassVertex(consumer, pose, a, y1, a, 1.0F, 0.0F, light);
        glassVertex(consumer, pose, a, y1, b, 0.0F, 0.0F, light);
        glassVertex(consumer, pose, b, y0, a, 0.0F, 1.0F, light);
        glassVertex(consumer, pose, b, y0, b, 1.0F, 1.0F, light);
        glassVertex(consumer, pose, b, y1, b, 1.0F, 0.0F, light);
        glassVertex(consumer, pose, b, y1, a, 0.0F, 0.0F, light);
    }

    private static void glassVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
                                    float z, float u, float v, int light) {
        consumer.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .uv2(light).normal(pose.normal(), 0.0F, 0.0F, -1.0F).endVertex();
    }

    /** Orange bar on the front panel; fills along the track (0 to 6 px). */
    private void drawProgressBar(TachyonExtractorBlockEntity extractor, PoseStack poseStack,
                                 MultiBufferSource buffers) {
        float fraction = extractor.isDone() ? 1.0F : extractor.progressFraction();
        if (fraction <= 0.0F) {
            return;
        }
        // the control screen on the front of the plinth (x 4..12, y 0.5..2.5, its face at z = -0.5 px)
        float x0 = 5.0F / 16.0F;
        float x1 = x0 + 6.0F / 16.0F * fraction;
        float y0 = 1.1F / 16.0F;
        float y1 = 1.9F / 16.0F;
        float z = -0.5F / 16.0F - 0.004F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        int r = 255, g = 150, b = 30, a = 255;
        consumer.vertex(matrix, x0, y0, z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x0, y1, z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1, y1, z).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1, y0, z).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(TachyonExtractorBlockEntity extractor) {
        return false;
    }
}

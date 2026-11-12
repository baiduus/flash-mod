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

        drawGlass(poseStack, buffers, powered ? LightTexture.FULL_BRIGHT : packedLight);
        if (powered) {
            drawProgressBar(extractor, poseStack, buffers);
        }
        poseStack.popPose();
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

package com.flash.client;

import com.flash.block.MjolnirStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Mjolnir stuck head first in the crater floor, the handle up, as big as when it is thrown to the ground. */
public class MjolnirStoneRenderer implements BlockEntityRenderer<MjolnirStoneBlockEntity> {

    public MjolnirStoneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MjolnirStoneBlockEntity stone, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        long seed = stone.getBlockPos().asLong();
        float time = stone.getLevel() != null ? stone.getLevel().getGameTime() + partialTick : 0.0F;
        // the size of a Mjolnir thrown to the ground (MjolnirRenderer's statue)
        MjolnirRenderer.drawStatue(pose, buffers, light, (seed & 0xFF) * 1.4F, time);
        pose.popPose();
    }
}

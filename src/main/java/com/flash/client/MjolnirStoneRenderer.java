package com.flash.client;

import com.flash.block.MjolnirStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Mjolnir stuck head first in the crater floor, the handle up and a little tilted, as left by its fall. */
public class MjolnirStoneRenderer implements BlockEntityRenderer<MjolnirStoneBlockEntity> {

    private final ItemStack hammer = new ItemStack(com.flash.registry.ModItems.MJOLNIR.get());

    public MjolnirStoneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MjolnirStoneBlockEntity stone, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0.42D, 0.5D);
        long seed = stone.getBlockPos().asLong();
        pose.mulPose(Axis.YP.rotationDegrees((seed & 0xFF) * 1.4F));
        // head down into the ground, the handle up, leaning a little
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F + 10.0F));
        pose.scale(1.2F, 1.2F, 1.2F);
        Minecraft.getInstance().getItemRenderer().renderStatic(this.hammer, ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, stone.getLevel(), 0);
        pose.popPose();
    }
}

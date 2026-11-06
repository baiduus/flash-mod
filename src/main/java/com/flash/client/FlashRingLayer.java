package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Flash ring on the left hand of players that carry it in the ring
 * slot: the ring model of the Flash ring pack.
 */
public class FlashRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public FlashRingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!FlashClientRing.has(player) || player.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().rightArm.translateAndRotate(poseStack);
        drawRing(poseStack, buffers, light, "slim".equals(player.getModelName()));
        poseStack.popPose();
    }

    private static final ResourceLocation RING_TEXTURE =
            new ResourceLocation("flash", "textures/entity/flash_ring.png");
    private static final ResourceLocation RING_GEO = new ResourceLocation("flash", "geo/flash_ring.geo.json");
    private static com.flash.client.geo.GeoModel ring;

    public static void reset() {
        ring = null;
    }

    /**
     * The Flash ring model (flash_ring.geo.json: gold band with a red stripe, red face, white
     * disc and yellow bolt) on a finger at the lower outer-front corner of the right hand,
     * emblem facing outwards. The pose must already be at the arm model part.
     */
    public static void drawRing(PoseStack poseStack, MultiBufferSource buffers, int light, boolean slim) {
        if (ring == null) {
            ring = com.flash.client.geo.GeoModel.load(RING_GEO, null);
        }
        ring.reset();
        float outer = slim ? -2.0F : -3.0F;
        float scale = 0.13F;
        poseStack.pushPose();
        // the finger: just inside the outer-front corner of the hand
        poseStack.translate((outer + 0.5F) / 16.0F, 9.2F / 16.0F, -1.5F / 16.0F);
        // the emblem of the model faces -Z: turn it to the outer side of the hand
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
        poseStack.scale(scale, scale, scale);
        // the band is centred on (0, 1, 0) of the model (java y = 23)
        poseStack.translate(0.0F, -23.0F / 16.0F, 0.0F);
        ring.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(RING_TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}

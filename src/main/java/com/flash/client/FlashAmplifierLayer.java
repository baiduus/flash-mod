package com.flash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the Tachyon Amplifier on the chest of players that carry it in the slot:
 * a round glowing device with a lightning bolt and four diagonal clamps.
 */
public class FlashAmplifierLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation POSITIVE =
            new ResourceLocation("flash", "textures/misc/tachyon_device.png");
    private static final ResourceLocation NEGATIVE =
            new ResourceLocation("flash", "textures/misc/tachyon_device_negative.png");

    private static final int FULL_BRIGHT = 0xF000F0;

    public FlashAmplifierLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!FlashClientRing.hasAmplifier(player) || player.isInvisible()) {
            return;
        }
        // body box: x -4..4, y 0..12, z -2..2. Armor on the chest sits one pixel further out.
        boolean armored = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        float z = armored ? -3.35F : -2.3F;
        ResourceLocation texture = FlashClientVisualLevels.isNegative(player) ? NEGATIVE : POSITIVE;

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float s = 1.0F / 16.0F;
        // 7x7 pixel square centred on the chest (the texture has transparent corners)
        float x0 = -3.5F * s, x1 = 3.5F * s, y0 = 0.7F * s, y1 = 7.7F * s, zz = z * s;
        vertex(consumer, m, n, x1, y1, zz, 0.0F, 0.0F);
        vertex(consumer, m, n, x1, y0, zz, 0.0F, 1.0F);
        vertex(consumer, m, n, x0, y0, zz, 1.0F, 1.0F);
        vertex(consumer, m, n, x0, y1, zz, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n,
                               float x, float y, float z, float u, float v) {
        c.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0.0F, 0.0F, -1.0F).endVertex();
    }
}

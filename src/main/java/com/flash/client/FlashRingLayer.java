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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the Flash ring on the left hand of players that carry it in the ring
 * slot, so it looks like part of the skin: a gold band with a red gem.
 */
public class FlashRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("flash", "textures/misc/ring_band.png");

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

    // colour index in ring_band.png (one pixel each)
    private static final int GOLD = 0, LIGHT = 1, DARK = 2, RED = 3;

    /**
     * The ring, modelled like the reference: a bevelled band that wraps the hand.
     * Gold with a light top edge, a dark bottom edge and a thin red inlay. The pose
     * must already be at the arm model part. Note that the arm box is not centred on
     * the part origin: it spans x -3..1 (default) or -2..1 (slim), y -2..10.
     */
    public static void drawRing(PoseStack poseStack, MultiBufferSource buffers, int light, boolean slim) {
        VertexConsumer c = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float cx = slim ? -0.5F : -1.0F;
        float hx = slim ? 1.5F : 2.0F;
        float hz = 2.0F;
        band(poseStack, c, light, cx, hx, hz, 7.45F, 7.70F, 0.16F, LIGHT);
        band(poseStack, c, light, cx, hx, hz, 7.70F, 8.80F, 0.30F, GOLD);
        band(poseStack, c, light, cx, hx, hz, 8.00F, 8.50F, 0.33F, RED);
        band(poseStack, c, light, cx, hx, hz, 8.80F, 9.05F, 0.16F, DARK);
    }

    private static void band(PoseStack stack, VertexConsumer c, int light, float cx, float hx, float hz,
                             float y0, float y1, float pad, int color) {
        box(stack, c, light, cx - hx - pad, y0, -hz - pad, cx + hx + pad, y1, hz + pad, color);
    }

    /** Axis-aligned box in model pixels; {@code u} selects the gold (0.0) or the red (0.5) half of the texture. */
    private static void box(PoseStack stack, VertexConsumer c, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        PoseStack.Pose pose = stack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float s = 1.0F / 16.0F;
        float ax = x0 * s, bx = x1 * s, ay = y0 * s, by = y1 * s, az = z0 * s, bz = z1 * s;
        float u0 = color * 0.25F + 0.06F, u1 = color * 0.25F + 0.19F;
        // -Z / +Z / -X / +X / bottom / top
        quad(c, m, n, light, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, u0, u1, 0, 0, -1);
        quad(c, m, n, light, bx, ay, bz, ax, ay, bz, ax, by, bz, bx, by, bz, u0, u1, 0, 0, 1);
        quad(c, m, n, light, ax, ay, bz, ax, ay, az, ax, by, az, ax, by, bz, u0, u1, -1, 0, 0);
        quad(c, m, n, light, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, u0, u1, 1, 0, 0);
        quad(c, m, n, light, ax, ay, bz, bx, ay, bz, bx, ay, az, ax, ay, az, u0, u1, 0, -1, 0);
        quad(c, m, n, light, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, u0, u1, 0, 1, 0);
    }

    private static void quad(VertexConsumer c, Matrix4f m, Matrix3f n, int light,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u0, float u1, float nx, float ny, float nz) {
        vertex(c, m, n, light, x1, y1, z1, u0, 1.0F, nx, ny, nz);
        vertex(c, m, n, light, x2, y2, z2, u1, 1.0F, nx, ny, nz);
        vertex(c, m, n, light, x3, y3, z3, u1, 0.0F, nx, ny, nz);
        vertex(c, m, n, light, x4, y4, z4, u0, 0.0F, nx, ny, nz);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n, int light,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        c.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}

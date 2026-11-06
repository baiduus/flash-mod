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
    private static final ResourceLocation V9 =
            new ResourceLocation("flash", "textures/misc/tachyon_device_v9.png");

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
        // Sits right on the chest surface: the suit hugs the body (+0.28), other armour
        // is inflated by 1.0 and the bare skin's jacket layer by 0.25.
        net.minecraft.world.item.ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float surface;
        if (chest.getItem() instanceof com.flash.item.FlashSuitItem) {
            surface = -2.0F - 0.28F;
        } else if (chest.getItem() instanceof net.minecraft.world.item.ArmorItem) {
            surface = -3.0F;
        } else {
            surface = -2.0F - 0.25F;
        }
        surface -= 0.01F;
        // core colour follows the lightning: positive (yellow) -> red, negative (red) -> yellow, V-9 -> blue
        ResourceLocation texture = FlashClientVisualLevels.isV9(player) ? V9
                : FlashClientVisualLevels.isNegative(player) ? NEGATIVE : POSITIVE;

        // the core breathes: brightness pulses about once a second
        float time = player.tickCount + partialTick;
        float pulse = 0.72F + 0.28F * (0.5F + 0.5F * net.minecraft.util.Mth.sin(time * 0.32F));
        int glow = (int) (255 * pulse);

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        // grey casing with the four clamps (7x7 px, half a pixel thick)
        box(poseStack, consumer, light, 255, -3.5F, 0.5F, surface - 0.5F, 3.5F, 7.5F, surface,
                0, 0, 7, 7, 30);
        // glowing tachyon core with the bolt (5x5 px, 10x10 texels), standing out a bit more
        box(poseStack, consumer, FULL_BRIGHT, glow, -2.5F, 1.5F, surface - 1.0F, 2.5F, 6.5F, surface - 0.5F,
                8, 0, 10, 10, 31);
        poseStack.popPose();
    }

    private static final float TEX_W = 32.0F;
    private static final float TEX_H = 16.0F;

    /**
     * Box in model pixels. The front (-Z) face shows the {@code tw}x{@code th} texels at
     * ({@code u}, {@code v}) the same way vanilla maps a cube's front (texture left = min x);
     * the other faces use the single texel at column {@code sideU}.
     */
    private static void box(PoseStack stack, VertexConsumer c, int light, int bright,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            int u, int v, int tw, int th, int sideU) {
        PoseStack.Pose pose = stack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float s = 1.0F / 16.0F;
        float ax = x0 * s, bx = x1 * s, ay = y0 * s, by = y1 * s, az = z0 * s, bz = z1 * s;
        float fu0 = u / TEX_W, fu1 = (u + tw) / TEX_W, fv0 = v / TEX_H, fv1 = (v + th) / TEX_H;
        float su0 = (sideU + 0.25F) / TEX_W, su1 = (sideU + 0.75F) / TEX_W;
        float sv0 = 0.25F / TEX_H, sv1 = 0.75F / TEX_H;
        // front: top-left (min x, top), top-right, bottom-right, bottom-left
        quad(c, m, n, light, bright, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, fu0, fv0, fu1, fv1, 0, 0, -1);
        quad(c, m, n, light, bright, ax, ay, bz, ax, ay, az, ax, by, az, ax, by, bz, su0, sv0, su1, sv1, -1, 0, 0);
        quad(c, m, n, light, bright, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, su0, sv0, su1, sv1, 1, 0, 0);
        quad(c, m, n, light, bright, ax, ay, bz, bx, ay, bz, bx, ay, az, ax, ay, az, su0, sv0, su1, sv1, 0, -1, 0);
        quad(c, m, n, light, bright, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, su0, sv0, su1, sv1, 0, 1, 0);
    }

    /** Corners in order: top-left, top-right, bottom-right, bottom-left of the texture rect. */
    private static void quad(VertexConsumer c, Matrix4f m, Matrix3f n, int light, int bright,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vertex(c, m, n, light, bright, x1, y1, z1, u0, v0, nx, ny, nz);
        vertex(c, m, n, light, bright, x2, y2, z2, u1, v0, nx, ny, nz);
        vertex(c, m, n, light, bright, x3, y3, z3, u1, v1, nx, ny, nz);
        vertex(c, m, n, light, bright, x4, y4, z4, u0, v1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n, int light, int bright,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        c.vertex(m, x, y, z).color(bright, bright, bright, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}

package com.flash.client;

import com.flash.entity.BlackHole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The black hole, with Hand of God's textures: a disk of perfect black facing the camera, a thin ring of light at
 * its edge (the photon ring), a violet halo, and the accretion disk, a tilted ring of burning cloud turning around
 * it (eletro_nuvem and telecinese_energia, additive).
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHole> {

    private static final ResourceLocation WHITE = new ResourceLocation("hand_of_god", "textures/entity/branco.png");
    private static final ResourceLocation CLOUD = new ResourceLocation("hand_of_god",
            "textures/entity/eletro_nuvem.png");
    private static final ResourceLocation ENERGY = new ResourceLocation("hand_of_god",
            "textures/entity/telecinese_energia.png");
    private static final int SEG = 48;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BlackHole e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float size = e.size(partial);
        if (size <= 0.01F) {
            return;
        }
        float t = e.tickCount + partial;
        float r = BlackHole.RADIUS * size;
        int full = 0xF000F0;
        pose.pushPose();
        // ---- facing the camera: the halo, the black, the photon ring
        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer black = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        disc(black, m, n, r, 0.0F, 0.0F, 0.0F, 1.0F);
        // additive: the colour fades to nothing outwards (the black heart is left untouched)
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
        glowRing(glow, m, n, r, r * 4.2F, 0.35F, 0.12F, 0.55F);
        glowRing(glow, m, n, r, r * 2.2F, 0.9F, 0.5F, 0.25F);
        glowRing(glow, m, n, r, r * 1.12F, 1.0F, 0.9F, 0.7F);
        pose.popPose();
        // ---- the accretion disk: tilted, turning
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(14.0F));
        pose.mulPose(Axis.XP.rotationDegrees(8.0F));
        pose.mulPose(Axis.YP.rotationDegrees(t * 6.0F));
        m = pose.last().pose();
        n = pose.last().normal();
        VertexConsumer cloud = buffers.getBuffer(RenderType.energySwirl(CLOUD, t * 0.01F % 1.0F, t * 0.004F % 1.0F));
        flatRing(cloud, m, n, r * 1.25F, r * 3.6F, 1.0F, 0.6F, 0.25F);
        VertexConsumer energy = buffers.getBuffer(RenderType.energySwirl(ENERGY, -t * 0.006F % 1.0F, 0.0F));
        flatRing(energy, m, n, r * 1.15F, r * 2.4F, 0.7F, 0.45F, 1.0F);
        pose.popPose();
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float w,
                               float r, float g, float b, float a) {
        v.vertex(m, x, y, z).color(r, g, b, a).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void quad(VertexConsumer v, Matrix4f m, Matrix3f n, float s, float r, float g, float b, float a) {
        vertex(v, m, n, -s, -s, 0.0F, 0.0F, 1.0F, r, g, b, a);
        vertex(v, m, n, s, -s, 0.0F, 1.0F, 1.0F, r, g, b, a);
        vertex(v, m, n, s, s, 0.0F, 1.0F, 0.0F, r, g, b, a);
        vertex(v, m, n, -s, s, 0.0F, 0.0F, 0.0F, r, g, b, a);
    }

    /** A filled circle (quads from the centre), in the camera plane. */
    private static void disc(VertexConsumer v, Matrix4f m, Matrix3f n, float rad, float r, float g, float b, float a) {
        for (int i = 0; i < SEG; i++) {
            float a0 = i * Mth.TWO_PI / SEG;
            float a1 = (i + 1) * Mth.TWO_PI / SEG;
            vertex(v, m, n, 0.0F, 0.0F, 0.01F, 0.5F, 0.5F, r, g, b, a);
            vertex(v, m, n, Mth.cos(a0) * rad, Mth.sin(a0) * rad, 0.01F, 0.5F, 0.5F, r, g, b, a);
            vertex(v, m, n, Mth.cos(a1) * rad, Mth.sin(a1) * rad, 0.01F, 0.5F, 0.5F, r, g, b, a);
            vertex(v, m, n, 0.0F, 0.0F, 0.01F, 0.5F, 0.5F, r, g, b, a);
        }
    }

    /** An additive ring from {@code in} (full colour) fading out to {@code out}, in the camera plane. */
    private static void glowRing(VertexConsumer v, Matrix4f m, Matrix3f n, float in, float out, float r, float g,
                                 float b) {
        annulus(v, m, n, in, out, 0.0F, r, g, b, 1.0F, 1.0F, 0.0F);
    }

    /** A ring in the camera plane, the colour going from full inside to {@code fade} of it outside. */
    private static void annulus(VertexConsumer v, Matrix4f m, Matrix3f n, float in, float out, float z, float r,
                                float g, float b, float aIn, float aOut, float fade) {
        float ro = r * fade;
        float go = g * fade;
        float bo = b * fade;
        for (int i = 0; i < SEG; i++) {
            float a0 = i * Mth.TWO_PI / SEG;
            float a1 = (i + 1) * Mth.TWO_PI / SEG;
            vertex(v, m, n, Mth.cos(a0) * in, Mth.sin(a0) * in, z, 0.5F, 0.5F, r, g, b, aIn);
            vertex(v, m, n, Mth.cos(a0) * out, Mth.sin(a0) * out, z, 0.5F, 0.5F, ro, go, bo, aOut);
            vertex(v, m, n, Mth.cos(a1) * out, Mth.sin(a1) * out, z, 0.5F, 0.5F, ro, go, bo, aOut);
            vertex(v, m, n, Mth.cos(a1) * in, Mth.sin(a1) * in, z, 0.5F, 0.5F, r, g, b, aIn);
        }
    }

    /** The flat ring of the disk (in the XZ plane), bright inside and fading out. */
    private static void flatRing(VertexConsumer v, Matrix4f m, Matrix3f n, float in, float out, float r, float g,
                                 float b) {
        for (int i = 0; i < SEG; i++) {
            float a0 = i * Mth.TWO_PI / SEG;
            float a1 = (i + 1) * Mth.TWO_PI / SEG;
            float u0 = (float) i / SEG * 4.0F;
            float u1 = (float) (i + 1) / SEG * 4.0F;
            vertex(v, m, n, Mth.cos(a0) * in, 0.0F, Mth.sin(a0) * in, u0, 0.0F, r, g, b, 1.0F);
            vertex(v, m, n, Mth.cos(a0) * out, 0.0F, Mth.sin(a0) * out, u0, 1.0F, r * 0.1F, g * 0.1F, b * 0.1F, 1.0F);
            vertex(v, m, n, Mth.cos(a1) * out, 0.0F, Mth.sin(a1) * out, u1, 1.0F, r * 0.1F, g * 0.1F, b * 0.1F, 1.0F);
            vertex(v, m, n, Mth.cos(a1) * in, 0.0F, Mth.sin(a1) * in, u1, 0.0F, r, g, b, 1.0F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHole e) {
        return WHITE;
    }
}

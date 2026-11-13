package com.flash.client;

import com.flash.entity.ChaosEnergy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The energy of chaos: a pulsing red heart of Hand of God's telekinetic energy, a halo, and two rings of it turning
 * around (the Scarlet Witch's hex), shedding red sparks.
 */
public class ChaosEnergyRenderer extends EntityRenderer<ChaosEnergy> {

    private static final ResourceLocation WHITE = new ResourceLocation("hand_of_god", "textures/entity/branco.png");
    private static final ResourceLocation ENERGY = new ResourceLocation("hand_of_god",
            "textures/entity/telecinese_energia.png");
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.92F, 0.06F, 0.16F), 1.0F);
    private static final int SEG = 40;

    public ChaosEnergyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChaosEnergy e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partial;
        float grow = Mth.clamp(t / 20.0F, 0.0F, 1.0F);
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.25F);
        float r = 0.45F * grow * pulse;
        pose.pushPose();
        pose.translate(0.0F, 0.25F, 0.0F);
        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
        ring(glow, m, n, 0.0F, r * 3.2F, 0.5F, 0.02F, 0.08F);
        ring(glow, m, n, 0.0F, r * 1.6F, 1.0F, 0.15F, 0.25F);
        ring(glow, m, n, 0.0F, r * 0.7F, 1.0F, 0.75F, 0.8F);
        pose.popPose();
        VertexConsumer swirl = buffers.getBuffer(RenderType.energySwirl(ENERGY, t * 0.02F % 1.0F, t * 0.01F % 1.0F));
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (k == 0 ? 5.0F : -7.0F)));
            pose.mulPose(Axis.XP.rotationDegrees(k == 0 ? 70.0F : 20.0F));
            flat(swirl, pose.last().pose(), pose.last().normal(), r * 1.4F, r * 1.9F);
            pose.popPose();
        }
        pose.popPose();
        if (e.tickCount % 2 == 0 && e.level().random.nextInt(2) == 0) {
            double a = e.level().random.nextDouble() * Math.PI * 2.0D;
            e.level().addParticle(RED, e.getX() + Math.cos(a) * 0.5D, e.getY() + 0.25D, e.getZ() + Math.sin(a) * 0.5D,
                    0.0D, 0.03D, 0.0D);
        }
        super.render(e, yaw, partial, pose, buffers, light);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float w,
                               float r, float g, float b) {
        v.vertex(m, x, y, z).color(r, g, b, 1.0F).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** An additive disc / ring in the camera plane fading out. */
    private static void ring(VertexConsumer v, Matrix4f m, Matrix3f n, float in, float out, float r, float g,
                             float b) {
        for (int i = 0; i < SEG; i++) {
            float a0 = i * Mth.TWO_PI / SEG;
            float a1 = (i + 1) * Mth.TWO_PI / SEG;
            vertex(v, m, n, Mth.cos(a0) * in, Mth.sin(a0) * in, 0.0F, 0.5F, 0.5F, r, g, b);
            vertex(v, m, n, Mth.cos(a0) * out, Mth.sin(a0) * out, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F);
            vertex(v, m, n, Mth.cos(a1) * out, Mth.sin(a1) * out, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F);
            vertex(v, m, n, Mth.cos(a1) * in, Mth.sin(a1) * in, 0.0F, 0.5F, 0.5F, r, g, b);
        }
    }

    private static void flat(VertexConsumer v, Matrix4f m, Matrix3f n, float in, float out) {
        for (int i = 0; i < SEG; i++) {
            float a0 = i * Mth.TWO_PI / SEG;
            float a1 = (i + 1) * Mth.TWO_PI / SEG;
            float u0 = (float) i / SEG * 3.0F;
            float u1 = (float) (i + 1) / SEG * 3.0F;
            vertex(v, m, n, Mth.cos(a0) * in, 0.0F, Mth.sin(a0) * in, u0, 0.0F, 1.0F, 0.2F, 0.25F);
            vertex(v, m, n, Mth.cos(a0) * out, 0.0F, Mth.sin(a0) * out, u0, 1.0F, 0.3F, 0.0F, 0.05F);
            vertex(v, m, n, Mth.cos(a1) * out, 0.0F, Mth.sin(a1) * out, u1, 1.0F, 0.3F, 0.0F, 0.05F);
            vertex(v, m, n, Mth.cos(a1) * in, 0.0F, Mth.sin(a1) * in, u1, 0.0F, 1.0F, 0.2F, 0.25F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ChaosEnergy e) {
        return WHITE;
    }
}

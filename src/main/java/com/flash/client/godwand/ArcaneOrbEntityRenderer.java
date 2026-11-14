package com.flash.client.godwand;

import com.flash.godwand.ArcaneOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Singularity's orb: its glow is drawn with the rest of God Wand's geometry (after the world). */
public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {

    public ArcaneOrbEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light) {
    }

    @Override
    public ResourceLocation getTextureLocation(ArcaneOrbEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }

    public static void renderAll(PoseStack matrices, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = camera.getPosition();
        for (Entity e : com.flash.client.ClientEntityIndex.ARCANE_ORBS) {
            if (!(e instanceof ArcaneOrbEntity orb) || e.isRemoved() || e.distanceToSqr(cam) > 16384.0) {
                continue;
            }
            Vec3 at = orb.getPosition(partial);
            float time = orb.tickCount + partial;
            int level = orb.getChargeLevel();
            float chargeRatio = level / 3.0F;
            float baseScale = 0.16F + level * 0.05F;
            matrices.pushPose();
            matrices.translate(at.x - cam.x, at.y - cam.y + 0.15F, at.z - cam.z);
            Vec3 vel = orb.getDeltaMovement();
            if (vel.lengthSqr() > 0.001) {
                double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                float velYaw = (float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
                float velPitch = (float) (Mth.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
                matrices.mulPose(Axis.YP.rotationDegrees(velYaw));
                matrices.mulPose(Axis.XN.rotationDegrees(velPitch));
            }
            matrices.mulPose(Axis.ZP.rotationDegrees(time * 65.0F));
            ArcaneOrbModel.render(matrices.last().pose(), baseScale, time, chargeRatio);
            matrices.popPose();
        }
    }
}

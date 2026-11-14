package com.flash.client.godwand;

import com.flash.godwand.StoneCannonProjectileEntity;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import org.joml.Matrix4f;

/** Stone Cannon's boulder and its wind ribbons, drawn with the rest of God Wand's geometry (after the world). */
public class StoneCannonEntityRenderer extends EntityRenderer<StoneCannonProjectileEntity> {

    public StoneCannonEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StoneCannonProjectileEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
    }

    @Override
    public ResourceLocation getTextureLocation(StoneCannonProjectileEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }

    public static void renderAll(PoseStack matrices, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = camera.getPosition();
        for (Entity e : com.flash.client.ClientEntityIndex.STONE_CANNONS) {
            if (!(e instanceof StoneCannonProjectileEntity cannon) || e.isRemoved() || e.distanceToSqr(cam) > 16384.0) {
                continue;
            }
            Vec3 vel = cannon.getDeltaMovement();
            if (vel.lengthSqr() < 0.001) {
                continue;
            }
            double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            Vec3 at = cannon.getPosition(partial);
            matrices.pushPose();
            matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
         float yaw = (float)(Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
         float pitch = (float)(Mth.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
         float time = cannon.tickCount + partial;
         int level = cannon.getChargeLevel();
         float spin = time * (50.0F + level * 20.0F);
         float baseScale = 0.38F + level * 0.14F;
                  matrices.mulPose(Axis.YP.rotationDegrees(yaw));
         matrices.mulPose(Axis.XN.rotationDegrees(pitch));
         matrices.mulPose(Axis.ZP.rotationDegrees(spin));
         StoneCannonModel.render(matrices.last().pose(), baseScale, time, 0.45F);
         GodWandGeometry.enableBlend();
         GodWandGeometry.additiveBlend();
         GodWandGeometry.disableCull();
         GodWandGeometry.depthMask(false);
         GodWandGeometry.colored();
         Matrix4f mat = matrices.last().pose();
         int ribbonArms = 3;
         int trailSteps = 24;
         float trailLength = 5.5F + level * 2.2F;
         int rCol = 245;
         int gCol = 250;
         int bCol = 255;

         for (int arm = 0; arm < ribbonArms; arm++) {
            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            float armPhase = arm * ((float) (Math.PI * 2) / ribbonArms);

            for (int s = 0; s <= trailSteps; s++) {
               float progress = (float)s / trailSteps;
               float z = -0.45F * baseScale - progress * trailLength;
               float r = (0.12F + progress * 0.35F) * (0.85F + level * 0.15F);
               float theta = armPhase + progress * 13.194689F - time * 0.25F;
               float cx = r * (float)Math.cos(theta);
               float cy = r * (float)Math.sin(theta);
               float w = 0.018F + progress * 0.025F;
               float nx = -((float)Math.sin(theta)) * w * 0.5F;
               float ny = (float)Math.cos(theta) * w * 0.5F;
               float alpha = (1.0F - progress) * (0.75F + level * 0.08F);
               int a = Mth.clamp((int)(alpha * 255.0F), 0, 255);
               buffer.vertex(mat, cx - nx, cy - ny, z).color(rCol, gCol, bCol, a).endVertex();
               buffer.vertex(mat, cx + nx, cy + ny, z).color(rCol, gCol, bCol, a).endVertex();
            }

            GodWandGeometry.draw(buffer.end());
         }

         GodWandGeometry.depthMask(true);
         GodWandGeometry.enableCull();
         GodWandGeometry.defaultBlendFunc();
         GodWandGeometry.disableBlend();
            matrices.popPose();
        }
    }
}

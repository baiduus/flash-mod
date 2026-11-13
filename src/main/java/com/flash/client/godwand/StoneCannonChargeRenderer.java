package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class StoneCannonChargeRenderer {
   public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != null) {
         Vec3 camPos = cameraObj.getPosition();

         for (Player player : client.level.players()) {
            if (true) {
            if (GodWandClient.charging(player, 4)) {
                  int useTicks = GodWandClient.chargeTicks(player);
                  float animTime = useTicks + tickDelta;
                  Vec3 look = player.getViewVector(tickDelta);
                  Vec3 eyePos = player.getEyePosition(tickDelta);
                  Vec3 drillCenter = eyePos.add(look.scale(1.85)).add(0.0, -0.15, 0.0);
                  Vec3 renderPos = drillCenter.subtract(camPos);
                  matrices.pushPose();
                  matrices.translate(renderPos.x, renderPos.y, renderPos.z);
                  float yaw = player.getViewYRot(tickDelta);
                  float pitch = player.getViewXRot(tickDelta);
                  matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
                  matrices.mulPose(Axis.XP.rotationDegrees(pitch));
                  float spinAngle = StoneCannonModel.chargeDrillRotation(animTime);
                  float chargeRatio = Math.min(1.0F, animTime / 80.0F);
                  float baseScale = 0.22F + chargeRatio * 0.4F;
                  StoneCannonModel.renderChargeWind(matrices.last().pose(), baseScale, animTime, chargeRatio);
                  matrices.mulPose(Axis.ZP.rotationDegrees(spinAngle));
                  StoneCannonModel.render(matrices.last().pose(), baseScale, animTime, 0.4F + chargeRatio * 0.6F);
                  matrices.popPose();
               }
            }
         }
      }
   }
}

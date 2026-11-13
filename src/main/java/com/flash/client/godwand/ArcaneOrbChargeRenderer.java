package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class ArcaneOrbChargeRenderer {
   public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != null) {
         Vec3 camPos = cameraObj.getPosition();

         for (Player player : client.level.players()) {
            if (true) {
            if (GodWandClient.charging(player, 1)) {
                  int useTicks = GodWandClient.chargeTicks(player);
                  float animTime = useTicks + tickDelta;
                  Vec3 look = player.getViewVector(tickDelta);
                  Vec3 eyePos = player.getEyePosition(tickDelta);
                  Vec3 orbCenter = eyePos.add(look.scale(2.15)).add(0.0, -0.16, 0.0);
                  Vec3 renderPos = orbCenter.subtract(camPos);
                  float chargeRatio = Math.min(1.0F, animTime / 45.0F);
                  matrices.pushPose();
                  matrices.translate(renderPos.x, renderPos.y, renderPos.z);
                  matrices.mulPose(cameraObj.rotation());
                  float pulse = (float)Math.sin(animTime * 0.18F) * (0.004F + chargeRatio * 0.006F);
                  float baseScale = 0.11F + chargeRatio * 0.14F + pulse;
                  ArcaneOrbModel.renderCharge(matrices.last().pose(), baseScale, animTime, chargeRatio);
                  matrices.popPose();
               }
            }
         }
      }
   }
}

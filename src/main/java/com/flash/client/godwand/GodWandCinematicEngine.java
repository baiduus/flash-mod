package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.flash.godwand.CinematicFxPayload;
import com.flash.godwand.SupernovaSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class GodWandCinematicEngine {
   private static float trauma = 0.0F;
   private static float traumaDecay = 0.045F;
   private static float shakeTime = 0.0F;
   private static float targetFovMultiplier = 1.0F;
   private static float currentFovMultiplier = 1.0F;
   private static float prevFovMultiplier = 1.0F;
   private static int fovHoldTicks = 0;
   private static float fovRecoverySpeed = 0.08F;
   private static int vacuumSilenceTicks = 0;
   private static float environmentGain = 1.0F;

   public static void onCinematicEvent(CinematicFxPayload payload) {
      float distFactor = 1.0F;
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         double distSq = client.player.distanceToSqr(payload.x(), payload.y(), payload.z());
         if (payload.x() != 0.0 || payload.y() != 0.0 || payload.z() != 0.0) {
            double dist = Math.sqrt(distSq);
            distFactor = distanceFactor(payload.eventType(), dist);
         }
      }

      switch (payload.eventType()) {
         case 0:
            addTrauma(payload.intensity() * distFactor, payload.durationTicks());
            break;
         case 1:
            setFovPunch(payload.intensity(), payload.durationTicks());
            break;
         case 2:
            if (distFactor > 0.1F) {
               vacuumSilenceTicks = Math.max(vacuumSilenceTicks, payload.durationTicks());
            }
            break;
         case 3:
            if (distFactor > 0.2F) {
               setFovFocus(payload.intensity(), payload.durationTicks(), 0.12F);
            }
            break;
         case 4:
            addTrauma(payload.intensity() * distFactor, payload.durationTicks());
            setFovPunch(1.0F + 0.25F * distFactor, 14);
            break;
         case 5:
            addTrauma(0.45F * distFactor, payload.durationTicks());
            setFovFocus(0.92F, payload.durationTicks(), 0.06F);
            break;
         case 6:
            vacuumSilenceTicks = 0;
            environmentGain = 1.0F;
            addTrauma(0.55F * distFactor, 8);
            setFovPunch(1.0F + 0.12F * distFactor, 8);
      }
   }

   public static float distanceFactor(int eventType, double distance) {
      boolean supernova = eventType == 5 || eventType == 6;
      double near = supernova ? 64.0 : 8.0;
      double far = supernova ? 250.0 : 64.0;
      return (float)Mth.clamp(1.0 - (distance - near) / (far - near), 0.0, 1.0);
   }

   public static void setFovFocus(float targetMultiplier, int holdTicks, float recoverySpeed) {
      targetFovMultiplier = targetMultiplier;
      fovHoldTicks = holdTicks;
      fovRecoverySpeed = recoverySpeed;
   }

   public static void addTrauma(float amount, int durationTicks) {
      trauma = Mth.clamp(trauma + amount, 0.0F, 1.0F);
      if (durationTicks > 0) {
         traumaDecay = Math.max(0.015F, 1.0F / durationTicks);
      }
   }

   public static void setFovPunch(float multiplier, int holdTicks) {
      targetFovMultiplier = multiplier;
      currentFovMultiplier = multiplier;
      fovHoldTicks = holdTicks;
      fovRecoverySpeed = 0.065F;
   }

   public static void setVacuumSilence(int ticks) {
      vacuumSilenceTicks = Math.max(0, ticks);
   }

   public static void onClientTick() {
      if (trauma > 0.0F) {
         trauma = Math.max(0.0F, trauma - traumaDecay);
         shakeTime += 0.85F;
      }

      Minecraft client = Minecraft.getInstance();
      double novaAge = SupernovaAccretionRenderer.age(0.0F);
      float charge = 0.0F;
      if (client != null && client.player != null) {
         if (GodWandClient.charging(client.player, 3)) {
            charge = Mth.clamp(GodWandClient.chargeTicks(client.player) / 140.0F, 0.0F, 1.0F);
         }
      }

      float distance = novaAge >= 0.0 && client != null && client.player != null
         ? distanceFactor(5, client.player.position().distanceTo(SupernovaAccretionRenderer.center()))
         : 1.0F;
      float targetEnvironment = 1.0F;
      if (novaAge >= 0.0) {
         float pressure = SupernovaSequence.pressure(novaAge);
         setFovFocus(1.0F + (SupernovaSequence.cameraFov(novaAge) - 1.0F) * (distance > 0.0F ? 0.45F + distance * 0.55F : 0.0F), 2, 0.08F);
         if (novaAge < 324.0) {
            trauma = Math.max(trauma, (0.06F + pressure * 0.2F) * distance);
         } else {
            trauma *= 0.5F;
            vacuumSilenceTicks = 2;
         }

         targetEnvironment = 1.0F - (1.0F - SupernovaSequence.environmentGain(novaAge)) * distance;
      } else if (charge > 0.0F) {
         setFovFocus(1.0F - charge * charge * 0.06F, 2, 0.08F);
         trauma = Math.max(trauma, 0.04F * charge);
      }

      if (vacuumSilenceTicks > 0) {
         targetEnvironment = Math.min(targetEnvironment, 0.04F);
      }

      environmentGain = Mth.lerp(0.22F, environmentGain, targetEnvironment);
      SupernovaSoundscape.tick(novaAge, charge, distance);
      prevFovMultiplier = currentFovMultiplier;
      if (fovHoldTicks > 0) {
         fovHoldTicks--;
         currentFovMultiplier = Mth.lerp(0.25F, currentFovMultiplier, targetFovMultiplier);
      } else {
         currentFovMultiplier = Mth.lerp(fovRecoverySpeed, currentFovMultiplier, 1.0F);
         if (Math.abs(currentFovMultiplier - 1.0F) < 0.002F) {
            currentFovMultiplier = 1.0F;
            targetFovMultiplier = 1.0F;
         }
      }

      if (vacuumSilenceTicks > 0) {
         vacuumSilenceTicks--;
      }
   }

   public static void applyScreenShake(PoseStack matrices, float tickDelta) {
      if (!(trauma <= 0.001F)) {
         float intensity = trauma * trauma * GodWandConfig.CLIENT.cameraShake;
         float t = shakeTime + tickDelta * 0.85F;
         float pitch = (float)(Math.sin(t * 1.6) * Math.cos(t * 0.7) * 4.2) * intensity;
         float yaw = (float)(Math.cos(t * 1.3) * Math.sin(t * 0.9) * 3.8) * intensity;
         float roll = (float)(Math.sin(t * 2.1) * 2.8) * intensity;
         float transX = (float)(Math.sin(t * 1.8) * 0.065) * intensity;
         float transY = (float)(Math.cos(t * 2.4) * 0.085) * intensity;
         float transZ = (float)(Math.sin(t * 1.1) * 0.045) * intensity;
         matrices.translate(transX, transY, transZ);
         matrices.mulPose(Axis.ZP.rotationDegrees(roll));
         matrices.mulPose(Axis.XP.rotationDegrees(pitch));
         matrices.mulPose(Axis.YP.rotationDegrees(yaw));
      }
   }

   /** The camera shake now (pitch, yaw, roll in degrees), or null. */
   public static float[] shake(float tickDelta) {
      if (trauma <= 0.001F) {
         return null;
      }
      float intensity = trauma * trauma * GodWandConfig.CLIENT.cameraShake;
      float t = shakeTime + tickDelta * 0.85F;
      return new float[] {(float)(Math.sin(t * 1.6) * Math.cos(t * 0.7) * 4.2) * intensity,
         (float)(Math.cos(t * 1.3) * Math.sin(t * 0.9) * 3.8) * intensity, (float)(Math.sin(t * 2.1) * 2.8) * intensity};
   }

   public static double getFovMultiplier(float tickDelta) {
      float interpolated = Mth.lerp(tickDelta, prevFovMultiplier, currentFovMultiplier);
      return GodWandConfig.CLIENT.cameraFov ? interpolated : 1.0;
   }

   public static float getEnvironmentGain() {
      return Math.min(FugaCinematic.environmentGain(), Math.min(environmentGain, AtomicCinematic.environmentGain()));
   }

   public static boolean isVacuumSilenceActive() {
      return vacuumSilenceTicks > 0;
   }

   public static void reset() {
      SupernovaSoundscape.reset();
      environmentGain = 1.0F;
      trauma = 0.0F;
      currentFovMultiplier = 1.0F;
      prevFovMultiplier = 1.0F;
      targetFovMultiplier = 1.0F;
      fovHoldTicks = 0;
      vacuumSilenceTicks = 0;
   }
}

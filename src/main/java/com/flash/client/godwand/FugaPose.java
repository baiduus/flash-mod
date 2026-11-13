package com.flash.client.godwand;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FugaPose {
   static final float TWIST = 0.85F;
   static final double CLAP = 95.0;

   private FugaPose() {
   }

   public static boolean apply(int casterId, float partial, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head) {
      FugaRenderer.Frame frame = FugaRenderer.frameFor(casterId, partial);
      if (frame != null && frame.phase() <= 2 && (frame.phase() != 2 || !(frame.age() >= 14.0))) {
         boolean aligned = frame.phase() == 0 && FugaAimRig.availableFor.test(casterId);
         FugaPose.Arms pose = arms(frame.phase(), frame.age(), head.xRot, aligned ? 0.0F : head.yRot);
         if (aligned) {
            head.yRot = 0.0F;
         }

         float w = pose.weight();
         rightArm.xRot = Mth.lerp(w, rightArm.xRot, pose.rightPitch());
         rightArm.yRot = Mth.lerp(w, rightArm.yRot, pose.rightYaw());
         rightArm.zRot = Mth.lerp(w, rightArm.zRot, pose.rightRoll());
         leftArm.xRot = Mth.lerp(w, leftArm.xRot, pose.leftPitch());
         leftArm.yRot = Mth.lerp(w, leftArm.yRot, pose.leftYaw());
         leftArm.zRot = Mth.lerp(w, leftArm.zRot, pose.leftRoll());
         body.yRot = Mth.lerp(w, body.yRot, pose.bodyYaw());
         rightArm.x = -5.0F * (float)Math.cos(pose.bodyYaw());
         rightArm.z = 5.0F * (float)Math.sin(pose.bodyYaw());
         leftArm.x = 5.0F * (float)Math.cos(pose.bodyYaw());
         leftArm.z = -5.0F * (float)Math.sin(pose.bodyYaw());
         return true;
      } else {
         return false;
      }
   }

   public static FugaPose.Arms arms(int phase, double age, float pitch, float yaw) {
      double charge = phase == 0 ? Math.min(age, 200.0) : 200.0;
      float pull = FugaCinematic.smooth(charge, 48.0, 64.0) * (1.0F - FugaCinematic.smooth(charge, 90.0, 95.0));
      float draw = FugaCinematic.smooth(charge, 144.0, 190.0);
      float settle = FugaCinematic.smooth(charge, 172.0, 200.0);
      float idle = phase == 0 && age > 200.0 ? (float)Math.sin((age - 200.0) * 0.065) * 0.009F * FugaCinematic.smooth(age, 200.0, 212.0) : 0.0F;
      float rightPitch = Mth.lerp(draw, -1.35F - 0.32F * pull, phase == 0 ? (float) (-Math.PI / 2) : -1.5F);
      float leftPitch = Mth.lerp(draw, -1.35F + 0.26F * pull, (float) (-Math.PI / 2));
      float rightYaw = Mth.lerp(draw, Mth.lerp(pull, -0.55F, 0.22F), -0.5F);
      float leftYaw = Mth.lerp(draw, Mth.lerp(pull, 0.55F, -0.22F), phase == 0 ? 0.1F : 0.42F);
      rightPitch += pitch * draw + (phase == 0 ? 0.0F : idle);
      leftPitch += pitch * draw;
      rightYaw += yaw * draw;
      leftYaw += yaw * draw;
      float release = phase == 0 ? 0.0F : (phase == 1 ? FugaCinematic.smooth(age, 0.0, 7.0) : 1.0F);
      float relax = phase == 1 ? FugaCinematic.smooth(age, 7.0, 18.0) : (phase == 2 ? 1.0F : 0.0F);
      rightPitch += release * 0.2F;
      rightYaw -= release * 0.22F;
      leftPitch -= release * (1.0F - relax) * 0.32F;
      float rest = relax * 0.8F;
      rightPitch = Mth.lerp(rest, rightPitch, -0.2F);
      rightYaw = Mth.lerp(rest, rightYaw, 0.0F);
      leftPitch = Mth.lerp(rest, leftPitch, -0.2F);
      leftYaw = Mth.lerp(rest, leftYaw, 0.0F);
      float weight = phase == 2 ? 1.0F - FugaCinematic.smooth(age, 0.0, 14.0) : 1.0F;
      float tension = 0.04F * (float)Math.sin(Math.PI * settle);
      return new FugaPose.Arms(
         rightPitch - (phase == 0 ? 0.0F : tension),
         rightYaw,
         -0.12F * (1.0F - draw) - (phase == 0 ? 0.0F : 0.08F) * draw - release * 0.2F,
         leftPitch,
         leftYaw,
         0.12F * (1.0F - draw),
         phase == 0 ? 0.85F * draw : ((0.85F + 0.23F * release) * draw + tension) * (1.0F - rest),
         weight
      );
   }

   public static void stance(int casterId, float partial, ModelPart rightLeg, ModelPart leftLeg) {
      FugaRenderer.Frame f = FugaRenderer.frameFor(casterId, partial);
      if (f != null && f.phase() <= 1) {
         float draw = f.phase() == 0 ? FugaCinematic.smooth(f.age(), 144.0, 190.0) : 1.0F - FugaCinematic.smooth(f.age(), 10.0, 24.0);
         float hips = 0.595F * draw;
         rightLeg.xRot += 0.34F * draw;
         leftLeg.xRot -= 0.18F * draw;
         rightLeg.yRot += hips;
         leftLeg.yRot += hips;
         rightLeg.z = rightLeg.z + 1.9F * Mth.sin(hips);
         leftLeg.z = leftLeg.z - 1.9F * Mth.sin(hips);
      }
   }

   static Vec3 handFocus(double age, float pitch) {
      return handFocus(age, pitch, false);
   }

   static Vec3 handFocus(double age, float pitch, boolean rig) {
      return hand(age, pitch, true, rig).add(hand(age, pitch, false, rig)).scale(0.5);
   }

   static Vec3 hand(double age, float pitch, boolean right) {
      return hand(age, pitch, right, false);
   }

   static Vec3 hand(double age, float pitch, boolean right, boolean rig) {
      FugaPose.Arms pose = arms(0, age, pitch, 0.0F);
      Vec3 hand = right ? wrist(pose.rightPitch(), pose.rightYaw(), pose.rightRoll(), true) : wrist(pose.leftPitch(), pose.leftYaw(), pose.leftRoll(), false);
      double side = right ? -1.0 : 1.0;
      hand = hand.add(-side * 5.0 * (Math.cos(pose.bodyYaw()) - 1.0) / 16.0, 0.0, side * 5.0 * Math.sin(pose.bodyYaw()) / 16.0);
      return right && rig ? hand.lerp(FugaAimRig.hand(age, pitch), FugaAimRig.weight(age)) : hand;
   }

   private static Vec3 wrist(float pitch, float yaw, float roll, boolean right) {
      Vector3f position = new Quaternionf().rotationZYX(roll, yaw, pitch).transform(new Vector3f(right ? -1.0F : 1.0F, 9.0F, 0.0F));
      return new Vec3(-(position.x + (right ? -5 : 5)) / 16.0, 1.5 - (position.y + 2.0F) / 16.0, -position.z / 16.0);
   }

   public record Arms(float rightPitch, float rightYaw, float rightRoll, float leftPitch, float leftYaw, float leftRoll, float bodyYaw, float weight) {
   }
}

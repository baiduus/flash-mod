package com.flash.client.godwand;

import java.util.function.IntPredicate;
import net.minecraft.world.phys.Vec3;

public final class FugaAimRig {
   public static IntPredicate availableFor = id -> false;

   private FugaAimRig() {
   }

   public static float weight(double age) {
      return age < 144.0 ? 0.0F : FugaCinematic.smooth(age, 144.0, 184.0);
   }

   public static FugaAimRig.Joints joints(double age, float pitch) {
      float turn = FugaPose.arms(0, age, pitch, 0.0F).bodyYaw();
      Vec3 shoulder = new Vec3(-5.0 * Math.cos(turn), 2.0, 5.0 * Math.sin(turn));
      Vec3 front = FugaPose.hand(age, pitch, false, false);
      Vec3 level = FugaPose.hand(age, 0.0F, false, false);
      Vec3 frontModel = new Vec3(-front.x * 16.0, (1.5 - front.y) * 16.0, -front.z * 16.0);
      double drawLength = 3.0 + level.z * 16.0;
      Vec3 direction = new Vec3(0.0, Math.sin(pitch), -Math.cos(pitch));
      Vec3 hand = frontModel.subtract(direction.scale(drawLength));
      Vec3 rear = hand.subtract(direction.scale(5.0));
      Vec3 elbow = new Vec3(-5.5, rear.y, rear.z - 1.5 * Math.cos(pitch));
      Vec3 tip = elbow.add(hand.subtract(elbow).scale(1.1428571428571428));
      return new FugaAimRig.Joints(shoulder, elbow, tip);
   }

   public static Vec3 hand(double age, float pitch) {
      FugaAimRig.Joints j = joints(age, pitch);
      Vec3 p = j.elbow().lerp(j.tip(), 0.875);
      return new Vec3(-p.x / 16.0, 1.5 - p.y / 16.0, -p.z / 16.0);
   }

   public record Joints(Vec3 shoulder, Vec3 elbow, Vec3 tip) {
   }
}

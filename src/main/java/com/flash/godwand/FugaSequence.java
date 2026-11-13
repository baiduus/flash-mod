package com.flash.godwand;

import net.minecraft.world.phys.Vec3;

public final class FugaSequence {
   public static final int CHARGE_TICKS = 200;
   public static final int ORB_TICKS = 80;
   public static final int RETRACT_TICK = 225;
   public static final int END_TICK = 305;
   public static final int COLUMN_HEIGHT = 384;
   public static final int SETTLE_TICKS = 240;
   public static final double SPEED = 4.0;
   public static final double MAX_RANGE = 160.0;
   public static final double RADIUS = 48.0;
   public static final int TRANSITION_TICKS = 14;
   static final double FLIGHT_EASE = 6.0;

   private FugaSequence() {
   }

   public static double chargeAge(double age) {
      return age * 120.0 / 200.0;
   }

   public static double visualAge(double age) {
      return age < 80.0 ? age * 0.5 : (age < 105.0 ? 40.0 + (age - 80.0) * 0.8 : 60.0 + (age - 80.0 - 25.0) * 0.5);
   }

   public static boolean movementLocked(int phase) {
      return phase == 0;
   }

   public static boolean ready(long now, long started) {
      return now >= started && now - started >= 200L;
   }

   public static double impactAge(double now, long phaseAt) {
      return Math.max(0.0, now - phaseAt);
   }

   public static boolean terrainActive(double age) {
      return age >= 72.0 && age < 545.0;
   }

   public static float columnRadius(double age) {
      return !(age < 80.0) && !(age >= 305.0) ? (float)(48.0 * (age <= 225.0 ? 1.0 : (305.0 - age) / 80.0)) : 0.0F;
   }

   public static Vec3 launchTarget(Vec3 origin, Vec3 look) {
      Vec3 direction = look.normalize();
      double range = 160.0;
      double[] starts = new double[]{origin.x, origin.y, origin.z};
      double[] axes = new double[]{direction.x, direction.y, direction.z};

      for (int i = 0; i < 3; i++) {
         if (Math.abs(axes[i]) > 1.0E-9) {
            range = Math.min(range, Math.max(0.0, ((axes[i] > 0.0 ? 29999999 : -29999999) - starts[i]) / axes[i]));
         }
      }

      return origin.add(direction.scale(range));
   }

   public static double flightDistance(double age) {
      return !(age > 0.0) ? 0.0 : 4.0 * age - 18.0 * (1.0 - Math.exp(-age / 6.0));
   }

   public static Vec3 arrowPosition(Vec3 origin, Vec3 target, double age) {
      double distance = origin.distanceTo(target);
      return distance < 1.0E-9 ? origin : origin.lerp(target, Math.max(0.0, Math.min(1.0, flightDistance(age) / distance)));
   }
}

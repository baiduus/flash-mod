package com.flash.godwand;

import net.minecraft.util.Mth;
public final class SupernovaSequence {
   public static final int CHARGE_TICKS = 140;
   public static final int SKY_RELEASE = 348;
   public static final int SKY_CLOSE_START = 442;
   public static final int SKY_CLOSE_END = 534;
   public static final int BURST_TICKS = 280;
   public static final int SEQUENCE_END = 628;
   public static final int PEEL_END = 250;
   public static final int SWALLOW_END = 326;
   public static final int QUIET_START = 324;
   public static final int CUT_START = 328;
   public static final int CUT_END = 330;
   public static final int FLARE_START = 332;
   public static final int IMPACT = 340;

   public static float phase(double age, double start, double end) {
      float t = (float)Mth.clamp((age - start) / (end - start), 0.0, 1.0);
      return t * t * (3.0F - 2.0F * t);
   }

   public static float chargeRiftScale(double ticks) {
      return phase(ticks, 0.0, 140.0);
   }

   public static float skyCollapse(double age) {
      return phase(age, 442.0, 534.0);
   }

   public static float skyBlackout(double age) {
      return phase(age, 348.0, 368.0) * (1.0F - skyCollapse(age));
   }

   public static float skyScale(double age) {
      return (1.0F + 0.65F * phase(age, 348.0, 368.0)) * (1.0F - skyCollapse(age));
   }

   public static float activationPulse(double age) {
      return phase(age, 0.0, 2.0) * (1.0F - phase(age, 5.0, 22.0));
   }

   public static float skyPulseRadius(double age) {
      return age < 442.0 ? 6.0F + 90.0F * phase(age, 0.0, 22.0) : 225.0F * (1.0F - skyCollapse(age));
   }

   public static float skyPulseStrength(double age) {
      return age < 442.0 ? activationPulse(age) * 0.65F : 0.65F * phase(age, 442.0, 448.0) * (1.0F - phase(age, 528.0, 534.0));
   }

   public static float swallow(double age, double seed) {
      return phase(age, 250.0 + Mth.clamp(seed, 0.0, 1.0) * 14.0, 326.0);
   }

   public static float pressure(double age) {
      return phase(age, 12.0, 220.0) * (0.35F + 0.65F * phase(age, 175.0, 320.0));
   }

   public static float ribbonVisibility(double age) {
      return phase(age, 0.0, 25.0) * (1.0F - phase(age, 322.0, 328.0));
   }

   public static float coreRadius(double age) {
      float grow = (float)Math.pow(Mth.clamp(age / 340.0, 0.0, 0.9647058823529412), 0.95);
      return (0.15F + grow * 8.35F) * (1.0F - phase(age, 328.0, 330.0));
   }

   public static float cameraFov(double age) {
      float wide = 0.96F + 0.09F * phase(age, 0.0, 80.0) + 0.03F * phase(age, 80.0, 220.0);
      return wide + (0.88F - wide) * phase(age, 250.0, 324.0) + 0.055F * activationPulse(age);
   }

   public static float environmentGain(double age) {
      return (1.0F - 0.2F * pressure(age)) * (1.0F - 0.96F * phase(age, 316.0, 324.0));
   }
}

package com.flash.godwand;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class AtomicSequence {
   public static final int BEAM_RETRACTION = 574;
   public static final int CHARGE_TICKS = 380;
   public static final int WAIT_TICK = 406;
   public static final int DETONATION = 414;
   public static final int END = 834;
   public static final int MAX_RADIUS = 98;
   public static final int CAGE_RADIUS = 26;
   public static final int ATOMIC_WORD = 396;
   public static final int BEAM_HEIGHT = 1024;
   public static final int BEAM_DEPTH = 30;
   public static final int CRATER_DEPTH = 16;
   public static final int SCAR_FADE_START = 1434;
   public static final int SCAR_END = 1494;

   public static float phase(double age, double start, double end) {
      float t = (float)Mth.clamp((age - start) / (end - start), 0.0, 1.0);
      return t * t * (3.0F - 2.0F * t);
   }

   public static float castAge(double now, long started, long releasedAt) {
      return (float)(releasedAt >= 0L && now >= releasedAt ? 406.0 + now - releasedAt : Math.min(406.0, Math.max(0.0, now - started)));
   }

   public static long releaseTime(long now, long started) {
      return Math.max(now, started + 406L + 3L);
   }

   public static float heartbeat(double ticks) {
      if (ticks < 0.0) {
         return 0.0F;
      }

      double p = ticks % 28.0;
      double first = Math.min(p, 28.0 - p);
      double second = p - 5.0;
      return (float)Math.max(Math.exp(-first * first / 2.0), 0.6 * Math.exp(-second * second / 2.0));
   }

   public static float holdIdle(double ticks) {
      return (float)(Math.sin(ticks * 0.075) * 0.018 + Math.sin(ticks * 0.023) * 0.006);
   }

   public static float releaseFlash(double age) {
      return phase(age, 406.0, 408.0) * (1.0F - phase(age, 408.0, 410.0));
   }

   public static float charge(double age) {
      return phase(age, 0.0, 380.0);
   }

   public static float visibility(double age) {
      return age < 0.0 ? 0.0F : 1.0F - phase(age, 814.0, 834.0);
   }

   public static float compression(double age) {
      double remaining = 1.0 - Mth.clamp((age - 396.0) / 10.0, 0.0, 1.0);
      return (float)(1.0 - remaining * remaining * remaining);
   }

   public static float blackout(double age) {
      return 0.64F * phase(age, 400.0, 406.0) * (1.0F - phase(age, 406.0, 408.0));
   }

   public static float circuitProgress(double age, double seed) {
      return phase(age, 115.0 + seed * 12.0, 180.0);
   }

   public static float junctionWaveRadius(double age) {
      return !(age <= 180.0) && !(age >= 203.0) ? (float)(age - 180.0) * 1.55F : 0.0F;
   }

   public static float armRaise(double age) {
      return phase(age, 294.0, 344.0);
   }

   public static float armSpread(double age) {
      return phase(age, 396.0, 402.0);
   }

   public static float armWindup(double age) {
      return phase(age, 382.0, 394.0) * (1.0F - armSpread(age));
   }

   public static float facing(double age) {
      return phase(age, 374.0, 394.0) * (1.0F - phase(age, 774.0, 802.0));
   }

   public static float blendHeadYaw(float current, float target, float amount) {
      float from = Mth.wrapDegrees(current * (180.0F / (float)Math.PI));
      float to = Mth.wrapDegrees(target * (180.0F / (float)Math.PI));
      return Mth.wrapDegrees(Mth.rotLerp(amount, from, to)) * (float) (Math.PI / 180.0);
   }

   public static Vec3 headLook(double age, double seed) {
      double beat = Math.floor(age / 58.0);
      double t = phase(age, beat * 58.0, (beat + 1.0) * 58.0);
      double[] look = new double[3];

      for (int axis = 0; axis < 3; axis++) {
         look[axis] = moteNoise(beat + seed * 17.0, axis + 5) * (1.0 - t) + moteNoise(beat + 1.0 + seed * 17.0, axis + 5) * t;
      }

      return new Vec3(-0.18 + look[0] * 0.1, (look[1] - 0.5) * 0.58, (look[2] - 0.5) * 0.035);
   }

   public static Vec3 wideFocus(Vec3 camera, double fov) {
      double distance = camera.horizontalDistance();
      double half = Math.atan(Math.tan(Math.toRadians(Mth.clamp(fov, 20.0, 128.0) / 2.0)) * 0.68);
      double foot = Math.atan2(-14.0 - camera.y, Math.max(0.1, distance - 98.0));
      double elevation = Math.min(Math.atan2(104.0 - camera.y, distance), foot + half);
      return new Vec3(0.0, camera.y + Math.tan(elevation) * distance, 0.0);
   }

   public static float transitionOpacity(double age) {
      float opacity = 0.0F;

      for (int cut : new int[]{115, 205, 285}) {
         opacity = Math.max(opacity, 0.48F * (1.0F - phase(Math.abs(age - cut), 0.0, 6.0)));
      }

      return opacity;
   }

   public static float armPitch(double age) {
      float start = -0.12F - 1.3F * activation(age);
      float up = start + (-2.2F - start) * armRaise(age);
      up += (-1.15F - up) * armWindup(age);
      return up + (-0.12F - up) * armSpread(age);
   }

   public static float holoRadius(double age) {
      return 0.25F + 2.1F * phase(age, 290.0, 370.0);
   }

   public static float shockRadius(double age) {
      return age < 414.0 ? 0.0F : 98.0F * phase(age, 414.0, 534.0);
   }

   public static float beamRadius(double age) {
      return shockRadius(age) * (1.0F - phase(age, 574.0, 734.0));
   }

   public static float beamHeight(double age) {
      return age < 414.0 ? 0.0F : 1024.0F * phase(age, 414.0, 430.0);
   }

   public static float blastPulse(double age) {
      return 1.0F;
   }

   public static float scarOpacity(double age) {
      return phase(age, 644.0, 734.0) * (0.42F + 0.33F * (1.0F - phase(age, 814.0, 834.0))) * (1.0F - phase(age, 1434.0, 1494.0));
   }

   public static boolean protectedDuringCast(double age) {
      return age >= 0.0 && age < 834.0;
   }

   public static boolean columnOccludes(Vec3 eye, Vec3 point, double age) {
      double radius = beamRadius(age);
      double height = beamHeight(age);
      if (!(radius <= 0.0) && !(height <= 0.0)) {
         Vec3 d = point.subtract(eye);
         double a = d.x * d.x + d.z * d.z;
         double b = eye.x * d.x + eye.z * d.z;
         double c = eye.x * eye.x + eye.z * eye.z - radius * radius;
         double near = 0.0;
         double far = 1.0;
         if (a < 1.0E-8) {
            if (c > 0.0) {
               return false;
            }
         } else {
            double discriminant = b * b - a * c;
            if (discriminant < 0.0) {
               return false;
            }

            double root = Math.sqrt(discriminant);
            near = Math.max(near, (-b - root) / a);
            far = Math.min(far, (-b + root) / a);
         }

         if (Math.abs(d.y) < 1.0E-8) {
            if (eye.y < -30.0 || eye.y > height) {
               return false;
            }
         } else {
            double low = (-30.0 - eye.y) / d.y;
            double high = (height - eye.y) / d.y;
            near = Math.max(near, Math.min(low, high));
            far = Math.min(far, Math.max(low, high));
         }

         return far > near && near < 0.999999;
      } else {
         return false;
      }
   }

   public static float letterbox(double age) {
      return age >= 396.0 && age < 414.0 ? 1.0F : 0.0F;
   }

   public static float returnBlend(double age) {
      return phase(age, 802.0, 826.0);
   }

   public static boolean movementLocked(double age) {
      return age >= 0.0 && age < 826.0;
   }

   public static float activation(double age) {
      return phase(age, 0.0, 8.0) * (1.0F - phase(age, 22.0, 40.0));
   }

   public static boolean impact(double age) {
      return age >= 10.0 && age < 14.0 || age >= 396.0 && age < 400.0 || age >= 410.0 && age < 412.0;
   }

   public static Vec3 groundTracePoint(double age, double seed) {
      double a = seed * Math.PI * 2.0;
      Vec3[] path = new Vec3[]{polar(2.0, 0.08, a), polar(6.0, 0.08, a + 0.07), polar(11.0, 0.08, a + 0.07), polar(15.0, 0.08, a), polar(26.0, 0.08, a)};
      double t = phase(age, 25.0 + seed * 55.0, 100.0 + seed * 45.0) * 4.0F;
      int i = Math.min(3, (int)t);
      return path[i].lerp(path[i + 1], t - i);
   }

   private static Vec3 polar(double r, double h, double angle) {
      return new Vec3(Math.cos(angle) * r, h, Math.sin(angle) * r);
   }

   public static Vec3 convergencePoint(double age, double seed, double progress, Vec3 target) {
      double t = Mth.clamp(progress, 0.0, 1.0);
      double u = 1.0 - t;
      double a = seed * Math.PI * 2.0 + age * 0.006;
      boolean overhead = seed >= 0.5;
      double r = overhead ? 4.0 + (seed - 0.5) * 8.0 : 1.8 + seed * 0.8;
      Vec3 source = polar(r, overhead ? 14.0 + seed * 5.0 : 0.12, a);
      Vec3 bend = polar(r * 0.8, overhead ? 9.0 : 1.2 + seed, a + 0.35);
      Vec3 approach = target.add(polar(0.7, overhead ? 2.5 : -0.65, a + 0.5));
      return source.scale(u * u * u).add(bend.scale(3.0 * u * u * t)).add(approach.scale(3.0 * u * t * t)).add(target.scale(t * t * t));
   }

   private static double moteNoise(double seed, double salt) {
      double value = Math.sin(seed * 1437.7 + salt * 311.7) * 43758.5453;
      return value - Math.floor(value);
   }

   public static Vec3 risingMote(double rise, double seed) {
      double a = moteNoise(seed, 1.0) * Math.PI * 2.0;
      double r = Math.sqrt(9.0 + moteNoise(seed, 0.0) * 567.0);
      return polar(r, 0.12 + rise * (14.0 + moteNoise(seed, 2.0) * 26.0), a);
   }

   public static double moteClock(double age) {
      return Math.min(age, 390.0);
   }

   public static Vec3 releaseMote(Vec3 point, double age) {
      float push = phase(age, 396.0, 402.0);
      double r = Math.max(0.001, point.horizontalDistance());
      return point.add(point.x / r * push * 12.0, push * (0.3 + point.y * 0.06), point.z / r * push * 12.0);
   }

   public static float fortressWaveHeight(double age) {
      if (!(age < 170.0) && !(age >= 350.0)) {
         double cycle = (age - 170.0) % 60.0;
         return !(cycle < 8.0) && !(cycle >= 50.0) ? 54.0F * phase(cycle, 8.0, 50.0) : -1.0F;
      } else {
         return -1.0F;
      }
   }

   public static float releaseRadius(double age) {
      return !(age < 396.0) && !(age >= 414.0) ? 1.0F + 34.0F * phase(age, 396.0, 404.0) : 0.0F;
   }

   public static float cameraBudget(int chunks) {
      return Mth.clamp((Math.max(2, chunks) - 1) * 16, 8, 112);
   }

   public static float cameraBudget(int chunks, double age) {
      return cameraBudget(chunks) + (age >= 466.0 && age < 614.0 ? 16 : 0);
   }

   public static float pressure(double age) {
      return phase(age, 422.0, 442.0) * (1.0F - phase(age, 614.0, 644.0));
   }

   public static float pressureWaveRadius(double age, int wave) {
      if (pressure(age) <= 0.0F) {
         return 0.0F;
      }

      double cycle = (age - 414.0) / 30.0 + wave * 0.25;
      return beamRadius(age) + (float)(cycle - Math.floor(cycle)) * 32.0F;
   }

   public static boolean pressureCrosses(double age, int wave, double distance) {
      if (!(distance <= 98.0) && !(distance > 130.0) && !(pressure(age) <= 0.15F) && !(pressure(age - 1.0) <= 0.15F)) {
         float before = pressureWaveRadius(age - 1.0, wave);
         float now = pressureWaveRadius(age, wave);
         return now >= before && distance >= before - 1.25 && distance <= now + 1.25;
      } else {
         return false;
      }
   }

   public static float pressureShake(double age, double cameraRadius) {
      float impulse = 0.0F;

      for (int wave = 0; wave < 4; wave++) {
         float radius = pressureWaveRadius(age + 2.0, wave);
         if (radius > 0.0F) {
            impulse += (float)Math.exp(-Math.pow((radius - cameraRadius) / 2.2, 2.0)) * 0.32F;
         }
      }

      return impulse * pressure(age);
   }

   public static float shake(double age) {
      if (!(age < 414.0)) {
         double t = age - 414.0;
         return (float)(
               1.3 * Math.exp(-t / 18.0)
                  + 0.34 * Math.exp(-Math.pow((t - 26.0) / 7.0, 2.0))
                  + 0.2 * Math.exp(-Math.pow((t - 120.0) / 12.0, 2.0))
                  + 0.12 * pressure(age)
            )
            * (1.0F - returnBlend(age));
      } else {
         float activation = !(age < 10.0) && !(age >= 28.0) ? (float)(0.32 * Math.exp(-(age - 10.0) / 4.0)) : 0.0F;
         float release = !(age < 396.0) && !(age >= 408.0) ? (float)(0.22 * Math.exp(-(age - 396.0) / 3.0)) : 0.0F;
         float gathering = 0.1F * phase(age, 294.0, 310.0) * (1.0F - phase(age, 378.0, 384.0));
         return activation + release + gathering + 0.55F * phase(age, 412.0, 414.0);
      }
   }

   public static AtomicSequence.Shot shot(double age, float yaw) {
      double radius;
      double height;
      double focus;
      double angle;
      float fov;
      if (age < 40.0) {
         float t = phase(age, 0.0, 40.0);
         radius = 5.2 + 1.6 * t;
         height = 0.6;
         focus = 0.8;
         angle = 70.0F - 18.0F * t;
         fov = 58.0F;
      } else {
         if (age < 115.0) {
            double t = (age - 40.0) / 75.0;
            Vec3 head = groundTracePoint(16.0 + (age - 40.0) * 0.52, 0.0);
            return new AtomicSequence.Shot(head.add(-5.0, 0.6 + 17.0 * t * t, 3.0 + 6.0 * t), head.add(8.0, 0.0, 0.0), 84.0F);
         }

         if (age < 205.0) {
            radius = 0.0;
            height = age < 190.0 ? 96.0 - (age - 115.0) * 35.0 / 75.0 : 61.0 - (age - 190.0) * 0.2;
            focus = 46.0;
            angle = 0.0;
            fov = 64.0F;
         } else if (age < 285.0) {
            double t = (age - 205.0) / 80.0;
            radius = 30.5 - 2.0 * t;
            height = 53.0 - 46.0 * t;
            focus = 30.0 - 12.0 * t;
            angle = -65.0 + 130.0 * t;
            fov = 100.0F;
         } else if (age < 414.0) {
            double t = Mth.clamp((age - 285.0) / 95.0, 0.0, 1.0);
            double out = phase(age, 370.0, 396.0);
            double push = phase(age, 396.0, 402.0);
            radius = 4.8 - 0.6 * t + 0.35 * out + 2.5 * push + 0.008 * Math.max(0.0, age - 396.0) + 0.2 * phase(age, 402.0, 414.0);
            height = 3.5 - 0.4 * t - 1.5 * out;
            focus = 2.15 - 0.8 * out;
            angle = (23.0 - (age - 285.0) * 0.22) * (1.0F - phase(age, 370.0, 396.0));
            fov = (float)(56.0 - 14.0 * out + 2.0 * push);
         } else if (age < 466.0) {
            radius = beamRadius(age) + 20.0F;
            height = 2.8;
            focus = 110.0;
            angle = -28.0;
            fov = 96.0F;
         } else if (age < 614.0) {
            double t = (age - 466.0) / 148.0;
            radius = 210.0 + 10.0 * t;
            height = 60.0;
            focus = 104.0;
            angle = -46.0 - 6.0 * t;
            fov = 128.0F;
         } else if (age < 734.0) {
            radius = beamRadius(age) + 18.0F;
            height = 8.0;
            focus = 70.0;
            angle = -52.0;
            fov = 96.0F;
         } else {
            if (age >= 774.0) {
               double a = Math.toRadians(yaw - 98.0F);
               double back = Math.toRadians(yaw + 196.0F);
               double front = Math.toRadians(yaw);
               Vec3 wide = new Vec3(-Math.sin(a) * 60.0, 52.0, Math.cos(a) * 60.0);
               Vec3 shoulder = new Vec3(-Math.sin(back) * 2.0, 1.55, Math.cos(back) * 2.0);
               Vec3 ahead = new Vec3(-Math.sin(front) * 8.0, 1.5, Math.cos(front) * 8.0);
               return new AtomicSequence.Shot(
                  wide.lerp(shoulder, phase(age, 774.0, 802.0)), new Vec3(0.0, -0.5, 0.0).lerp(ahead, phase(age, 774.0, 798.0)), 76.0F
               );
            }

            radius = 60.0;
            height = 52.0;
            focus = -0.5;
            angle = -84.0 - (age - 734.0) * 0.35;
            fov = 76.0F;
         }
      }

      double radians = Math.toRadians(yaw + angle);
      return new AtomicSequence.Shot(new Vec3(-Math.sin(radians) * radius, height, Math.cos(radians) * radius), new Vec3(0.0, focus, 0.0), fov);
   }

   public record Shot(Vec3 offset, Vec3 focus, float fov) {
   }
}

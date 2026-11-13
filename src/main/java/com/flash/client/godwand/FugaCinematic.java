package com.flash.client.godwand;

import com.flash.godwand.FugaSequence;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class FugaCinematic {
   private static long cast = Long.MIN_VALUE;
   private static boolean skipped;
   private static ClientLevel world;
   private static double renderedFov = 70.0;
   private static float cameraPartial;
   static final double END_CUT = 120.0;
   static final double PILLAR_CUT = 90.0;
   private static final double[] IMPACT_KEYS = new double[]{0.0, 36.0, 39.0, 41.0, 52.0, 100.0, 122.0, 140.0, 160.0};
   private static final double[] IMPACT_OUT = new double[]{30.0, 44.0, 52.0, 60.0, 48.0, 48.0, 48.0, 48.0, 48.0};
   private static final double[] IMPACT_SIDE = new double[]{4.0, 4.0, 4.0, 4.0, 4.0, 4.0, 4.0, 4.0, 4.0};
   private static final double[] CHARGE_KEYS = new double[]{0.0, 24.0, 48.0, 90.0, 96.0, 104.0, 128.0, 144.0, 172.0, 200.0};
   private static final double[] ORBIT = new double[]{-0.32, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.82, 2.25, 2.94};
   private static final double[] DISTANCE = new double[]{0.95, 1.05, 1.1, 2.4, 1.2, 3.6, 4.0, 4.4, 3.9, 3.26};
   private static final double[] HEIGHT = new double[]{1.74, 1.68, 1.38, 1.5, 1.45, 1.6, 1.8, 1.95, 2.1, 1.87};

   private FugaCinematic() {
   }

   private static boolean present(Minecraft client, FugaRenderer.Frame frame) {
      boolean valid = frame != null
         && client.player != null
         && client.level != null
         && client.player.isAlive()
         && !client.player.isSpectator()
         && (frame.phase() == 0 || frame.phase() == 1 || frame.phase() == 2 && frame.age() < 305.0);
      if (valid && (cast != frame.startedAt() || world != client.level)) {
         cast = frame.startedAt();
         world = client.level;
         skipped = false;
      }

      return valid;
   }

   public static boolean openingFaceVisible(FugaRenderer.Frame frame) {
      return frame != null && frame.phase() == 0 && frame.age() < 48.0;
   }

   public static boolean showCaster(float partial) {
      return openingFaceVisible(FugaRenderer.localFrame(partial));
   }

   public static boolean active() {
      Minecraft client = Minecraft.getInstance();
      FugaRenderer.Frame frame = FugaRenderer.localFrame(0.0F);
      return present(client, frame)
         && client.screen == null
         && client.isWindowActive()
         && (frame.phase() == 0 || GodWandConfig.CLIENT.fugaCutscene && !skipped);
   }

   public static boolean movementLocked() {
      Minecraft client = Minecraft.getInstance();
      FugaRenderer.Frame frame = FugaRenderer.localFrame(0.0F);
      return present(client, frame) && (frame.phase() == 0 || GodWandConfig.CLIENT.fugaCutscene && !skipped);
   }

   public static boolean rotationLocked() {
      FugaRenderer.Frame frame = FugaRenderer.localFrame(0.0F);
      return movementLocked() && frame.phase() == 0 && frame.age() < 120.0 && GodWandConfig.CLIENT.fugaCutscene && !skipped;
   }

   public static void tick(Minecraft client) {
      FugaRenderer.Frame frame = FugaRenderer.localFrame(0.0F);
      if (!present(client, frame)) {
         reset();
      } else if (client.screen == null && client.isWindowActive()) {
         if (InputConstants.isKeyDown(client.getWindow().getWindow(), 294)) {
            skip();
         }
      }
   }

   public static void skip() {
      skipped = true;
   }

   public static void reset() {
      cast = Long.MIN_VALUE;
      world = null;
      skipped = false;
      renderedFov = 70.0;
   }

   public static AtomicCinematic.Pose camera(float delta, Vec3 vanilla, float yaw, float pitch) {
      if (!active()) {
         return null;
      }

      Minecraft client = Minecraft.getInstance();
      FugaRenderer.Frame frame = FugaRenderer.localFrame(delta);
      cameraPartial = delta;
      AtomicCinematic.Pose shot = cameraFor(
         frame,
         vanilla,
         yaw,
         pitch,
         client.player.getPosition(delta),
         client.player.getViewYRot(delta),
         client.player.getViewXRot(delta),
         GodWandConfig.CLIENT.fugaCutscene && !skipped
      );
      if (shot != null && !client.level.getChunkSource().hasChunk(Mth.floor(shot.position().x) >> 4, Mth.floor(shot.position().z) >> 4)) {
         return frame.phase() == 0 ? shoulder(client.player.getPosition(delta), client.player.getViewYRot(delta), client.player.getViewXRot(delta)) : null;
      }

      if (shot != null && frame.phase() == 0 && frame.age() >= 24.0 && frame.age() < 144.0) {
         Vec3 subject = client.player.getPosition(delta).add(0.0, 1.3, 0.0);
         BlockHitResult hit = client.level.clip(new ClipContext(subject, shot.position(), Block.COLLIDER, Fluid.NONE, client.player));
         if (hit.getType() != Type.MISS) {
            Vec3 safe = hit.getLocation().add(subject.subtract(hit.getLocation()).normalize().scale(0.15));
            if (safe.distanceTo(subject) < 0.65) {
               return shoulder(client.player.getPosition(delta), client.player.getViewYRot(delta), client.player.getViewXRot(delta));
            }

            shot = look(safe, subject);
         }
      }

      if (shot != null && frame.phase() == 0 && (!GodWandConfig.CLIENT.fugaCutscene || skipped || frame.age() >= 200.0)) {
         Vec3 eye = client.player.getEyePosition(delta);
         BlockHitResult hit = client.level.clip(new ClipContext(eye, shot.position(), Block.COLLIDER, Fluid.NONE, client.player));
         if (hit.getType() != Type.MISS) {
            Vec3 safe = hit.getLocation().add(eye.subtract(hit.getLocation()).normalize().scale(0.15));
            shot = look(safe, eye.add(client.player.getViewVector(delta).scale(80.0)));
         }
      }

      return shot;
   }

   public static AtomicCinematic.Pose cameraFor(
      FugaRenderer.Frame frame, Vec3 vanilla, float vanillaYaw, float vanillaPitch, Vec3 player, float yaw, float pitch, boolean director
   ) {
      if (frame != null && frame.phase() <= 2) {
         double age = Math.max(0.0, frame.age());
         if (frame.phase() == 2) {
            age = FugaSequence.visualAge(age);
         }

         if (frame.phase() == 0) {
            age = FugaSequence.chargeAge(age);
         }

         Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
         Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
         AtomicCinematic.Pose aim = shoulder(player, yaw, pitch);
         if (frame.phase() == 0) {
            return director && !(frame.age() >= 200.0) ? chargeCamera(frame.age(), player, forward, right, pitch, aim, frame.casterId()) : aim;
         }

         if (!director) {
            return null;
         }

         if (frame.phase() == 1) {
            Vec3 d = frame.target().subtract(frame.origin());
            d = d.lengthSqr() < 1.0E-6 ? forward : d.normalize();
            Vec3 side = d.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-6 ? right : side.normalize();
            Vec3 arrow = frame.arrowPosition();
            AtomicCinematic.Pose chase = look(arrow.subtract(d.scale(16.0)).add(side.scale(3.0)).add(0.0, 3.5, 0.0), arrow.add(d.scale(10.0)));
            double stop = FugaRenderer.hitStop(frame.casterId(), cameraPartial);
            if (stop >= 0.0) {
               double rise = smooth(stop, 0.08, 1.0);
               double top = 6.0F + 110.0F * smooth(stop, 0.0, 1.0);
               Vec3 position = chase.position().add(0.0, 14.0 * rise, 0.0).subtract(d.scale(6.0 * rise));
               return look(position, arrow.lerp(arrow.add(d.scale(10.0)), 1.0 - rise).add(0.0, top * 0.8 * rise, 0.0));
            } else {
               return blend(aim, chase, smooth(age, 2.0, 10.0));
            }
         } else {
            if (age >= 160.0) {
               return null;
            }

            Vec3 center = frame.center();
            Vec3 away = new Vec3(player.x - center.x, 0.0, player.z - center.z);
            away = away.lengthSqr() < 1.0 ? forward.scale(-1.0) : away.normalize();
            Vec3 across = away.cross(new Vec3(0.0, 1.0, 0.0));
            AtomicCinematic.Pose impact;
            if (age < 40.0) {
               Vec3 position = center.add(away.scale(spline(age, IMPACT_KEYS, IMPACT_OUT)))
                  .add(across.scale(spline(age, IMPACT_KEYS, IMPACT_SIDE)))
                  .add(0.0, 2.2 + 5.0F * (1.0F - smooth(age, 0.0, 15.0)), 0.0);
               impact = look(position, center.add(0.0, FugaRenderer.orbGap(age) + 3.0, 0.0));
            } else if (age < 90.0) {
               double k = smooth(age, 40.0, 90.0);
               Vec3 position = center.add(away.scale(72.0 + 70.0 * k)).add(across.scale(6.0)).add(0.0, 2.2, 0.0);
               impact = look(position, center.add(0.0, 150.0 - 40.0 * k, 0.0));
            } else if (age < 120.0) {
               double orbit = 1.6 * smooth(age, 90.0, 160.0);
               Vec3 around = away.scale(Math.cos(0.6 + orbit)).add(across.scale(Math.sin(0.6 + orbit)));
               impact = look(center.add(around.scale(150.0)).add(0.0, 95.0, 0.0), center.add(0.0, 35.0, 0.0));
            } else {
               Vec3 eye = player.add(0.0, 1.62, 0.0);
               Vec3 column = center.add(0.0, 55.0, 0.0);
               Vec3 toward = new Vec3(column.x - eye.x, 0.0, column.z - eye.z);
               double gap = toward.length();
               toward = gap < 1.0 ? forward : toward.scale(1.0 / gap);
               Vec3 near = eye.add(toward.scale(Math.min(gap * 0.55, 70.0))).add(0.0, 4.0, 0.0);
               Vec3 position = near.lerp(vanilla, smooth(age, 120.0, 154.0));
               impact = blend(look(position, column), new AtomicCinematic.Pose(vanilla, vanillaYaw, vanillaPitch), smooth(age, 138.0, 156.0));
            }

            return impact;
         }
      } else {
         return null;
      }
   }

   private static AtomicCinematic.Pose blend(AtomicCinematic.Pose a, AtomicCinematic.Pose b, float t) {
      return new AtomicCinematic.Pose(a.position().lerp(b.position(), t), Mth.rotLerp(t, a.yaw(), b.yaw()), Mth.lerp(t, a.pitch(), b.pitch()));
   }

   private static double spline(double age, double[] keys, double[] values) {
      int i = 0;
      int last = keys.length - 1;

      while (i < last - 1 && age > keys[i + 1]) {
         i++;
      }

      double span = keys[i + 1] - keys[i];
      double t = Math.max(0.0, Math.min(1.0, (age - keys[i]) / span));
      double m0 = i == 0 ? 0.0 : (values[i + 1] - values[i - 1]) / (keys[i + 1] - keys[i - 1]);
      double m1 = i + 1 == last ? 0.0 : (values[i + 2] - values[i]) / (keys[i + 2] - keys[i]);
      return (2.0 * t * t * t - 3.0 * t * t + 1.0) * values[i]
         + (t * t * t - 2.0 * t * t + t) * span * m0
         + (-2.0 * t * t * t + 3.0 * t * t) * values[i + 1]
         + (t * t * t - t * t) * span * m1;
   }

   private static AtomicCinematic.Pose chargeCamera(double age, Vec3 player, Vec3 forward, Vec3 right, float pitch, AtomicCinematic.Pose aim, int casterId) {
      Vec3 endpoint = aim.position().subtract(player);
      double angle = sample(age, ORBIT, Math.atan2(endpoint.dot(right), endpoint.dot(forward)));
      double distance = sample(age, DISTANCE, endpoint.horizontalDistance());
      if (age >= 24.0) {
         angle = Math.atan2(endpoint.dot(right), endpoint.dot(forward)) * smooth(age, 144.0, 200.0);
      }

      double height = sample(age, HEIGHT, endpoint.y);
      Vec3 position = player.add(forward.scale(Math.cos(angle) * distance)).add(right.scale(Math.sin(angle) * distance)).add(0.0, height, 0.0);
      Vec3 hands = FugaPose.handFocus(age, (float)Math.toRadians(pitch), FugaAimRig.availableFor.test(casterId));
      Vec3 subject = player.add(right.scale(hands.x)).add(forward.scale(hands.z)).add(0.0, hands.y, 0.0);
      Vec3 focus = age < 24.0 ? player.add(0.0, 1.57, 0.0) : subject;
      focus = focus.lerp(player.add(forward.scale(0.45)).add(0.0, 1.42, 0.0), smooth(age, 144.0, 164.0));
      AtomicCinematic.Pose portrait = look(position, focus);
      float handoff = smooth(age, 188.0, 200.0);
      return new AtomicCinematic.Pose(position, Mth.rotLerp(handoff, portrait.yaw(), aim.yaw()), Mth.lerp(handoff, portrait.pitch(), aim.pitch()));
   }

   private static double sample(double age, double[] values, double endpoint) {
      int i = 0;
      int last = CHARGE_KEYS.length - 1;

      while (i < last - 1 && age > CHARGE_KEYS[i + 1]) {
         i++;
      }

      double span = CHARGE_KEYS[i + 1] - CHARGE_KEYS[i];
      double t = Math.max(0.0, Math.min(1.0, (age - CHARGE_KEYS[i]) / span));
      double a = values[i];
      double b = i + 1 == last ? endpoint : values[i + 1];
      double before = i == 0 ? a : values[i - 1];
      double after = i + 2 >= last ? endpoint : values[i + 2];
      double m0 = i == 0 ? (b - a) / span : (b - before) / (CHARGE_KEYS[i + 1] - CHARGE_KEYS[i - 1]);
      double m1 = i + 1 == last ? 0.0 : (after - a) / (CHARGE_KEYS[i + 2] - CHARGE_KEYS[i]);
      return (2.0 * t * t * t - 3.0 * t * t + 1.0) * a
         + (t * t * t - 2.0 * t * t + t) * span * m0
         + (-2.0 * t * t * t + 3.0 * t * t) * b
         + (t * t * t - t * t) * span * m1;
   }

   private static AtomicCinematic.Pose shoulder(Vec3 player, float yaw, float pitch) {
      Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
      Vec3 right = Vec3.directionFromRotation(0.0F, yaw).cross(new Vec3(0.0, 1.0, 0.0));
      Vec3 eye = player.add(0.0, 1.62, 0.0);
      return look(eye.subtract(forward.scale(3.4)).add(right.scale(-1.15)).add(0.0, 1.05, 0.0), eye.add(forward.scale(80.0)));
   }

   private static AtomicCinematic.Pose look(Vec3 position, Vec3 target) {
      Vec3 d = target.subtract(position);
      return new AtomicCinematic.Pose(position, (float)Math.toDegrees(Math.atan2(-d.x, d.z)), (float)(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))));
   }

   static float smooth(double age, double start, double end) {
      float t = (float)Math.max(0.0, Math.min(1.0, (age - start) / (end - start)));
      return t * t * (3.0F - 2.0F * t);
   }

   public static float environmentGain() {
      FugaRenderer.Frame frame = FugaRenderer.localFrame(0.0F);
      if (frame != null && frame.phase() == 2 && present(Minecraft.getInstance(), frame)) {
         return 0.25F + 0.75F * smooth(frame.age(), 260.0, 300.0);
      } else if (!active() || !GodWandConfig.CLIENT.fugaCutscene || skipped) {
         return 1.0F;
      } else {
         return frame.phase() == 0 ? 0.015F + 0.985F * (1.0F - darkness(frame.age())) : 1.0F;
      }
   }

   static float darkness(double age) {
      return 1.0F - smooth(age, 110.0, 140.0);
   }

   static float silence(double age) {
      return smooth(age, 96.0, 97.5) * (1.0F - smooth(age, 104.0, 112.0));
   }

   public static double fov(double vanilla, float delta) {
      FugaRenderer.Frame frame = FugaRenderer.localFrame(delta);
      if (!active() || !GodWandConfig.CLIENT.cameraFov || !GodWandConfig.CLIENT.fugaCutscene || skipped) {
         return vanilla;
      } else if (frame.phase() == 0) {
         return Mth.lerp(smooth(frame.age(), 140.0, 200.0), 46.0, vanilla);
      } else if (frame.phase() == 2) {
         double age = FugaSequence.visualAge(frame.age());
         return age < 40.0 ? 80.0 : Mth.lerp(smooth(age, 138.0, 156.0), 105.0, vanilla);
      } else {
         return frame.phase() == 1 ? vanilla + 14.0F * smooth(frame.age(), 2.0, 12.0) : vanilla;
      }
   }

   public static float shake(float delta) {
      FugaRenderer.Frame frame = FugaRenderer.localFrame(delta);
      if (frame == null) {
         return 0.0F;
      }

      double age = frame.phase() == 2 ? FugaSequence.visualAge(frame.age()) : (frame.phase() == 0 ? FugaSequence.chargeAge(frame.age()) : frame.age());
      if (frame.phase() == 0) {
         float clap = smooth(frame.age(), 94.0, 95.0) * (1.0F - smooth(frame.age(), 95.0, 100.0));
         return (
               1.4F * clap
                  + 0.16F * smooth(frame.age(), 120.0, 150.0)
                  + 0.14F * smooth(frame.age(), 144.0, 190.0)
                  + 0.22F * smooth(frame.age(), 186.0, 200.0) * (1.0F + smooth(frame.age(), 190.0, 300.0)) * (0.7F + 0.3F * (float)Math.sin(frame.age() * 0.9))
            )
            * GodWandConfig.CLIENT.cameraShake;
      }

      double stop = FugaRenderer.hitStop(frame.casterId(), delta);
      if (frame.phase() == 1 && stop >= 0.0) {
         return (2.6F * (1.0F - smooth(stop, 0.0, 0.25)) + 0.45F) * GodWandConfig.CLIENT.cameraShake;
      }

      float strength = frame.phase() == 1
         ? (1.0F - smooth(age, 0.0, 8.0)) * 1.6F + smooth(age, 3.0, 10.0) * (0.38F + 0.12F * (float)Math.sin(age * 2.3))
         : (
            frame.phase() == 2
               ? (1.0F - smooth(age, 0.0, 10.0)) * 1.5F
                  + (age < 40.0 ? (0.25F + 0.5F * smooth(age, 8.0, 37.0)) * (0.8F + 0.2F * (float)Math.sin(age * 0.8)) : 0.0F)
                  + smooth(age, 39.8, 40.4) * (1.0F - smooth(age, 42.0, 66.0)) * 2.4F
                  + smooth(age, 40.0, 48.0) * (1.0F - smooth(age, 130.0, 160.0)) * (0.35F + 0.9F * ringKick(age))
               : 0.0F
         );
      return strength * GodWandConfig.CLIENT.cameraShake;
   }

   static float ringKick(double age) {
      double best = 0.0;

      for (int i = 0; i < 3; i++) {
         double cycle = ((age - 40.0) / 10.0 + i / 3.0) % 1.0;
         if (cycle < 0.0) {
            cycle++;
         }

         best = Math.max(best, Math.exp(-cycle * 14.0));
      }

      return (float)best;
   }

   static float letterbox(int phase, double age) {
      if (phase == 0) {
         float kick = age >= 95.0 ? 0.06F * (float)Math.exp(-(age - 95.0) / 4.0) : 0.0F;
         return (0.12F - 0.04F * smooth(age, 20.0, 40.0) + 0.03F * smooth(age, 95.0, 99.0) + kick) * (1.0F - smooth(age, 165.0, 195.0));
      }

      if (phase == 1) {
         return 0.08F + 0.05F * (float)Math.exp(-age / 5.0);
      }

      if (age < 40.0) {
         return 0.07F + 0.05F * smooth(age, 8.0, 37.0);
      }

      float kick = 0.09F * (float)Math.exp(-(age - 40.0) / 5.0);
      return (age < 90.0 ? 0.1F : (age < 120.0 ? 0.07F : 0.05F)) + kick * (1.0F - smooth(age, 148.0, 160.0)) - 0.05F * smooth(age, 148.0, 160.0);
   }

   public static void recordFov(double fov) {
      if (Double.isFinite(fov) && fov > 1.0 && fov < 179.0) {
         renderedFov = fov;
      }
   }

   static Vec3 project(Vec3 target, Vec3 camera, float yaw, float pitch, double fov, int width, int height) {
      if (width > 0 && height > 0 && Double.isFinite(fov) && !(fov <= 1.0) && !(fov >= 179.0)) {
         Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
         Vec3 right = Vec3.directionFromRotation(0.0F, yaw).cross(new Vec3(0.0, 1.0, 0.0));
         Vec3 up = right.cross(forward);
         Vec3 relative = target.subtract(camera);
         double depth = relative.dot(forward);
         if (depth <= 0.01) {
            return null;
         }

         double scale = height / (2.0 * Math.tan(Math.toRadians(fov) * 0.5));
         double x = width * 0.5 + relative.dot(right) * scale / depth;
         double y = height * 0.5 - relative.dot(up) * scale / depth;
         return Double.isFinite(x) && Double.isFinite(y) && !(x < 7.0) && !(x > width - 7) && !(y < 7.0) && !(y > height - 7) ? new Vec3(x, y, depth) : null;
      } else {
         return null;
      }
   }

   private static Vec3 aimTarget(Minecraft client, float partial) {
      Vec3 eye = client.player.getEyePosition(partial);
      Vec3 end = eye.add(client.player.getViewVector(partial).scale(160.0));
      BlockHitResult block = client.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, client.player));
      Vec3 target = block.getLocation();
      double nearest = eye.distanceToSqr(target);
      AABB bounds = new AABB(eye, target).inflate(1.0);

      for (Entity entity : client.level.getEntities(client.player, bounds, entityx -> entityx.isPickable() && !entityx.isSpectator())) {
         AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
         if (!box.contains(eye)) {
            Optional<Vec3> hit = box.clip(eye, target);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
               target = hit.get();
               nearest = eye.distanceToSqr(target);
            }
         }
      }

      return target;
   }

   private static void reticle(GuiGraphics graphics, Minecraft client, float partial) {
      Camera camera = client.gameRenderer.getMainCamera();
      Vec3 point = project(aimTarget(client, partial), camera.getPosition(), camera.getYRot(), camera.getXRot(), renderedFov, graphics.guiWidth(), graphics.guiHeight());
      if (point != null) {
         int x = (int)Math.round(point.x);
         int y = (int)Math.round(point.y);
         graphics.fill(x - 7, y - 2, x - 3, y + 3, -1071642112);
         graphics.fill(x + 3, y - 2, x + 7, y + 3, -1071642112);
         graphics.fill(x - 2, y - 7, x + 3, y - 3, -1071642112);
         graphics.fill(x - 2, y + 3, x + 3, y + 7, -1071642112);
         graphics.fill(x - 6, y, x - 3, y + 1, -26062);
         graphics.fill(x + 3, y, x + 6, y + 1, -26062);
         graphics.fill(x, y - 6, x + 1, y - 3, -26062);
         graphics.fill(x, y + 3, x + 1, y + 6, -26062);
         graphics.fill(x - 1, y - 1, x + 2, y + 2, -3368);
      }
   }

   public static float overlayFlash(int phase, double age) {
      return phase == 1 ? smooth(age, 3.0, 3.6) * (1.0F - smooth(age, 3.6, 7.0)) * 0.32F : (phase == 2 ? flash(age) * 0.92F : 0.0F);
   }

   public static float flash(double impactAge) {
      return smooth(impactAge, 40.4, 40.8) * (1.0F - smooth(impactAge, 41.5, 46.0));
   }

   private static void tint(GuiGraphics graphics, int width, int height, int rgb, float top, float bottom, float reach) {
      int bands = 48;
      int end = Math.round(height * reach);

      for (int i = 0; i < bands; i++) {
         int a = Math.round(255.0F * Mth.clamp(Mth.lerp(i / (bands - 1.0F), top, bottom), 0.0F, 1.0F));
         if (a > 0) {
            graphics.fill(0, end * i / bands, width, end * (i + 1) / bands, a << 24 | rgb);
         }
      }
   }

   private static void grade(GuiGraphics graphics, Minecraft client, FugaRenderer.Frame frame, double age, float partial) {
      int width = graphics.guiWidth();
      int height = graphics.guiHeight();
      float charge = frame == null
         ? 0.0F
         : (
            frame.phase() == 0
               ? smooth(frame.age(), 48.0, 110.0) * (0.55F + 0.45F * smooth(frame.age(), 96.0, 190.0)) * (1.0F - darkness(frame.age()))
               : (frame.phase() == 1 ? 1.0F - smooth(age, 10.0, 40.0) : 0.0F)
         );
      float scorch = client.player == null ? 0.0F : FugaRenderer.aftermath(client.player.getEyePosition(partial));
      if (charge > 0.0F) {
         tint(graphics, width, height, 5903364, 0.5F * charge, 0.0F, 0.75F);
         graphics.fill(0, 0, width, height, Math.round(50.0F * charge) << 24 | 16734736);
      }

      if (scorch > 0.0F) {
         tint(graphics, width, height, 1312774, 0.4F * scorch, 0.0F, 0.5F);
         tint(graphics, width, height, 12601872, 0.35F * scorch, 0.0F, 1.0F);
         graphics.fill(0, 0, width, height, Math.round(34.0F * scorch) << 24 | 16738844);
      }
   }

   public static void render(GuiGraphics graphics, float partial) {
      FugaRenderer.Frame frame = FugaRenderer.localFrame(partial);
      Minecraft client = Minecraft.getInstance();
      if (client.screen == null && client.isWindowActive()) {
         boolean here = present(client, frame);
         grade(graphics, client, here ? frame : null, !here ? 0.0 : (frame.phase() == 2 ? FugaSequence.visualAge(frame.age()) : frame.age()), partial);
         if (here) {
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            double age = frame.phase() == 2 ? FugaSequence.visualAge(frame.age()) : (frame.phase() == 0 ? FugaSequence.chargeAge(frame.age()) : frame.age());
            boolean directing = GodWandConfig.CLIENT.fugaCutscene && !skipped;
            if (frame.phase() == 0 && (age >= 120.0 || !directing) && GodWandConfig.CLIENT.targetMarker) {
               reticle(graphics, client, partial);
            }

            int bars = directing ? Math.round(height * letterbox(frame.phase(), frame.phase() == 0 ? frame.age() : age)) : 0;
            if (bars > 0) {
               graphics.fill(0, 0, width, bars, -16252159);
               graphics.fill(0, height - bars, width, height, -16252159);
            }

            if (directing && frame.phase() == 0) {
               int black = Math.round(255.0F * silence(frame.age()));
               if (black > 0) {
                  graphics.fill(0, 0, width, height, black << 24);
               }
            }

            if (directing && frame.phase() == 0 && frame.age() < 26.0) {
               int alpha = Math.round(255.0F * smooth(frame.age(), 2.0, 8.0) * (1.0F - smooth(frame.age(), 18.0, 26.0)));
               if (alpha > 3) {
                  graphics.pose().pushPose();
                  float scale = Math.min(6.0F, width / 85.0F);
                  graphics.pose().translate(width * 0.5F, height * 0.68F, 0.0F);
                  graphics.pose().scale(scale, scale, 1.0F);
                  graphics.drawString(Minecraft.getInstance().font, "F U G A", -Minecraft.getInstance().font.width("F U G A") / 2, 0, alpha << 24 | 16773340, false);
                  graphics.pose().popPose();
               }
            }

            if (GodWandConfig.CLIENT.impactFrames) {
               int alpha = Math.round(255.0F * overlayFlash(frame.phase(), age));
               if (alpha > 0) {
                  graphics.fill(0, 0, width, height, alpha << 24 | 16774361);
               }
            }
         }
      }
   }
}

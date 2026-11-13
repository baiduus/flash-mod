package com.flash.client.godwand;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public final class AtomicCinematic {
   private static final ResourceLocation TITLE_FONT = new ResourceLocation("flash", "godwand_atomic_title");
   private static boolean skipped;
   private static boolean movementReleased;
   private static boolean movementReleaseSent;
   private static long wideStarted = Long.MIN_VALUE;
   private static float wideAngle;
   private static double wideHeight;
   private static volatile float environmentGain = 1.0F;

   public static boolean movementLocked() {
      Minecraft client = Minecraft.getInstance();
      AtomicRenderer.Frame frame = AtomicRenderer.localFrame(0.0F);
      return frame != null && client.player.isAlive() && !client.player.isSpectator() && !movementReleased && AtomicSequence.movementLocked(frame.age());
   }

   public static boolean active() {
      Minecraft client = Minecraft.getInstance();
      AtomicRenderer.Frame frame = AtomicRenderer.localFrame(0.0F);
      return frame != null
         && GodWandConfig.CLIENT.atomicCutscene
         && client.player.isAlive()
         && !client.player.isSpectator()
         && !skipped
         && client.screen == null
         && client.isWindowActive();
   }

   public static void tick(Minecraft client) {
      AtomicRenderer.Frame nearest = AtomicRenderer.nearestFrame(0.0F);
      environmentGain = 1.0F;
      if (nearest != null && nearest.age() >= 388.0F && nearest.age() < 414.0F) {
         float distance = AtomicRenderer.distanceGain(client.player.position().distanceTo(nearest.center()));
         environmentGain = 1.0F - 0.98F * distance * AtomicSequence.phase(nearest.age(), 392.0, 402.0);
      }

      AtomicRenderer.Frame local = AtomicRenderer.localFrame(0.0F);
      if (local != null) {
         if (movementReleased && !movementReleaseSent) {
            com.flash.network.FlashNetwork.CHANNEL.sendToServer(new com.flash.network.YasWandPacket(5, com.flash.godwand.GodWandSkills.SKIP, 0));
            movementReleaseSent = true;
         }

         if (client.isWindowActive() && client.screen == null) {
            if (InputConstants.isKeyDown(client.getWindow().getWindow(), 294)) {
               skip();
            }
         }
      }
   }

   public static void reset() {
      skipped = false;
      movementReleased = false;
      movementReleaseSent = false;
      wideStarted = Long.MIN_VALUE;
      environmentGain = 1.0F;
   }

   public static void onCastStart() {
      skipped = false;
      movementReleased = false;
      movementReleaseSent = false;
      wideStarted = Long.MIN_VALUE;
   }

   public static void skip() {
      skipped = true;
      AtomicRenderer.Frame frame = AtomicRenderer.localFrame(0.0F);
      if (frame != null && frame.age() >= 414.0F) {
         movementReleased = true;
      }
   }

   public static AtomicCinematic.Pose camera(float delta, Vec3 vanillaPosition, float vanillaYaw, float vanillaPitch) {
      if (!active()) {
         return null;
      }

      Minecraft client = Minecraft.getInstance();
      AtomicRenderer.Frame frame = AtomicRenderer.localFrame(delta);
      AtomicSequence.Shot shot = AtomicSequence.shot(frame.age(), frame.yaw());
      boolean wide = frame.age() >= 466.0F && frame.age() < 614.0F;
      Vec3 target = frame.center().add(shot.focus());
      Vec3 offset = shot.offset();
      float idle = AtomicSequence.holdIdle(frame.waitTicks()) * (1.0F - AtomicSequence.phase(frame.age(), 406.0, 410.0));
      offset = offset.add(idle * 2.0F, idle * 0.5, 0.0);
      double budget = AtomicSequence.cameraBudget(client.options.getEffectiveRenderDistance(), frame.age());
      if (offset.horizontalDistance() > budget) {
         double scale = budget / offset.horizontalDistance();
         offset = new Vec3(offset.x * scale, offset.y, offset.z * scale);
      }

      Vec3 desired = frame.center().add(offset);

      for (int step = 0; step < 12 && !loadedNeighborhood(client, desired); step++) {
         offset = new Vec3(offset.x * 0.8, offset.y, offset.z * 0.8);
         desired = frame.center().add(offset);
      }

      if (wide) {
         double radius = offset.horizontalDistance();
         if (wideStarted != frame.startedAt()) {
            wideStarted = frame.startedAt();
            wideAngle = 0.0F;
            wideHeight = Double.POSITIVE_INFINITY;

            for (int angle : new int[]{0, -30, 30, -60, 60, 180}) {
               double height = wideClearance(client, frame, radius, angle);
               if (height < wideHeight) {
                  wideHeight = height;
                  wideAngle = angle;
               }

               if (height <= Math.max(6.0, (radius - 98.0) * 0.6)) {
                  break;
               }
            }

            wideHeight = Math.min(60.0, wideHeight);
         }

         offset = offset.yRot((float)Math.toRadians(wideAngle));
         desired = frame.center().add(offset.x, wideHeight, offset.z);
         double lens = GodWandConfig.CLIENT.cameraFov ? shot.fov() : client.options.fov().get().intValue();
         target = frame.center().add(AtomicSequence.wideFocus(desired.subtract(frame.center()), lens));
      }

      Vec3 direction = target.subtract(desired);
      float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
      float pitch = (float)(-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
      float blend = AtomicSequence.returnBlend(frame.age());
      return new AtomicCinematic.Pose(desired.lerp(vanillaPosition, blend), Mth.rotLerp(blend, yaw, vanillaYaw), Mth.lerp(blend, pitch, vanillaPitch));
   }

   private static double wideClearance(Minecraft client, AtomicRenderer.Frame frame, double radius, int angle) {
      double height = Math.min(60.0, Math.max(6.0, (radius - 98.0) * 0.6));
      Vec3 arc = AtomicSequence.shot(466.0, frame.yaw()).offset();

      for (int turn = 0; turn >= -6; turn--) {
         Vec3 direction = arc.yRot((float)Math.toRadians(angle + turn)).scale(1.0 / arc.horizontalDistance());
         if (!loadedNeighborhood(client, frame.center().add(direction.scale(radius)))) {
            return Double.POSITIVE_INFINITY;
         }

         for (int step = 1; step <= 8; step++) {
            double t = step / 8.0;
            double r = Math.min(98.0, radius) + Math.max(0.0, radius - 98.0) * t;
            Vec3 sample = frame.center().add(direction.scale(r));
            int x = Mth.floor(sample.x);
            int z = Mth.floor(sample.z);
            if (!client.level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
               return Double.POSITIVE_INFINITY;
            }

            double ground = client.level.getHeight(Types.MOTION_BLOCKING, x, z) + 0.75 - frame.center().y;
            height = Math.max(height, ground / t);
         }
      }

      return height;
   }

   private static boolean loadedNeighborhood(Minecraft client, Vec3 point) {
      for (int x : new int[]{-8, 8}) {
         for (int z : new int[]{-8, 8}) {
            if (!client.level.getChunkSource().hasChunk(Mth.floor(point.x + x) >> 4, Mth.floor(point.z + z) >> 4)) {
               return false;
            }
         }
      }

      return true;
   }

   public static double fov(double vanilla, float delta) {
      if (active() && GodWandConfig.CLIENT.cameraFov) {
         AtomicRenderer.Frame frame = AtomicRenderer.localFrame(delta);
         return Mth.lerp(AtomicSequence.returnBlend(frame.age()), AtomicSequence.shot(frame.age(), frame.yaw()).fov(), vanilla);
      } else {
         return vanilla;
      }
   }

   public static float environmentGain() {
      return environmentGain;
   }

   /** I am Atomic's camera shake now (pitch, roll in degrees; God Wand's, without the small sideways shift). */
   public static float[] shake(float delta) {
      AtomicRenderer.Frame frame = AtomicRenderer.nearestFrame(delta);
      Minecraft client = Minecraft.getInstance();
      if (frame == null || client.player == null) {
         return null;
      }
      float gain = AtomicRenderer.distanceGain(client.player.position().distanceTo(frame.center()));
      float strength = AtomicSequence.shake(frame.age());
      if (active() && frame.age() >= 466.0F && frame.age() < 614.0F) {
         strength += AtomicSequence.pressureShake(frame.age(), client.gameRenderer.getMainCamera().getPosition().subtract(frame.center()).horizontalDistance());
      }
      strength *= gain * GodWandConfig.CLIENT.cameraShake;
      double t = frame.age();
      float roll = (float)(Math.sin(t * 2.23) * 1.1 * strength);
      if (active() && t >= 466.0 && t < 614.0) {
         roll += (float)(5.0 * Math.sin((t - 466.0) / 148.0 * Math.PI));
      }
      return new float[] {(float)(Math.sin(t * 1.67) * 1.7 * strength), roll};
   }

   public static void render(GuiGraphics graphics, float partial) {
      AtomicRenderer.Frame frame = AtomicRenderer.localFrame(partial);
      if (frame != null) {
         if (!active()) {
            releaseHint(graphics, frame);
         } else {
            float age = frame.age();
            float envelope = AtomicSequence.letterbox(age);
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            int transition = Math.round(255.0F * AtomicSequence.transitionOpacity(age));
            if (transition > 0) {
               graphics.fill(0, 0, width, height, transition << 24 | 1313059);
            }

            int bars = Math.round(height * 0.105F * envelope);
            graphics.fill(0, 0, width, bars, -16580343);
            graphics.fill(0, height - bars, width, height, -16580343);
            if (age < 100.0F) {
               graphics.drawString(
                  Minecraft.getInstance().font,
                  Component.translatable("gui.godwand.atomic_skip"),
                  width - Minecraft.getInstance().font.width(Component.translatable("gui.godwand.atomic_skip")) - 10,
                  height - 16,
                  -4806711,
                  false
               );
            }

            int darkness = Math.round(255.0F * AtomicSequence.blackout(age) * (AtomicPostFx.available() ? 0.15F : 0.95F));
            if (darkness > 0) {
               graphics.fill(0, bars, width, height - bars, darkness << 24);
            }

            if (frame.waiting() && GodWandConfig.CLIENT.impactFrames) {
               int pulse = Math.round(18.0F * AtomicSequence.heartbeat(frame.waitTicks()));
               if (pulse > 0) {
                  graphics.fill(0, bars, width, height - bars, pulse << 24 | 11770856);
               }
            }

            if (!AtomicPostFx.available() && GodWandConfig.CLIENT.impactFrames) {
               int flash = Math.round(255.0F * AtomicSequence.releaseFlash(age));
               if (flash > 0) {
                  graphics.fill(0, bars, width, height - bars, flash << 24 | 15982335);
               }

               if (age >= 410.0F && age < 412.0F) {
                  graphics.fill(0, bars, width, height - bars, (int)age % 2 == 0 ? -926209 : -16055532);
               }
            }

            releaseHint(graphics, frame);
            if (age >= 298.0F && age < 384.0F) {
               float alpha = AtomicSequence.phase(age, 298.0, 312.0) * (1.0F - AtomicSequence.phase(age, 372.0, 384.0));
               title(graphics, age < 320.0F ? "I" : "I AM", width * 0.5F, height * 0.82F, Math.min(1.5F, width / 220.0F), alpha, false);
            }

            if (age >= 396.0F && age < 406.0F) {
               float alpha = AtomicSequence.phase(age, 396.0, 398.0) * (1.0F - AtomicSequence.phase(age, 402.0, 406.0));
               float scale = Math.min(width * 0.36F / Minecraft.getInstance().font.width(lettering("ATOMIC")), 6.0F);
               scale *= 1.0F + 0.1F * (1.0F - AtomicSequence.phase(age, 396.0, 404.0));
               title(graphics, "ATOMIC", width * 0.5F, height * 0.3F, scale, alpha, true);
            }
         }
      }
   }

   private static void releaseHint(GuiGraphics graphics, AtomicRenderer.Frame frame) {
      if (frame.waiting() && !(frame.waitTicks() < 60.0F)) {
         Minecraft client = Minecraft.getInstance();
         MutableComponent text = Component.translatable("gui.godwand.atomic_release");
         int alpha = Math.round(190.0F * AtomicSequence.phase(frame.waitTicks(), 60.0, 70.0));
         if (alpha > 3) {
            graphics.drawString(
               client.font, text, (graphics.guiWidth() - client.font.width(text)) / 2, Math.round(graphics.guiHeight() * 0.84F), alpha << 24 | 12957912, false
            );
         }
      }
   }

   private static void title(GuiGraphics graphics, String text, float x, float y, float scale, float alpha, boolean impact) {
      if (!(alpha < 0.01)) {
         Font font = Minecraft.getInstance().font;
         Component letters = lettering(text);
         int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
         graphics.pose().pushPose();
         graphics.pose().translate(x, y, 0.0F);
         graphics.pose().scale(scale, scale, 1.0F);
         float left = -font.width(letters) / 2.0F;
         graphics.pose().translate(left, -4.0F, 0.0F);
         if (impact) {
            graphics.drawString(font, letters, -1, 0, Math.max(4, a / 3) << 24 | 10302207, false);
            graphics.drawString(font, letters, 1, 0, Math.max(4, a / 3) << 24 | 14311935, false);
         }

         graphics.drawString(font, letters, 0, 0, a << 24 | 16773375, false);
         graphics.pose().popPose();
      }
   }

   private static Component lettering(String text) {
      return Component.literal(text).withStyle(style -> style.withFont(TITLE_FONT));
   }

   public record Pose(Vec3 position, float yaw, float pitch) {
   }
}

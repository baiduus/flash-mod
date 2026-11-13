package com.flash.client.godwand;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.flash.godwand.ImpactFxPayload;
import com.flash.godwand.SupernovaManager;
import com.flash.godwand.SupernovaSequence;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SupernovaAccretionRenderer {
   private static ClientLevel world;
   private static Vec3 center = Vec3.ZERO;
   private static long start;
   private static boolean active;
   private static ClientLevel chargeWorld;
   private static Vec3 chargeCenter = Vec3.ZERO;
   private static float chargeTicks;
   private static float previousChargeTicks;
   private static boolean charging;
   private static final float[] EDGES = new float[]{-1.0F, -0.22F, 0.22F, 1.0F};
   private static Vec3 currentCameraRel = Vec3.ZERO;

   public static void reset() {
      active = false;
      world = null;
      chargeWorld = null;
      previousChargeTicks = 0.0F;
      chargeTicks = 0.0F;
      charging = false;
      currentCameraRel = Vec3.ZERO;
   }

   public static void receive(ImpactFxPayload payload) {
      if (payload.fxType() != 8 && payload.fxType() != 3) {
         if (payload.fxType() == 4) {
            reset();
            world = Minecraft.getInstance().level;
            if (world != null) {
               center = new Vec3(payload.x(), payload.y(), payload.z());
               start = world.getGameTime();
               active = true;
               LocalPlayer player = Minecraft.getInstance().player;
               if (player != null) {
                  GodWandCinematicEngine.addTrauma(0.22F * GodWandCinematicEngine.distanceFactor(5, player.position().distanceTo(center)), 6);
               }
            }
         }
      }
   }

   public static void tickCharge(Minecraft client) {
      if (client.level != chargeWorld) {
         previousChargeTicks = 0.0F;
         chargeTicks = 0.0F;
         chargeWorld = client.level;
         charging = false;
      }

      previousChargeTicks = chargeTicks;
      if (client.player != null && client.level != null && true) {
         if (GodWandClient.charging(client.player, 3)) {
            if (!charging || GodWandClient.chargeTicks(client.player) < chargeTicks) {
               BlockPos target = SupernovaManager.findTarget(client.player);
               if (target == null) {
                  chargeTicks = Math.max(0.0F, chargeTicks - 8.0F);
                  return;
               }

               chargeCenter = Vec3.atCenterOf(target).add(0.0, 20.0, 0.0);
               charging = true;
            }

            chargeTicks = Mth.clamp(GodWandClient.chargeTicks(client.player), 0, 140);
            return;
         }
      }

      charging = false;
      chargeTicks = Math.max(0.0F, chargeTicks - 8.0F);
   }

   public static SupernovaAccretionRenderer.SkyFrame skyFrame(float delta) {
      double age = skyAge(delta);
      if (!(age >= 0.0) || !(SupernovaSequence.skyScale(age) > 0.001) && !(SupernovaSequence.skyPulseStrength(age) > 0.001)) {
         float ticks = previousChargeTicks + (chargeTicks - previousChargeTicks) * delta;
         return ticks > 0.0F ? new SupernovaAccretionRenderer.SkyFrame(chargeCenter, SupernovaSequence.chargeRiftScale(ticks), 0.0F, 0.0F, 0.0F, ticks) : null;
      } else {
         return new SupernovaAccretionRenderer.SkyFrame(
            center,
            SupernovaSequence.skyScale(age),
            SupernovaSequence.skyCollapse(age),
            SupernovaSequence.skyPulseRadius(age),
            SupernovaSequence.skyPulseStrength(age),
            (float)age + 140.0F
         );
      }
   }

   public static double age(float delta) {
      double age = skyAge(delta);
      return age < 348.0 ? age : -1.0;
   }

   public static double skyAge(float delta) {
      Minecraft current = Minecraft.getInstance();
      return active && current != null && world == current.level && world.getGameTime() - start < 628L ? (float)(world.getGameTime() - start) + delta : -1.0;
   }

   public static Vec3 center() {
      return center;
   }

   public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         float delta = tickDelta;
         double age = age(delta);
         boolean marker = age < 0.0 && charging && chargeTicks > 0.0F && GodWandConfig.CLIENT.targetMarker;
         boolean preImpactWave = age >= 330.0 && age <= 342.0;
         if (marker || preImpactWave || !(age < 0.0) && !(SupernovaSequence.ribbonVisibility(age) <= 0.0F)) {
            Vec3 origin = marker ? chargeCenter : center;
            Vec3 camera = cameraObj.getPosition();
            currentCameraRel = camera.subtract(origin);
            matrices.pushPose();
            matrices.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
            GodWandGeometry.enableBlend();
            GodWandGeometry.defaultBlendFunc();
            GodWandGeometry.enableDepthTest();
            GodWandGeometry.depthMask(false);
            GodWandGeometry.disableCull();
            GodWandGeometry.colored();

            try {
               BufferBuilder vertices = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
               if (marker) {
                  emitChargeMarker(vertices, matrices.last().pose(), chargeTicks / 140.0F);
               } else {
                  emit(vertices, matrices.last().pose(), age);
               }

               BufferBuilder.RenderedBuffer built = vertices.end();
               if (built != null) {
                  GodWandGeometry.draw(built);
               }
               if (!marker && age >= 0.0) {
                  drawCore(matrices.last().pose(), age);
               }
            } finally {
               matrices.popPose();
               GodWandGeometry.depthMask(true);
               GodWandGeometry.enableCull();
               GodWandGeometry.defaultBlendFunc();
               GodWandGeometry.disableBlend();
            }
         }
      }
   }

   /**
    * The black hole itself (God Wand draws it with its post shaders, not part of this port): an opaque black
    * sphere of the core's radius and a glowing photon ring around it, facing the camera.
    */
   private static void drawCore(Matrix4f m, double age) {
      float r = SupernovaSequence.coreRadius(age);
      if (r <= 0.05F) {
         return;
      }
      GodWandGeometry.defaultBlendFunc();
      GodWandGeometry.depthMask(true);
      int rings = 16;
      int segments = 32;
      for (int i = 0; i < rings; i++) {
         double t1 = -Math.PI / 2 + Math.PI * i / rings;
         double t2 = -Math.PI / 2 + Math.PI * (i + 1) / rings;
         BufferBuilder strip = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
         for (int j = 0; j <= segments; j++) {
            double phi = Math.PI * 2 * j / segments;
            strip.vertex(m, (float)(r * Math.cos(t1) * Math.cos(phi)), (float)(r * Math.sin(t1)), (float)(r * Math.cos(t1) * Math.sin(phi))).color(0, 0, 0, 255).endVertex();
            strip.vertex(m, (float)(r * Math.cos(t2) * Math.cos(phi)), (float)(r * Math.sin(t2)), (float)(r * Math.cos(t2) * Math.sin(phi))).color(0, 0, 0, 255).endVertex();
         }
         GodWandGeometry.draw(strip.end());
      }
      Vec3 dir = currentCameraRel.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : currentCameraRel.normalize();
      Vec3 u = dir.cross(Math.abs(dir.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
      Vec3 w = dir.cross(u).normalize();
      GodWandGeometry.additiveBlend();
      GodWandGeometry.depthMask(false);
      float pulse = 0.85F + 0.15F * (float)Math.sin(age * 0.35);
      for (int layer = 0; layer < 2; layer++) {
         float inner = r * (layer == 0 ? 1.0F : 1.02F);
         float outer = r * (layer == 0 ? 1.75F : 1.18F);
         int alpha = (int)((layer == 0 ? 150 : 235) * pulse * SupernovaSequence.ribbonVisibility(age));
         BufferBuilder ring = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
         for (int j = 0; j <= 48; j++) {
            double a = Math.PI * 2 * j / 48.0;
            Vec3 o = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
            Vec3 in = o.scale(inner).add(dir.scale(r * 0.05));
            Vec3 out = o.scale(outer).add(dir.scale(r * 0.05));
            ring.vertex(m, (float)in.x, (float)in.y, (float)in.z).color(255, layer == 0 ? 120 : 220, 235, alpha).endVertex();
            ring.vertex(m, (float)out.x, (float)out.y, (float)out.z).color(180, 40, 200, 0).endVertex();
         }
         GodWandGeometry.draw(ring.end());
      }
      GodWandGeometry.defaultBlendFunc();
   }

   static void emitChargeMarker(VertexConsumer v, Matrix4f m, float charge) {
      float pulse = 0.82F + 0.18F * (float)Math.sin(System.currentTimeMillis() % 10000L * 0.014);
      float bottomY = -20.0F;
      float topY = 120.0F;

      for (int i = 0; i < 4; i++) {
         double angle = i * (Math.PI / 4);
         Vec3 side = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
         double coreW = 0.12 + charge * 0.08;
         Vec3 coreSide = side.scale(coreW);
         int coreAlpha = (int)(220.0F * pulse * (0.6F + charge * 0.4F));
         vertex(v, m, coreSide.reverse().add(0.0, bottomY, 0.0), 255, 235, 250, coreAlpha);
         vertex(v, m, coreSide.add(0.0, bottomY, 0.0), 255, 235, 250, coreAlpha);
         vertex(v, m, coreSide.add(0.0, topY, 0.0), 255, 180, 240, (int)(coreAlpha * 0.4F));
         vertex(v, m, coreSide.reverse().add(0.0, topY, 0.0), 255, 180, 240, (int)(coreAlpha * 0.4F));
         double haloW = 0.38 + charge * 0.22;
         Vec3 haloSide = side.scale(haloW);
         int haloAlpha = (int)(80.0F * pulse * (0.5F + charge * 0.5F));
         vertex(v, m, haloSide.reverse().add(0.0, bottomY, 0.0), 210, 50, 200, haloAlpha);
         vertex(v, m, haloSide.add(0.0, bottomY, 0.0), 210, 50, 200, haloAlpha);
         vertex(v, m, haloSide.add(0.0, topY, 0.0), 160, 20, 180, 0);
         vertex(v, m, haloSide.reverse().add(0.0, topY, 0.0), 160, 20, 180, 0);
      }
   }

   private static double streamPhase(double age, int strand) {
      double travel = age * (0.006 + SupernovaSequence.pressure(age) * 0.009) + strand * 0.618034;
      return travel - Math.floor(travel);
   }

   public static Vec3 streamPoint(double age, int strand, double tail) {
      double pressure = SupernovaSequence.pressure(age);
      double collapse = SupernovaSequence.swallow(age, 0.5);
      double travel = streamPhase(age, strand) - tail * 0.3;
      travel = Math.max(0.0, travel);
      double core = SupernovaSequence.coreRadius(age);
      double radius = core * 0.65 + Math.pow(1.0 - travel, 1.8) * (15.0 + 30.0 * pressure) * (1.0 - collapse * 0.8);
      double angle = strand * 2.399963 + travel * (3.2 + pressure * 4.5) + age * 0.012;
      double elevation = Math.sin(strand * 1.7 + travel * 2.5) * 0.42;
      return new Vec3(Math.cos(angle) * radius, Math.sin(elevation) * radius, Math.sin(angle) * radius);
   }

   static void emit(VertexConsumer v, Matrix4f m, double age) {
      if (age >= 330.0 && age <= 342.0) {
         emitPreImpactShockwave(v, m, age);
      }

      double visible = SupernovaSequence.ribbonVisibility(age);
      if (!(visible <= 0.0)) {
         double pressure = SupernovaSequence.pressure(age);

         for (int strand = 0; strand < 12; strand++) {
            for (int segment = 0; segment < 48; segment++) {
               Vec3 segCenter = streamPoint(age, strand, (segment + 0.5) / 48.0);
               if (!(segCenter.distanceTo(currentCameraRel) < 1.4)) {
                  for (int lane = 0; lane < 3; lane++) {
                     for (int corner = 0; corner < 4; corner++) {
                        double t = (segment + (corner != 1 && corner != 2 ? 0 : 1)) / 48.0;
                        double edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
                        Vec3 point = streamPoint(age, strand, t);
                        Vec3 width = new Vec3(Math.sin(strand * 2.4), 0.8, Math.cos(strand * 2.4)).normalize();
                        double feather = Math.abs(edge) > 0.9 ? 0.0 : 1.0;
                        double taper = Math.pow(Math.sin(t * Math.PI), 0.8);
                        double cycle = streamPhase(age, strand);
                        double lifetime = SupernovaSequence.phase(cycle, 0.0, 0.12) * (1.0F - SupernovaSequence.phase(cycle, 0.86, 1.0));
                        point = point.add(width.scale(edge * (0.4 + pressure * 0.8) * taper));
                        vertex(
                           v,
                           m,
                           point,
                           lane == 1 ? 255 : 220,
                           lane == 1 ? 170 : 55,
                           lane == 1 ? 225 : 200,
                           (int)(visible * feather * taper * lifetime * (125.0 + pressure * 85.0))
                        );
                     }
                  }
               }
            }
         }

         double collapse = SupernovaSequence.swallow(age, 0.0);

         for (int orbit = 0; orbit < 3; orbit++) {
            double radius = SupernovaSequence.coreRadius(age) * (1.45 + (0.65 + orbit * 0.48) * (1.0 - collapse));
            arc(v, m, radius, age * (0.015 + pressure * 0.045) + orbit * 2.4, 0.25 + orbit * 0.42, 0.0, visible * 0.85, 0.28 + pressure * 0.25, 235, 65, 205);
         }

         emitInfallSparks(v, m, age, visible, pressure);
      }
   }

   private static void emitInfallSparks(VertexConsumer v, Matrix4f m, double age, double visible, double pressure) {
      for (int spark = 0; spark < 24; spark++) {
         double cycle = streamPhase(age, spark + 20);
         double life = SupernovaSequence.phase(cycle, 0.0, 0.12) * (1.0F - SupernovaSequence.phase(cycle, 0.86, 1.0));

         for (int segment = 0; segment < 5; segment++) {
            double t = segment / 45.0;
            Vec3 a = streamPoint(age, spark + 20, t);
            Vec3 b = streamPoint(age, spark + 20, t + 0.022222222222222223);
            if (!(a.distanceTo(currentCameraRel) < 1.4)) {
               Vec3 width = b.subtract(a).cross(new Vec3(0.3, 1.0, 0.2)).normalize().scale((0.05 + pressure * 0.08) * (1.0 - segment / 5.0));
               int alpha = (int)(230.0 * visible * life * (1.0 - segment / 5.0));
               vertex(v, m, a.subtract(width), 255, 190, 230, alpha);
               vertex(v, m, b.subtract(width), 235, 65, 205, alpha);
               vertex(v, m, b.add(width), 235, 65, 205, alpha);
               vertex(v, m, a.add(width), 255, 190, 230, alpha);
            }
         }
      }

      double core = SupernovaSequence.coreRadius(age);

      for (int strand = 0; strand < 4; strand++) {
         for (int segment = 0; segment < 6; segment++) {
            double wave = 0.5 + 0.5 * Math.sin(age * 0.07 + strand * 1.8);
            double a = strand * 1.6 + age * 0.012 + segment * 0.12;
            double b = a + 0.12;
            double radius = core * (1.7 + strand * 0.15);
            double bend = Math.sin(segment * 2.3 + strand) * core * 0.035;
            Vec3 p = new Vec3(Math.cos(a) * radius, Math.sin(a) * radius * 0.8 + bend, Math.sin(a) * radius * 0.65);
            Vec3 q = new Vec3(Math.cos(b) * radius, Math.sin(b) * radius * 0.8 - bend, Math.sin(b) * radius * 0.65);
            Vec3 width = new Vec3(0.025 + pressure * 0.025, 0.025, 0.0);
            int alpha = (int)(180.0 * visible * wave * Math.sin((segment + 0.5) / 6.0 * Math.PI));
            vertex(v, m, p.subtract(width), 255, 145, 230, alpha);
            vertex(v, m, q.subtract(width), 255, 145, 230, alpha);
            vertex(v, m, q.add(width), 255, 145, 230, alpha);
            vertex(v, m, p.add(width), 255, 145, 230, alpha);
         }
      }
   }

   private static void arc(
      VertexConsumer v, Matrix4f m, double radius, double rotation, double tilt, double y, double strength, double width, int red, int green, int blue
   ) {
      for (int segment = 0; segment < 64; segment++) {
         double angleMid = rotation + (segment + 0.5) / 64.0 * 2.4;
         Vec3 arcMid = new Vec3(Math.cos(angleMid) * radius, Math.sin(angleMid) * radius * Math.sin(tilt) + y, Math.sin(angleMid) * radius * Math.cos(tilt));
         if (!(arcMid.distanceTo(currentCameraRel) < 1.4)) {
            for (int lane = 0; lane < 3; lane++) {
               for (int corner = 0; corner < 4; corner++) {
                  double t = (segment + (corner != 1 && corner != 2 ? 0 : 1)) / 64.0;
                  double edge = EDGES[lane + (corner >= 2 ? 1 : 0)];
                  double angle = rotation + t * 2.4;
                  double r = radius + edge * width;
                  Vec3 point = new Vec3(Math.cos(angle) * r, Math.sin(angle) * r * Math.sin(tilt) + y, Math.sin(angle) * r * Math.cos(tilt));
                  vertex(v, m, point, red, green, blue, (int)(220.0 * strength * Math.sin(t * Math.PI) * (Math.abs(edge) > 0.9 ? 0 : 1)));
               }
            }
         }
      }
   }

   private static void vertex(VertexConsumer v, Matrix4f m, Vec3 point, int r, int g, int b, int alpha) {
      double dist = point.distanceTo(currentCameraRel);
      if (dist < 1.4) {
         alpha = 0;
      } else if (dist < 2.8) {
         alpha = (int)(alpha * Mth.clamp((dist - 1.4) / 1.4, 0.0, 1.0));
      }

      v.vertex(m, (float)point.x, (float)point.y, (float)point.z).color(r, g, b, Mth.clamp(alpha, 0, 255)).endVertex();
   }

   private static void emitPreImpactShockwave(VertexConsumer v, Matrix4f m, double age) {
      double waveT = Mth.clamp((age - 330.0) / 10.0, 0.0, 1.0);
      float alpha = (float)Math.sin(waveT * Math.PI);
      if (!(alpha <= 0.001F)) {
         double radius = 1.5 + Math.pow(waveT, 0.55) * 32.0;
         double width = 0.8 + waveT * 2.5;
         emitShockwaveRing(v, m, radius, width, 0.0, 0.0, alpha);
         emitShockwaveRing(v, m, radius * 0.92, width * 0.85, 0.28, waveT * 0.5, alpha * 0.75F);
      }
   }

   private static void emitShockwaveRing(VertexConsumer v, Matrix4f m, double radius, double width, double tilt, double rotation, float alpha) {
      int segments = 64;

      for (int seg = 0; seg < segments; seg++) {
         double angleMid = rotation + (seg + 0.5) * 2.0 * Math.PI / segments;
         Vec3 ringMid = new Vec3(Math.cos(angleMid) * radius, Math.sin(angleMid) * radius * Math.sin(tilt), Math.sin(angleMid) * radius * Math.cos(tilt));
         if (!(ringMid.distanceTo(currentCameraRel) < 1.4)) {
            double a1 = rotation + seg * 2.0 * Math.PI / segments;
            double a2 = rotation + (seg + 1) * 2.0 * Math.PI / segments;
            double rIn = Math.max(0.1, radius - width);
            double rMid = radius;
            double rOut = radius + width;
            Vec3 p1In = new Vec3(Math.cos(a1) * rIn, Math.sin(a1) * rIn * Math.sin(tilt), Math.sin(a1) * rIn * Math.cos(tilt));
            Vec3 p2In = new Vec3(Math.cos(a2) * rIn, Math.sin(a2) * rIn * Math.sin(tilt), Math.sin(a2) * rIn * Math.cos(tilt));
            Vec3 p1Mid = new Vec3(Math.cos(a1) * rMid, Math.sin(a1) * rMid * Math.sin(tilt), Math.sin(a1) * rMid * Math.cos(tilt));
            Vec3 p2Mid = new Vec3(Math.cos(a2) * rMid, Math.sin(a2) * rMid * Math.sin(tilt), Math.sin(a2) * rMid * Math.cos(tilt));
            Vec3 p1Out = new Vec3(Math.cos(a1) * rOut, Math.sin(a1) * rOut * Math.sin(tilt), Math.sin(a1) * rOut * Math.cos(tilt));
            Vec3 p2Out = new Vec3(Math.cos(a2) * rOut, Math.sin(a2) * rOut * Math.sin(tilt), Math.sin(a2) * rOut * Math.cos(tilt));
            int aIn = (int)(60.0F * alpha);
            int aMid = (int)(235.0F * alpha);
            int aOut = 0;
            vertex(v, m, p1In, 210, 45, 230, aIn);
            vertex(v, m, p2In, 210, 45, 230, aIn);
            vertex(v, m, p2Mid, 255, 140, 250, aMid);
            vertex(v, m, p1Mid, 255, 140, 250, aMid);
            vertex(v, m, p1Mid, 255, 140, 250, aMid);
            vertex(v, m, p2Mid, 255, 140, 250, aMid);
            vertex(v, m, p2Out, 220, 30, 210, aOut);
            vertex(v, m, p1Out, 220, 30, 210, aOut);
         }
      }
   }

   public record SkyFrame(Vec3 center, float scale, float collapse, float pulseRadius, float pulseStrength, float time) {
   }
}

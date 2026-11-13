package com.flash.client.godwand;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import com.flash.godwand.CannonPressurePayload;
import com.flash.godwand.ImpactFxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix4f;

public final class ImpactDebrisRenderer {
   private static final int SEGMENTS = 64;
   private static final int RINGS = 12;
   private static final List<ImpactDebrisRenderer.Burst> BURSTS = new ArrayList<>();
   private static ClientLevel world;

   public static void reset() {
      BURSTS.clear();
      world = null;
   }

   private static void add(Vec3 center, Vec3 normal, double radius, int kind) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (world != null && !(normal.lengthSqr() < 0.01)) {
         if (BURSTS.size() >= 8) {
            BURSTS.remove(0);
         }

         ImpactDebrisRenderer.Burst burst = new ImpactDebrisRenderer.Burst(center, normal, radius, kind, world.getGameTime());
         burst.sampleSurface();
         BURSTS.add(burst);
      }
   }

   public static void receive(CannonPressurePayload payload) {
      add(payload.center(), payload.surfaceNormal(), 3 + payload.level(), 0);
   }

   public static void receive(ImpactFxPayload payload) {
      Vec3 center = new Vec3(payload.x(), payload.y(), payload.z());
      if (payload.fxType() == 3) {
         add(center, new Vec3(0.0, 1.0, 0.0), 122.0, 2);
      }

      if (payload.fxType() == 5 && payload.durationTicks() >= 22) {
         add(center, new Vec3(0.0, 1.0, 0.0), Mth.clamp(10.0F + payload.intensity() * 3.0F, 12.0F, 26.0F), 1);
      }
   }

   public static double waveRadius(double radius, double age, boolean nova) {
      double progress = Mth.clamp((age - (nova ? 6 : 0)) / (nova ? 42 : 18), 0.0, 1.0);
      return radius * Math.sqrt(progress);
   }

   public static Vec3 fragmentOffset(Vec3 normal, Vec3 outward, double age, double strength) {
      return outward.scale(age * strength * 0.18).add(normal.scale(age * strength * 0.24)).add(0.0, -0.025 * age * age, 0.0);
   }

   public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (world != null) {
         double now = (float)world.getGameTime() + tickDelta;
         BURSTS.removeIf(bx -> now - bx.tick >= (bx.kind == 2 ? 140 : 44));
         if (!BURSTS.isEmpty()) {
            GodWandGeometry.enableBlend();
            GodWandGeometry.defaultBlendFunc();
            GodWandGeometry.enableDepthTest();
            GodWandGeometry.depthMask(false);
            GodWandGeometry.disableCull();
            GodWandGeometry.colored();
            matrices.pushPose();
            Vec3 camera = cameraObj.getPosition();
            matrices.translate(-camera.x, -camera.y, -camera.z);

            try {
               BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
               Matrix4f matrix = matrices.last().pose();

               for (ImpactDebrisRenderer.Burst b : BURSTS) {
                  double age = now - b.tick;
                  emit(buffer, matrix, b, age);
                  if (b.kind == 2 && age >= 6.0 && !b.sounded && waveRadius(b.radius, age, true) >= Math.hypot(camera.x - b.center.x, camera.z - b.center.z)) {
                     b.sounded = true;
                     if (Math.abs(
                           camera.y
                              - (
                                 b.center.y
                                    + b.height(
                                       Math.hypot(camera.x - b.center.x, camera.z - b.center.z),
                                       Math.atan2(camera.subtract(b.center).dot(b.up), camera.subtract(b.center).dot(b.side))
                                    )
                              )
                        )
                        < 24.0) {
                        GodWandCinematicEngine.addTrauma(0.22F, 8);
                     }
                  }
               }

               BufferBuilder.RenderedBuffer built = buffer.end();
               if (built != null) {
                  GodWandGeometry.draw(built);
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

   static void emit(VertexConsumer v, Matrix4f m, ImpactDebrisRenderer.Burst b, double age) {
      boolean nova = b.kind == 2;
      double front = waveRadius(b.radius, age, nova);
      double fade = Mth.clamp(1.0 - (age - (nova ? 32 : 12)) / (nova ? 30 : 20), 0.0, 1.0);
      if (front > 0.1 && fade > 0.0) {
         for (int layer = 0; layer < 2; layer++) {
            for (int a = 0; a < 128; a++) {
               for (int row = 0; row < 3; row++) {
                  for (int corner = 0; corner < 4; corner++) {
                     double angle = (a + (corner != 1 && corner != 2 ? 0 : 1)) * Math.PI / 64.0;
                     double t = (row + (corner >= 2 ? 1 : 0)) / 3.0;
                     double lobe = 0.7 + 0.2 * Math.sin(angle * 11.0 + age * 0.1) + 0.1 * Math.cos(angle * 19.0 - age * 0.13);
                     double r = Math.max(0.1, front - layer * (nova ? 4.8 : 0.55) - Math.sin(t * Math.PI) * (nova ? 4.0 : 0.8));
                     double ground = b.height(r, angle);
                     if (!Double.isFinite(ground)) {
                        ground = 0.0;
                     }

                     double height = ground + 0.1 + t * lobe * (nova ? 11.0 : 3.2) * fade;
                     double alpha = Math.sin(t * Math.PI) * fade * (layer == 0 ? 0.48 : 0.22);
                     if (!Double.isFinite(b.height(r, angle))) {
                        alpha = 0.0;
                     }

                     int shade = (int)(18.0 * Math.cos(angle - 0.8) * Math.sin(t * Math.PI));
                     vertex(v, m, b.point(r, angle, height), 160 + row * 20 + shade, 133 + row * 18 + shade, 113 + row * 17 + shade, (int)(alpha * 255.0));
                  }
               }
            }
         }
      }

      if (nova) {
         emitNovaPressure(v, m, b, age);
      }

      if (!nova && age < 18.0) {
         for (int branch = 0; branch < 10; branch++) {
            for (int step = 0; step < 5; step++) {
               double angle = branch * Math.PI * 0.2 + Math.sin(step * 2.7 + branch) * 0.075;
               double r = b.radius * (0.35 + step * 0.12);
               double next = r + b.radius * 0.13;
               double h = b.height(r, angle);
               double h2 = b.height(next, angle + 0.025);
               if (Double.isFinite(h) && Double.isFinite(h2)) {
                  Vec3 a = b.point(r, angle, h + 0.055);
                  Vec3 end = b.point(next, angle + 0.025, h2 + 0.055);
                  Vec3 width = b.side.scale(-Math.sin(angle) * 0.04).add(b.up.scale(Math.cos(angle) * 0.04));
                  quad(v, m, a.subtract(width), end.subtract(width), end.add(width), a.add(width), 255, 160, 75, (int)(200.0 * (1.0 - age / 18.0)));
               }
            }
         }
      }

      int count = nova ? 64 : 24;

      for (int i = 0; i < count; i++) {
         double angle = i * 2.399963;
         double fraction = 0.25 + 0.7 * Math.sqrt((i + 0.5) / count);
         double launch = nova ? 6.0 + fraction * fraction * 42.0 : fraction * fraction * 8.0;
         double t = age - launch;
         if (!(t < 0.0) && !(t > 24.0)) {
            double r = b.radius * fraction;
            double h = b.height(r, angle);
            if (Double.isFinite(h)) {
               Vec3 outward = b.side.scale(Math.cos(angle)).add(b.up.scale(Math.sin(angle)));
               Vec3 point = b.point(r, angle, h + 0.1).add(fragmentOffset(b.normal, outward, t, nova ? 1.8 : 1.2));
               double size = (nova ? 0.23 : 0.12) * (0.7 + i % 5 * 0.2);
               Vec3 x = b.side.scale(Math.cos(t * 0.18) * size).add(b.normal.scale(Math.sin(t * 0.18) * size));
               Vec3 y = b.up.scale(size * 0.7);
               Vec3 z = x.cross(y).normalize().scale(size);
               int alpha = (int)(255.0 * Math.min(1.0, (24.0 - t) / 7.0));
               quad(v, m, point.add(x), point.add(y), point.add(z), point.subtract(y), 112, 102, 92, alpha);
               quad(v, m, point.add(x), point.subtract(z), point.subtract(x), point.add(z), 75, 70, 67, alpha);
               quad(v, m, point.add(y), point.subtract(x), point.subtract(y), point.subtract(z), 151, 135, 111, alpha);
            }
         }
      }

      if (nova) {
         emitNovaEmbers(v, m, b, age);
      }
   }

   private static void emitNovaPressure(VertexConsumer v, Matrix4f m, ImpactDebrisRenderer.Burst b, double age) {
      for (int wave = 0; wave < 3; wave++) {
         double t = age - 6.0 - wave * 5;
         if (!(t <= 0.0) && !(t >= 42.0)) {
            double front = waveRadius(b.radius, age - wave * 5, true);
            double fade = Math.min(1.0, t / 3.0) * (1.0 - Mth.clamp((t - 26.0) / 16.0, 0.0, 1.0));
            double width = 1.6 + front * 0.025;

            for (int segment = 0; segment < 128; segment++) {
               for (int lane = 0; lane < 4; lane++) {
                  for (int corner = 0; corner < 4; corner++) {
                     double angle = (segment + (corner != 1 && corner != 2 ? 0 : 1)) * Math.PI / 64.0;
                     double u = (lane + (corner >= 2 ? 1 : 0)) / 4.0;
                     double radius = front + (u - 0.5) * width * 2.0;
                     double ground = b.height(radius, angle);
                     if (!Double.isFinite(ground)) {
                        ground = 0.0;
                     }

                     double crest = Math.sin(u * Math.PI);
                     double lift = 0.22 + crest * (1.2 + wave * 0.4) * (0.85 + 0.15 * Math.sin(angle * 9.0 - t * 0.13));
                     int alpha = Double.isFinite(b.height(radius, angle)) ? (int)(220.0 * crest * fade / (1.0 + wave * 0.35)) : 0;
                     vertex(v, m, b.point(radius, angle, ground + lift), 255, lane != 1 && lane != 2 ? 157 : 238, lane != 1 && lane != 2 ? 65 : 172, alpha);
                  }
               }
            }
         }
      }

      if (!(age < 6.0) && !(age > 62.0)) {
         for (int branch = 0; branch < 18; branch++) {
            for (int step = 0; step < 8; step++) {
               double r = b.radius * (0.77 + step * 0.029);
               double start = 6.0 + 42.0 * Math.pow(r / b.radius, 2.0);
               double fade = Mth.clamp((age - start) / 2.0, 0.0, 1.0) * Mth.clamp((start + 16.0 - age) / 12.0, 0.0, 1.0);
               if (!(fade <= 0.0)) {
                  double angle = branch * Math.PI / 9.0 + Math.sin(branch * 3 + step * 2.4) * 0.012;
                  double next = r + b.radius * 0.031;
                  double h = b.height(r, angle);
                  double h2 = b.height(next, angle + 0.006);
                  if (Double.isFinite(h) && Double.isFinite(h2)) {
                     Vec3 a = b.point(r, angle, h + 0.075);
                     Vec3 end = b.point(next, angle + 0.006, h2 + 0.075);
                     Vec3 width = b.side.scale(-Math.sin(angle) * 0.12).add(b.up.scale(Math.cos(angle) * 0.12));
                     quad(v, m, a.subtract(width), end.subtract(width), end.add(width), a.add(width), 255, 190, 83, (int)(210.0 * fade));
                  }
               }
            }
         }
      }
   }

   private static void emitNovaEmbers(VertexConsumer v, Matrix4f m, ImpactDebrisRenderer.Burst b, double age) {
      for (int i = 0; i < 192; i++) {
         double fraction = Math.sqrt((i + 0.5) / 192.0);
         double angle = i * 2.399963;
         double t = age - 6.0 - fraction * fraction * 42.0;
         if (!(t < 0.0) && !(t > 90.0)) {
            double radius = b.radius * fraction;
            double h = b.height(radius, angle);
            if (Double.isFinite(h)) {
               Vec3 point = b.point(radius, angle + t * 0.0015, h + 8.0 + i % 13 * 1.3 + t * 0.24 - t * t * 0.004);
               double size = 0.1 + i % 5 * 0.035;
               Vec3 x = b.side.scale(size);
               Vec3 y = b.normal.scale(size * (2.0 + Math.abs(0.24 - t * 0.008) * 4.0));
               int alpha = (int)(230.0 * Math.min(1.0, t / 4.0) * Math.min(1.0, (90.0 - t) / 22.0));
               quad(v, m, point.subtract(x), point.subtract(y), point.add(x), point.add(y), 255, 202 + i % 36, 112, alpha);
            }
         }
      }
   }

   private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
      v.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, Mth.clamp(a, 0, 255)).endVertex();
   }

   private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int blue, int alpha) {
      vertex(v, m, a, r, g, blue, alpha);
      vertex(v, m, b, r, g, blue, alpha);
      vertex(v, m, c, r, g, blue, alpha);
      vertex(v, m, d, r, g, blue, alpha);
   }

   static final class Burst {
      final Vec3 center;
      final Vec3 normal;
      final Vec3 side;
      final Vec3 up;
      final double radius;
      final int kind;
      final long tick;
      final double[][] surface = new double[13][64];
      boolean sounded;

      Burst(Vec3 center, Vec3 normal, double radius, int kind, long tick) {
         this.center = center;
         this.normal = normal.normalize();
         this.radius = radius;
         this.kind = kind;
         this.side = this.normal.cross(Math.abs(normal.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
         this.up = this.normal.cross(this.side);
         this.tick = tick;
      }

      void sampleSurface() {
         for (int r = 0; r <= 12; r++) {
            for (int a = 0; a < 64; a++) {
               Vec3 point = this.point(this.radius * r / 12.0, a * Math.PI * 2.0 / 64.0, 0.0);
               BlockPos block = BlockPos.containing(point);
               double height = Double.NaN;
               if (ImpactDebrisRenderer.world.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) {
                  if (this.kind == 2) {
                     height = ImpactDebrisRenderer.world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, block.getX(), block.getZ()) - this.center.y;
                  } else {
                     BlockHitResult hit = ImpactDebrisRenderer.world
                        .clip(
                           new ClipContext(
                              point.add(this.normal.scale(3.0)),
                              point.subtract(this.normal.scale(5.0)),
                              Block.COLLIDER,
                              Fluid.NONE,
                              Minecraft.getInstance().player
                           )
                        );
                     if (hit.getType() != Type.MISS) {
                        height = hit.getLocation().subtract(point).dot(this.normal);
                     }
                  }
               }

               this.surface[r][a] = height;
            }
         }
      }

      Vec3 point(double r, double angle, double height) {
         return this.center.add(this.side.scale(Math.cos(angle) * r)).add(this.up.scale(Math.sin(angle) * r)).add(this.normal.scale(height));
      }

      double height(double r, double angle) {
         double radial = Mth.clamp(r / this.radius * 12.0, 0.0, 12.0);
         int low = (int)radial;
         int high = Math.min(12, low + 1);
         double angular = angle / (Math.PI * 2) * 64.0;
         int a = Math.floorMod((int)Math.floor(angular), 64);
         int next = (a + 1) % 64;
         double t = angular - Math.floor(angular);
         double near = this.surface[low][a] * (1.0 - t) + this.surface[low][next] * t;
         double far = this.surface[high][a] * (1.0 - t) + this.surface[high][next] * t;
         return near * (1.0 - (radial - low)) + far * (radial - low);
      }
   }
}

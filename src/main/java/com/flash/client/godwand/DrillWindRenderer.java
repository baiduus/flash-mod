package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import com.flash.godwand.DrillWindPayload;
import com.flash.godwand.RatioCollapseManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DrillWindRenderer {
   private static final Map<Long, DrillWindRenderer.Wind> WINDS = new HashMap<>();
   private static ClientLevel world;

   public static void receive(DrillWindPayload payload) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (current != null) {
         WINDS.put(payload.id(), new DrillWindRenderer.Wind(payload, current.getGameTime()));
      }
   }

   public static void reset() {
      WINDS.clear();
      world = null;
   }

   public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (world != null) {
         long now = world.getGameTime();
         WINDS.values().removeIf(w -> now - w.tick() > 4L);
         Vec3 camera = cameraObj.getPosition();

         for (DrillWindRenderer.Wind wind : WINDS.values()) {
            DrillWindPayload p = wind.payload();
            if (!(camera.distanceToSqr(p.x(), p.y(), p.z()) > 16384.0)) {
               float elapsed = (float)(now - wind.tick()) + tickDelta;
               matrices.pushPose();
               matrices.translate(p.x() - camera.x, p.y() - camera.y, p.z() - camera.z);
               matrices.mulPose(Axis.XP.rotationDegrees(90.0F));
               float reveal = RatioCollapseManager.drillWindVisibility(p.speed());
               if (reveal <= 0.0F) {
                  matrices.popPose();
               } else {
                  float heat = RatioCollapseManager.drillHeat(p.speed()) * reveal;
                  draw(
                     matrices.last().pose(),
                     p.angle() + elapsed * p.speed(),
                     (p.radius() * 1.18F + 0.7F) * (0.7F + reveal * 0.3F),
                     p.radius() * 3.2F,
                     (0.45F + heat * 0.55F) * reveal,
                     heat
                  );
                  drawCloud(matrices.last().pose(), p.angle() + elapsed * p.speed(), p.radius() * 1.45F * (0.7F + reveal * 0.3F), heat, reveal);
                  matrices.popPose();
               }
            }
         }
      }
   }

   private static void drawCloud(Matrix4f matrix, float phase, float radius, float heat, float reveal) {
      GodWandGeometry.enableBlend();
      GodWandGeometry.defaultBlendFunc();
      GodWandGeometry.disableCull();
      GodWandGeometry.depthMask(false);
      GodWandGeometry.colored();

      try {
         for (int ring = 0; ring < 6; ring++) {
            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int step = 0; step <= 72; step++) {
               float t = step / 72.0F;
               float angle = phase * 0.6F + ring * 1.3F + t * 5.2F;
               float distance = radius * (1.4F + ring * 0.48F + t * 0.4F);
               float width = radius * 0.24F * (float)Math.sin(t * Math.PI);
               float z = -radius * (2.8F + ring * 0.12F) + (float)Math.sin(angle * 2.0F) * radius * 0.08F;
               int alpha = (int)((50.0F + heat * 65.0F) * Math.sin(t * Math.PI) * reveal);
               float x = (float)Math.cos(angle);
               float y = (float)Math.sin(angle);
               buffer.vertex(matrix, x * (distance - width), y * (distance - width), z).color(180, 155, 150, alpha).endVertex();
               buffer.vertex(matrix, x * (distance + width), y * (distance + width), z).color(230, 215, 205, alpha).endVertex();
            }

            GodWandGeometry.draw(buffer.end());
         }
      } finally {
         GodWandGeometry.depthMask(true);
         GodWandGeometry.enableCull();
         GodWandGeometry.disableBlend();
      }
   }

   public static void draw(Matrix4f matrix, float phase, float radius, float length, float strength) {
      draw(matrix, phase, radius, length, strength, 0.0F);
   }

   private static void draw(Matrix4f matrix, float phase, float radius, float length, float strength, float heat) {
      int green = (int)(240.0F - heat * 155.0F);
      int blue = (int)(255.0F - heat * 225.0F);
      GodWandGeometry.enableBlend();
      GodWandGeometry.additiveBlend();
      GodWandGeometry.disableCull();
      GodWandGeometry.depthMask(false);
      GodWandGeometry.colored();

      try {
         for (int ribbon = 0; ribbon < 5; ribbon++) {
            for (int layer = 0; layer < 2; layer++) {
               BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

               for (int step = 0; step <= 48; step++) {
                  float t = step / 48.0F;
                  float angle = phase + ribbon * (float) (Math.PI * 2.0 / 5.0) + t * 8.8F;
                  float r = 0.12F + radius * (float)Math.pow(t, 0.72);
                  float z = (0.62F - t) * length;
                  float taper = (float)Math.sin(Math.PI * t);
                  float width = radius * (layer == 0 ? 0.1F : 0.018F) * taper;
                  int alpha = (int)(taper * strength * (layer == 0 ? 65 : 220));
                  float x = (float)Math.cos(angle);
                  float y = (float)Math.sin(angle);
                  buffer.vertex(matrix, x * r - y * width, y * r + x * width, z).color((int)(205.0F + heat * 50.0F), green, blue, alpha).endVertex();
                  buffer.vertex(matrix, x * r + y * width, y * r - x * width, z).color((int)(205.0F + heat * 50.0F), green, blue, alpha).endVertex();
               }

               GodWandGeometry.draw(buffer.end());
            }
         }

         if (heat > 0.01F) {
            BufferBuilder seams = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (int strand = 0; strand < 7; strand++) {
               for (int step = 0; step < 32; step++) {
                  for (int corner = 0; corner < 4; corner++) {
                     float t = (step + (corner != 1 && corner != 2 ? 0 : 1)) / 32.0F;
                     float angle = phase + strand * 0.8976F + t * 8.8F;
                     float r = (0.12F + radius * (float)Math.pow(t, 0.72)) * 0.84F;
                     float width = (corner < 2 ? -1 : 1) * radius * 0.012F;
                     float z = (0.62F - t) * length;
                     int alpha = (int)(heat * 210.0F * Math.sin(t * Math.PI));
                     seams.vertex(
                           matrix, (float)Math.cos(angle) * r - (float)Math.sin(angle) * width, (float)Math.sin(angle) * r + (float)Math.cos(angle) * width, z
                        ).color(255, (int)(65.0F + heat * 110.0F), 25, alpha).endVertex();
                  }
               }
            }

            for (int ember = 0; ember < 18; ember++) {
               for (int segment = 0; segment < 8; segment++) {
                  float h = (ember + 0.5F) / 18.0F;

                  for (int corner = 0; corner < 4; corner++) {
                     float trail = (segment + (corner != 1 && corner != 2 ? 0 : 1)) / 8.0F;
                     float angle = phase + ember * 2.4F + h * 8.8F - trail * 0.55F;
                     float r = radius * (0.2F + h * 1.15F + trail * 0.1F);
                     float width = (corner < 2 ? -1 : 1) * radius * 0.009F;
                     seams.vertex(
                           matrix,
                           (float)Math.cos(angle) * r - (float)Math.sin(angle) * width,
                           (float)Math.sin(angle) * r + (float)Math.cos(angle) * width,
                           (0.62F - h - trail * 0.08F) * length
                        ).color(255, 145, 35, (int)(heat * 210.0F * (1.0F - trail))).endVertex();
                  }
               }
            }

            GodWandGeometry.draw(seams.end());
         }

         for (int ring = 0; ring < 3; ring++) {
            float expansion = (phase * 0.08F + ring / 3.0F) % 1.0F;
            float r = radius * (0.65F + expansion * 1.7F);
            float z = ((heat > 0.0F ? 0.35F : 0.52F) - expansion * (heat > 0.0F ? 1.7F : 0.8F)) * length;
            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int step = 0; step <= 32; step++) {
               float t = step / 32.0F;
               float angle = phase + ring * 2.094F + t * 4.4F;
               float width = radius * 0.045F * (float)Math.sin(Math.PI * t);
               int alpha = (int)(180.0F * strength * (1.0F - expansion) * Math.sin(Math.PI * t));
               float x = (float)Math.cos(angle);
               float y = (float)Math.sin(angle);
               buffer.vertex(matrix, x * (r - width), y * (r - width), z).color(225, 245, 255, alpha).endVertex();
               buffer.vertex(matrix, x * (r + width), y * (r + width), z).color(225, 245, 255, alpha).endVertex();
            }

            GodWandGeometry.draw(buffer.end());
         }
      } finally {
         GodWandGeometry.depthMask(true);
         GodWandGeometry.enableCull();
         GodWandGeometry.defaultBlendFunc();
         GodWandGeometry.disableBlend();
      }
   }

   private record Wind(DrillWindPayload payload, long tick) {
   }
}

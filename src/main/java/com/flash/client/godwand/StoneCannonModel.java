package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class StoneCannonModel {
   public static Vector3f chargeWindPoint(float t, float phase, float scale, float charge) {
      float angle = phase - t * (7.0F + charge * 3.0F);
      float radius = 0.08F + (float)Math.sin(t * Math.PI * 0.5) * (1.65F - charge * 0.35F);
      float nose = 2.45F * scale;
      return new Vector3f((float)Math.cos(angle) * radius, (float)Math.sin(angle) * radius, nose + t * (-4.0F - charge * 2.5F - nose));
   }

   public static float chargeWindVisibility(float time) {
      float fade = Math.max(0.0F, Math.min(1.0F, (time - 20.0F) / 24.0F));
      return fade * fade * (3.0F - 2.0F * fade);
   }

   public static float chargeDrillRotation(float time) {
      float ramp = Math.min(time, 80.0F);
      return time * 62.0F + ramp * ramp * 0.45F + Math.max(0.0F, time - 80.0F) * 72.0F;
   }

   public static float chargeWindRotation(float time) {
      float elapsed = Math.max(0.0F, time - 20.0F);
      float ramp = Math.min(elapsed, 60.0F);
      return elapsed * 0.84F + ramp * ramp * 0.0056F + Math.max(0.0F, elapsed - 60.0F) * 0.672F;
   }

   public static void renderChargeWind(Matrix4f matrix, float scale, float time, float charge) {
      float visibility = chargeWindVisibility(time);
      if (!(visibility <= 0.0F)) {
         GodWandGeometry.enableBlend();
         GodWandGeometry.additiveBlend();
         GodWandGeometry.enableDepthTest();
         GodWandGeometry.disableCull();
         GodWandGeometry.depthMask(false);
         GodWandGeometry.colored();
         float rotation = chargeWindRotation(time);

         try {
            for (int arm = 0; arm < 2; arm++) {
               BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

               for (int step = 0; step <= 80; step++) {
                  float t = step / 80.0F;
                  float phase = arm * (float) Math.PI + rotation;
                  Vector3f point = chargeWindPoint(t, phase, scale, charge);
                  float angle = phase - t * (7.0F + charge * 3.0F);
                  float taper = (float)Math.pow(Math.sin(t * Math.PI), 0.75);
                  float width = (0.12F + charge * 0.22F) * taper;
                  float eyeFade = 1.0F - 0.65F * (float)Math.exp(-Math.pow((point.z + 1.85F) / 0.65F, 2.0));
                  int alpha = (int)((58.0F + charge * 42.0F) * taper * eyeFade * visibility);

                  for (int sign : new int[]{-1, 1}) {
                     buffer.vertex(matrix, point.x - sign * (float)Math.sin(angle) * width, point.y + sign * (float)Math.cos(angle) * width, point.z).color(220, 242, 255, alpha).endVertex();
                  }
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
   }

   public static Vector3f surface(float t, float angle) {
      float radius = (float)Math.pow(1.0F - t, 0.8) * (0.4F + 0.065F * (float)Math.cos(angle * 3.0F - t * 18.0F));
      return new Vector3f((float)Math.cos(angle) * radius, (float)Math.sin(angle) * radius, -0.75F + t * 3.2F);
   }

   public static void render(Matrix4f matrix, float scale, float time, float charge) {
      GodWandGeometry.enableDepthTest();
      GodWandGeometry.disableBlend();
      GodWandGeometry.depthMask(true);
      GodWandGeometry.colored();
      GodWandGeometry.disableCull();

      try {
         BufferBuilder stone = GodWandGeometry.begin(PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

         for (int ring = 0; ring < 16; ring++) {
            for (int side = 0; side < 24; side++) {
               float t = ring / 16.0F;
               float next = (ring + 1) / 16.0F;
               float a = side * (float) Math.PI / 12.0F;
               float b = (side + 1) * (float) Math.PI / 12.0F;
               Vector3f p = surface(t, a);
               Vector3f q = surface(t, b);
               Vector3f r = surface(next, b);
               Vector3f s = surface(next, a);
               float shade = 0.6F + 0.3F * (float)Math.cos(a - 0.7) + 0.08F * (float)Math.sin(ring * 2.3 + side * 4.1);
               int red = (int)(160.0F * shade);
               int green = (int)(124.0F * shade);
               int blue = (int)(83.0F * shade);

               for (Vector3f point : new Vector3f[]{p, q, r, p, r, s}) {
                  stone.vertex(matrix, point.x * scale, point.y * scale, point.z * scale).color(red, green, blue, 255).endVertex();
               }
            }
         }

         for (int side = 0; side < 24; side++) {
            Vector3f p = surface(0.0F, side * (float) Math.PI / 12.0F);
            Vector3f q = surface(0.0F, (side + 1) * (float) Math.PI / 12.0F);
            stone.vertex(matrix, 0.0F, 0.0F, -0.75F * scale).color(100, 72, 44, 255).endVertex();
            stone.vertex(matrix, p.x * scale, p.y * scale, p.z * scale).color(130, 95, 56, 255).endVertex();
            stone.vertex(matrix, q.x * scale, q.y * scale, q.z * scale).color(130, 95, 56, 255).endVertex();
         }

         GodWandGeometry.draw(stone.end());
         GodWandGeometry.enableBlend();
         GodWandGeometry.additiveBlend();
         GodWandGeometry.depthMask(false);

         for (int layer = 0; layer < 2; layer++) {
            BufferBuilder glow = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int step = 0; step <= 144; step++) {
               float t = step / 144.0F;
               float angle = t * 24.0F - time * 0.16F;
               float radius = (0.47F * (1.0F - t) + 0.04F) * scale;
               float width = (layer == 0 ? 0.085F : 0.018F) * scale;
               int alpha = (int)(Math.sin(t * Math.PI) * (layer == 0 ? 100 : 240) * charge);

               for (int sign : new int[]{-1, 1}) {
                  float r = radius + sign * width;
                  glow.vertex(matrix, (float)Math.cos(angle) * r, (float)Math.sin(angle) * r, (-0.75F + t * 3.2F) * scale).color(255, layer == 0 ? 130 : 235, layer == 0 ? 12 : 145, alpha).endVertex();
               }
            }

            GodWandGeometry.draw(glow.end());
         }
      } finally {
         GodWandGeometry.depthMask(true);
         GodWandGeometry.enableCull();
         GodWandGeometry.defaultBlendFunc();
         GodWandGeometry.disableBlend();
      }
   }
}

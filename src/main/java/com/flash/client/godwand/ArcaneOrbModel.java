package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.joml.Matrix4f;

public final class ArcaneOrbModel {
   public static float chargeGlowAlpha(float radius) {
      float edge = Math.max(0.0F, 1.0F - radius * radius);
      return edge * edge;
   }

   public static void renderCharge(Matrix4f matrix, float scale, float time, float charge) {
      GodWandGeometry.enableBlend();
      GodWandGeometry.additiveBlend();
      GodWandGeometry.enableDepthTest();
      GodWandGeometry.depthMask(false);
      GodWandGeometry.disableCull();
      GodWandGeometry.colored();

      try {
         BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);

         for (int band = 0; band < 10; band++) {
            for (int segment = 0; segment < 64; segment++) {
               for (int corner = 0; corner < 4; corner++) {
                  float radius = (band + (corner != 1 && corner != 2 ? 0 : 1)) / 10.0F;
                  float angle = (segment + (corner >= 2 ? 1 : 0)) * 0.09817477F;
                  float core = (float)Math.exp(-radius * radius * 14.0F);
                  buffer.vertex(matrix, (float)Math.cos(angle) * radius * scale * 1.65F, (float)Math.sin(angle) * radius * scale * 1.65F, 0.0F).color((int)(190.0F * core), (int)(175.0F + 80.0F * core), 255, (int)(chargeGlowAlpha(radius) * (190.0F + 65.0F * charge))).endVertex();
               }
            }
         }

         GodWandGeometry.draw(buffer.end());
         renderEnergyRing(matrix, scale * 1.05F, scale * 0.025F, 72.0F, 12.0F, time * 3.0F, 40, 230, 255, (int)(95.0F + 65.0F * charge));
         renderEnergyRing(matrix, scale * 1.28F, scale * 0.016F, 48.0F, -30.0F, -time * 2.0F, 110, 170, 255, (int)(65.0F + 45.0F * charge));
      } finally {
         GodWandGeometry.depthMask(true);
         GodWandGeometry.enableCull();
         GodWandGeometry.defaultBlendFunc();
         GodWandGeometry.disableBlend();
      }
   }

   public static void render(Matrix4f matrix, float scale, float time, float charge) {
      GodWandGeometry.enableBlend();
      GodWandGeometry.additiveBlend();
      GodWandGeometry.enableDepthTest();
      GodWandGeometry.depthMask(false);
      GodWandGeometry.disableCull();
      GodWandGeometry.colored();

      try {
         float coreScale = scale * 0.46F;
         float coreSpin = time * (32.0F + charge * 140.0F);
         renderSphere(matrix, coreScale, coreSpin, coreSpin * 0.8F, 0.0F, 220, 255, 255, (int)(200.0F + charge * 55.0F));
         float shellScale = scale * 0.88F;
         float shellSpin = -time * (24.0F + charge * 110.0F);
         renderSphere(matrix, shellScale, shellSpin, 0.0F, time * (28.0F + charge * 120.0F), 0, 240, 255, (int)(110.0F + charge * 80.0F));
         float haloScale = scale * 1.12F;
         float haloSpin = time * (16.0F + charge * 80.0F);
         renderSphere(matrix, haloScale, haloSpin, -time * 14.0F, time * 12.0F, 40, 160, 255, (int)(45.0F + charge * 50.0F));
         renderEnergyRing(matrix, scale * 1.32F, scale * 0.05F, 45.0F, 0.0F, time * (45.0F + charge * 240.0F), 0, 255, 255, (int)(140.0F + charge * 100.0F));
         renderEnergyRing(matrix, scale * 1.48F, scale * 0.045F, 0.0F, -55.0F, -time * (55.0F + charge * 270.0F), 80, 200, 255, (int)(130.0F + charge * 90.0F));
         if (charge >= 0.35F) {
            float ring3Fade = Math.min(1.0F, (charge - 0.35F) / 0.3F);
            renderEnergyRing(matrix, scale * 1.62F, scale * 0.04F, 70.0F, 35.0F, time * (75.0F + charge * 340.0F), 175, 95, 255, (int)(ring3Fade * 170.0F));
         }

         renderInwardSpirals(matrix, scale, time, charge);
      } finally {
         GodWandGeometry.depthMask(true);
         GodWandGeometry.enableCull();
         GodWandGeometry.defaultBlendFunc();
         GodWandGeometry.disableBlend();
      }
   }

   private static void renderSphere(Matrix4f matrix, float radius, float rotY, float rotX, float rotZ, int r, int g, int b, int a) {
      Matrix4f local = new Matrix4f(matrix);
      local.rotateY((float)Math.toRadians(rotY));
      local.rotateX((float)Math.toRadians(rotX));
      local.rotateZ((float)Math.toRadians(rotZ));
      int rings = 8;
      int segments = 12;

      for (int i = 0; i < rings; i++) {
         float theta1 = (float) (-Math.PI / 2) + (float) Math.PI * i / rings;
         float theta2 = (float) (-Math.PI / 2) + (float) Math.PI * (i + 1) / rings;
         float y1 = radius * (float)Math.sin(theta1);
         float y2 = radius * (float)Math.sin(theta2);
         float r1 = radius * (float)Math.cos(theta1);
         float r2 = radius * (float)Math.cos(theta2);
         BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

         for (int j = 0; j <= segments; j++) {
            float phi = (float) (Math.PI * 2) * j / segments;
            float cosPhi = (float)Math.cos(phi);
            float sinPhi = (float)Math.sin(phi);
            buffer.vertex(local, r1 * cosPhi, y1, r1 * sinPhi).color(r, g, b, a).endVertex();
            buffer.vertex(local, r2 * cosPhi, y2, r2 * sinPhi).color(r, g, b, a).endVertex();
         }

         GodWandGeometry.draw(buffer.end());
      }
   }

   private static void renderEnergyRing(Matrix4f matrix, float radius, float halfWidth, float tiltX, float tiltZ, float spinAngle, int r, int g, int b, int a) {
      Matrix4f local = new Matrix4f(matrix);
      local.rotateX((float)Math.toRadians(tiltX));
      local.rotateZ((float)Math.toRadians(tiltZ));
      local.rotateY((float)Math.toRadians(spinAngle));
      int steps = 24;
      BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

      for (int i = 0; i <= steps; i++) {
         float phi = (float) (Math.PI * 2) * i / steps;
         float cos = (float)Math.cos(phi);
         float sin = (float)Math.sin(phi);
         float rInner = radius - halfWidth;
         float rOuter = radius + halfWidth;
         buffer.vertex(local, rInner * cos, 0.0F, rInner * sin).color(r, g, b, a).endVertex();
         buffer.vertex(local, rOuter * cos, 0.0F, rOuter * sin).color(r, g, b, a / 2).endVertex();
      }

      GodWandGeometry.draw(buffer.end());
   }

   private static void renderInwardSpirals(Matrix4f matrix, float scale, float time, float charge) {
      int arms = 3;
      int steps = 22;
      float spiralSpin = time * (0.8F + charge * 3.4F);

      for (int arm = 0; arm < arms; arm++) {
         float armOffset = arm * (float) Math.PI * 2.0F / arms;
         BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

         for (int step = 0; step <= steps; step++) {
            float t = (float)step / steps;
            float spiralRadius = scale * (2.4F - t * 1.6F);
            float angle = armOffset + (1.0F - t) * 6.5F - spiralSpin;
            float height = (1.0F - t) * (arm % 2 == 0 ? 0.32F : -0.32F);
            float width = scale * 0.05F * (float)Math.sin(t * Math.PI);
            int alpha = (int)(Math.sin(t * Math.PI) * (70.0F + charge * 80.0F));
            float x = spiralRadius * (float)Math.cos(angle);
            float z = spiralRadius * (float)Math.sin(angle);
            buffer.vertex(matrix, x, height - width, z).color(0, 245, 255, alpha).endVertex();
            buffer.vertex(matrix, x, height + width, z).color(120, 210, 255, alpha).endVertex();
         }

         GodWandGeometry.draw(buffer.end());
      }
   }
}

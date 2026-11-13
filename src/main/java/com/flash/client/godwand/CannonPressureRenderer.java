package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import com.flash.godwand.CannonPressurePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class CannonPressureRenderer {
   private static final List<CannonPressureRenderer.Burst> BURSTS = new ArrayList<>();
   private static ClientLevel world;

   public static void reset() {
      BURSTS.clear();
      world = null;
   }

   public static void receive(CannonPressurePayload payload) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (world != null && !(payload.direction().lengthSqr() < 0.001)) {
         if (BURSTS.size() >= 32) {
            BURSTS.remove(0);
         }

         BURSTS.add(new CannonPressureRenderer.Burst(payload, world.getGameTime()));
      }
   }

   public static Vec3 conePoint(float t, float angle, float expansion, int level) {
      double radius = t * (1.1 + level * 0.3) * expansion;
      return new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius, -0.12 - t * (1.4 + level * 0.35) * expansion);
   }

   public static void render(PoseStack matrices, Camera camera, float delta) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (world != null && !BURSTS.isEmpty()) {
         long now = world.getGameTime();
         BURSTS.removeIf(b -> now - b.tick >= 8L);
         if (!BURSTS.isEmpty()) {
            Matrix4f matrix = matrices.last().pose();
            GodWandGeometry.enableBlend();
            GodWandGeometry.defaultBlendFunc();
            GodWandGeometry.enableDepthTest();
            GodWandGeometry.depthMask(false);
            GodWandGeometry.disableCull();
            GodWandGeometry.colored();

            try {
               for (CannonPressureRenderer.Burst burst : BURSTS) {
                  CannonPressurePayload p = burst.payload;
                  Vec3 forward = p.direction().normalize();
                  Vec3 side = forward.cross(Math.abs(forward.y) < 0.99 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
                  Vec3 up = forward.cross(side);
                  Vec3 origin = p.center().subtract(camera.getPosition());
                  float age = ((float)(now - burst.tick) + delta) / 8.0F;
                  float expansion = 0.15F + 0.85F * (1.0F - (1.0F - age) * (1.0F - age));

                  for (int band = 0; band < 3; band++) {
                     BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

                     for (int step = 0; step <= 64; step++) {
                        float angle = step * (float) Math.PI / 32.0F;

                        for (int edge = 0; edge < 2; edge++) {
                           float t = 0.25F + band * 0.3F + edge * 0.12F;
                           Vec3 local = conePoint(t, angle, expansion, p.level());
                           Vec3 point = origin.add(side.scale(local.x)).add(up.scale(local.y)).add(forward.scale(local.z));
                           int alpha = (int)(170.0F * (1.0F - age) * (1.0F - age) * (edge == 0 ? 1.0 : 0.15));
                           buffer.vertex(matrix, (float)point.x, (float)point.y, (float)point.z).color(220, 240, 255, alpha).endVertex();
                        }
                     }

                     GodWandGeometry.draw(buffer.end());
                  }
               }
            } finally {
               GodWandGeometry.depthMask(true);
               GodWandGeometry.enableCull();
               GodWandGeometry.defaultBlendFunc();
               GodWandGeometry.disableBlend();
            }
         }
      }
   }

   private record Burst(CannonPressurePayload payload, long tick) {
   }
}

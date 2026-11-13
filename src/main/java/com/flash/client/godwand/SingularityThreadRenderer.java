package com.flash.client.godwand;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import com.flash.godwand.SingularityThreadsPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Display.RenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class SingularityThreadRenderer {
   private static final Map<Integer, SingularityThreadRenderer.Thread> THREADS = new HashMap<>();
   private static ClientLevel world;

   public static void reset() {
      THREADS.clear();
      world = null;
   }

   public static void receive(SingularityThreadsPayload payload) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (current != null) {
         for (int id : payload.beamIds()) {
            THREADS.put(id, new SingularityThreadRenderer.Thread(current.getGameTime(), payload.recallTicks()));
         }
      }
   }

   public static float pulseFraction(float time, int seed, float recallProgress) {
      float phase = Math.floorMod(seed * 31, 101) / 101.0F;
      if (recallProgress >= 0.0F) {
         return 1.0F - Mth.clamp((recallProgress + phase * 0.12F) / 0.85F, 0.0F, 1.0F);
      }

      float cycle = time * 0.032F + phase;
      return 1.0F - (cycle - (float)Math.floor(cycle));
   }

   public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != world) {
         reset();
         world = current;
      }

      if (world != null && !THREADS.isEmpty()) {
         long now = world.getGameTime();
         THREADS.values().removeIf(t -> now - t.received > (t.recallTicks > 0 ? t.recallTicks + 2 : 160));
         if (!THREADS.isEmpty()) {
            float delta = tickDelta;
            Vec3 camera = cameraObj.getPosition();
            Matrix4f matrix = matrices.last().pose();
            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            GodWandGeometry.enableBlend();
            GodWandGeometry.additiveBlend();
            GodWandGeometry.enableDepthTest();
            GodWandGeometry.depthMask(false);
            GodWandGeometry.disableCull();
            GodWandGeometry.colored();

            try {
               for (Entry<Integer, SingularityThreadRenderer.Thread> entry : THREADS.entrySet()) {
                  if (world.getEntity(entry.getKey()) instanceof BlockDisplay beam && !(beam.distanceToSqr(camera) > 9216.0)) {
                     RenderState state = beam.renderState();
                     if (state != null) {
                        Matrix4fc transform = ((Transformation)state.transformation().get(beam.calculateInterpolationProgress(delta))).getMatrix();
                        Vector3f start = transform.transformPosition(new Vector3f(0.5F, 0.5F, 0.0F));
                        Vector3f end = transform.transformPosition(new Vector3f(0.5F, 0.5F, 1.0F));
                        Vec3 origin = beam.getPosition(delta).subtract(camera).add(start.x, start.y, start.z);
                        Vec3 direction = new Vec3(end.x - start.x, end.y - start.y, end.z - start.z);
                        if (!(direction.lengthSqr() < 0.01)) {
                           Vec3 side = direction.cross(origin);
                           if (side.lengthSqr() < 1.0E-5) {
                              side = direction.cross(new Vec3(0.0, 1.0, 0.0));
                           }

                           side = side.normalize();
                           SingularityThreadRenderer.Thread thread = entry.getValue();
                           float recall = thread.recallTicks > 0 ? ((float)(now - thread.received) + delta) / thread.recallTicks : -1.0F;
                           float fade = recall >= 0.0F ? Mth.clamp((1.0F - recall) * 4.0F, 0.0F, 1.0F) : 1.0F;

                           for (int pulse = 0; pulse < 2; pulse++) {
                              float fraction = pulseFraction((float)(now % 100000L) + delta, entry.getKey() + pulse * 47, recall);
                              Vec3 head = origin.add(direction.scale(fraction));
                              if (!(head.lengthSqr() < 1.0)) {
                                 float threadFade = fade;
                                 if (head.lengthSqr() < 4.0) {
                                    threadFade *= (float)Mth.clamp((head.length() - 1.0) / 1.0, 0.0, 1.0);
                                 }

                                 double tailLength = Math.min(0.13, 0.9 / direction.length());
                                 Vec3 tail = origin.add(direction.scale(Math.min(1.0, fraction + tailLength)));

                                 for (int layer = 0; layer < 2; layer++) {
                                    Vec3 width = side.scale(layer == 0 ? 0.026 : 0.006);
                                    int alpha = (int)((layer == 0 ? 65 : 210) * threadFade);
                                    Vec3[] corners = new Vec3[]{head.subtract(width), head.add(width), tail.add(width), tail.subtract(width)};

                                    for (int v = 0; v < 4; v++) {
                                       Vec3 point = corners[v];
                                       buffer.vertex(matrix, (float)point.x, (float)point.y, (float)point.z).color(layer == 0 ? 95 : 210, 220, 255, v < 2 ? alpha : 0).endVertex();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               BufferBuilder.RenderedBuffer built = buffer.end();
               if (built != null) {
                  GodWandGeometry.draw(built);
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

   private record Thread(long received, int recallTicks) {
   }
}

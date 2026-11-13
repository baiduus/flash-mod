package com.flash.client.godwand;

import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.flash.godwand.AtomicSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class AtomicPostFx {
      private static GodWandPostChain chain;
   private static int width;
   private static int height;
   private static int downsample;
   private static boolean failed;
   private static Method irisInUse;
   private static Object iris;
   private static boolean irisChecked;

   public static boolean available() {
      return GodWandConfig.CLIENT.postEffects && chain != null && !failed;
   }

   public static void reset() {
      reload();
   }

   public static void reload() {
      if (chain != null) {
         chain.close();
      }

      chain = null;
      failed = false;
      downsample = 0;
      height = 0;
      width = 0;
   }

   public static boolean enabled() {
      return GodWandConfig.CLIENT.postEffects && !failed;
   }

   public static boolean shaderPackActive() {
      try {
         if (!irisChecked) {
            irisChecked = true;
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            iris = api.getMethod("getInstance").invoke(null);
            irisInUse = api.getMethod("isShaderPackInUse");
         }

         return irisInUse != null && (Boolean)irisInUse.invoke(iris);
      } catch (ReflectiveOperationException | LinkageError absent) {
         irisInUse = null;
         return false;
      }
   }

   private static void create(Minecraft client) throws Exception {
      chain = new GodWandPostChain("godwand_atomic");
   }

   public static void prewarm() {
      Minecraft client = Minecraft.getInstance();
      if (chain == null && !failed && GodWandConfig.CLIENT.postEffects && client.level != null) {
         try {
            create(client);
         } catch (Exception error) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().error("Atomic post effect disabled until resource reload; world geometry remains available", error);
            return;
         }

         try {
            warm();
         } catch (Exception error) {
            com.mojang.logging.LogUtils.getLogger().warn("God Wand post effect warm-up skipped", error);
         }
      }
   }

   private static void warm() {
   }

   /** After the world, each frame (GodWandClient). */
   public static void render(Matrix4f view, Matrix4f projection, Camera cameraObj, float delta) {
      if (!GodWandConfig.CLIENT.postEffects || AtomicRenderer.nearestFrame(delta) == null && FugaRenderer.shaderFrame(delta) == null) {
         return;
      }
      AtomicPostFx.Frame context = new AtomicPostFx.Frame(view, projection, delta);
      if (!failed) {
         AtomicRenderer.Frame fx = AtomicRenderer.nearestFrame(context.delta);
         FugaRenderer.Frame fuga = FugaRenderer.shaderFrame(context.delta);
         Minecraft client = Minecraft.getInstance();
         if ((fx != null || fuga != null) && client.level != null) {
            if (fx != null || fuga.phase() != 0 || !(fuga.age() < 4.0)) {
               Vec3 center = fx != null ? fx.center() : (fuga.phase() == 1 ? fuga.arrowPosition() : fuga.center());
               double rise = fx == null && fuga.phase() == 1 ? FugaRenderer.hitStop(fuga.casterId(), context.delta) : -1.0;
               if (rise >= 0.0) {
                  center = center.add(0.0, 6.0F + 110.0F * FugaCinematic.smooth(rise, 0.0, 1.0) - 3.0F, 0.0);
               }

               if (fx == null && fuga.phase() == 0) {
                  Vec3 d = fuga.target().subtract(fuga.origin()).normalize();
                  center = FugaRenderer.chargeHand(fuga, fuga.origin(), false)
                     .add(d.scale(1.6 * FugaCinematic.smooth(fuga.age(), 144.0, 190.0)))
                     .subtract(0.0, 2.0, 0.0);
                  if (FugaRenderer.ribbonVolume(fuga.age()) > 0.0) {
                     center = FugaRenderer.chargeHand(fuga, fuga.origin(), true)
                        .lerp(FugaRenderer.chargeHand(fuga, fuga.origin(), false), 0.5)
                        .subtract(0.0, 2.0, 0.0);
                  }
               }

               int w = client.getMainRenderTarget().width;
               int h = client.getMainRenderTarget().height;
               if (w > 0 && h > 0) {
                  try {
                     if (chain == null) {
                        create(client);
                     }

                     if (w != width || h != height || downsample != GodWandConfig.CLIENT.bloomDownsample) {
                        chain.resize(w, h);
                        width = w;
                        height = h;
                        downsample = GodWandConfig.CLIENT.bloomDownsample;
                     }

                     Vec3 camera = cameraObj.getPosition();
                     Vector4f clip = new Vector4f((float)(center.x - camera.x), (float)(center.y + 2.0 - camera.y), (float)(center.z - camera.z), 1.0F)
                        .mul(context.view)
                        .mul(context.projection);
                     float centerX = clip.w > 0.001 ? clip.x / clip.w * 0.5F + 0.5F : 0.5F;
                     float centerY = clip.w > 0.001 ? clip.y / clip.w * 0.5F + 0.5F : 0.5F;
                     Matrix4f inverseProjection = new Matrix4f(context.projection).invert();
                     Matrix4f inverseView = new Matrix4f(context.view).invert();
                     chain.setUniform("InvProjMat", inverseProjection);
                     chain.setUniform("InvViewMat", inverseView);
                     chain.setUniform("CameraPos", (float)camera.x, (float)camera.y, (float)camera.z);
                     chain.setUniform("BlastCenter", (float)center.x, (float)center.y, (float)center.z);
                     chain.setUniform("FugaPhase", fx == null ? fuga.phase() : -1.0F);
                     chain.setUniform("DepthTrust", shaderPackActive() ? 0.0F : 1.0F);
                     if (fx == null) {
                        Vec3 aim = FugaRenderer.shaderAxis(fuga);
                        if (rise >= 0.0) {
                           aim = new Vec3(aim.x, 0.0, aim.z).normalize().scale(0.3).add(0.0, 1.0, 0.0).normalize();
                        }

                        chain.setUniform("FugaDirX", (float)aim.x);
                        chain.setUniform("FugaDirY", (float)aim.y);
                        chain.setUniform("FugaDirZ", (float)aim.z);
                        chain.setUniform("Age", (float)(rise >= 0.0 ? 12.0 + 8.0 * rise : fuga.age()));
                        chain.setUniform("BloomStrength", GodWandConfig.CLIENT.bloomStrength * 0.8F);
                        chain.setUniform("BloomBurst", 0.0F);
                        chain.setUniform("ImpactFrames", GodWandConfig.CLIENT.impactFrames ? 1.0F : 0.0F);
                        boolean own = client.player != null && fuga.casterId() == client.player.getId();
                        chain.setUniform("ObserverGain", own ? 1.0F : 0.0F);
                        chain.setUniform("Cut", own ? FugaRenderer.impactBeat(fuga.casterId(), context.delta) : 0.0F);
                        chain.setUniform("CenterX", clip.w > 0.001 ? centerX : -10.0F);
                        chain.setUniform("CenterY", centerY);
                     } else {
                        chain.setUniform("Age", fx.age());
                        chain.setUniform("SequenceEnd", 834.0F);
                        chain.setUniform("DetonationTick", 414.0F);
                        chain.setUniform("ReleaseFlash", AtomicSequence.releaseFlash(fx.age()));
                        chain.setUniform("Compression", AtomicSequence.compression(fx.age()));
                        chain.setUniform("FxFade", AtomicSequence.visibility(fx.age()));
                        chain.setUniform("Cut", AtomicSequence.blackout(fx.age()));
                        chain.setUniform("HoloRadius", AtomicSequence.holoRadius(fx.age()));
                        chain.setUniform("BeamRadius", AtomicSequence.beamRadius(fx.age()));
                        chain.setUniform("BeamHeight", AtomicSequence.beamHeight(fx.age()));
                        chain.setUniform("BeamDepth", 30.0F);
                        chain.setUniform("BlastPulse", GodWandConfig.CLIENT.impactFrames ? AtomicSequence.blastPulse(fx.age()) : 1.0F);
                        chain.setUniform("ShockRadius", AtomicSequence.shockRadius(fx.age()));
                        chain.setUniform("ObserverGain", AtomicRenderer.distanceGain(client.player.position().distanceTo(fx.center())));
                        chain.setUniform("ImpactPulse", AtomicSequence.impact(fx.age()) ? 1.0F : 0.0F);
                        chain.setUniform("ImpactFrames", GodWandConfig.CLIENT.impactFrames ? 1.0F : 0.0F);
                        chain.setUniform("Darken", GodWandConfig.CLIENT.skyBlackout ? 1.0F : 0.0F);
                        chain.setUniform("CenterX", centerX);
                        chain.setUniform("CenterY", centerY);
                        chain.setUniform("BloomStrength", GodWandConfig.CLIENT.bloomStrength);
                        chain.setUniform("BloomBurst", fx.age() >= 414.0F && clip.w > 0.0F ? Math.max(0.0F, 1.0F - (fx.age() - 414.0F) / 24.0F) : 0.0F);
                     }

                     chain.process();
                  } catch (Exception error) {
                     failed = true;
                     com.mojang.logging.LogUtils.getLogger().error("Atomic post effect disabled until resource reload; world geometry remains available", error);
                     if (chain != null) {
                        chain.close();
                        chain = null;
                     }
                  }
               }
            }
         }
      }
   }

   private record Frame(Matrix4f view, Matrix4f projection, float delta) {
   }
}

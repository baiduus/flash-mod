package com.flash.client.godwand;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.flash.godwand.SupernovaSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GodWandPostFxManager {
   private static GodWandPostFxManager.Frame pendingFrame;
   private static GodWandPostChain postProcessor;
   private static GodWandPostChain novaProcessor;
   private static boolean active = false;
   private static int currentTick = 0;
   private static int waitingForBurstTicks;
   private static int maxTicks = 0;
   private static int fxType = 0;
   private static float baseIntensity = 1.0F;
   private static Vec3 blastCenter = Vec3.ZERO;
   private static float recallCoreStart;

   public static float singularityRadius(float progress) {
      float grow = Mth.clamp(progress / 0.1F, 0.0F, 1.0F);
      float collapse = Mth.clamp((progress - 0.8F) / 0.2F, 0.0F, 1.0F);
      return Mth.lerp(grow * (2.0F - grow), 0.18F, 0.68F) * (1.0F - collapse * collapse);
   }

   public static float singularityRecallRadius(float start, float progress) {
      float collapse = Mth.clamp(progress / 0.6F, 0.0F, 1.0F);
      return start * (1.0F - collapse * collapse);
   }

   public static float supernovaRadius(float progress) {
      float burst = Mth.clamp(progress / 0.1F, 0.0F, 1.0F);
      float radius = Mth.lerp(1.0F - (1.0F - burst) * (1.0F - burst), 14.0F, 68.0F);
      float swell = Mth.clamp((progress - 0.1F) / 0.3F, 0.0F, 1.0F);
      return Mth.lerp(swell * (2.0F - swell), radius, 98.0F);
   }

   public static float blackHoleRadius(float progress) {
      return SupernovaSequence.coreRadius(progress * 340.0F);
   }

   public static float supernovaAftermath(float progress) {
      float t = Mth.clamp((progress - 0.22F) / 0.18F, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   public static void triggerImpact(double x, double y, double z, int type, int durationTicks, float intensity) {
      if (type == 8) {
         CustomImpactFrameOverlay.trigger();
      } else {
         if (type == 7) {
            float progress = 1.0F - (float)currentTick / Math.max(1, maxTicks);
            boolean sameCore = active && blastCenter.distanceToSqr(x, y, z) < 0.01;
            recallCoreStart = !sameCore
               ? 0.0F
               : (fxType == 0 ? singularityRadius(progress) : (fxType == 7 ? singularityRecallRadius(recallCoreStart, progress) : 0.0F));
         }

         blastCenter = new Vec3(x, y, z);
         fxType = type;
         maxTicks = Math.max(1, durationTicks);
         currentTick = maxTicks;
         waitingForBurstTicks = 0;
         baseIntensity = Math.max(0.1F, intensity);
         active = true;
      }
   }

   public static void onClientTick() {
      if (active) {
         currentTick--;
         if (currentTick <= 0) {
            if (fxType == 4 && ++waitingForBurstTicks <= 40) {
               currentTick = 0;
            } else {
               active = false;
            }
         }
      }
   }

   public static void reset() {
      pendingFrame = null;
      reloadShaders();
      active = false;
      waitingForBurstTicks = 0;
   }

   public static void reloadShaders() {
      if (postProcessor != null) {
         postProcessor.close();
         postProcessor = null;
      }

      if (novaProcessor != null) {
         novaProcessor.close();
         novaProcessor = null;
      }
   }

   public static int bloomDimension(int fullSize) {
      int divisor = Math.max(1, GodWandConfig.CLIENT.bloomDownsample);
      return Math.max(1, (fullSize + divisor - 1) / divisor);
   }

   /** After the world, each frame (GodWandClient). */
   public static void render(Matrix4f view, Matrix4f projection, Camera camera, float tickDelta) {
      if (GodWandConfig.CLIENT.postEffects && (active || SupernovaAccretionRenderer.skyFrame(0.0F) != null)) {
         renderFrame(new GodWandPostFxManager.Frame(view, projection, tickDelta), camera);
      }
   }

   private static void renderFrame(GodWandPostFxManager.Frame context, Camera camera) {
      SupernovaAccretionRenderer.SkyFrame sky = SupernovaAccretionRenderer.skyFrame(0.0F);
      if (active || sky != null) {
         float tickDelta = context.tickDelta();
         sky = SupernovaAccretionRenderer.skyFrame(tickDelta);
         boolean skyOnly = sky != null && (!active || fxType != 3 && fxType != 4);
         Vec3 renderCenter = skyOnly ? sky.center() : blastCenter;
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.getMainRenderTarget() != null) {
            int fbWidth = client.getMainRenderTarget().width;
            int fbHeight = client.getMainRenderTarget().height;
            if (fbWidth > 0 && fbHeight > 0) {
               boolean cosmic = skyOnly || active && (fxType == 3 || fxType == 4);
               GodWandPostChain processor = cosmic ? novaProcessor : postProcessor;
               if (processor == null) {
                  try {
                     processor = new GodWandPostChain(cosmic ? "godwand_nova" : "godwand_impact");
                     processor.resize(fbWidth, fbHeight);
                     if (cosmic) {
                        novaProcessor = processor;
                     } else {
                        postProcessor = processor;
                     }
                  } catch (Exception e) {
                     if (processor != null) {
                        processor.close();
                     }

                     if (cosmic) {
                        novaProcessor = null;
                     } else {
                        postProcessor = null;
                     }

                     com.mojang.logging.LogUtils.getLogger().error("Failed to initialize God Wand PostEffectProcessor", e);
                     active = false;
                     return;
                  }
               }

               float progress = Mth.clamp(1.0F - (currentTick - tickDelta) / Math.max(1, maxTicks), 0.0F, 1.0F);
               float impactIntensity = active && !skyOnly ? baseIntensity * (1.0F - progress * 0.75F) : 0.0F;
               float impactFrame = 0.0F;
               float impactMode = 0.0F;
               float shockwaveRadius = 0.0F;
               float distortionPower = 0.0F;
               float chromaticAmount = 0.0F;
               float zoomBlur = 0.0F;
               float retinaBurn = 0.0F;
               float coreRadius = 0.0F;
               float lensingPower = 0.0F;
               float gTime = (float)(System.currentTimeMillis() % 10000000L / 1000.0);
               int tickIdx = Math.max(0, maxTicks - currentTick);
               if (active && !skyOnly) {
                  switch (fxType) {
                     case 0:
                        if (tickIdx < 12) {
                           impactFrame = 1.0F - tickIdx / 12.0F;
                           int[] detModes = new int[]{3, 3, 3, 2, 2, 2, 0, 0, 0, 33, 33, 33};
                           impactMode = detModes[tickIdx];
                        }

                        if (tickIdx < 22) {
                           float shockProg = tickIdx / 22.0F;
                           shockwaveRadius = (float)Math.pow(shockProg, 0.65) * 1.55F;
                           float distCurve = (float)Math.sin(shockProg * Math.PI);
                           distortionPower = 0.052F * baseIntensity * distCurve;
                           chromaticAmount = 0.028F * baseIntensity * (1.0F - shockProg);
                           zoomBlur = 0.07F * baseIntensity * (1.0F - shockProg);
                           retinaBurn = Math.max(0.0F, 0.75F * baseIntensity * (1.0F - shockProg * 1.3F));
                        } else {
                           shockwaveRadius = 0.0F;
                           distortionPower = 0.0F;
                           zoomBlur = 0.0F;
                        }

                        coreRadius = singularityRadius(progress);
                        lensingPower = 0.12F;
                        break;
                     case 1:
                        if (progress < 0.3F) {
                           float suckCurve = progress / 0.3F;
                           distortionPower = -0.042F * baseIntensity * suckCurve;
                           shockwaveRadius = 0.7F * (1.0F - suckCurve);
                           zoomBlur = -0.052F * baseIntensity * suckCurve;
                           impactMode = 4.0F;
                           coreRadius = suckCurve * 1.5F;
                           lensingPower = 1.6F * suckCurve;
                        } else {
                           float slamProg = (progress - 0.3F) / 0.7F;
                           if (slamProg < 0.3F) {
                              impactFrame = 1.0F - slamProg / 0.3F;
                              impactMode = tickIdx % 2 == 0 ? 3.0F : 2.0F;
                           }

                           shockwaveRadius = slamProg * 1.45F;
                           distortionPower = 0.052F * baseIntensity * (1.0F - slamProg);
                           zoomBlur = 0.048F * baseIntensity * (1.0F - slamProg);
                           retinaBurn = Math.max(0.0F, 0.45F * baseIntensity * (1.0F - slamProg * 1.5F));
                           coreRadius = Math.max(0.0F, (1.0F - slamProg) * 1.5F);
                           lensingPower = Math.max(0.0F, 2.2F * (1.0F - slamProg));
                        }

                        chromaticAmount = 0.03F * baseIntensity * (1.0F - progress);
                        break;
                     case 2:
                        impactFrame = 0.0F;
                        shockwaveRadius = progress * 1.1F;
                        distortionPower = 0.0F;
                        chromaticAmount = 0.0F;
                        zoomBlur = 0.0F;
                        coreRadius = 0.0F;
                        lensingPower = 0.0F;
                        break;
                     case 3:
                        impactFrame = 0.0F;
                        shockwaveRadius = 0.0F;
                        distortionPower = 0.0F;
                        float aftermath = supernovaAftermath(progress * maxTicks / 240.0F);
                        chromaticAmount = 0.006F * baseIntensity * aftermath * (1.0F - progress);
                        zoomBlur = 0.0F;
                        retinaBurn = 0.0F;
                        coreRadius = supernovaRadius(progress * maxTicks / 240.0F);
                        lensingPower = 0.0F;
                        impactIntensity = baseIntensity * (float)Math.pow(1.0F - progress, 1.45);
                        break;
                     case 4:
                        impactIntensity = 1.0F;
                        coreRadius = blackHoleRadius(progress);
                        lensingPower = Mth.lerp(progress, 0.4F, 3.6F) * Mth.clamp(coreRadius / 2.0F, 0.0F, 1.0F);
                        if (progress >= 0.9705882F) {
                           float waveProgress = (progress - 0.9705882F) / 0.029411765F;
                           shockwaveRadius = (float)Math.pow(waveProgress, 0.65) * 1.55F;
                           float waveFade = (float)Math.sin(waveProgress * Math.PI);
                           distortionPower = 0.045F * waveFade;
                           chromaticAmount = 0.02F * waveFade;
                           zoomBlur = 0.032F * waveFade;
                        } else {
                           shockwaveRadius = 0.0F;
                           distortionPower = 0.0F;
                           zoomBlur = 0.0F;
                        }
                        break;
                     case 5:
                        if (tickIdx < 12) {
                           impactFrame = 1.0F - tickIdx / 12.0F;
                           int[] slamModes = new int[]{3, 3, 3, 2, 2, 2, 0, 0, 0, 33, 33, 33};
                           impactMode = slamModes[tickIdx];
                        } else {
                           impactFrame = 0.0F;
                        }

                        shockwaveRadius = (float)Math.pow(progress, 0.45) * 1.6F;
                        distortionPower = 0.0F;
                        chromaticAmount = 0.025F * baseIntensity * (1.0F - progress);
                        zoomBlur = 0.0F;
                        retinaBurn = 0.0F;
                        coreRadius = 0.0F;
                        lensingPower = 0.0F;
                        break;
                     case 6:
                        float grow = Math.min(1.0F, progress / 0.12F);
                        coreRadius = 0.52F * grow * Math.min(1.1F, baseIntensity);
                        lensingPower = 0.0F;
                        distortionPower = 0.0F;
                        zoomBlur = 0.0F;
                        impactIntensity = 1.6F + (float)(Math.sin(gTime * 12.0) * 0.25);
                        break;
                     case 7:
                        if (tickIdx < 4) {
                           impactFrame = 0.22F * (1.0F - tickIdx / 4.0F);
                           impactMode = tickIdx % 2 == 0 ? 33.0F : 0.0F;
                        } else {
                           impactFrame = 0.0F;
                        }

                        float flash = (1.0F - progress) * baseIntensity;
                        retinaBurn = flash * 0.18F;
                        chromaticAmount = flash * 0.006F;
                        zoomBlur = 0.0F;
                        distortionPower = 0.0F;
                        shockwaveRadius = 0.0F;
                        coreRadius = singularityRecallRadius(recallCoreStart, progress);
                        lensingPower = 0.0F;
                  }
               }

               Vec3 camPos = camera.getPosition();
               Vector4f clip = new Vector4f((float)(renderCenter.x - camPos.x), (float)(renderCenter.y - camPos.y), (float)(renderCenter.z - camPos.z), 1.0F);
               clip.mul(context.positionMatrix()).mul(context.projectionMatrix());
               float centerX = clip.w > 0.001F ? clip.x / clip.w * 0.5F + 0.5F : 0.5F;
               float centerY = clip.w > 0.001F ? clip.y / clip.w * 0.5F + 0.5F : 0.5F;
               float aspect = (float)fbWidth / fbHeight;
               Matrix4f invProj = new Matrix4f(context.projectionMatrix()).invert();
               Matrix4f invView = new Matrix4f(context.positionMatrix()).invert();
               processor.setUniform("InvProjMat", invProj);
               processor.setUniform("InvViewMat", invView);
               processor.setUniform("CameraPos", (float)camPos.x, (float)camPos.y, (float)camPos.z);
               processor.setUniform("BlastCenter", (float)renderCenter.x, (float)renderCenter.y, (float)renderCenter.z);
               Vec3 skyCenter = sky != null ? sky.center() : renderCenter;
               processor.setUniform("SkyRiftCenter", (float)skyCenter.x, (float)skyCenter.y, (float)skyCenter.z);
               processor.setUniform("BloomStrength", GodWandConfig.CLIENT.bloomStrength);
               processor.setUniform("BloomBurst", fxType == 3 && clip.w > 0.001F ? Math.max(0.0F, 1.0F - progress * maxTicks / 28.0F) : 0.0F);
               processor.setUniform("ImpactIntensity", impactIntensity);
               processor.setUniform("ImpactFrame", GodWandConfig.CLIENT.impactFrames ? impactFrame : 0.0F);
               processor.setUniform("ImpactMode", impactMode);
               processor.setUniform("ShockwaveRadius", shockwaveRadius);
               processor.setUniform("DistortionPower", distortionPower);
               processor.setUniform("ChromaticAmount", chromaticAmount);
               processor.setUniform("ZoomBlur", zoomBlur);
               processor.setUniform("RetinaBurn", retinaBurn);
               processor.setUniform("CenterX", centerX);
               processor.setUniform("CenterY", centerY);
               processor.setUniform("Aspect", aspect);
               processor.setUniform("GTime", gTime);
               processor.setUniform("CoreRadius", coreRadius);
               processor.setUniform("LensingPower", lensingPower);
               processor.setUniform("FxType", skyOnly ? 9.0F : (active ? fxType : -1.0F));
               processor.setUniform("CollapseProgress", fxType == 4 ? Mth.clamp((progress * 340.0F - 250.0F) / 90.0F, 0.0F, 1.0F) : 0.0F);
               processor.setUniform("SkyRiftAge", sky != null ? sky.time() : -1.0F);
               processor.setUniform("SkyRiftScale", sky != null ? sky.scale() : 0.0F);
               processor.setUniform("SkyRiftCollapse", sky != null ? sky.collapse() : 0.0F);
               processor.setUniform("SkyBlackout", sky != null && GodWandConfig.CLIENT.skyBlackout ? SupernovaSequence.skyBlackout(sky.time() - 140.0F) : 0.0F);
               processor.setUniform("RiftPulseRadius", sky != null ? sky.pulseRadius() : 0.0F);
               processor.setUniform("RiftPulseStrength", sky != null ? sky.pulseStrength() : 0.0F);
               processor.setUniform("NovaProgress", !skyOnly && fxType == 3 ? progress * maxTicks / 240.0F : 0.0F);
               processor.setUniform("NovaAftermath", !skyOnly && fxType == 3 ? supernovaAftermath(progress * maxTicks / 240.0F) : 0.0F);

               try {
                  processor.resize(fbWidth, fbHeight);
                  processor.process();
               } catch (Exception error) {
                  com.mojang.logging.LogUtils.getLogger().error("God Wand post processing failed", error);
                  reloadShaders();
                  active = false;
               }
            }
         }
      }
   }

   private record Frame(Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {
   }
}

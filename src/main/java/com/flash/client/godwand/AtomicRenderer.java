package com.flash.client.godwand;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.flash.godwand.AtomicFxPayload;
import com.flash.godwand.AtomicSequence;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class AtomicRenderer {
   private static final Map<Integer, AtomicRenderer.State> ACTIVE = new LinkedHashMap<>();
   private static final List<AtomicRenderer.State> AUDIO_TAILS = new ArrayList<>();
   private static final Map<Integer, AtomicRenderer.Scar> SCARS = new LinkedHashMap<>();
   private static final ResourceLocation ENERGY_TEXTURE = new ResourceLocation("flash", "godwand/atomic_energy");
   private static boolean textureReady;
   private static ClientLevel world;
   private static long scarHeightTick = Long.MIN_VALUE;
   private static final Map<Long, Integer> SCAR_HEIGHTS = new HashMap<>();

   public static void receive(AtomicFxPayload payload) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != world) {
         reset();
      }

      world = client.level;
      if (world != null && payload.phase() >= 0 && payload.phase() <= 4) {
         if (Double.isFinite(payload.x()) && Double.isFinite(payload.y()) && Double.isFinite(payload.z())) {
            if (payload.phase() == 0) {
               SCARS.remove(payload.casterId());
            }

            if (payload.phase() == 0 && client.player != null && payload.casterId() == client.player.getId()) {
               AtomicCinematic.onCastStart();
            }

            AtomicRenderer.State state = ACTIVE.get(payload.casterId());
            if (payload.phase() == 3) {
               if (payload.releasedAt() >= 0L && AtomicSequence.castAge(world.getGameTime(), payload.startedAt(), payload.releasedAt()) >= 834.0F) {
                  if (state != null) {
                     AUDIO_TAILS.add(state);
                     ACTIVE.remove(payload.casterId());
                  }
               } else {
                  if (state != null) {
                     stop(state);
                     ACTIVE.remove(payload.casterId());
                  }

                  AUDIO_TAILS.removeIf(tail -> {
                     if (tail.payload.casterId() != payload.casterId()) {
                        return false;
                     }

                     stop(tail);
                     return true;
                  });
               }
            } else {
               if (state != null && payload.phase() == 0 && state.payload.startedAt() != payload.startedAt()) {
                  stop(state);
                  ACTIVE.remove(payload.casterId());
                  state = null;
               }

               boolean newBurst = payload.phase() == 2 && (state == null || state.payload.phase() != 2);
               if (state == null) {
                  if (ACTIVE.size() >= 8) {
                     Iterator<AtomicRenderer.State> first = ACTIVE.values().iterator();
                     stop(first.next());
                     first.remove();
                  }

                  Entity entity = world.getEntity(payload.casterId());
                  state = new AtomicRenderer.State(payload, entity == null ? 0.0F : Mth.wrapDegrees(entity.getYRot()));
                  ACTIVE.put(payload.casterId(), state);
                  if (payload.phase() == 0) {
                     state.charge = sound("atomic_charge", payload, 0.8F);
                  }
               }

               state.payload = payload;
               if ((payload.phase() == 4 || newBurst) && state.blast == null) {
                  state.blast = sound("atomic_blast", payload, 1.0F);
               }

               if (newBurst) {
                  SCARS.put(payload.casterId(), new AtomicRenderer.Scar(new Vec3(payload.x(), payload.y(), payload.z()), payload.releasedAt() - 406L));
                  if (SCARS.size() > 64) {
                     SCARS.remove(SCARS.keySet().iterator().next());
                  }

                  float gain = distanceGain(
                     client.player == null ? 256.0 : client.player.position().distanceTo(new Vec3(payload.x(), payload.y(), payload.z()))
                  );
                  GodWandCinematicEngine.addTrauma(0.94F * gain, 18);
               }
            }
         }
      }
   }

   private static SimpleSoundInstance sound(String name, AtomicFxPayload p, float volume) {
      Minecraft client = Minecraft.getInstance();
      float gain = distanceGain(client.player == null ? 256.0 : client.player.position().distanceTo(new Vec3(p.x(), p.y(), p.z())));
      SimpleSoundInstance sound = new SimpleSoundInstance(
         com.flash.godwand.GodWandSounds.id(name),
         SoundSource.PLAYERS,
         volume * gain,
         1.0F,
         RandomSource.create(),
         false,
         0,
         Attenuation.NONE,
         0.0,
         0.0,
         0.0,
         true
      );
      client.getSoundManager().play(sound);
      return sound;
   }

   public static float distanceGain(double distance) {
      return (float)Mth.clamp(1.0 - Math.max(0.0, distance - 48.0) / 208.0, 0.0, 1.0);
   }

   private static void stop(AtomicRenderer.State state) {
      SoundManager sounds = Minecraft.getInstance().getSoundManager();
      if (state.charge != null) {
         sounds.stop(state.charge);
      }

      if (state.blast != null) {
         sounds.stop(state.blast);
      }

      if (state.heartbeatSound != null) {
         sounds.stop(state.heartbeatSound);
      }
   }

   public static void tick(Minecraft client) {
      if (client.level == world && client.player != null) {
         AUDIO_TAILS.removeIf(
            tail -> (tail.charge == null || !client.getSoundManager().isActive(tail.charge))
               && (tail.blast == null || !client.getSoundManager().isActive(tail.blast))
         );
         SCARS.values().removeIf(s -> world.getGameTime() - s.started >= 1494L);
         Iterator<AtomicRenderer.State> iterator = ACTIVE.values().iterator();

         while (iterator.hasNext()) {
            AtomicRenderer.State state = iterator.next();
            AtomicRenderer.Frame view = frame(state, 0.0F);
            float age = view.age();
            boolean waiting = view.waiting() && state.payload.releasedAt() < 0L;
            long heartbeat = (long)(view.waitTicks() / 28.0F);
            if (waiting && heartbeat != state.lastHeartbeat) {
               state.lastHeartbeat = heartbeat;
               float gain = distanceGain(client.player.position().distanceTo(view.center()));
               state.heartbeatSound = new SimpleSoundInstance(
                  new ResourceLocation("minecraft", "entity.warden.heartbeat"),
                  SoundSource.PLAYERS,
                  gain * 0.3F,
                  0.8F,
                  RandomSource.create(),
                  false,
                  0,
                  Attenuation.NONE,
                  0.0,
                  0.0,
                  0.0,
                  true
               );
               client.getSoundManager().play(state.heartbeatSound);
            }

            if (!waiting && state.heartbeatSound != null) {
               client.getSoundManager().stop(state.heartbeatSound);
               state.heartbeatSound = null;
            }

            if (age >= 834.0F) {
               AUDIO_TAILS.add(state);
               iterator.remove();
            } else if (age < -20.0F || state.payload.phase() == 0 && world.getGameTime() - state.payload.startedAt() > 420L) {
               stop(state);
               iterator.remove();
            }
         }

         AtomicCinematic.tick(client);
      } else {
         if (world != null || !ACTIVE.isEmpty()) {
            reset();
         }

         world = client.player == null ? null : client.level;
      }
   }

   private static AtomicRenderer.Frame frame(AtomicRenderer.State state, float delta) {
      AtomicFxPayload p = state.payload;
      Vec3 center = new Vec3(p.x(), p.y(), p.z());
      if (p.phase() == 0) {
         Entity entity = world.getEntity(p.casterId());
         if (entity != null) {
            center = entity.getPosition(delta);
         }
      }

      double now = (float)world.getGameTime() + delta;
      float age = p.phase() == 0 ? (float)Math.min(Math.max(0.0, now - p.startedAt()), 380.0) : AtomicSequence.castAge(now, p.startedAt(), p.releasedAt());
      float waited = age >= 406.0F ? (float)Math.max(0.0, (p.releasedAt() < 0L ? now : Math.min(now, p.releasedAt())) - p.startedAt() - 406.0) : 0.0F;
      boolean waiting = p.phase() != 0 && now >= p.startedAt() + 406L && (p.releasedAt() < 0L || now < p.releasedAt());
      return new AtomicRenderer.Frame(p.casterId(), p.startedAt(), center, age, state.yaw, waited, waiting);
   }

   public static AtomicRenderer.Frame localFrame(float delta) {
      Minecraft client = Minecraft.getInstance();
      return world != null && world == client.level && client.player != null ? frameFor(client.player.getId(), delta) : null;
   }

   public static AtomicRenderer.Frame frameFor(int casterId, float delta) {
      if (world != null && world == Minecraft.getInstance().level) {
         AtomicRenderer.State state = ACTIVE.get(casterId);
         return state == null ? null : frame(state, delta);
      } else {
         return null;
      }
   }

   public static AtomicRenderer.Frame heldFrame(ItemStack stack, float delta) {
      if (world != null && world == Minecraft.getInstance().level) {
         for (AtomicRenderer.State state : ACTIVE.values()) {
            if (world.getEntity(state.payload.casterId()) instanceof LivingEntity living
               && (living.getMainHandItem() == stack || living.getOffhandItem() == stack)) {
               return frame(state, delta);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static void captureSwordTip(AtomicRenderer.Frame frame, Matrix4f itemPose, float delta) {
      AtomicRenderer.State state = ACTIVE.get(frame.casterId());
      if (state != null && !(frame.age() < 294.0F) && !(frame.age() > 406.0F)) {
         state.pendingTip = itemPose.transformPosition(new Vector3f(0.0F, 3.4375F, 0.0F));
         state.pendingBladeBase = itemPose.transformPosition(new Vector3f(0.0F, 0.35F, 0.0F));
         state.pendingTipTime = (float)world.getGameTime() + delta;
      }
   }

   static void sampleSwordTip(List<AtomicRenderer.TipSample> trail, Vec3 point, double now) {
      trail.removeIf(p -> now - p.time() > 16.0 || now < p.time());
      if (Double.isFinite(point.lengthSqr())) {
         if (!trail.isEmpty()) {
            AtomicRenderer.TipSample last = trail.get(trail.size() - 1);
            if (now - last.time() < 0.2) {
               return;
            }

            if (last.position().distanceToSqr(point) > 16.0) {
               trail.clear();
            }
         }

         if (trail.size() >= 81) {
            trail.remove(0);
         }

         trail.add(new AtomicRenderer.TipSample(point, now));
      }
   }

   private static void swordTrail(VertexConsumer v, Matrix4f m, List<AtomicRenderer.TipSample> trail, Vec3 center, double now) {
      trail.removeIf(p -> now - p.time() > 16.0 || now < p.time());

      for (int i = 1; i < trail.size(); i++) {
         AtomicRenderer.TipSample a = trail.get(i - 1);
         AtomicRenderer.TipSample b = trail.get(i);
         float light = (float)Math.pow(Math.max(0.0, 1.0 - (now - a.time()) / 16.0), 2.0);
         stroke(v, m, a.position().subtract(center), b.position().subtract(center), 0.028F, light);
      }

      if (!trail.isEmpty()) {
         AtomicRenderer.TipSample last = trail.get(trail.size() - 1);
         float light = (float)Math.max(0.0, 1.0 - (now - last.time()) / 6.0);
         shard(v, m, last.position().subtract(center), 0.11F, light);
      }
   }

   public static AtomicRenderer.Frame nearestFrame(float delta) {
      Minecraft client = Minecraft.getInstance();
      if (world != null && world == client.level && client.player != null) {
         AtomicRenderer.Frame own = localFrame(delta);
         if (own != null) {
            return own;
         }

         AtomicRenderer.Frame nearest = null;
         double distance = Double.MAX_VALUE;

         for (AtomicRenderer.State state : ACTIVE.values()) {
            AtomicRenderer.Frame candidate = frame(state, delta);
            double d = candidate.center.distanceToSqr(client.gameRenderer.getMainCamera().getPosition());
            if (d < distance) {
               distance = d;
               nearest = candidate;
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   public static void reset() {
      scarHeightTick = Long.MIN_VALUE;
      SCAR_HEIGHTS.clear();

      for (AtomicRenderer.State state : ACTIVE.values()) {
         stop(state);
      }

      for (AtomicRenderer.State state : AUDIO_TAILS) {
         stop(state);
      }

      AUDIO_TAILS.clear();
      ACTIVE.clear();
      SCARS.clear();
      world = null;
      AtomicCinematic.reset();
      AtomicPostFx.reset();
   }

   public static void render(PoseStack matrices, Camera camera, float delta) {
      if (world != null && world == Minecraft.getInstance().level && (!ACTIVE.isEmpty() || !SCARS.isEmpty())) {
         GodWandGeometry.enableBlend();
         GodWandGeometry.additiveBlend();
         GodWandGeometry.enableDepthTest();
         GodWandGeometry.depthMask(false);
         GodWandGeometry.disableCull();
         ensureEnergyTexture();
         GodWandGeometry.textured();
         GodWandGeometry.setShaderTexture(0, ENERGY_TEXTURE);

         try {
            for (AtomicRenderer.State state : ACTIVE.values()) {
               AtomicRenderer.Frame frame = frame(state, delta);
               if (!(camera.getPosition().distanceToSqr(frame.center) > 102400.0)) {
                  if (state.pendingTip != null) {
                     Matrix4f inverse = new Matrix4f(matrices.last().pose()).invert();
                     Vector3f tip = inverse.transformPosition(state.pendingTip);
                     Vector3f base = inverse.transformPosition(state.pendingBladeBase);
                     Vec3 point = new Vec3(tip.x, tip.y, tip.z).add(camera.getPosition());
                     if (point.distanceToSqr(frame.center) < 64.0) {
                        state.bladeTip = point;
                        state.bladeBase = new Vec3(base.x, base.y, base.z).add(camera.getPosition());
                        state.bladeTime = state.pendingTipTime;
                        if (frame.age() >= 382.0F && frame.age() <= 402.0F) {
                           sampleSwordTip(state.swordTrail, point, state.pendingTipTime);
                        }
                     }

                     state.pendingTip = null;
                  }

                  matrices.pushPose();
                  matrices.translate(frame.center.x - camera.getPosition().x, frame.center.y - camera.getPosition().y, frame.center.z - camera.getPosition().z);

                  try {
                     BufferBuilder mesh = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                     emit(mesh, matrices.last().pose(), frame.age, camera.getPosition().subtract(frame.center));
                     swordTrail(mesh, matrices.last().pose(), state.swordTrail, frame.center, (float)world.getGameTime() + delta);
                     if (state.bladeTip != null && (float)world.getGameTime() + delta - state.bladeTime < 1.0) {
                        bladeConvergence(
                           mesh,
                           matrices.last().pose(),
                           frame.age,
                           state.bladeBase.subtract(frame.center),
                           state.bladeTip.subtract(frame.center),
                           camera.getPosition().subtract(frame.center)
                        );
                     }

                     BufferBuilder.RenderedBuffer built = mesh.end();
                     if (built != null) {
                        GodWandGeometry.draw(built);
                     }
                  } finally {
                     matrices.popPose();
                  }
               }
            }

            for (AtomicRenderer.Scar scar : SCARS.values()
               .stream()
               .filter(s -> s.center.distanceToSqr(camera.getPosition()) < 65536.0)
               .sorted(Comparator.comparingDouble(s -> s.center.distanceToSqr(camera.getPosition())))
               .limit(8L)
               .toList()) {
               matrices.pushPose();
               matrices.translate(scar.center.x - camera.getPosition().x, scar.center.y - camera.getPosition().y, scar.center.z - camera.getPosition().z);

               try {
                  BufferBuilder mesh = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                  scars(mesh, matrices.last().pose(), scar);
                  BufferBuilder.RenderedBuffer built = mesh.end();
                  if (built != null) {
                     GodWandGeometry.draw(built);
                  }
               } finally {
                  matrices.popPose();
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

   public static void emit(VertexConsumer v, Matrix4f m, double age, Vec3 eye) {
      float fade = AtomicSequence.visibility(age);
      if (!(fade <= 0.0F)) {
         if (age < 414.0) {
            if (age > 0.0) {
               float compression = AtomicSequence.compression(age);
               float scale = 1.0F - compression;
               if (scale <= 0.0F) {
                  return;
               }

               Matrix4f local = new Matrix4f(m).scale(scale);
               Vec3 localEye = new Vec3(eye.x / scale, eye.y / scale, eye.z / scale);
               charge(v, local, age, localEye);
               float particleCollapse = AtomicSequence.phase(age, 400.0, 406.0);
               float particleScale = 1.0F - particleCollapse;
               if (particleScale > 0.0F) {
                  Matrix4f particles = new Matrix4f(m).scale(particleScale);
                  Vec3 particleEye = new Vec3(eye.x / particleScale, eye.y / particleScale, eye.z / particleScale);
                  motes(v, particles, age, particleEye);
               }

               float releaseCollapse = AtomicSequence.phase(age, 404.0, 406.0);
               float releaseScale = 1.0F - releaseCollapse;
               if (releaseScale > 0.0F) {
                  release(v, new Matrix4f(m).scale(releaseScale), age);
               }

               for (int i = 0; i < 3; i++) {
                  double start = 396 + i * 2;
                  if (!(age <= start)) {
                     float inward = AtomicSequence.phase(age, start, 406.0);
                     float light = AtomicSequence.phase(age, start, start + 2.0) * (1.0F - AtomicSequence.phase(age, 404.0, 406.0));
                     ring(v, m, 0.08F + i * 2 * (1.0F - inward), (32 + i * 3) * (1.0F - inward), 0.08F, light * 0.8F);
                  }
               }
            }
         } else {
            blast(v, m, age, fade, eye);
         }
      }
   }

   private static void charge(VertexConsumer v, Matrix4f m, double age, Vec3 eye) {
      float q = AtomicSequence.charge(age);
      float compression = AtomicSequence.compression(age);
      float spread = 1.0F;
      float radius = 26.0F;
      float alpha = chargeAlpha(age);
      float kick = AtomicSequence.activation(age);
      float kickRadius = 1.0F + 15.0F * AtomicSequence.phase(age, 6.0, 36.0);
      ring(v, m, 0.12F, kickRadius, 0.12F, kick * alpha * 3.0F);
      ring(v, m, 0.32F, kickRadius * 0.85F, 0.055F, kick * alpha * 2.0F);
      float sourceScale = AtomicSequence.phase(age, 8.0, 36.0);
      float source = AtomicSequence.phase(age, 8.0, 16.0) * (0.65F + q * 0.35F) * (1.0F - AtomicSequence.blackout(age));
      ringProgress(v, m, 0.065F, 2.0F * sourceScale, 0.055F, source, 1.0F);
      ringProgress(v, m, 0.085F, 1.55F * sourceScale, 0.025F, source * 0.65F, 1.0F);

      for (int i = 0; i < 24; i++) {
         double a = i * Math.PI / 12.0;
         stroke(v, m, polar(1.75 * sourceScale, 0.07, a), polar(2.12 * sourceScale, 0.07, a), 0.022F, source * 0.7F);
         shard(v, m, polar(2.0F * sourceScale, 0.09, a), 0.04F * sourceScale, source * 0.6F);
      }

      float ground = AtomicSequence.phase(age, 20.0, 160.0);
      ringProgress(v, m, 0.07F, radius, 0.055F, alpha, ground);
      ringProgress(v, m, 0.1F, radius * 0.76F, 0.035F, alpha * 0.7F, AtomicSequence.phase(age, 48.0, 178.0));
      float traceAlpha = (0.42F + q * 0.55F) * (1.0F - AtomicSequence.blackout(age)) * AtomicSequence.phase(age, 20.0, 38.0);

      for (int i = 0; i < 24; i++) {
         double a = i * Math.PI / 12.0;
         double seed = fract(i * 0.61803398875);
         float trace = AtomicSequence.phase(age, 25.0 + seed * 55.0, 100.0 + seed * 45.0);
         Vec3[] path = new Vec3[]{
            polar(2.0F * spread * sourceScale, 0.08, a),
            polar(6.0F * spread, 0.08, a + 0.07),
            polar(11.0F * spread, 0.08, a + 0.07),
            polar(15.0F * spread, 0.08, a),
            polar(radius, 0.08, a)
         };
         trace(v, m, path, trace, 0.036F, traceAlpha);
         float fork = (float)Mth.clamp((trace - 0.25) / 0.75, 0.0, 1.0);

         for (int side : new int[]{-1, 1}) {
            Vec3 joint = polar(17.0F * spread, 0.08, a + side * 0.14);
            Vec3[] branch = new Vec3[]{path[1], polar(11.0F * spread, 0.08, a + side * 0.09), joint, polar(radius, 0.08, a + side * 0.22)};
            trace(v, m, branch, fork, 0.028F, traceAlpha * 0.8F);

            for (int twig : new int[]{-1, 1}) {
               Vec3[] tips = new Vec3[]{
                  joint, polar(21.0F * spread, 0.08, a + side * 0.17 + twig * 0.035), polar(radius, 0.08, a + side * 0.22 + twig * 0.055)
               };
               trace(v, m, tips, (float)Mth.clamp((trace - 0.62) / 0.38, 0.0, 1.0), 0.018F, traceAlpha * 0.65F);
            }
         }
      }

      fortress(v, m, age, alpha);
      float roofWave = AtomicSequence.junctionWaveRadius(age);
      ring(v, m, 46.1F, roofWave, 0.12F, alpha * AtomicSequence.phase(age, 180.0, 181.0) * (1.0F - AtomicSequence.phase(age, 196.0, 203.0)) * 1.2F);
      if (age >= 170.0 && age < 350.0 && (age - 170.0) % 60.0 < 8.0) {
         ring(v, m, 0.14F, 2.0F + 30.0F * AtomicSequence.phase((age - 170.0) % 60.0, 0.0, 8.0), 0.09F, alpha * 0.85F);
      }

      ring(v, m, 46.0F * spread, (0.2F + 2.0F * AtomicSequence.phase(age, 178.0, 198.0)) * spread, 0.07F, alpha * AtomicSequence.phase(age, 175.0, 182.0));
      float holo = AtomicSequence.holoRadius(age);
      float light = AtomicSequence.phase(age, 290.0, 350.0) * alpha * (1.0F - AtomicSequence.phase(age, 392.0, 396.0));
      ring(v, m, 3.2F, holo, 0.065F, light);

      for (int i = 0; i < 20; i++) {
         float reveal = AtomicSequence.phase(age, 66 + i % 5 * 12, 156 + i % 5 * 12);
         double seed = fract(i * 0.61803398875);
         double angle = i * 2.39996 + age * 0.008;
         Vec3 last = null;

         for (int j = 0; j <= 12; j++) {
            double t = j / 12.0;
            double h = fract(t + age * 0.004 + seed);
            double r = (3.0 + seed * 11.0) * (1.0 - h * 0.48) * spread;
            Vec3 p = polar(r, 0.15 + h * (8.0F + q * 16.0F) * spread, angle + h * 0.8 + Math.sin(h * 7.0 + age * 0.025) * 0.12);
            if (last != null && p.y > last.y) {
               stroke(
                  v,
                  m,
                  AtomicSequence.releaseMote(last, age),
                  AtomicSequence.releaseMote(p, age),
                  (float)((0.1 + 0.7 * q) * Math.sin(Math.PI * h)) * spread,
                  alpha * reveal * 0.065F
               );
            }

            last = p;
         }
      }
   }

   private static void bladeConvergence(VertexConsumer v, Matrix4f m, double age, Vec3 base, Vec3 tip, Vec3 eye) {
      float gather = AtomicSequence.phase(age, 294.0, 344.0) * (1.0F - AtomicSequence.phase(age, 378.0, 384.0));
      if (!(gather <= 0.0F)) {
         for (int i = 0; i < 8; i++) {
            double seed = i / 8.0;
            double packet = fract(age * 0.024 + seed);
            Vec3 target = base.lerp(tip, 0.45 + seed * 0.5);
            Vec3 previous = AtomicSequence.convergencePoint(age, seed, 0.0, target);

            for (int j = 1; j <= 20; j++) {
               double t = j / 20.0;
               Vec3 next = AtomicSequence.convergencePoint(age, seed, t, target);
               float flow = (float)(0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.sin((t - packet) * Math.PI * 6.0), 2.0));
               stroke(v, m, previous, next, 0.025F + (float)Math.sin(t * Math.PI) * 0.025F, gather * flow);
               previous = next;
            }

            mote(v, m, AtomicSequence.convergencePoint(age, seed, packet, target), eye, 0.08F, gather * 1.6F);
            mote(v, m, target, eye, 0.045F, gather);
         }

         mote(v, m, tip, eye, 0.09F, gather * 1.8F);
      }
   }

   private static float chargeAlpha(double age) {
      return (0.12F + AtomicSequence.charge(age) * 0.65F) * (1.0F - AtomicSequence.blackout(age)) * AtomicSequence.phase(age, 0.0, 12.0);
   }

   private static void motes(VertexConsumer v, Matrix4f m, double age, Vec3 eye) {
      float q = AtomicSequence.charge(age);
      float alpha = chargeAlpha(age);
      float spread = 1.0F;
      double moteAge = AtomicSequence.moteClock(age);

      for (int i = 0; i < 1536; i++) {
         double seed = fract(i * 0.61803398875);
         double birth = 20.0 + i * 0.06;
         if (!(age < birth)) {
            double life = (moteAge - birth) / (90.0 + seed * 70.0);
            double rise = fract(life);
            double emitter = seed + Math.floor(life) * 0.754877666;
            float reveal = AtomicSequence.phase(age, birth, birth + 12.0);
            float glow = reveal * AtomicSequence.phase(rise, 0.0, 0.08) * (1.0F - AtomicSequence.phase(rise, 0.72, 1.0));
            Vec3 tip = AtomicSequence.releaseMote(AtomicSequence.risingMote(rise, emitter), age);
            float size = (0.03F + 0.028F * (float)seed) * (1.0F + 0.2F * q) * spread;
            mote(v, m, tip, eye, size, glow * alpha * 2.0F);
            if (i % 12 == 0 && rise >= 0.05) {
               stroke(v, m, AtomicSequence.releaseMote(AtomicSequence.risingMote(rise - 0.05, emitter), age - 1.2), tip, 0.01F * spread, glow * alpha * 0.45F);
            }

            if (i % 48 == 0 && rise < 0.12) {
               Vec3 sourcePoint = AtomicSequence.risingMote(0.0, emitter);
               Matrix4f local = new Matrix4f(m).translate((float)sourcePoint.x, 0.0F, (float)sourcePoint.z);
               ring(v, local, 0.075F, (float)(0.15 + rise * 7.0) * spread, 0.022F, alpha * reveal * (float)(1.0 - rise / 0.12) * 0.55F);
            }
         }
      }

      for (int i = 0; i < 96; i++) {
         double seed = fract(i * 0.61803398875);
         double life = moteAge * (0.005 + seed * 0.005) + seed;
         double rise = fract(life);
         float sparkLight = (float)(Math.sin(rise * Math.PI) * alpha * AtomicSequence.phase(age, 32.0, 100.0) * 0.6);
         Vec3 tip = AtomicSequence.releaseMote(AtomicSequence.risingMote(rise, seed + Math.floor(life) * 0.754877666 + 17.0), age);
         if (i % 2 == 0) {
            stroke(v, m, tip.add(0.0, -0.35 - rise * 0.65, 0.0), tip, 0.012F, sparkLight);
         }

         mote(v, m, tip, eye, 0.022F, sparkLight);
      }
   }

   private static void release(VertexConsumer v, Matrix4f m, double age) {
      float alpha = chargeAlpha(age);
      float release = AtomicSequence.phase(age, 396.0, 398.0) * (1.0F - AtomicSequence.phase(age, 410.0, 414.0));
      if (release > 0.0F) {
         float travel = AtomicSequence.phase(age, 396.0, 404.0);
         ring(v, m, 0.12F, AtomicSequence.releaseRadius(age), 0.16F, release * alpha * 1.3F);
         ring(v, m, 3.2F, 2.3F + 0.4F * travel, 0.09F, release * alpha * 1.5F);
      }
   }

   private static void fortress(VertexConsumer v, Matrix4f m, double age, float alpha) {
      float wave = AtomicSequence.fortressWaveHeight(age);
      float power = alpha * 1.5F;

      for (int i = 0; i < 8; i++) {
         double angle = i * Math.PI / 4.0;
         float grow = AtomicSequence.phase(age, 65 + i * 4, 155 + i * 4);
         if (!(grow <= 0.0F)) {
            Vec3 tower = polar(32.0, 0.0, angle);
            float height = (i % 2 == 0 ? 42 : 36) * grow;
            Vec3[] corners = new Vec3[]{tower.add(-2.3, 0.0, -2.3), tower.add(2.3, 0.0, -2.3), tower.add(2.3, 0.0, 2.3), tower.add(-2.3, 0.0, 2.3)};
            Vec3 peak = tower.add(0.0, height + 8.0F * grow, 0.0);

            for (int side = 0; side < 4; side++) {
               Vec3 a = corners[side];
               Vec3 b = corners[(side + 1) % 4];
               Vec3 topA = a.add(0.0, height, 0.0);
               Vec3 topB = b.add(0.0, height, 0.0);
               quad(v, m, a, b, topB, topA, 6562242, alpha * 0.035F);
               circuitPanel(v, m, a, b, topB, topA, alpha * 0.65F);
               stroke(v, m, a, topA, 0.055F, alpha);
               stroke(v, m, topA, topB, 0.05F, alpha);
               Vec3 roofA = tower.add(a.subtract(tower).scale(0.65)).add(0.0, height + 2.0F * grow, 0.0);
               Vec3 roofB = tower.add(b.subtract(tower).scale(0.65)).add(0.0, height + 2.0F * grow, 0.0);
               quad(v, m, roofA, roofB, peak, peak, 10438382, alpha * 0.055F);
               stroke(v, m, roofA, peak, 0.045F, alpha * 0.85F);
               if (wave >= 0.0F && wave <= height) {
                  fortressBand(v, m, a.add(0.0, wave, 0.0), b.add(0.0, wave, 0.0), age, power);
               } else if (wave >= height + 2.0F * grow && wave < peak.y) {
                  double taper = 0.65 * (peak.y - wave) / (6.0F * grow);
                  fortressBand(
                     v,
                     m,
                     tower.add(a.subtract(tower).scale(taper)).add(0.0, wave, 0.0),
                     tower.add(b.subtract(tower).scale(taper)).add(0.0, wave, 0.0),
                     age,
                     power
                  );
               }

               for (int merlon = 0; merlon < 2; merlon++) {
                  Vec3 left = topA.lerp(topB, 0.08 + merlon * 0.5);
                  Vec3 right = topA.lerp(topB, 0.36 + merlon * 0.5);
                  Vec3 up = new Vec3(0.0, 2.2 * grow, 0.0);
                  stroke(v, m, left, left.add(up), 0.04F, alpha);
                  stroke(v, m, left.add(up), right.add(up), 0.04F, alpha);
                  stroke(v, m, right.add(up), right, 0.04F, alpha);
                  if (wave > height && wave <= height + 2.2F * grow) {
                     fortressBand(v, m, left.add(0.0, wave - height, 0.0), right.add(0.0, wave - height, 0.0), age, power);
                  }
               }
            }

            float wallGrow = AtomicSequence.phase(age, 80 + i * 3, 170 + i * 3);
            Vec3 wallA = polar(26.0, 0.1, angle);
            Vec3 wallB = polar(26.0, 0.1, angle + (Math.PI / 4));
            Vec3 edge = wallB.subtract(wallA);
            float wallHeight = 28.0F * wallGrow;
            if (wave >= 0.0F && wave <= wallHeight) {
               fortressBand(v, m, wallA.add(0.0, wave - 0.1, 0.0), wallB.add(0.0, wave - 0.1, 0.0), age, power);
            }

            quad(v, m, wallA, wallB, wallB.add(0.0, wallHeight, 0.0), wallA.add(0.0, wallHeight, 0.0), 8070383, alpha * wallGrow * 0.025F);

            for (int panel = 0; panel < 2; panel++) {
               Vec3 a = wallA.lerp(wallB, panel * 0.5);
               Vec3 b = wallA.lerp(wallB, (panel + 1) * 0.5);
               circuitPanel(v, m, a, b, b.add(0.0, wallHeight, 0.0), a.add(0.0, wallHeight, 0.0), alpha * wallGrow * 0.75F);
            }

            stroke(v, m, wallA.add(0.0, wallHeight, 0.0), wallB.add(0.0, wallHeight, 0.0), 0.045F, alpha * wallGrow);

            for (int tooth = 0; tooth < 3; tooth++) {
               Vec3 left = wallA.lerp(wallB, 0.04 + tooth / 3.0).add(0.0, wallHeight, 0.0);
               Vec3 right = wallA.lerp(wallB, 0.22 + tooth / 3.0).add(0.0, wallHeight, 0.0);
               Vec3 up = new Vec3(0.0, 1.7 * wallGrow, 0.0);
               stroke(v, m, left, left.add(up), 0.035F, alpha * wallGrow);
               stroke(v, m, left.add(up), right.add(up), 0.035F, alpha * wallGrow);
               stroke(v, m, right.add(up), right, 0.035F, alpha * wallGrow);
               if (wave > wallHeight && wave <= wallHeight + 1.7F * wallGrow) {
                  fortressBand(v, m, left.add(0.0, wave - wallHeight - 0.1, 0.0), right.add(0.0, wave - wallHeight - 0.1, 0.0), age, power);
               }
            }

            for (int window = 0; window < 2; window++) {
               Vec3 center = wallA.lerp(wallB, 0.28 + window * 0.44);
               Vec3 across = edge.normalize().scale(1.2);
               Vec3[] arch = new Vec3[]{
                  center.subtract(across).add(0.0, 5.0F * wallGrow, 0.0),
                  center.subtract(across).add(0.0, 19.0F * wallGrow, 0.0),
                  center.add(0.0, 23.0F * wallGrow, 0.0),
                  center.add(across).add(0.0, 19.0F * wallGrow, 0.0),
                  center.add(across).add(0.0, 5.0F * wallGrow, 0.0)
               };
               trace(v, m, arch, wallGrow, 0.035F, alpha * wallGrow * 0.75F);
            }

            stroke(v, m, polar(26.0, 14.0F * grow, angle), tower.add(0.0, 22.0F * grow, 0.0), 0.05F, alpha * grow * 0.8F);
            Vec3[] circuit = new Vec3[]{peak, polar(24.0, 46.0, angle), polar(12.0, 46.0, angle + (Math.PI / 8)), new Vec3(0.0, 46.0, 0.0)};
            float roofLight = (0.85F + 0.15F * AtomicSequence.charge(age)) * (1.0F - AtomicSequence.blackout(age));
            trace(v, m, circuit, AtomicSequence.circuitProgress(age, i / 8.0), 0.14F, roofLight);
         }
      }
   }

   private static void fortressBand(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double age, float alpha) {
      Vec3 middle = a.lerp(b, 0.5).add(0.0, Math.sin(age * 1.7 + a.x * 0.4 + a.z * 0.7) * 0.12, 0.0);
      stroke(v, m, a, middle, 0.065F, alpha);
      stroke(v, m, middle, b, 0.065F, alpha);
   }

   private static void trace(VertexConsumer v, Matrix4f m, Vec3[] path, float progress, float width, float alpha) {
      for (int j = 1; j < path.length; j++) {
         double part = Mth.clamp(progress * (path.length - 1) - (j - 1), 0.0F, 1.0F);
         if (part <= 0.0) {
            break;
         }

         Vec3 end = path[j - 1].lerp(path[j], part);
         stroke(v, m, path[j - 1], end, width, alpha);
         if (part < 1.0) {
            shard(v, m, end, width * 3.0F, alpha);
            break;
         }
      }
   }

   private static void blast(VertexConsumer v, Matrix4f m, double age, float fade, Vec3 eye) {
      double t = age - 414.0;
      fade *= GodWandConfig.CLIENT.impactFrames ? AtomicSequence.blastPulse(age) : 1.0F;
      float radius = AtomicSequence.beamRadius(age);
      float height = AtomicSequence.beamHeight(age);
      if (radius > 0.001F) {
         cylinder(v, m, radius * 0.78F, height, 0.7F * fade, 16113663, age);
         cylinder(v, m, radius, height, 0.22F * fade, 9645055, age + 10.0);

         for (int i = 0; i < 40; i++) {
            double a = i * Math.PI / 20.0 + 0.004 * age;
            Vec3 last = polar(radius * (0.88 + 0.06 * Math.sin(i)), -30.0, a);

            for (int j = 1; j <= 5; j++) {
               double y = -30.0F + j * (height + 30.0F) / 5.0F;
               double jitter = Math.sin(i * 18.23 + j * 8.3 + age * 0.11) * 0.035;
               Vec3 next = polar(radius * (0.93 + jitter), y, a + jitter);
               stroke(v, m, last, next, 0.045F + i % 4 * 0.016F, fade * (i % 3 == 0 ? 0.85F : 0.35F));
               last = next;
            }
         }
      }

      for (int i = 0; i < 3; i++) {
         float r = AtomicSequence.shockRadius(age - i * 7);
         if (r > 0.01) {
            float opacity = fade * (1.0F - 0.22F * i) * (1.0F - AtomicSequence.phase(age, 614.0, 704.0));
            ring(v, m, 0.2F + i * 0.18F, r, 0.18F + i * 0.05F, opacity);
            ring(v, m, 6 + i * 8, r * 0.94F, 0.12F, opacity * 0.45F);
         }
      }

      float pressure = AtomicSequence.pressure(age);

      for (int i = 0; i < 4; i++) {
         float waveRadius = AtomicSequence.pressureWaveRadius(age, i);
         float cycle = (waveRadius - radius) / 32.0F;
         ring(v, m, 0.2F + i * 0.1F, waveRadius, 0.25F, fade * pressure * (1.0F - cycle) * 0.7F);
         ring(v, m, height * cycle, radius * (1.0F + 0.06F * cycle), 0.18F, fade * pressure * (1.0F - cycle) * 0.45F);
      }

      float wave = AtomicSequence.shockRadius(age);

      for (int i = 0; i < 48; i++) {
         double a = i * Math.PI / 24.0;
         double r = wave * (0.82 + 0.1 * Math.sin(i * 18.7));
         stroke(
            v,
            m,
            polar(r, 0.15, a),
            polar(Math.min(144.0, r + 3.0 + wave * 0.13), 0.18, a + 0.012),
            0.035F,
            fade * 0.65F * (1.0F - AtomicSequence.phase(age, 614.0, 704.0))
         );
      }

      float dust = AtomicSequence.phase(age, 420.0, 444.0) * (1.0F - AtomicSequence.phase(age, 538.0, 574.0));
      if (dust > 0.0F) {
         for (int i = 0; i < 72; i++) {
            double a = i * Math.PI / 36.0;
            double seed = fract(i * 0.61803398875);
            double r = wave + 2.0F + seed * 7.0;
            double h = 2.0 + seed * 9.0;
            if (!AtomicSequence.columnOccludes(eye, polar(r, h * 0.5, a), age)) {
               for (int j = 0; j < 4; j++) {
                  double curl = age * 0.05 + seed * 6.0 + j * 0.7;
                  Vec3 lo = polar(r + Math.sin(curl) * (1 + j), 0.2 + j * h / 4.0, a);
                  Vec3 hi = polar(r + Math.sin(curl + 0.7) * (2 + j), 0.2 + (j + 1) * h / 4.0, a + 0.014);
                  Vec3 side = polar(1.2 + seed * 2.0, 0.0, a + (Math.PI / 2));
                  quad(v, m, lo.subtract(side), lo.add(side), hi.add(side), hi.subtract(side), 10388664, dust * fade * 0.07F);
               }
            }
         }
      }

      if (!AtomicPostFx.available()) {
         pressureAura(v, m, age, fade, eye);
      }

      for (int i = 0; i < 144; i++) {
         double seed = fract(i * 0.61803398875);
         double life = t - i % 24;
         if (!(life < 0.0) && !(life > 90.0)) {
            double a = i * 2.39996;
            double r = 4.0 + seed * 48.0 + life * 0.3;
            double y = 1.0 + life * (0.7 + seed * 0.8) - life * life * 0.005;
            shard(v, m, polar(r, Math.max(0.1, y), a), 0.06F + 0.12F * (float)seed, fade * (float)Math.sin(Math.PI * life / 90.0));
         }
      }
   }

   private static void pressureAura(VertexConsumer v, Matrix4f m, double age, float fade, Vec3 eye) {
      if (!(age < 414.0) && !(age >= 734.0)) {
         fade *= 1.0F - AtomicSequence.phase(age, 574.0, 734.0);
         double elapsed = age - 414.0;

         for (int plume = 0; plume < 192; plume++) {
            double clock = elapsed - plume * 0.1875;
            if (!(clock <= 0.0)) {
               double cycle = Math.floor(clock / 36.0);
               double life = clock - cycle * 36.0;
               double seed = fract(plume * 0.61803398875 + cycle * 0.173);
               double angle = plume * 2.39996;
               double size = 5.0 + seed * 6.0;
               Vec3 center = polar(AtomicSequence.beamRadius(age) + life * 6.0, size + 2.0 + seed * 16.0, angle);
               if (!AtomicSequence.columnOccludes(eye, center, age)) {
                  float alpha = fade * AtomicSequence.phase(life, 0.0, 3.0) * (1.0F - AtomicSequence.phase(life, 28.0, 36.0));
                  Vec3 normal = eye.subtract(center).normalize();
                  Vec3 right = new Vec3(normal.z, 0.0, -normal.x);
                  if (right.lengthSqr() < 1.0E-4) {
                     right = new Vec3(1.0, 0.0, 0.0);
                  }

                  right = right.normalize();
                  Vec3 up = normal.cross(right).normalize();
                  double turn = seed * 6.0 + life * 0.06;
                  Vec3 x = right.scale(Math.cos(turn)).add(up.scale(Math.sin(turn))).scale(size);
                  Vec3 y = up.scale(Math.cos(turn)).subtract(right.scale(Math.sin(turn))).scale(size * 0.85);
                  disc(
                     v, m, center.subtract(x).subtract(y), center.add(x).subtract(y), center.add(x).add(y), center.subtract(x).add(y), 7480024, alpha * 0.085F
                  );
                  x = x.scale(0.7);
                  y = y.scale(0.7);
                  disc(
                     v, m, center.subtract(x).subtract(y), center.add(x).subtract(y), center.add(x).add(y), center.subtract(x).add(y), 11358962, alpha * 0.055F
                  );
               }
            }
         }
      }
   }

   static int scarHeight(int x, int z) {
      long tick = world.getGameTime();
      if (tick != scarHeightTick) {
         SCAR_HEIGHTS.clear();
         scarHeightTick = tick;
      }

      long key = x & 4294967295L | (long)z << 32;
      Integer height = SCAR_HEIGHTS.get(key);
      if (height == null) {
         height = world.getHeight(Types.MOTION_BLOCKING, x, z);
         SCAR_HEIGHTS.put(key, height);
      }

      return height;
   }

   private static void scars(VertexConsumer v, Matrix4f m, AtomicRenderer.Scar scar) {
      float alpha = AtomicSequence.scarOpacity(world.getGameTime() - scar.started);

      for (int ray = 0; ray < 26; ray++) {
         Vec3 previous = null;
         int steps = ray < 24 ? 28 : 128;

         for (int step = 0; step <= steps; step++) {
            double r = ray < 24 ? 3 + step * 95 / steps : (ray == 24 ? 74.48 : 98.0);
            double angle = ray < 24 ? ray * Math.PI / 12.0 : step * Math.PI * 2.0 / steps;
            if (!(r > 98.0) && (ray < 24 || !(Math.sin(angle * 8.0) <= 0.1))) {
               Vec3 p = polar(r, 0.0, angle);
               int x = (int)Math.floor(scar.center.x + p.x);
               int z = (int)Math.floor(scar.center.z + p.z);
               if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                  previous = null;
               } else {
                  double y = scarHeight(x, z) + 0.06 - scar.center.y;
                  Vec3 next = new Vec3(p.x, y, p.z);
                  if (previous != null && Math.abs(next.y - previous.y) < 4.0) {
                     stroke(v, m, previous, next, 0.055F, alpha);
                  }

                  previous = next;
               }
            } else {
               previous = null;
            }
         }
      }
   }

   private static Vec3 polar(double radius, double y, double angle) {
      return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
   }

   private static double fract(double n) {
      return n - Math.floor(n);
   }

   private static void ring(VertexConsumer v, Matrix4f m, float y, float r, float width, float alpha) {
      ringProgress(v, m, y, r, width, alpha, 1.0F);
   }

   private static void ringProgress(VertexConsumer v, Matrix4f m, float y, float r, float width, float alpha, float progress) {
      if (!(r <= 0.0F) && !(alpha <= 0.0F)) {
         int resolution = r < 4.0F ? 32 : (r < 12.0F ? 64 : 96);
         int segments = (int)Math.ceil(resolution * Mth.clamp(progress, 0.0F, 1.0F));

         for (int layer = 2; layer >= 0; layer--) {
            float w = width * (layer == 2 ? 7 : (layer == 1 ? 3 : 1));
            float a = alpha * (layer == 2 ? 0.07F : (layer == 1 ? 0.23F : 0.85F));
            int color = layer == 0 ? 10834175 : 8659199;

            for (int i = 0; i < segments; i++) {
               double p = i * Math.PI * 2.0 / resolution;
               double q = Math.min((double)(i + 1) / resolution, progress) * Math.PI * 2.0;
               quad(v, m, polar(Math.max(0.0F, r - w), y, p), polar(r + w, y, p), polar(r + w, y, q), polar(Math.max(0.0F, r - w), y, q), color, a);
            }
         }
      }
   }

   private static void stroke(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, float width, float alpha) {
      if (!(alpha <= 0.0F)) {
         Vec3 direction = b.subtract(a).normalize();
         Vec3 side = direction.cross(Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)).normalize();
         Vec3 other = direction.cross(side).normalize();

         for (int layer = 2; layer >= 0; layer--) {
            double w = width * (layer == 2 ? 5.0 : (layer == 1 ? 2.4 : 1.0));
            float opacity = alpha * (layer == 2 ? 0.055F : (layer == 1 ? 0.22F : 0.85F));
            int color = layer == 0 ? 10834175 : 8133375;

            for (int axis = 0; axis < 2; axis++) {
               Vec3 n = (axis == 0 ? side : other).scale(w);
               quad(v, m, a.subtract(n), a.add(n), b.add(n), b.subtract(n), color, opacity);
            }
         }
      }
   }

   private static void cylinder(VertexConsumer v, Matrix4f m, float r, float height, float alpha, int color, double age) {
      for (int i = 0; i < 64; i++) {
         double a = i * Math.PI / 32.0;
         double b = (i + 1) * Math.PI / 32.0;

         for (int j = 0; j < 6; j++) {
            double low = -30.0F + (height + 30.0F) * j / 6.0F;
            double high = -30.0F + (height + 30.0F) * (j + 1) / 6.0F;
            double ra = r * (0.94 + 0.06 * Math.sin(i * 2.7 + j * 1.8 + age * 0.13));
            quad(v, m, polar(ra, low, a), polar(ra, low, b), polar(ra, high, b), polar(ra, high, a), color, alpha);
         }
      }
   }

   private static void shard(VertexConsumer v, Matrix4f m, Vec3 p, float size, float alpha) {
      disc(v, m, p.add(-size, -size, 0.0), p.add(size, -size, 0.0), p.add(size, size, 0.0), p.add(-size, size, 0.0), 13003519, alpha);
      disc(v, m, p.add(0.0, -size, -size), p.add(0.0, -size, size), p.add(0.0, size, size), p.add(0.0, size, -size), 15781375, alpha);
   }

   private static void mote(VertexConsumer v, Matrix4f m, Vec3 p, Vec3 eye, float size, float alpha) {
      if (!(size <= 0.0F) && !(alpha <= 0.0F)) {
         Vec3 facing = eye.subtract(p).normalize();
         if (facing.lengthSqr() < 1.0E-8) {
            facing = new Vec3(0.0, 0.0, 1.0);
         }

         Vec3 right = facing.cross(Math.abs(facing.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)).normalize();
         Vec3 up = right.cross(facing).normalize();

         for (int layer = 2; layer >= 1; layer--) {
            double radius = size * (layer == 2 ? 3.2 : (layer == 1 ? 1.3 : 0.4));
            Vec3 x = right.scale(radius);
            Vec3 y = up.scale(radius);
            disc(
               v,
               m,
               p.subtract(x).subtract(y),
               p.add(x).subtract(y),
               p.add(x).add(y),
               p.subtract(x).add(y),
               layer == 2 ? 10433279 : (layer == 1 ? 13000959 : 15781375),
               alpha * (layer == 2 ? 0.13F : (layer == 1 ? 0.65F : 0.95F))
            );
         }
      }
   }

   public static int energyAlpha(int x, int y) {
      double r = Math.hypot((x + 0.5 - 16.0) / 16.0, (y + 0.5 - 16.0) / 16.0);
      return r >= 1.0 ? 0 : (int)Math.round(255.0 * Math.exp(-r * r * 3.0) * Mth.clamp((1.0 - r) * 8.0, 0.0, 1.0));
   }

   public static int circuitAlpha(int x, int y) {
      if (x >= 3 && x <= 60 && y >= 2 && y <= 125) {
         double light = 0.0;

         for (int lane = 0; lane < 6; lane++) {
            double bend = 22 + lane * 5;
            double direction = lane % 2 == 0 ? 1.0 : -1.0;
            double path = 6 + lane * 10 + direction * 3.0 * (Mth.clamp((y - bend) / 3.0, 0.0, 1.0) - Mth.clamp((y - (84 - lane * 3)) / 3.0, 0.0, 1.0));
            double d = x - path;
            light = Math.max(light, 220.0 * Math.exp(-d * d / 0.4) + 35.0 * Math.exp(-d * d / 2.5));
            double node = Math.hypot(d, y - bend - 3.0);
            light = Math.max(light, 220.0 * Math.max(0.0, 1.0 - node / 2.4));
         }

         return Mth.clamp((int)Math.round(light), 0, 255);
      } else {
         return 0;
      }
   }

   private static void circuitPanel(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
      int opacity = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
      energyVertex(v, m, a, 10834175, opacity, 0.5136719F, 0.98046875F);
      energyVertex(v, m, b, 10834175, opacity, 0.7363281F, 0.98046875F);
      energyVertex(v, m, c, 10834175, opacity, 0.7363281F, 0.01953125F);
      energyVertex(v, m, d, 10834175, opacity, 0.5136719F, 0.01953125F);
   }

   private static void ensureEnergyTexture() {
      if (!textureReady) {
         NativeImage pixels = new NativeImage(256, 128, false);

         for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 256; x++) {
               pixels.setPixelRGBA(x, y, (x < 128 ? energyAlpha(x / 4, y / 4) : (x >= 192 ? 255 : circuitAlpha(x - 128, y))) << 24 | 16777215);
            }
         }

         AtomicRenderer.EnergyTexture texture = new AtomicRenderer.EnergyTexture(pixels);
         Minecraft.getInstance().getTextureManager().register(ENERGY_TEXTURE, texture);
         textureReady = true;
      }
   }

   private static void disc(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
      int opacity = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
      energyVertex(v, m, a, color, opacity, 0.0078125F, 0.984375F);
      energyVertex(v, m, b, color, opacity, 0.4921875F, 0.984375F);
      energyVertex(v, m, c, color, opacity, 0.4921875F, 0.015625F);
      energyVertex(v, m, d, color, opacity, 0.0078125F, 0.015625F);
   }

   private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
      int opacity = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
      energyVertex(v, m, a, color, opacity, 0.875F, 0.5F);
      energyVertex(v, m, b, color, opacity, 0.875F, 0.5F);
      energyVertex(v, m, c, color, opacity, 0.875F, 0.5F);
      energyVertex(v, m, d, color, opacity, 0.875F, 0.5F);
   }

   private static void energyVertex(VertexConsumer v, Matrix4f m, Vec3 p, int color, int opacity, float u, float texV) {
      v.vertex(m, (float)p.x, (float)p.y, (float)p.z).uv(u, texV).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, opacity).endVertex();
   }

   private static final class EnergyTexture extends DynamicTexture {
      EnergyTexture(NativeImage pixels) {
         super(pixels);
         this.setFilter(true, false);
      }
   }

   public record Frame(int casterId, long startedAt, Vec3 center, float age, float yaw, float waitTicks, boolean waiting) {
   }

   private record Scar(Vec3 center, long started) {
   }

   private static final class State {
      AtomicFxPayload payload;
      final List<AtomicRenderer.TipSample> swordTrail = new ArrayList<>();
      Vector3f pendingTip;
      Vector3f pendingBladeBase;
      Vec3 bladeTip;
      Vec3 bladeBase;
      double bladeTime;
      double pendingTipTime;
      final float yaw;
      SimpleSoundInstance charge;
      SimpleSoundInstance blast;
      SimpleSoundInstance heartbeatSound;
      long lastHeartbeat = -1L;

      State(AtomicFxPayload payload, float yaw) {
         this.payload = payload;
         this.yaw = yaw;
      }
   }

   record TipSample(Vec3 position, double time) {
   }
}

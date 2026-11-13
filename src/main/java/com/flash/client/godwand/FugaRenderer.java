package com.flash.client.godwand;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.flash.godwand.FugaFxPayload;
import com.flash.godwand.FugaSequence;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class FugaRenderer {
   private static final Map<Integer, FugaFxPayload> ACTIVE = new LinkedHashMap<>();
   private static final Map<Integer, Long> RECEIVED = new LinkedHashMap<>();
   private static final Map<Integer, Long> PHASE_SEEN = new LinkedHashMap<>();
   private static final Map<Integer, FugaFxPayload> LATEST = new LinkedHashMap<>();
   private static ClientLevel world;
   private static final List<FugaRenderer.Aftermath> AFTERMATH = new ArrayList<>();
   static final int AFTERMATH_TICKS = 900;
   private static final double TAU = Math.PI * 2;
   private static FugaRenderer.Glow glow;
   static final int GLOW_TICKS = 340;
   private static long clientTicks;
   static final long FUTURE_TOLERANCE = 200L;
   private static final Map<Integer, FugaRenderer.Clock> CLOCKS = new LinkedHashMap<>();
   static final double IMPACT_BEATS = 4.0;
   static final double HIT_STOP = 10.0;
   static final double FREEZE_AGE = 30.0;
   private static float renderPartial;
   private static final Map<Integer, Vec3> LAUNCH = new HashMap<>();
   private static int shaderCaster = Integer.MIN_VALUE;
   private static int pass = -1;
   private static final ResourceLocation FLAME_TEXTURE = new ResourceLocation("flash", "godwand/fuga_flame");
   private static boolean textureReady;
   static final float SOLID_U = 0.5F;
   static final float SOLID_V = 0.9375F;
   static final float EDGE_V = 0.0078125F;
   static final float SPINE_V = 0.4375F;
   static final float FAR_V = 0.8671875F;
   private static double flow;
   private static FugaRenderer.Deferred deferred;
   static final boolean PULL_BALL = true;
   static final int STREAMS = 10;
   private static final double[] PUFF_COS = new double[14];
   private static final double[] PUFF_SIN = new double[14];

   private FugaRenderer() {
   }

   static boolean newer(FugaFxPayload next, FugaFxPayload previous) {
      return previous == null
         || next.startedAt() > previous.startedAt()
         || next.startedAt() == previous.startedAt() && previous.phase() < 3 && next.phase() >= previous.phase() && next.phaseAt() >= previous.phaseAt();
   }

   public static void receive(FugaFxPayload p) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != world) {
         reset();
      }

      world = client.level;
      if (world != null && p.phase() >= 0 && p.phase() <= 4 && finite(p.x(), p.y(), p.z(), p.tx(), p.ty(), p.tz()) && newer(p, LATEST.get(p.casterId()))) {
         LATEST.remove(p.casterId());
         LATEST.put(p.casterId(), p);
         if (LATEST.size() > 64) {
            LATEST.remove(LATEST.keySet().iterator().next());
         }

         if (p.phase() >= 3) {
            ACTIVE.remove(p.casterId());
            RECEIVED.remove(p.casterId());
         } else {
            FugaFxPayload previous = ACTIVE.get(p.casterId());
            if (previous == null || previous.startedAt() != p.startedAt()) {
               RECEIVED.put(p.casterId(), world.getGameTime());
            }

            if (previous == null || previous.startedAt() != p.startedAt() || previous.phase() != p.phase()) {
               PHASE_SEEN.put(p.casterId(), clientTicks);
            }

            if (previous != null && previous.phase() == 1 && p.phase() == 2 && previous.startedAt() == p.startedAt()) {
               LAUNCH.put(p.casterId(), new Vec3(previous.x(), previous.y(), previous.z()));
            }

            ACTIVE.put(p.casterId(), p);
            if (ACTIVE.size() > 16) {
               ACTIVE.remove(ACTIVE.keySet().iterator().next());
            }

            RECEIVED.keySet().retainAll(ACTIVE.keySet());
            PHASE_SEEN.keySet().retainAll(ACTIVE.keySet());
         }
      }
   }

   public static void reset() {
      FugaSoundscape.reset();
      CLOCKS.clear();
      LAUNCH.clear();
      ACTIVE.clear();
      RECEIVED.clear();
      PHASE_SEEN.clear();
      LATEST.clear();
      AFTERMATH.clear();
      glow = null;
      world = null;
   }

   public static float aftermath(Vec3 eye) {
      if (world != null && eye != null) {
         float best = 0.0F;

         for (FugaRenderer.Aftermath a : AFTERMATH) {
            double t = (world.getGameTime() - a.at()) / 900.0;
            if (!(t < 0.0) && !(t >= 1.0)) {
               double near = 1.0 - smooth((eye.distanceTo(a.center()) - 220.0) / 260.0);
               best = Math.max(best, (float)(smooth(t * 40.0) * (1.0 - smooth(t)) * near));
            }
         }

         return best;
      } else {
         return 0.0F;
      }
   }

   public static double phaseSeen(int casterId, float partial) {
      Long seen = PHASE_SEEN.get(casterId);
      return world != null && seen != null ? (float)clientTicks + partial - (float)seen.longValue() : Double.MAX_VALUE;
   }

   public static void tick(Minecraft client) {
      clientTicks++;
      if (client.level == world && client.player != null) {
         ACTIVE.values()
            .removeIf(
               px -> {
                  Entity caster = world.getEntity(px.casterId());
                  long age = world.getGameTime() - px.phaseAt();
                  return caster != null && !caster.isAlive()
                     || age < -200L
                     || px.phase() == 0
                        && world.getGameTime() - RECEIVED.getOrDefault(px.casterId(), 0L) > 20L
                        && !(
                           caster instanceof LivingEntity livingx && GodWandClient.charging(livingx, 6)
                        )
                     || px.phase() == 1 && age > 80L
                     || px.phase() == 2 && age >= 305L;
               }
            );
         RECEIVED.keySet().retainAll(ACTIVE.keySet());
         CLOCKS.keySet().retainAll(ACTIVE.keySet());
         AtomicPostFx.prewarm();
         FugaSoundscape.tick(client, world, ACTIVE);
         long now = world.getGameTime();

         for (FugaFxPayload p : ACTIVE.values()) {
            if (p.phase() <= 1 && world.getEntity(p.casterId()) instanceof LivingEntity living) {
               living.yBodyRot = living.yBodyRotO = living.getYHeadRot();
            }

            if (p.phase() == 2 && p.casterId() == client.player.getId()) {
               glow = new FugaRenderer.Glow(p.casterId(), p.startedAt(), p.phaseAt(), new Vec3(p.x(), p.y(), p.z()));
            }
         }

         AFTERMATH.removeIf(a -> now - a.at() >= 900L);

         for (FugaFxPayload p : ACTIVE.values()) {
            if (p.phase() == 2 && AFTERMATH.stream().noneMatch(a -> a.cast() == p.startedAt())) {
               AFTERMATH.add(new FugaRenderer.Aftermath(new Vec3(p.x(), p.y(), p.z()), now, p.startedAt()));
               if (AFTERMATH.size() > 8) {
                  AFTERMATH.remove(0);
               }
            }
         }

         if (client.options != null && client.options.particles().get() != ParticleStatus.MINIMAL) {
            particles(client);
         }
      } else {
         reset();
      }
   }

   private static void particles(Minecraft client) {
      RandomSource random = world.getRandom();
      Vec3 eye = client.player.getEyePosition();

      for (int caster : ACTIVE.keySet()) {
         FugaRenderer.Frame f = frameFor(caster, 0.0F);
         if (f != null && (!(f.origin().distanceToSqr(eye) > 9216.0) || f.phase() == 1)) {
            Vec3 o = f.origin();
            if (f.phase() == 1) {
               Vec3 head = f.arrowPosition();
               Vec3 d = f.target().subtract(o).normalize();

               for (int i = 0; i < 14; i++) {
                  Vec3 p = head.subtract(d.scale(random.nextDouble() * 10.0))
                     .add(random.nextGaussian() * 0.9, random.nextGaussian() * 0.9, random.nextGaussian() * 0.9);
                  world.addAlwaysVisibleParticle(
                     i % 4 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                     true,
                     p.x,
                     p.y,
                     p.z,
                     random.nextGaussian() * 0.15,
                     0.05 + random.nextDouble() * 0.15,
                     random.nextGaussian() * 0.15
                  );
               }
            }
         }
      }

      for (FugaRenderer.Aftermath a : AFTERMATH) {
         double t = (world.getGameTime() - a.at()) / 900.0;
         if (!(t < 0.0) && !(t >= 1.0) && world.getGameTime() - a.at() >= 80L && !(a.center().distanceToSqr(eye) > 160000.0)) {
            double strength = 1.0 - t;

            for (int i = 0; i < 1.0 + 3.0 * strength; i++) {
               double ang = random.nextDouble() * (Math.PI * 2);
               double r = Math.sqrt(random.nextDouble()) * 48.0 * 1.2;
               Vec3 p = a.center().add(Math.cos(ang) * r, random.nextDouble() * 2.0, Math.sin(ang) * r);
               world.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, p.x, p.y, p.z, 0.0, 0.06 + 0.08 * strength, 0.0);
               if (random.nextFloat() < strength) {
                  world.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, p.x, p.y, p.z, 0.0, 0.0, 0.0);
               }

               if (random.nextFloat() < 0.7 * strength) {
                  world.addAlwaysVisibleParticle(
                     ParticleTypes.FLAME,
                     true,
                     p.x,
                     p.y + 0.2,
                     p.z,
                     random.nextGaussian() * 0.02,
                     0.04 + random.nextDouble() * 0.1,
                     random.nextGaussian() * 0.02
                  );
               }
            }
         }
      }
   }

   public static FugaRenderer.Frame frameFor(int casterId, float partial) {
      if (world != null && world == Minecraft.getInstance().level) {
         FugaFxPayload p = ACTIVE.get(casterId);
         if (p == null) {
            return null;
         }

         Vec3 origin = new Vec3(p.x(), p.y(), p.z());
         Vec3 target = new Vec3(p.tx(), p.ty(), p.tz());
         if (p.phase() == 0) {
            Entity caster = world.getEntity(casterId);
            if (caster != null) {
               origin = caster.getPosition(partial);
               target = origin.add(caster.getViewVector(partial).scale(160.0));
            }
         }

         if (p.phase() == 2 && phaseSeen(casterId, partial) < 10.0) {
            Vec3 launch = LAUNCH.get(casterId);
            if (launch == null) {
               Vec3 dir = target.subtract(origin);
               dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
               launch = origin.subtract(dir.scale(FugaSequence.flightDistance(30.0)));
            }

            return new FugaRenderer.Frame(casterId, p.startedAt(), 1, flightTime(launch.distanceTo(origin)), launch, origin);
         } else {
            return new FugaRenderer.Frame(
               casterId,
               p.startedAt(),
               p.phase(),
               Math.max(0.0, steadyAge(casterId, p, (float)world.getGameTime() + partial - (float)p.phaseAt(), partial)),
               origin,
               target
            );
         }
      } else {
         return null;
      }
   }

   static double steadyAge(int casterId, FugaFxPayload p, double gameAge, float partial) {
      double now = (float)clientTicks + partial;
      FugaRenderer.Clock c = CLOCKS.get(casterId);
      double age = c != null && c.token() == p.startedAt() && c.phase() == p.phase() ? Math.max(gameAge, c.age() + now - c.at()) : gameAge;
      CLOCKS.put(casterId, new FugaRenderer.Clock(p.startedAt(), p.phase(), age, now));
      return age;
   }

   static double hitStop(int casterId) {
      return hitStop(casterId, renderPartial);
   }

   static double hitStop(int casterId, float partial) {
      FugaFxPayload p = ACTIVE.get(casterId);
      if (p != null && p.phase() == 2) {
         double seen = phaseSeen(casterId, partial);
         return seen < 10.0 ? seen / 10.0 : -1.0;
      } else {
         return -1.0;
      }
   }

   public static float impactBeat(int casterId, float partial) {
      FugaFxPayload p = ACTIVE.get(casterId);
      if (p == null) {
         return 0.0F;
      } else {
         double seen = phaseSeen(casterId, partial);
         if (p.phase() == 1) {
            return seen < 3.0 ? 1.0F : 0.0F;
         } else if (p.phase() == 2 && seen < 1.0) {
            return 1.0F;
         } else {
            return p.phase() == 2 && seen >= 10.0 && seen < 14.0 ? 1 + (int)(seen - 10.0) : 0.0F;
         }
      }
   }

   static double flightTime(double distance) {
      double lo = 0.0;
      double hi = 400.0;

      for (int i = 0; i < 40; i++) {
         double mid = (lo + hi) / 2.0;
         if (FugaSequence.flightDistance(mid) < distance) {
            lo = mid;
         } else {
            hi = mid;
         }
      }

      return hi;
   }

   public static FugaRenderer.Frame localFrame(float partial) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return null;
      }

      FugaRenderer.Frame frame = frameFor(player.getId(), partial);
      return frame != null ? frame : afterglow(player.getId(), partial);
   }

   public static FugaRenderer.Frame shaderFrame(float partial) {
      FugaRenderer.Frame own = localFrame(partial);
      LocalPlayer player = Minecraft.getInstance().player;
      if (own == null && player != null) {
         FugaRenderer.Frame best = null;
         double bestDistance = 262144.0;

         for (int caster : ACTIVE.keySet()) {
            FugaRenderer.Frame f = frameFor(caster, partial);
            if (f != null && f.phase() <= 2) {
               double d = (f.phase() == 2 ? f.center() : f.origin()).distanceToSqr(player.position());
               if (d < bestDistance) {
                  best = f;
                  bestDistance = d;
               }
            }
         }

         return best;
      } else {
         return own;
      }
   }

   private static FugaRenderer.Frame afterglow(int caster, float partial) {
      if (glow != null && world != null && glow.caster() == caster && !ACTIVE.containsKey(caster)) {
         double age = (float)world.getGameTime() + partial - (float)glow.phaseAt();
         return age >= 305.0 && age < 645.0 ? new FugaRenderer.Frame(caster, glow.token(), 2, age, glow.center(), glow.center()) : null;
      } else {
         return null;
      }
   }

   private static void ensureFlameTexture() {
      if (!textureReady) {
         NativeImage pixels = new NativeImage(256, 64, false);

         for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 256; x++) {
               pixels.setPixelRGBA(x, y, flameAlpha(x, y) << 24 | 16777215);
            }
         }

         FugaRenderer.FlameTexture texture = new FugaRenderer.FlameTexture(pixels);
         Minecraft.getInstance().getTextureManager().register(FLAME_TEXTURE, texture);
         textureReady = true;
      }
   }

   static int flameAlpha(int x, int y) {
      if (y >= 56) {
         return 255;
      }

      double u = x / 256.0;
      double w = Math.abs(y - 27.5) / 27.5;
      int edge = y < 28 ? 0 : 1;
      double rim = 0.38 + 0.5 * fibre(u * 6.0, edge * 5 + 1.3, 6) + 0.2 * fibre(u * 19.0, edge * 7 + 4.1, 19);
      double slit = fibre(u * 6.0, w * 4.5 + 8.0, 6) * (1.0 - w * 0.5);
      double f = Math.max(0.0, Math.min(1.0, Math.min((rim - w) * 6.0, (0.64 - slit) * 5.0)));
      return (int)Math.round(255.0 * f);
   }

   private static double fibre(double x, double y, int period) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      double fx = x - ix;
      double fy = y - iy;
      fx = fx * fx * (3.0 - 2.0 * fx);
      fy = fy * fy * (3.0 - 2.0 * fy);
      int x0 = Math.floorMod(ix, period);
      int x1 = Math.floorMod(ix + 1, period);
      double a = hash(x0, iy);
      double b = hash(x1, iy);
      double c = hash(x0, iy + 1);
      double d = hash(x1, iy + 1);
      return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
   }

   public static void render(PoseStack pose, Camera camera, float partial) {
      if (world != null && world == Minecraft.getInstance().level && (!ACTIVE.isEmpty() || !AFTERMATH.isEmpty())) {
         GodWandGeometry.enableBlend();
         GodWandGeometry.defaultBlendFunc();
         GodWandGeometry.enableDepthTest();
         GodWandGeometry.depthMask(false);
         GodWandGeometry.disableCull();
         ensureFlameTexture();
         GodWandGeometry.textured();
         GodWandGeometry.setShaderTexture(0, FLAME_TEXTURE);
         flow = (float)world.getGameTime() + partial;
         renderPartial = partial;

         try {
            FugaRenderer.Frame local = localFrame(partial);
            FugaRenderer.Frame shaded = shaderFrame(partial);
            shaderCaster = shaded == null ? Integer.MIN_VALUE : shaded.casterId();
            double darkness = local != null ? 1.0F - FugaCinematic.environmentGain() : 0.0;
            if (local != null && local.phase() == 0 && local.age() < 176.0 && darkness > 0.0) {
               BufferBuilder backdrop = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
               emitBackdrop(backdrop, pose.last().pose(), local, camera.getPosition(), darkness);
               BufferBuilder.RenderedBuffer backdropMesh = backdrop.end();
               if (backdropMesh != null) {
                  GodWandGeometry.draw(backdropMesh);
               }
            }

            List<Integer> casters = new ArrayList<>(ACTIVE.keySet());
            if (!AFTERMATH.isEmpty()) {
               casters.add(Integer.MIN_VALUE);
            }

            for (pass = 0; pass < 2; pass++) {
               if (pass == 1) {
                  GodWandGeometry.additiveBlend();
               } else {
                  GodWandGeometry.defaultBlendFunc();
               }

               for (int caster : casters) {
                  FugaRenderer.Frame frame = caster == Integer.MIN_VALUE ? null : frameFor(caster, partial);
                  if (frame != null || caster == Integer.MIN_VALUE) {
                     BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                     if (frame == null) {
                        if (!AtomicPostFx.shaderPackActive()) {
                           storms(buffer, pose.last().pose(), camera.getPosition());
                        }
                     } else if (frame.phase() != 0 && AtomicPostFx.available() && shaderCaster == caster && AtomicRenderer.nearestFrame(partial) == null) {
                        accents(buffer, pose.last().pose(), frame, camera.getPosition());
                     } else {
                        emit(buffer, pose.last().pose(), frame, camera.getPosition(), world.getMinBuildHeight() - frame.origin().y);
                     }

                     BufferBuilder.RenderedBuffer mesh = buffer.end();
                     if (mesh != null) {
                        GodWandGeometry.draw(mesh);
                     }
                  }
               }
            }
         } finally {
            pass = -1;
            GodWandGeometry.depthMask(true);
            GodWandGeometry.enableCull();
            GodWandGeometry.defaultBlendFunc();
            GodWandGeometry.disableBlend();
         }
      }
   }

   public static boolean deferForShaderPack(PoseStack pose, Camera camera, float partial) {
      return false;
   }

   public static void flushDeferred() {
   }

   private static boolean valid(FugaRenderer.Frame f, Vec3 eye) {
      return Double.isFinite(f.age())
         && finite(f.origin().x, f.origin().y, f.origin().z, f.target().x, f.target().y, f.target().z, eye.x, eye.y, eye.z)
         && f.age() >= 0.0
         && f.phase() >= 0
         && f.phase() <= 2
         && (f.phase() != 2 || !(f.age() >= 305.0));
   }

   private static void accents(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 eye) {
      if (valid(f, eye)) {
         Vec3 base = f.origin().subtract(eye);
         if (f.phase() == 1) {
            launch(v, m, f, base);
            streamers(v, m, f, base);
            lingeringTrail(v, m, f, base);
            airRush(v, m, f, base);
            impactPoint(v, m, f, base);
         } else if (f.phase() == 2) {
            impactAccents(v, m, FugaSequence.visualAge(f.age()), base);
            if (AtomicPostFx.shaderPackActive()) {
               groundRings(v, m, FugaSequence.visualAge(f.age()), base, f.origin());
            }
         }
      }
   }

   private static void groundRings(VertexConsumer v, Matrix4f m, double age, Vec3 base, Vec3 origin) {
      double near = 1.0 - smooth(orbGap(age) / 10.0);
      if (age < 40.0) {
         double pressure = 1.0 - smooth((orbCenter(age) - orbRadius(age) * orbScale(age)) / 48.0);

         for (int i = 0; i < 3; i++) {
            double f = (age * (0.06 + 0.16 * near) + i / 3.0) % 1.0;
            groundRing(v, m, base, origin, orbRadius(age) * (0.55 + f * (1.9 + 1.6 * near)), 0.6 + f * 1.8, 16756832, (1.0 - f) * pressure * (0.5 + 0.8 * near));
         }
      } else {
         double wave = 4.0 + 230.0 * easeOut((age - 40.0) / 30.0);
         double fade = 1.0 - smooth((age - 42.0) / 36.0);
         groundRing(v, m, base, origin, wave, 1.4 + wave * 0.012, 16769200, fade * 0.85);
         double retract = 1.0 - smooth((age - 120.0) / 40.0);
         double column = 46.0 * Math.sqrt(retract) * (0.7 + 0.3 * smooth((age - 40.0) / 12.0));
         double life = smooth((age - 40.0) / 5.0) * smooth(retract / 0.35);

         for (int i = 0; i < 3; i++) {
            double cyc = ((age - 40.0) / 10.0 + i / 3.0) % 1.0;
            groundRing(v, m, base, origin, column + 4.0 + cyc * 110.0, 0.8 + cyc * 2.4, 16747056, (1.0 - cyc) * life * 0.7);
         }
      }
   }

   private static void groundRing(VertexConsumer v, Matrix4f m, Vec3 base, Vec3 origin, double radius, double width, int color, double alpha) {
      if (!(alpha <= 0.0) && !(radius <= 0.0) && world != null) {
         int n = (int)Math.min(160.0, Math.max(32.0, radius * 1.2));
         Vec3 prev = null;

         for (int i = 0; i <= n; i++) {
            double a = i * (Math.PI * 2) / n;
            double x = origin.x + Math.cos(a) * radius;
            double z = origin.z + Math.sin(a) * radius;
            int top = world.getHeight(Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double y = top < origin.y - 64.0 ? origin.y : top + 0.15;
            Vec3 p = base.add(x - origin.x, y - origin.y, z - origin.z);
            if (prev != null) {
               line(v, m, prev, p, width, width, color, true, alpha);
            }

            prev = p;
         }
      }
   }

   public static void emitBackdrop(VertexConsumer v, Matrix4f m, FugaRenderer.Frame frame, Vec3 eye, double alpha) {
      if (frame.phase() == 0 && !(frame.age() < 0.0) && !(frame.age() >= 176.0) && !(alpha <= 0.0)) {
         Vec3 aim = frame.target().subtract(frame.origin());
         Vec3 view = eye.subtract(frame.origin());
         Vec3 facing = direction(new Vec3(view.x, 0.0, view.z).lengthSqr() < 1.0E-4 ? new Vec3(aim.x, 0.0, aim.z) : new Vec3(view.x, 0.0, view.z));
         double back = frame.age() < 97.0 ? 0.4 : 3.4;
         Vec3 center = frame.origin().subtract(facing.scale(back)).add(0.0, 1.5, 0.0).subtract(eye);
         Vec3 right = side(facing).scale(64.0);
         Vec3 up = new Vec3(0.0, 64.0, 0.0);
         quad(v, m, center.subtract(right).subtract(up), center.add(right).subtract(up), center.add(right).add(up), center.subtract(right).add(up), 0, alpha);
         Vec3 floor = frame.origin().add(0.0, 0.09, 0.0).subtract(eye);
         Vec3 across = side(facing).scale(48.0);
         Vec3 depth = facing.scale(48.0);
         quad(
            v,
            m,
            floor.subtract(across).subtract(depth),
            floor.add(across).subtract(depth),
            floor.add(across).add(depth),
            floor.subtract(across).add(depth),
            0,
            alpha
         );
      }
   }

   public static void emit(VertexConsumer v, Matrix4f matrix, FugaRenderer.Frame f, Vec3 eye) {
      emit(v, matrix, f, eye, -384.0);
   }

   public static void emit(VertexConsumer v, Matrix4f matrix, FugaRenderer.Frame f, Vec3 eye, double floor) {
      if (valid(f, eye) && Double.isFinite(floor)) {
         Vec3 base = f.origin().subtract(eye);
         if (f.phase() == 0) {
            ritualRibbon(v, matrix, f, base);
         } else if (f.phase() == 1) {
            flight(v, matrix, f, base);
            launch(v, matrix, f, base);
            streamers(v, matrix, f, base);
            lingeringTrail(v, matrix, f, base);
            airRush(v, matrix, f, base);
            impactPoint(v, matrix, f, base);
         } else {
            double age = FugaSequence.visualAge(f.age());
            impactVolume(v, matrix, age, base, Math.min(0.0, floor));
            impactAccents(v, matrix, age, base);
         }
      }
   }

   public static double ribbonVolume(double age) {
      return FugaCinematic.smooth(age, 24.0, 40.0) * (1.0F - FugaCinematic.smooth(age, 93.0, 96.0));
   }

   public static Vec3 shaderAxis(FugaRenderer.Frame f) {
      if (f.phase() == 0 && ribbonVolume(f.age()) > 0.0) {
         Vec3 span = chargeHand(f, f.origin(), false).subtract(chargeHand(f, f.origin(), true));
         if (span.lengthSqr() > 1.0E-4) {
            return span;
         }
      }

      return f.target().subtract(f.origin()).normalize();
   }

   static Vec3 chargeHand(FugaRenderer.Frame f, Vec3 base, boolean rightHand) {
      Vec3 d = direction(f.target().subtract(f.origin()));
      Vec3 forward = direction(new Vec3(d.x, 0.0, d.z));
      Vec3 right = side(forward);
      Vec3 hand = FugaPose.hand(f.age(), (float)(-Math.asin(d.y)), rightHand, FugaAimRig.availableFor.test(f.casterId()));
      double surface = 0.13 * FugaCinematic.smooth(f.age(), 144.0, 190.0);
      return base.add(right.scale(hand.x)).add(0.0, hand.y + surface, 0.0).add(forward.scale(hand.z));
   }

   private static void ritualRibbon(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      Vec3 a = chargeHand(f, base, true);
      Vec3 b = chargeHand(f, base, false);
      double age = f.age();
      double gain = FugaCinematic.smooth(age, 24.0, 40.0);
      double draw = FugaCinematic.smooth(age, 144.0, 190.0);
      if (!(gain <= 0.0)) {
         Vec3 aim = direction(f.target().subtract(f.origin()));
         double span = a.distanceTo(b);
         double gather = FugaCinematic.smooth(age, 24.0, 96.0);
         double press = FugaCinematic.smooth(age, 96.0, 144.0);
         Vec3 tip = b.lerp(b.add(aim.scale(1.6)), draw);
         Vec3 axis = direction(tip.subtract(a));
         Vec3 end = tip.subtract(axis.scale(0.35 * draw));
         double width = Mth.lerp(draw, (0.06 + 0.08 * Math.min(1.0, span / 0.8)) * (1.0 + 0.35 * gather - 0.3 * press), 0.07);
         double tremble = 0.035 * press * (1.0 - draw) * Math.sin(age * 2.3);
         boolean volume = pass >= 0 && AtomicPostFx.enabled() && shaderCaster == f.casterId() && AtomicRenderer.nearestFrame(0.0F) == null;
         double mesh = gain * (volume ? Math.max(draw, crackle(age)) : 1.0);
         fireBody(v, m, a, end, width, (0.06 + tremble) * (1.0 - draw), age * (1.0 + press), mesh, 0.0);
         fireBody(v, m, a, end.subtract(axis.scale(0.1 * draw)), width * 0.62, (0.09 - tremble) * (1.0 - draw), age * (1.4 + press), mesh * 0.6, 5.0);
         line(v, m, a, end, width * (0.16 + 0.14 * press), width * (0.1 + 0.1 * press), 16774880, true, mesh * (0.3 + 0.35 * press));
         streaks(v, m, a, end, width * 1.1, age, mesh * 0.5, 5);
         Vec3 ru = side(axis);
         Vec3 rw = axis.cross(ru).normalize();

         for (int i = 0; i < 40; i++) {
            double t = (hash(i, 31) + age * (0.016 + 0.01 * hash(i, 32))) % 1.0;
            double turn = hash(i, 33) * (Math.PI * 2) + age * (0.3 + 0.15 * press) + t * 7.0;
            double rad = width * (0.35 + 0.55 * Math.sin(Math.PI * t)) * (0.7 + 0.5 * hash(i, 34));
            Vec3 at = a.lerp(end, t).add(ru.scale(Math.cos(turn) * rad)).add(rw.scale(Math.sin(turn) * rad));
            double size = width * (0.45 + 0.55 * Math.sin(Math.PI * t)) * (0.7 + 0.6 * hash(i, 35));
            double fade = mesh * Math.sin(Math.PI * t);
            int body = t < 0.35 ? 16754744 : (t < 0.7 ? 15751180 : 13115400);
            flame(v, m, at, size, body, fade * 0.45, false);
            if (i % 2 == 0) {
               flame(v, m, at, size * 0.45, 16769162, fade * 0.4, true);
            }
         }

         boolean ballOn = volume && ribbonVolume(age) > 0.0;
         light(v, m, a.lerp(end, 0.5), tip, aim, age, ballOn ? gain * (1.0 - ribbonVolume(age)) : gain, press, draw);
         glowDisc(v, m, base.add(0.0, 0.03, 0.0), 1.0 + 2.0 * gather - 0.4 * press, 16738842, gain * (0.12 + 0.2 * press + 0.1 * draw));
         if (draw > 0.0) {
            charging(v, m, tip, axis, age, gain * draw);
            flowArrow(v, m, a, tip, axis, age, gain * draw, draw);
         }

         Vec3 u = side(axis);
         Vec3 w = axis.cross(u).normalize();
         if (volume) {
            Vec3 core = a.lerp(b, 0.5);
            Vec3 fwd = direction(new Vec3(aim.x, 0.0, aim.z));
            Vec3 spanAxis = direction(a.subtract(b));
            Vec3 toward = direction(fwd.scale(-1.0).subtract(spanAxis.scale(spanAxis.dot(fwd.scale(-1.0)))));
            Vec3 normal = direction(spanAxis.scale(Math.cos(0.7)).add(toward.scale(Math.sin(0.7))));
            Vec3 e1 = direction(normal.cross(new Vec3(0.0, 1.0, 0.0)));
            Vec3 e2 = direction(normal.cross(e1));

            for (int i = 0; i < 140; i++) {
               double life = (hash(i, 131) + age * (0.045 + 0.035 * hash(i, 132))) % 1.0;
               double r0 = 0.25 + 0.5 * hash(i, 135);
               double rad = r0 * Math.pow(1.0 - life, 0.75) + 0.012;
               double theta = hash(i, 133) * (Math.PI * 2) + 0.22 * (1.0 / (rad + 0.05) - 1.0 / (r0 + 0.05)) + age * 0.04;
               double off = (hash(i, 134) - 0.5) * 0.07 * rad / r0;
               Vec3 at = core.add(e1.scale(Math.cos(theta) * rad)).add(e2.scale(Math.sin(theta) * rad)).add(normal.scale(off));
               double fade = (gain > 0.0 ? 1 : 0) * (1.0 - smooth((life - 0.85) / 0.15)) * (1.0F - FugaCinematic.smooth(age, 94.0, 96.0));
               fade *= smooth((Math.min(at.distanceTo(a), at.distanceTo(b)) - 0.09) / 0.08);
               dot(v, m, at, 0.011 + 0.01 * hash(i, 138), i % 3 == 0 ? 16774352 : 16756800, fade, true);
            }
         } else {
            for (int i = 0; i < 90; i++) {
               double t = (hash(i, 1) + age * (0.012 + 0.01 * press) * (0.7 + 0.6 * hash(i, 2))) % 1.0;
               double turn = hash(i, 3) * (Math.PI * 2) + t * (Math.PI * 2) * 2.0 * (0.6 + 0.8 * hash(i, 4)) + age * 0.22;
               double rad = width * (0.3 + 0.6 * hash(i, 5)) * (0.7 + 0.4 * Math.sin(Math.PI * t)) + 0.01;
               Vec3 p = a.lerp(end, t).add(u.scale(Math.cos(turn) * rad)).add(w.scale(Math.sin(turn) * rad));
               Vec3 trail = axis.scale(-0.05).add(u.scale(Math.sin(turn) * rad * 0.6)).subtract(w.scale(Math.cos(turn) * rad * 0.6));
               ember(v, m, p, trail, 0.016 + 0.012 * Math.sin(Math.PI * t), i % 4 == 0 ? 16774352 : 16756800, gain * Math.sin(Math.PI * t) * 0.9);
            }
         }

         clap(v, m, a.lerp(b, 0.5), age);
         double ignite = FugaCinematic.smooth(age, 99.0, 109.0);
         double heading = Math.atan2(aim.z, aim.x);
         vortex(v, m, base, heading, age, ignite * (1.0 - 0.35 * draw), 1.0);
         ground(v, m, base, heading, age, ignite * (0.45 + 0.55 * draw));
         cinders(v, m, base, heading, age, ignite);

         for (int i = 0; i < 18; i++) {
            double life = (i * 0.618034 + age * 0.018) % 1.0;
            double ang = heading + i * 2.399963 + age * 0.05;
            puff(
               v,
               m,
               base.add(Math.cos(ang) * (0.5 + 0.7 * life), 0.3 + life * 3.2, Math.sin(ang) * (0.5 + 0.7 * life)),
               0.35 + 0.7 * life,
               i % 3 == 0 ? 16752704 : 16732180,
               ignite * 0.09 * Math.max(0.001, Math.sin(Math.PI * life)),
               true
            );
         }
      }
   }

   static double crackle(double age) {
      if (!(age < 40.0) && !(age >= 95.0)) {
         int tick = (int)Math.floor(age);
         if (tick != 93 && tick != 91) {
            double chance = 0.07 + 0.4 * smooth((age - 73.0) / 20.0);
            return hash(tick, 211) < chance ? 0.55 + 0.45 * hash(tick, 212) : 0.0;
         } else {
            return 1.0;
         }
      } else {
         return 0.0;
      }
   }

   private static void clap(VertexConsumer v, Matrix4f m, Vec3 c, double age) {
      double t = age - 95.0;
      if (!(t < -1.0) && !(t >= 10.0)) {
         double k = Math.max(0.0, t) / 10.0;
         double fade = (1.0 - k) * (1.0 - k) * FugaCinematic.smooth(t, -1.0, 0.0);
         puff(v, m, c, 0.12 + 0.5 * k, 16773840, fade * 0.45, true);
         puff(v, m, c, 0.25 + 0.9 * k, 16738840, fade * 0.25, true);

         for (int i = 0; i < 14; i++) {
            double a = i * (Math.PI * 2) / 14.0 + 0.3;
            Vec3 out = new Vec3(Math.cos(a), 0.35 * Math.sin(i * 1.7), Math.sin(a));
            ember(v, m, c.add(out.scale(0.15 + 1.6 * k)), out.scale(-0.25), 0.03 * (1.0 - k) + 0.012, i % 3 == 0 ? 16777215 : 16760928, fade);
         }
      }
   }

   static Vec3 stream(Vec3 base, double heading, double age, int k, double t, double spread) {
      boolean inner = k >= 6;
      double phase = heading + k * (Math.PI * 2) / (inner ? 4 : 6) + (inner ? -0.13 : 0.075) * age;
      double outer = inner ? 1.6 : 3.4;
      double lift = inner ? 0.8 : 0.35;
      double a = phase + (inner ? -1 : 1) * t * 2.8;
      double rr = spread * (0.45 + outer * (1.0 - t));
      double wave = 0.18 * Math.sin(t * 7.0 + age * 0.23 + k) + 0.1 * Math.sin(t * 13.0 - age * 0.37 + k * 2.1);
      return base.add(
         Math.cos(a) * (rr + wave),
         0.06 + lift * t * t * (1.0 + 0.15 * Math.sin(age * 0.19 + k)) / spread + 0.12 * Math.sin(t * 9.0 - age * 0.3 + k),
         Math.sin(a) * (rr + wave)
      );
   }

   static void vortex(VertexConsumer v, Matrix4f m, Vec3 base, double heading, double age, double gain, double spread) {
      if (!(gain <= 0.0)) {
         int n = 14;
         Vec3[] p = new Vec3[n];
         double[] r = new double[n];
         double[] al = new double[n];

         for (int k = 0; k < 10; k++) {
            double thick = k >= 6 ? 0.55 : 0.8;

            for (int i = 0; i < n; i++) {
               double t = i / (n - 1.0);
               double swell = Math.sin(Math.PI * Math.min(1.0, t * 1.15));
               p[i] = stream(base, heading, age, k, t, spread);
               r[i] = thick * swell * (0.6 + 0.4 * noise(t * 6.0 - age * 0.012 * 6.0, k * 3.1));
               al[i] = gain * 0.32 * Math.pow(swell, 0.7) * smooth((p[i].length() - 0.9) / 1.4);
            }

            strand(v, m, p, r, al, n, 13117450, true, false);
         }

         for (int i = 0; i < 80; i++) {
            int k = i % 10;
            double life = (hash(i, 21) + age * (0.014 + 0.012 * hash(i, 22))) % 1.0;
            double t = Math.min(1.0, life * 1.1);
            Vec3 at = stream(base, heading, age, k, t, spread)
               .add((hash(i, 23) - 0.5) * 0.5, (hash(i, 24) - 0.5) * 0.15 + life * life * 0.3, (hash(i, 25) - 0.5) * 0.5);
            Vec3 flowDir = stream(base, heading, age, k, Math.min(1.0, t + 0.04), spread)
               .subtract(stream(base, heading, age, k, Math.max(0.0, t - 0.04), spread));
            double size = (0.28 + 0.42 * Math.sin(Math.PI * life)) * (0.7 + 0.6 * hash(i, 26)) * (k >= 6 ? 0.85 : 1.0);
            double fade = gain * Math.sin(Math.PI * Math.min(1.0, life * 1.2)) * smooth((at.length() - 0.9) / 1.4);
            int body = life < 0.3 ? 16756800 : (life < 0.65 ? 15753230 : 12591112);
            flame(v, m, at, size, body, fade * 0.55, false, flowDir);
            if (i % 2 == 0) {
               flame(v, m, at.add(0.0, -size * 0.15, 0.0), size * 0.5, 16765040, fade * (1.0 - life) * 0.7, true, flowDir);
            }
         }
      }
   }

   private static void flame(VertexConsumer v, Matrix4f m, Vec3 c, double size, int color, double alpha, boolean glow) {
      flame(v, m, c, size, color, alpha, glow, new Vec3(0.0, 1.0, 0.0));
   }

   private static void flame(VertexConsumer v, Matrix4f m, Vec3 c, double size, int color, double alpha, boolean glow, Vec3 along) {
      if (layer(glow) && !(size <= 0.0)) {
         Vec3 axis = c.cross(along).cross(c);
         if (axis.lengthSqr() < 1.0E-10) {
            axis = new Vec3(0.0, 1.0, 0.0).cross(c).cross(c);
         }

         if (axis.lengthSqr() < 1.0E-10) {
            axis = side(c);
         }

         Vec3 u = direction(axis).scale(size * 1.3);
         Vec3 r = direction(c.cross(u)).scale(size * 0.75);

         for (int i = 0; i < 12; i++) {
            double a0 = i * (Math.PI * 2) / 12.0;
            double a1 = (i + 1) * (Math.PI * 2) / 12.0;
            double w0 = 1.0 + 0.15 * Math.sin(a0 * 3.0 + size * 9.0);
            double w1 = 1.0 + 0.15 * Math.sin(a1 * 3.0 + size * 9.0);
            vertex(v, m, c, color, alpha);
            vertex(v, m, c.add(r.scale(Math.cos(a0) * w0)).add(u.scale(Math.sin(a0) * w0 * (Math.sin(a0) > 0.0 ? 1.0 : 0.55))), color, 0.0);
            vertex(v, m, c.add(r.scale(Math.cos(a1) * w1)).add(u.scale(Math.sin(a1) * w1 * (Math.sin(a1) > 0.0 ? 1.0 : 0.55))), color, 0.0);
            vertex(v, m, c, color, alpha);
         }
      }
   }

   private static void ground(VertexConsumer v, Matrix4f m, Vec3 base, double heading, double age, double gain) {
      if (!(gain <= 0.0)) {
         Vec3 feet = base.add(0.0, 0.04, 0.0);

         for (int i = 0; i < 2; i++) {
            double life = (age * 0.022 + i / 2.0) % 1.0;
            ring(v, m, feet, 0.6 + life * 7.0, 0.2 + 0.3 * (1.0 - life), 16742944, gain * (1.0 - life) * 0.55, true, heading, 32);
         }

         for (int i = 0; i < 2; i++) {
            double life = (age * (i == 0 ? 0.07 : 0.045) + i * 0.37) % 1.0;
            ring(
               v,
               m,
               feet.add(0.0, 0.05, 0.0),
               0.3 + life * (i == 0 ? 2.6 : 4.8),
               0.08 + 0.12 * (1.0 - life),
               i == 0 ? 16760928 : 16747056,
               gain * (1.0 - life) * (1.0 - life) * 0.8,
               true,
               heading + i,
               32
            );
         }

         for (int i = 0; i < 3; i++) {
            double life = (age * 0.06 + i / 3.0) % 1.0;
            boolean dark = i == 1;
            ring(
               v,
               m,
               feet.add(0.0, 0.03 + 0.01 * i, 0.0),
               0.4 + life * (3.5 + 1.5 * i),
               0.1 + 0.18 * (1.0 - life),
               dark ? 4852738 : (i == 0 ? 14172170 : 16738840),
               gain * (1.0 - life) * (dark ? 0.7 : 0.6),
               !dark,
               heading + 2 * i,
               32
            );
         }
      }
   }

   private static void cinders(VertexConsumer v, Matrix4f m, Vec3 base, double heading, double age, double gain) {
      if (!(gain <= 0.0)) {
         for (int i = 0; i < 70; i++) {
            int k = i % 10;
            double life = (hash(i, 7) + age * (0.011 + 0.008 * hash(i, 8))) % 1.0;
            double t = Math.min(1.0, life * 1.6);
            Vec3 p = stream(base, heading, age, k, t, 1.0);
            Vec3 q = stream(base, heading, age, k, Math.max(0.0, t - 0.04), 1.0);
            double up = Math.max(0.0, life - 0.6) / 0.4;
            double a = heading + hash(i, 9) * (Math.PI * 2) + age * 0.16 + up * 4.0;
            p = p.add(Math.cos(a) * up * (0.3 + 0.9 * hash(i, 12)), up * up * 3.4, Math.sin(a) * up * (0.3 + 0.9 * hash(i, 12)))
               .add((hash(i, 13) - 0.5) * 0.25, 0.0, (hash(i, 14) - 0.5) * 0.25);
            Vec3 tail = q.subtract(stream(base, heading, age, k, t, 1.0)).scale(0.8).add(0.0, -0.03 - 0.05 * up, 0.0);
            double fade = gain * Math.sin(Math.PI * life);
            if (i % 5 == 4) {
               dot(v, m, p, 0.035, 5901828, fade * 0.8, false);
            } else {
               ember(v, m, p, tail, 0.025 + 0.025 * (1.0 - life), i % 3 == 0 ? 16773832 : 16751152, fade);
            }
         }

         for (int i = 0; i < 70; i++) {
            double life = (hash(i, 161) + age * (0.035 + 0.03 * hash(i, 162))) % 1.0;
            double a = hash(i, 163) * (Math.PI * 2) + Math.floor(hash(i, 161) + age * (0.035 + 0.03 * hash(i, 162))) * 1.7;
            double rr = 0.4 + 2.6 * hash(i, 164);
            double drift = life * (0.8 + 1.6 * hash(i, 165));
            Vec3 p = base.add(
               Math.cos(a) * (rr + drift * 0.4) + Math.sin(age * 0.7 + i) * 0.08,
               0.05 + drift * (1.6 + 1.6 * hash(i, 166)) - life * life * 0.6,
               Math.sin(a) * (rr + drift * 0.4)
            );
            dot(v, m, p, 0.02 + 0.02 * (1.0 - life), i % 3 == 0 ? 16774352 : 16756800, gain * Math.sin(Math.PI * life), true);
         }

         for (int i = 0; i < 70; i++) {
            double life = (hash(i, 151) + age * (0.012 + 0.006 * hash(i, 152))) % 1.0;
            double a = heading + hash(i, 153) * (Math.PI * 2) + age * 0.13 + life * 5.0;
            double rr = 0.6 + 1.8 * life * (0.4 + 0.6 * hash(i, 154));
            Vec3 p = base.add(Math.cos(a) * rr, 0.2 + life * 4.4, Math.sin(a) * rr);
            dot(v, m, p, 0.022 + 0.024 * (1.0 - life), i % 3 == 0 ? 16773832 : 16752704, gain * Math.sin(Math.PI * life), true);
         }
      }
   }

   private static void storms(VertexConsumer v, Matrix4f m, Vec3 eye) {
      for (FugaRenderer.Aftermath a : AFTERMATH) {
         storm(v, m, a.center().subtract(eye), world.getGameTime() - a.at());
      }
   }

   static void storm(VertexConsumer v, Matrix4f m, Vec3 base, double ticks) {
      double grow = easeOut((ticks - 10.0) / 200.0);
      double fade = smooth((ticks - 5.0) / 45.0) * (1.0 - smooth((ticks - 495.00000000000006) / 405.0));
      if (!(fade <= 0.0) && !(ticks < 0.0)) {
         double ceiling = 105.0;
         double spread = 50.0 + 250.0 * grow;

         for (int i = 0; i < 36; i++) {
            double h = 4.0 + i / 36.0 * (ceiling - 4.0) * grow;
            double a = h * 0.045 + ticks * 0.012 + i * 0.7;
            double w = 7.0 + h * 0.09;
            Vec3 c = base.add(Math.cos(a) * w * 0.5, h, Math.sin(a) * w * 0.5);
            double column = fade * smooth((ticks - 80.0) / 40.0);
            puff(v, m, c, w + 6.0, 1840144, 0.5 * column, false);
            puff(v, m, c, w * 0.6, 16738844, 0.08 * column * (1.0 - h / ceiling), true);
         }

         for (int i = 0; i < 160; i++) {
            double s = (i + 0.5) / 160.0;
            double r = spread * Math.sqrt(s);
            double a = i * 2.399963 + ticks * 0.0015 * (1.2 - s);
            double y = ceiling + 12.0 * Math.sin(i * 1.7) + 18.0 * (1.0 - s) + 6.0 * Math.sin(ticks * 0.01 + i);
            double size = (38.0 + 26.0 * noise(i * 0.31, ticks * 0.008)) * (0.7 + 0.6 * grow);
            double edge = 1.0 - smooth((s - 0.8) / 0.2);
            Vec3 c = base.add(Math.cos(a) * r, y, Math.sin(a) * r);
            puff(v, m, c, size, i % 5 == 0 ? 2758672 : 1182216, 0.42 * fade * edge, false);
            puff(
               v,
               m,
               c.subtract(0.0, size * 0.5, 0.0),
               size * 0.85,
               16732176,
               (0.07 + 0.06 * (1.0 - s)) * fade * edge * (0.75 + 0.25 * Math.sin(ticks * 0.05 + i)),
               true
            );
         }

         for (int k = 0; k < 5; k++) {
            double clock = ticks * 0.05 + k * 0.29;
            double cycle = Math.floor(clock);
            double life = clock - cycle;
            if (!(life > 0.12)) {
               double flash = (1.0 - life / 0.12) * fade;
               double a = noise(cycle * 3.7, k) * (Math.PI * 2) * 3.0;
               double r = spread * 0.8 * noise(k * 5.1, cycle * 1.9);
               Vec3 at = base.add(Math.cos(a) * r, ceiling - 5.0, Math.sin(a) * r);
               Vec3 heading = new Vec3(noise(cycle, k + 9) - 0.5, -0.8, noise(cycle + 4.0, k) - 0.5).normalize();
               int n = 12;
               Vec3[] p = new Vec3[n];
               double[] w = new double[n];
               double[] al = new double[n];

               for (int i = 0; i < n; i++) {
                  p[i] = at;
                  w[i] = 1.6 * (1.0 - (double)i / n) + 0.4;
                  al[i] = flash;
                  at = at.add(heading.scale(9.0))
                     .add((noise(cycle * 7.0 + i, k) - 0.5) * 12.0, (noise(i * 3.3, cycle) - 0.5) * 4.0, (noise(k + i * 1.9, cycle * 2.0) - 0.5) * 12.0);
               }

               strand(v, m, p, w, al, n, 15265023, true, false);
               puff(v, m, p[0], 80.0, 16767152, flash * 0.35, true);
            }
         }
      }
   }

   private static void puff(VertexConsumer v, Matrix4f m, Vec3 c, double size, int color, double alpha, boolean glow) {
      if (layer(glow) && !(alpha <= 0.0) && !(size <= 0.0)) {
         Vec3 s = side(c);
         Vec3 r = s.scale(size);
         Vec3 u = direction(c.cross(s)).scale(size);
         Vec3[] rim = new Vec3[15];

         for (int i = 0; i < 14; i++) {
            double a = i * (Math.PI * 2) / 14.0;
            double w = 1.0 + 0.12 * Math.sin(a * 3.0 + size) + 0.06 * Math.sin(a * 7.0 + size * 2.0);
            rim[i] = c.add(r.scale(PUFF_COS[i] * w)).add(u.scale(PUFF_SIN[i] * w));
         }

         rim[14] = rim[0];

         for (int i = 0; i < 14; i++) {
            vertex(v, m, c, color, alpha);
            vertex(v, m, rim[i], color, 0.0);
            vertex(v, m, rim[i + 1], color, 0.0);
            vertex(v, m, c, color, alpha);
         }
      }
   }

   private static void light(VertexConsumer v, Matrix4f m, Vec3 hands, Vec3 tip, Vec3 aim, double age, double gain, double press, double draw) {
      double breathe = 0.85 + 0.15 * Math.sin(age * 0.37) + 0.08 * Math.sin(age * 1.3);
      puff(v, m, hands, (0.8 + 0.6 * press) * breathe, 16734740, gain * (0.14 + 0.12 * press) * (1.0 - 0.5 * draw), true);
      puff(v, m, hands, (0.35 + 0.2 * press) * breathe, 16762992, gain * (0.14 + 0.14 * press) * (1.0 - 0.5 * draw), true);
      if (draw > 0.0) {
         puff(v, m, tip, 2.2 * breathe, 16730636, gain * draw * 0.22, true);
         puff(v, m, tip, 1.1 * breathe, 16752704, gain * draw * 0.25, true);
         puff(v, m, tip, 0.45 * breathe, 16777215, gain * draw * 0.45, true);
      }
   }

   private static void loop(VertexConsumer v, Matrix4f m, Vec3 c, Vec3 axis, double radius, double width, int color, double alpha) {
      Vec3 u = side(axis);
      Vec3 w = axis.cross(u).normalize();
      int n = 25;
      Vec3[] p = new Vec3[n];
      double[] r = new double[n];
      double[] a = new double[n];

      for (int i = 0; i < n; i++) {
         double t = i * (Math.PI * 2) / (n - 1);
         p[i] = c.add(u.scale(Math.cos(t) * radius)).add(w.scale(Math.sin(t) * radius));
         double torn = noise(t * 3.0 + radius * 2.3, 7.7);
         r[i] = width * (0.4 + 0.9 * noise(t * 5.0 + radius * 3.0, 2.2));
         a[i] = alpha * Math.min(1.0, 0.15 + 1.3 * torn * torn);
      }

      strand(v, m, p, r, a, n, color, true);
   }

   private static void glowDisc(VertexConsumer v, Matrix4f m, Vec3 c, double radius, int color, double alpha) {
      if (layer(true) && !(alpha <= 0.0) && !(radius <= 0.0)) {
         for (int i = 0; i < 32; i++) {
            double a0 = i * (Math.PI * 2) / 32.0;
            double a1 = (i + 1) * (Math.PI * 2) / 32.0;
            vertex(v, m, c, color, alpha);
            vertex(v, m, c.add(Math.cos(a0) * radius, 0.0, Math.sin(a0) * radius), color, 0.0);
            vertex(v, m, c.add(Math.cos(a1) * radius, 0.0, Math.sin(a1) * radius), color, 0.0);
            vertex(v, m, c, color, alpha);
         }
      }
   }

   private static void streaks(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, double age, double gain, int count) {
      Vec3 along = direction(b.subtract(a));
      Vec3 u = side(along);
      Vec3 w = along.cross(u).normalize();
      int n = 14;
      Vec3[] p = new Vec3[n];
      double[] r = new double[n];
      double[] al = new double[n];

      for (int k = 0; k < count; k++) {
         double lane = (k + 0.5) / count * 2.0 - 1.0;

         for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0);
            double env = Math.pow(Math.sin(Math.PI * t), 0.5);
            double drift = Math.sin(t * (4 + k % 3) - age * (0.13 + 0.02 * k) + k * 1.9);
            p[i] = a.lerp(b, t).add(u.scale((lane * 0.75 + 0.3 * drift) * width * env)).add(w.scale(Math.cos(t * 3.0 + k - age * 0.1) * width * 0.35 * env));
            r[i] = width * (0.08 + 0.06 * noise(t * 5.0 - age * 0.2, k)) * env;
            al[i] = gain * env * (0.7 + 0.3 * Math.sin(t * 9.0 - age * 0.3 + k));
         }

         strand(v, m, p, r, al, n, k % 3 == 0 ? 16774848 : 16765018, true);
      }
   }

   private static int mixRgb(int a, int b, double t) {
      int r = (int)Math.round((a >> 16 & 0xFF) * (1.0 - t) + (b >> 16 & 0xFF) * t);
      int g = (int)Math.round((a >> 8 & 0xFF) * (1.0 - t) + (b >> 8 & 0xFF) * t);
      int bl = (int)Math.round((a & 0xFF) * (1.0 - t) + (b & 0xFF) * t);
      return r << 16 | g << 8 | bl;
   }

   private static void ember(VertexConsumer v, Matrix4f m, Vec3 p, Vec3 tail, double size, int color, double alpha) {
      dot(v, m, p, size, hot(color), alpha, true);
      line(v, m, p, p.add(tail), size * 0.4, 0.0, color, true, alpha * 0.55);
   }

   private static void dot(VertexConsumer v, Matrix4f m, Vec3 c, double size, int color, double alpha, boolean glow) {
      if (layer(glow)) {
         Vec3 r = side(c).scale(size);
         Vec3 u = direction(c.cross(side(c))).scale(size);

         for (int i = 0; i < 6; i++) {
            double a0 = i * (Math.PI * 2) / 6.0;
            double a1 = (i + 1) * (Math.PI * 2) / 6.0;
            vertex(v, m, c, color, alpha);
            vertex(v, m, c.add(r.scale(Math.cos(a0))).add(u.scale(Math.sin(a0))), color, 0.0);
            vertex(v, m, c.add(r.scale(Math.cos(a1))).add(u.scale(Math.sin(a1))), color, 0.0);
            vertex(v, m, c, color, alpha);
         }
      }
   }

   private static void glint(VertexConsumer v, Matrix4f m, Vec3 p, double size, double alpha, int color) {
      if (!(size <= 0.0) && !(alpha <= 0.0)) {
         puff(v, m, p, size * 0.3, color, alpha * 0.3, true);
         puff(v, m, p, size * 0.09, 16777215, alpha, true);
         Vec3 across = side(p).scale(size * 2.2);
         line(v, m, p.subtract(across), p.add(across), size * 0.03, size * 0.03, color, true, alpha * 0.45);
      }
   }

   private static void flowArrow(VertexConsumer v, Matrix4f m, Vec3 nock, Vec3 tip, Vec3 axis, double age, double gain, double draw) {
      if (!(gain <= 0.0)) {
         Vec3 u = side(axis);
         Vec3 w = axis.cross(u).normalize();
         double form = easeOut(draw);
         double L = Math.max(0.5, tip.distanceTo(nock));
         double h = 1.1 * form;
         double H = 0.95 * form;
         double fin = L * 0.9 * form;
         double spin = age * 0.03;

         for (int i = 0; i < 112; i++) {
            int k = i % 4;
            double s = (hash(i, 121) + age * (0.03 + 0.015 * hash(i, 122))) % 1.0;
            double q = k * Math.PI / 2.0 + spin + (hash(i, 123) - 0.5) * 0.25;
            Vec3 out = u.scale(Math.cos(q)).add(w.scale(Math.sin(q)));
            double head = Math.min(1.0, s / 0.3);
            double back = Math.max(0.0, (s - 0.3) / 0.7);
            double x = h * head + fin * back;
            double r = H * head * (1.0 - 0.55 * back) + 0.06 * Math.sin(age * 0.4 + i);
            Vec3 curl = out.cross(axis).scale(0.12 * back * Math.sin(age * 0.3 + i * 1.3));
            Vec3 at = tip.subtract(axis.scale(x)).add(out.scale(r)).add(curl).add(0.0, back * back * 0.2, 0.0);
            double size = (0.16 + 0.46 * Math.sqrt(s)) * (0.7 + 0.6 * hash(i, 124));
            double fade = gain * Math.pow(Math.sin(Math.PI * s), 0.6);
            int color = s < 0.12 ? 16773312 : (s < 0.35 ? 16760896 : (s < 0.7 ? 15753230 : 12591112));
            flame(
               v,
               m,
               at,
               size * (0.4 + 0.6 * form),
               color,
               fade * 0.6 * smooth((draw * 1.3 - hash(i, 128)) / 0.3),
               s < 0.35,
               axis.scale(-1.0).add(out.scale(head < 1.0 ? 0.6 : 0.2))
            );
         }

         for (int i = 0; i < 26; i++) {
            double s = (hash(i, 125) + age * 0.05) % 1.0;
            Vec3 at = tip.subtract(axis.scale(s * (L + 1.2))).add(u.scale((hash(i, 126) - 0.5) * 0.12)).add(w.scale((hash(i, 127) - 0.5) * 0.12));
            flame(
               v, m, at, 0.14 + 0.24 * s, s < 0.25 ? 16774352 : (s < 0.6 ? 16752688 : 14168080), gain * Math.sin(Math.PI * s) * 0.7, s < 0.4, axis.scale(-1.0)
            );
         }

         int n = 18;
         Vec3[] p = new Vec3[n];
         double[] r = new double[n];
         double[] al = new double[n];
         double[] rc = new double[n];
         double[] ac = new double[n];

         for (int k = 0; k < 4; k++) {
            boolean big = k == 0;
            double reach = big ? 5.2 : 3.4 + 0.6 * k;
            double amp = big ? 0.55 : 0.3;
            double width = big ? 0.32 : 0.09;
            double ph = k * 2.1;

            for (int i = 0; i < n; i++) {
               double t = i / (n - 1.0);
               double sway = Math.sin(t * 5.5 - age * 0.32 + ph) * amp * t;
               double lift = Math.cos(t * 4.2 - age * 0.27 + ph * 1.3) * amp * 0.7 * t;
               p[i] = nock.subtract(axis.scale(0.2 + t * reach))
                  .add(u.scale(sway + (big ? 0.0 : Math.cos(ph) * 0.25)))
                  .add(w.scale(lift + (big ? 0.0 : Math.sin(ph) * 0.25)));
               r[i] = width * (1.0 - 0.75 * t) * (0.8 + 0.3 * Math.sin(t * 9.0 - age * 0.5 + k));
               al[i] = gain * Math.pow(1.0 - t, 0.9) * (big ? 0.85 : 0.75);
               rc[i] = r[i] * 0.38;
               ac[i] = al[i];
            }

            strand(v, m, p, r, al, n, big ? 15220746 : 15753232, !big, big);
            strand(v, m, p, rc, ac, n, 16765040, true, false);
         }

         for (int i = 0; i < 44; i++) {
            int k = i % 2;
            double s = (hash(i, 141) + age * 0.045) % 1.0;
            double turn = k * Math.PI + s * (Math.PI * 2) * 2.2 + age * 0.18;
            double rr = (0.22 + 0.3 * (1.0 - s)) * form;
            Vec3 at = nock.lerp(tip, s).add(u.scale(Math.cos(turn) * rr)).add(w.scale(Math.sin(turn) * rr));
            flame(v, m, at, (0.1 + 0.18 * Math.sin(Math.PI * s)) * form, s > 0.7 ? 16769162 : 15753230, gain * Math.sin(Math.PI * s) * 0.65, s > 0.7, axis);
         }

         for (int i = 0; i < 2; i++) {
            double life = (age / 26.0 + i * 0.5) % 1.0;
            loop(
               v,
               m,
               tip.add(axis.scale(0.2 + life * 2.2)),
               axis,
               (0.3 + life * 2.4) * form,
               0.08 * (1.0 - life) + 0.02,
               16760928,
               gain * (1.0 - life) * (1.0 - life) * 0.9
            );
         }

         dot(v, m, tip, 0.22, 16775398, gain * 0.9, true);
         dot(v, m, tip.subtract(axis.scale(0.25)), 0.35, 16756800, gain * 0.5, true);
      }
   }

   private static void charging(VertexConsumer v, Matrix4f m, Vec3 tip, Vec3 axis, double age, double gain) {
      Vec3 flat = tip.cross(axis);
      flat = flat.lengthSqr() < 1.0E-8 ? side(axis) : flat.normalize();
      Vec3 up = axis.cross(flat).normalize();
      double held = smooth((age - 190.0) / 110.0);

      for (int i = 0; i < 100; i++) {
         double life = (i * 0.618034 + age * (0.05 + 0.03 * held)) % 1.0;
         double a = i * 2.399963 + age * (0.15 + 0.12 * held);
         double rr = (2.4 + 1.6 * held) * (1.0 - life) + 0.1;
         Vec3 at = tip.subtract(axis.scale(0.4)).add(flat.scale(Math.cos(a) * rr)).add(up.scale(Math.sin(a) * rr));
         ember(v, m, at, tip.subtract(at).scale(0.15), 0.02 + 0.02 * life, i % 3 == 0 ? 16774352 : 16751152, gain * Math.sin(Math.PI * life));
      }

      for (int i = 0; i < 4; i++) {
         double life = (age * (0.045 + 0.04 * held) + i / 4.0) % 1.0;
         loop(
            v,
            m,
            tip.subtract(axis.scale(0.6 + 0.5 * life)),
            axis,
            (1.4 + 0.8 * held) * (1.0 - life) + 0.15,
            0.06 + 0.06 * life,
            16760928,
            gain * life * (0.6 + 0.4 * held)
         );
      }

      for (int i = 0; i < 16; i++) {
         double life = (hash(i, 96) + age * (0.04 + 0.03 * held)) % 1.0;
         double a = hash(i, 97) * (Math.PI * 2) + age * 0.2;
         double rr = (1.6 + 1.4 * held) * (1.0 - life);
         Vec3 at = tip.subtract(axis.scale(0.5)).add(flat.scale(Math.cos(a) * rr)).add(up.scale(Math.sin(a) * rr));
         flame(v, m, at, 0.1 + 0.16 * life, 15753230, gain * Math.sin(Math.PI * life) * (0.3 + 0.4 * held), false);
      }
   }

   private static void flight(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      Vec3 d = direction(f.target().subtract(f.origin()));
      Vec3 head = base.add(f.arrowPosition().subtract(f.origin()));
      double age = f.age();
      double length = Math.min(100.0, age * 4.0 + 8.0);
      fireBody(v, m, head.subtract(d.scale(14.0)), head, 1.7, 0.0, age * 1.6, 1.0, 3.0);
      fireBody(v, m, head.subtract(d.scale(9.0)), head.subtract(d.scale(0.8)), 1.1, 0.6, age * 2.1, 0.85, 7.0);
      licks(v, m, head.subtract(d.scale(2.5)), head.subtract(d.scale(0.3)), 1.5, age * 1.8, 1.0, 10, 1.0, d.scale(-1.0).add(0.0, 0.5, 0.0));
      line(v, m, head.subtract(d.scale(4.0)), head, 1.3, 0.0, 16774362, true, 1.0);

      for (int k = 0; k < 10; k++) {
         double a = k * (Math.PI * 2) / 10.0 + age * 0.2;
         Vec3 out = side(d).scale(Math.cos(a)).add(d.cross(side(d)).normalize().scale(Math.sin(a)));
         line(v, m, head, head.subtract(d.scale(7.0)).add(out.scale(3.2)), 0.12, 0.02, 16773336, true, 0.45);
      }

      Vec3 u = side(d);
      Vec3 w = d.cross(u).normalize();
      int n = 24;
      Vec3[] p = new Vec3[n];
      double[] r = new double[n];
      double[] al = new double[n];
      double[] rc = new double[n];
      double[] ac = new double[n];

      for (int lane = 0; lane < 4; lane++) {
         for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0);
            double angle = lane * (Math.PI * 2) / 4.0 + t * 7.0 - age * 0.35;
            double radius = (0.15 + 2.2 * t) * (0.8 + 0.3 * Math.sin(lane * 1.7));
            p[i] = head.subtract(d.scale(2.0 + length * t))
               .add(u.scale(Math.cos(angle) * radius))
               .add(w.scale(Math.sin(angle) * radius * 0.8))
               .add(0.0, t * t * 3.0, 0.0);
            r[i] = (0.8 + 2.4 * t) * (0.5 + 0.5 * noise(t * 8.0 - age * 0.5, lane * 3.7));
            al[i] = Math.pow(1.0 - t, 1.5) * (lane < 2 ? 0.75 : 0.55);
            rc[i] = r[i] * 0.32;
            ac[i] = al[i] * 0.9;
         }

         strand(v, m, p, r, al, n, lane % 2 == 0 ? 15218186 : 16738834, false);
         if (lane < 2) {
            strand(v, m, p, rc, ac, n, 16769696, true);
         }
      }

      licks(v, m, head.subtract(d.scale(3.0)), head.subtract(d.scale(Math.min(length, 60.0))), 2.2, age, 1.0, 14, 0.0, d.scale(-1.0).add(0.0, 0.6, 0.0));

      for (int i = 0; i < 24; i++) {
         double life = (i * 0.618034 + age * 0.06) % 1.0;
         Vec3 q = head.subtract(d.scale(3.0 + life * length * 0.8))
            .add(u.scale(Math.cos(i * 2.4) * (0.6 + life * 3.0)))
            .add(w.scale(Math.sin(i * 2.4) * (0.6 + life * 3.0)))
            .add(0.0, life * 2.5, 0.0);
         spark(v, m, q, 0.05 + 0.07 * (1.0 - life), 1.0 - life);
      }
   }

   private static void streamers(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      Vec3 d = direction(f.target().subtract(f.origin()));
      Vec3 head = base.add(f.arrowPosition().subtract(f.origin()));
      double age = f.age();

      for (int i = 0; i < 9; i++) {
         double life = (i * 0.618034 + age * 0.12) % 1.0;
         double a = i * 2.399963 + life * 6.0 + age * 0.9;
         Vec3 out = side(d).scale(Math.cos(a)).add(d.cross(side(d)).normalize().scale(Math.sin(a)));
         Vec3 root = head.subtract(d.scale(1.5 + life * 11.0)).add(out.scale(0.25 + life * 1.1));
         licks(
            v,
            m,
            root,
            root.subtract(d.scale(2.2 + life * 2.0)).add(out.scale(0.3)),
            0.7 * (1.0 - life) + 0.25,
            age * 2.0 + i,
            1.0 - life,
            1,
            i * 1.7,
            d.scale(-1.0)
         );
      }
   }

   private static void airRush(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      if (!(hitStop(f.casterId()) >= 0.0)) {
         Vec3 d = direction(f.target().subtract(f.origin()));
         Vec3 u = side(d);
         Vec3 w = d.cross(u).normalize();
         Vec3 head = base.add(f.arrowPosition().subtract(f.origin()));
         double age = f.age();
         double speed = smooth((age - 2.0) / 8.0);
         if (!(speed <= 0.0)) {
            for (int i = 0; i < 10; i++) {
               double life = (hash(i, 191) + age * (0.3 + 0.12 * hash(i, 192))) % 1.0;
               double a = hash(i, 193) * (Math.PI * 2);
               Vec3 out = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
               Vec3 from = head.add(d.scale(-0.2 - life * 3.0)).add(out.scale((0.45 + 0.3 * hash(i, 194)) * (1.0 + life * 1.2)));
               Vec3 to = from.subtract(d.scale(1.2 + 1.6 * hash(i, 195))).add(out.scale(0.2 * life));
               line(v, m, from, to, 0.04 + 0.03 * (1.0 - life), 0.006, 16773852, true, speed * Math.sin(Math.PI * life) * 0.6);
            }

            for (int r = 0; r < 3; r++) {
               double life = (age * 0.28 + r / 3.0) % 1.0;
               double radius = 0.5 + 2.6 * life;
               double alpha = speed * (1.0 - life) * (1.0 - life) * 0.5;
               Vec3 c = head.subtract(d.scale(0.4 + 7.0 * life));

               for (int i = 0; i < 20; i++) {
                  double a = i * (Math.PI * 2) / 20.0;
                  double b = (i + 1) * (Math.PI * 2) / 20.0;
                  line(
                     v,
                     m,
                     c.add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius)),
                     c.add(u.scale(Math.cos(b) * radius)).add(w.scale(Math.sin(b) * radius)),
                     0.05 * (1.0 - life) + 0.02,
                     0.0,
                     16771272,
                     true,
                     alpha
                  );
               }
            }

            for (int i = 0; i < 12; i++) {
               double a = i * (Math.PI * 2) / 12.0;
               double b = (i + 1) * (Math.PI * 2) / 12.0;
               Vec3 pa = head.add(d.scale(-0.2)).add(u.scale(Math.cos(a) * 0.55)).add(w.scale(Math.sin(a) * 0.55));
               Vec3 pb = head.add(d.scale(-0.2)).add(u.scale(Math.cos(b) * 0.55)).add(w.scale(Math.sin(b) * 0.55));
               line(v, m, pa, pb.subtract(d.scale(0.5)), 0.05, 0.02, 16774880, true, speed * 0.3 * (0.7 + 0.3 * Math.sin(age * 3.0 + i)));
            }
         }
      }
   }

   private static void impactPoint(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      double stop = hitStop(f.casterId());
      if (!(stop < 0.0)) {
         Vec3 head = base.add(f.arrowPosition().subtract(f.origin()));
         double k = smooth(stop);
         puff(v, m, head, 0.6 + 3.4 * k, 16777215, 0.9, true);
         puff(v, m, head, 2.0 + 7.0 * k, 16752704, 0.45, true);
         skyBeam(v, m, head, k, 1.0);
      }
   }

   static void skyBeam(VertexConsumer v, Matrix4f m, Vec3 foot, double rise, double gain) {
      if (!(gain <= 0.0)) {
         double top = 6.0 + 110.0 * rise;
         line(v, m, foot, foot.add(0.0, top, 0.0), 0.35 + 0.9 * rise, 0.2 + 0.5 * rise, 16774880, true, gain * 0.95);
         line(v, m, foot, foot.add(0.0, top * 0.8, 0.0), 1.2 + 2.4 * rise, 0.6, 16747056, true, gain * 0.4);

         for (int i = 0; i < 6; i++) {
            puff(v, m, foot.add(0.0, top * (i + 0.5) / 6.0, 0.0), 0.8 + 1.6 * rise, 16765056, gain * 0.25, true);
         }
      }
   }

   private static void lingeringTrail(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      Vec3 d = direction(f.target().subtract(f.origin()));
      double span = f.origin().distanceTo(f.target());
      double age = f.age();

      for (int k = 0; k < 28; k++) {
         double since = k + (age - Math.floor(age));
         double at = age - since;
         if (at < 0.0) {
            break;
         }

         double dist = Math.min(span, FugaSequence.flightDistance(at));
         double life = since / 28.0;
         double turn = k * 0.9 + at * 0.7;
         double spread = 0.05 * since;
         Vec3 p = base.add(d.scale(dist)).add(Math.cos(turn) * spread, Math.sin(turn) * spread, Math.sin(turn + 1.3) * spread);
         double fade = Math.exp(-since / 9.0) * smooth(since / 1.5);
         puff(v, m, p, 0.45 + 0.09 * since, mixRgb(16756800, 9050630, Math.min(1.0, life * 1.6)), fade * 0.32, true);
         puff(v, m, p, 0.6 + 0.12 * since, 2757132, fade * 0.18 * life, false);
      }
   }

   private static void launch(VertexConsumer v, Matrix4f m, FugaRenderer.Frame f, Vec3 base) {
      double age = f.age();
      if (!(age >= 18.0)) {
         Vec3 d = direction(f.target().subtract(f.origin()));
         Vec3 u = side(d);
         Vec3 w = d.cross(u).normalize();
         double k = age / 18.0;
         double fade = (1.0 - k) * (1.0 - k);
         Vec3 muzzle = base.add(d.scale(1.2));

         for (int i = 0; i < 12; i++) {
            double angle = i * (Math.PI * 2) / 12.0 + 0.2;
            Vec3 out = u.scale(Math.cos(angle)).add(w.scale(Math.sin(angle)));
            Vec3 from = muzzle.add(out.scale(0.4 + age * 0.5)).subtract(d.scale(age * 0.2));
            Vec3 to = from.add(out.scale(1.0 + age * 0.6)).subtract(d.scale(0.8 + age * 0.3));
            line(v, m, from, to, 0.05 * (1.0 - k) + 0.015, 0.0, 16771264, true, fade * 0.45);
         }

         Vec3 feet = base.subtract(0.0, 1.6, 0.0);
         vortex(v, m, feet, Math.atan2(d.z, d.x), age * 2.0, 1.0 - smooth(age / 10.0), 1.0 + age * 0.4);
         glowDisc(v, m, feet, 2.5 + age * 0.6, 16742944, fade * 0.5);
         if (age < 4.0) {
            for (int i = 0; i < 10; i++) {
               spark(
                  v,
                  m,
                  muzzle.add(u.scale(Math.cos(i * 2.4) * 0.5)).add(w.scale(Math.sin(i * 2.4) * 0.5)).add(d.scale(i * 0.15)),
                  0.25 * (1.0 - age / 4.0),
                  1.0 - age / 4.0
               );
            }
         }
      }
   }

   static double orbRadius(double age) {
      return 44.0;
   }

   static double orbScale(double age) {
      return 1.0 + 0.1 * smooth((age - 33.0) / 6.0) + 0.12 * smooth((age - 38.5) / 1.1);
   }

   static double orbGap(double age) {
      return 10.0 * Math.pow(1.0 - Math.pow(Math.max(0.0, Math.min(1.0, age / 39.0)), 1.25), 1.2);
   }

   static double orbCenter(double age) {
      return orbGap(age) + orbRadius(age) * orbScale(age);
   }

   private static void impactVolume(VertexConsumer v, Matrix4f m, double age, Vec3 base, double floor) {
      if (age < 40.0) {
         double k = orbScale(age);
         double radius = orbRadius(age) * k;
         double sy = 1.0;
         double sxz = 1.0;
         Vec3 center = base.add(0.0, orbCenter(age), 0.0);
         ellipsoid(v, m, center, radius * sxz, radius * sy, age, 12065286, 0.7, false);
         ellipsoid(v, m, center, radius * 0.9 * sxz, radius * 0.9 * sy, age + 3.0, 16738840, 0.5, false);
         ellipsoid(v, m, center, radius * 0.55 * sxz, radius * 0.55 * sy, age + 5.0, 16775406, 0.5, true);
         ring(v, m, center.add(0.0, -radius * sy * 0.62, 0.0), radius * sxz * 0.95, 0.9, 9065727, 0.4, true);

         for (int i = 0; i < 20; i++) {
            double a = i * (Math.PI * 2) / 20.0 + age * 0.05;
            double lift = Math.sin(i * 1.7);
            Vec3 root = center.add(Math.cos(a) * radius * sxz * 0.9, lift * radius * sy * 0.7, Math.sin(a) * radius * sxz * 0.9);
            Vec3 out = new Vec3(Math.cos(a), 0.6 + lift * 0.3, Math.sin(a)).normalize();
            licks(v, m, root, root.add(out.scale(radius * 0.25)), radius * 0.12, age * 2.0, 0.8, 2, i, out.add(0.0, 0.8, 0.0));
         }
      } else {
         double retract = 1.0 - smooth((age - 120.0) / 40.0);
         double height = 384.0 * easeOut((age - 40.0) / 5.0) * (1.0 - 0.85 * smooth((age - 116.0) / 42.0));
         double core = 36.0 * Math.sqrt(retract);
         if (!(core <= 0.0)) {
            int n = 26;
            Vec3[] p = new Vec3[n];
            double[] r = new double[n];
            double[] al = new double[n];

            for (int s = 0; s < 18; s++) {
               for (int i = 0; i < n; i++) {
                  double t = i / (n - 1.0);
                  double h = floor + (height - floor) * t;
                  double angle = s * (Math.PI * 2) / 18.0 - h * 0.028 + age * 0.14;
                  double flare = 1.0 + 0.75 * Math.exp(-Math.max(h, 0.0) / 13.0);
                  double top = 1.0 + 0.35 * smooth((h - 160.0) / 224.0);
                  double radius = core * flare * top * (0.45 + 0.45 * noise(s * 1.3, h * 0.02 - age * 0.12));
                  p[i] = base.add(Math.cos(angle) * radius, h, Math.sin(angle) * radius);
                  r[i] = core * 0.28 * (0.6 + 0.4 * noise(s * 2.1 + 7.0, h * 0.03 - age * 0.08)) * taper(h, floor, height);
                  al[i] = (s % 3 == 2 ? 0.55 : 0.5) * retract * Math.min(1.0, (height - h) / 30.0 + 0.2);
               }

               strand(v, m, p, r, al, n, s % 3 == 0 ? 13114886 : (s % 3 == 1 ? 15747594 : 16756800), s % 3 == 2);
            }

            for (int i = 0; i < n; i++) {
               double t = i / (n - 1.0);
               double h = floor + (height - floor) * t;
               p[i] = base.add(Math.sin(h * 0.05 + age * 0.2) * 1.5, h, Math.cos(h * 0.04 - age * 0.17) * 1.5);
               r[i] = core * 0.42 * (1.0 + 0.9 * Math.exp(-Math.max(h, 0.0) / 15.0)) * taper(h, floor, height);
               al[i] = 0.85 * retract;
            }

            strand(v, m, p, r, al, n, 16773584, true);

            for (int s = 0; s < 4; s++) {
               for (int i = 0; i < n; i++) {
                  double t = i / (n - 1.0);
                  double h = Math.max(0.0, floor) + (height - Math.max(0.0, floor)) * t;
                  double angle = s * (Math.PI * 2) / 4.0 + 1.0 - h * 0.03 + age * 0.09;
                  double radius = core * (1.1 + 0.5 * Math.exp(-h / 20.0) + 0.35 * noise(s * 4.3, h * 0.025 - age * 0.04));
                  p[i] = base.add(Math.cos(angle) * radius, h, Math.sin(angle) * radius);
                  r[i] = core * 0.08 * taper(h, floor, height);
                  al[i] = 0.4 * retract * smooth((h - 20.0) / 60.0);
               }

               strand(v, m, p, r, al, n, s % 2 == 0 ? 9065727 : 5016831, true);
            }
         }
      }
   }

   private static double taper(double h, double floor, double height) {
      return Math.min(1.0, Math.min(h - floor, height - h) / 30.0);
   }

   private static void impactAccents(VertexConsumer v, Matrix4f m, double age, Vec3 base) {
      if (age < 40.0) {
         double radius = orbRadius(age);
         double bottom = orbCenter(age) - radius * orbScale(age);
         double pressure = 1.0 - smooth(bottom / 48.0);

         for (int i = 0; i < 16; i++) {
            double near = 1.0 - smooth(orbGap(age) / 10.0);
            double a = i * (Math.PI * 2) / 16.0 + age * (0.02 + 0.01 * (i % 3));
            double rr = radius * (1.0 + 0.08 * i) + age * 0.4 + near * 24.0;
            Vec3 from = base.add(Math.cos(a) * rr, 0.6 + i % 4 * 0.7, Math.sin(a) * rr);
            Vec3 to = base.add(Math.cos(a + 0.5) * (rr + 6.0), 1.5 + i % 3, Math.sin(a + 0.5) * (rr + 6.0));
            strand(
               v,
               m,
               new Vec3[]{from, from.lerp(to, 0.5), to},
               new double[]{2.4 + pressure * 3.0, 1.8 + pressure * 1.5, 1.2},
               new double[]{0.3 * pressure * (1.0 + 1.4 * near), 0.3 * pressure * (1.0 + 1.4 * near), 0.18 * pressure},
               3,
               7233104,
               false,
               false
            );
         }

         for (int i = 0; i < 24; i++) {
            double life = (i * 0.618034 + age * 0.03) % 1.0;
            double a = i * 2.399963;
            Vec3 p = base.add(Math.cos(a) * radius * (1.3 - life * 0.4), life * bottom * 0.6, Math.sin(a) * radius * (1.3 - life * 0.4));
            line(v, m, p, p.add(0.0, 0.6, 0.0), 0.35, 0.35, 3023902, false, pressure * (1.0 - life));
         }

         Vec3 c = base.add(0.0, orbCenter(age), 0.0);
         double R = radius * orbScale(age);
         double live = 1.0 - smooth((age - 38.0) / 1.6);

         for (int i = 0; i < 72; i++) {
            double life = (i * 0.618034 + age * 0.05) % 1.0;
            double y = 1.0 - 2.0 * ((i + 0.5) / 72.0);
            double band = Math.sqrt(1.0 - y * y);
            double a = i * 2.399963 + age * 0.01;
            Vec3 dir = new Vec3(Math.cos(a) * band, y, Math.sin(a) * band);
            puff(
               v,
               m,
               c.add(dir.scale(R * (1.0 + 0.55 * life))),
               R * (0.12 + 0.2 * life),
               i % 3 == 0 ? 16751168 : 16730644,
               Math.sin(Math.PI * life) * live * 0.2,
               true
            );
         }

         for (int i = 0; i < 48; i++) {
            double life = (i * 0.618034 + age * 0.11) % 1.0;
            double a = i * 2.399963;
            double e = Math.asin(i * 0.37 % 1.0 * 1.6 - 0.6);
            Vec3 dir = new Vec3(Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e));
            Vec3 from = c.add(dir.scale(R * (1.05 + life * 0.9)));
            line(
               v,
               m,
               from,
               from.add(dir.scale(5.0 + life * 14.0)),
               0.07 * (1.0 - life) + 0.03,
               0.0,
               i % 3 == 0 ? 16769184 : 16747056,
               true,
               (1.0 - life) * 0.6 * live
            );
         }

         double falling = 1.0 - smooth((age - 28.0) / 6.0);

         for (int i = 0; i < 40; i++) {
            double life = (i * 0.618034 + age * 0.16) % 1.0;
            double a = i * 2.399963;
            double rr = R * (0.75 + 0.4 * (i * 0.37 % 1.0));
            Vec3 from = c.add(Math.cos(a) * rr, -R * 0.3 + life * R * 2.2, Math.sin(a) * rr);
            line(
               v,
               m,
               from,
               from.add(0.0, 14.0 + 22.0 * (i * 0.53 % 1.0), 0.0),
               0.12 * (1.0 - life) + 0.05,
               0.02,
               i % 4 == 0 ? 16774360 : 16754768,
               true,
               Math.sin(Math.PI * life) * falling * 0.55
            );
         }

         double under = orbCenter(age) - R;

         for (int i = 0; i < 60; i++) {
            double life = (hash(i, 51) + age * (0.035 + 0.03 * hash(i, 52))) % 1.0;
            double a = hash(i, 53) * (Math.PI * 2);
            double rr = R * Math.sqrt(hash(i, 54)) * 0.85;
            double drop = Math.min(1.0, life * 1.8);
            double landed = Math.max(0.0, life - 0.5555555555555556) / 0.4444444444444444;
            double size = 0.25 + 0.35 * hash(i, 56);
            Vec3 p = base.add(Math.cos(a) * rr, Math.max(0.25, (under + 1.0) * (1.0 - drop * drop)), Math.sin(a) * rr);
            double fade = smooth(life / 0.06) * (1.0 - smooth((life - 0.9) / 0.1)) * live;
            dot(v, m, p, size * (1.0 + 0.3 * landed), 1705988, fade * 0.9, false);
            dot(
               v,
               m,
               p.add(0.0, 0.02, 0.0),
               size * (0.75 - 0.35 * landed),
               mixRgb(16760904, 10099204, Math.min(1.0, landed * 1.6)),
               fade * (1.0 - 0.6 * landed),
               true
            );
            line(v, m, p, p.add(0.0, 1.2 + 2.0 * hash(i, 55), 0.0), size * 0.5, 0.0, 16747050, true, landed > 0.0 ? 0.0 : fade * 0.5);
            dot(v, m, p.add(0.0, 0.03, 0.0), size * 2.2, 16732176, landed > 0.0 ? fade * 0.3 * (1.0 - landed) : 0.0, true);
         }

         for (int i = 0; i < 70; i++) {
            double life = (hash(i, 61) + age * (0.04 + 0.03 * hash(i, 62))) % 1.0;
            double a = hash(i, 63) * (Math.PI * 2);
            double rr = R * (0.2 + 0.9 * hash(i, 64));
            Vec3 p = base.add(Math.cos(a) * (rr + life * 4.0), 0.3 + life * (3.0 + 9.0 * hash(i, 65)) * pressure, Math.sin(a) * (rr + life * 4.0));
            dot(v, m, p, 0.25 + 0.45 * hash(i, 66), i % 4 == 0 ? 5907472 : 1971730, Math.sin(Math.PI * life) * pressure * live, false);
         }

         double beam = 1.0 - smooth(age / 2.5);
         if (beam > 0.0) {
            skyBeam(v, m, base, 1.0, beam);
         }

         double flare = smooth((age - 38.0) / 2.0);
         if (flare > 0.0) {
            puff(v, m, c.subtract(0.0, R * 0.6, 0.0), R * (0.8 + 0.8 * flare), 16774360, flare * 0.55, true);
            puff(v, m, c, R * (1.2 + flare), 16756832, flare * 0.3, true);
         }
      } else {
         double t = age - 40.0;
         double retract = 1.0 - smooth((age - 120.0) / 40.0);
         if (t < 8.0) {
            puff(v, m, base.add(0.0, 20.0, 0.0), 60.0 + t * 20.0, 16777215, (1.0 - t / 8.0) * 0.9, true);
            puff(v, m, base.add(0.0, 20.0, 0.0), 100.0 + t * 30.0, 16752712, (1.0 - t / 8.0) * 0.3, true);
         }

         if (t < 16.0) {
            for (int i = 0; i < 36; i++) {
               double a = i * (Math.PI * 2) / 18.0 + Math.sin(i * 3.1) * 0.2;
               double elev = 0.15 + 0.5 * Math.abs(Math.sin(i * 1.9));
               double len = 25.0 + t * 4.5;
               Vec3 dir = new Vec3(Math.cos(a) * Math.cos(elev), Math.sin(elev), Math.sin(a) * Math.cos(elev));
               line(
                  v,
                  m,
                  base.add(dir.scale(4.0 + t * 1.5)),
                  base.add(dir.scale(4.0 + t * 1.5 + len)),
                  1.3 * (1.0 - t / 16.0) + 0.2,
                  0.0,
                  i % 3 == 0 ? 11563263 : 16770736,
                  true,
                  1.0 - t / 16.0
               );
            }
         }

         if (t < 60.0) {
            for (int i = 0; i < 36; i++) {
               double a = i * 2.399963;
               double rr = 6.0 + i * 5.3 % 30.0 + t * 1.2;
               Vec3 out = new Vec3(Math.cos(a), 1.2 + 0.6 * Math.sin(i * 1.7), Math.sin(a));
               Vec3 root = base.add(Math.cos(a) * rr, 2 + i % 5 * 3, Math.sin(a) * rr);
               licks(v, m, root, root.add(out.scale(6.0)), 6 + 4 * (i % 3), age * 2.0 + i, 1.0 - t / 60.0, 2, i, out);
            }
         }

         if (t < 70.0) {
            for (int k = 0; k < 2; k++) {
               double front = 20.0 + t * (k == 0 ? 6.5 : 4.5);
               double fade = 1.0 - t / 70.0;

               for (int i = 0; i < 24; i++) {
                  double a = i * (Math.PI * 2) / 24.0 + k * 0.13 + Math.sin(i * 2.7) * 0.1;
                  Vec3 root = base.add(Math.cos(a) * front, 1.0, Math.sin(a) * front);
                  line(
                     v,
                     m,
                     root,
                     root.add(Math.cos(a) * 10.0, 6.0 + 4.0 * Math.sin(i * 1.3), Math.sin(a) * 10.0),
                     3.0 * fade + 1.0,
                     0.5,
                     k == 0 ? 5915702 : 16748608,
                     k == 1,
                     fade * 0.5
                  );
               }
            }
         }

         if (t < 20.0) {
            for (int side = -1; side <= 1; side += 2) {
               line(
                  v,
                  m,
                  base.add(0.0, 6.0, 0.0),
                  base.add(side * (30.0 + t * 12.0), 6.0, 0.0),
                  3.0 * (1.0 - t / 20.0) + 0.5,
                  0.0,
                  16767136,
                  true,
                  (1.0 - t / 20.0) * 0.9
               );
            }
         }

         if (t < 70.0) {
            for (int i = 0; i < 90; i++) {
               double a = i * 2.399963;
               double speed = 1.0 + i % 7 * 0.35;
               double up = 1.2 + i % 5 * 0.5;
               double x = Math.cos(a) * speed * t;
               double z = Math.sin(a) * speed * t;
               double y = up * t - 0.045 * t * t;
               if (!(y < -2.0)) {
                  Vec3 p = base.add(x, y + 2.0, z);
                  line(
                     v,
                     m,
                     p,
                     p.add(Math.cos(a) * 1.2, 0.8, Math.sin(a) * 1.2),
                     0.7 + i % 3 * 0.4,
                     0.4,
                     i % 4 == 0 ? 16742960 : 2760988,
                     i % 4 == 0,
                     1.0 - t / 70.0
                  );
               }
            }
         }

         for (int i = 0; i < 16; i++) {
            double a = i * (Math.PI * 2) / 16.0 + age * 0.01;
            double rr = 48.0 * (0.9 + 0.25 * Math.sin(i * 2.3));
            Vec3 root = base.add(Math.cos(a) * rr, 0.5, Math.sin(a) * rr);
            licks(
               v,
               m,
               root,
               root.add(Math.cos(a + 1.57) * 8.0, 0.0, Math.sin(a + 1.57) * 8.0),
               3.0 * retract + 0.6,
               age * 1.5,
               0.75 * retract + 0.15 * smooth((age - 110.0) / 10.0),
               2,
               i,
               new Vec3(Math.cos(a) * 0.3, 1.0, Math.sin(a) * 0.3)
            );
         }

         if (retract > 0.0) {
            for (int arm = 0; arm < 8; arm++) {
               int n = 16;
               Vec3[] p = new Vec3[n];
               double[] r = new double[n];
               double[] al = new double[n];

               for (int i = 0; i < n; i++) {
                  double q = i / (n - 1.0);
                  double a = arm * (Math.PI * 2) / 8.0 + age * 0.06 - q * 2.2;
                  double rr = 48.0 * (1.1 + q * 1.2);
                  p[i] = base.add(Math.cos(a) * rr, 1.0 + q * 14.0, Math.sin(a) * rr);
                  r[i] = 2.0 + 4.0 * q;
                  al[i] = retract * 0.35 * Math.sin(Math.PI * q);
               }

               strand(v, m, p, r, al, n, arm % 2 == 0 ? 8022624 : 16748624, arm % 2 == 1);
            }
         }

         if (t < 60.0) {
            for (int i = 0; i < 12; i++) {
               double life = (i * 0.618034 + age * 0.05) % 1.0;
               double a = i * 2.399963;
               glint(
                  v,
                  m,
                  base.add(Math.cos(a) * (20 + i * 5 % 40), 6 + i * 13 % 60, Math.sin(a) * (20 + i * 5 % 40)),
                  8.0 * Math.sin(Math.PI * life),
                  Math.sin(Math.PI * life) * (1.0 - t / 60.0),
                  i % 3 == 0 ? 16777215 : 16758856
               );
            }
         }

         double pillar = 384.0 * easeOut((age - 40.0) / 5.0) * (1.0 - 0.85 * smooth((age - 116.0) / 42.0));

         for (int i = 0; i < 48; i++) {
            double life = (i * 0.618034 + age * 0.02) % 1.0;
            double a = i * 2.399963 + age * 0.03;
            double rr = 48.0 * (0.75 + 0.35 * life);
            puff(
               v,
               m,
               base.add(Math.cos(a) * rr, 4.0 + life * pillar * 0.55, Math.sin(a) * rr),
               6.0 + 10.0 * life,
               i % 3 == 0 ? 16752704 : 16734744,
               retract * 0.13 * Math.sin(Math.PI * life),
               true
            );
         }

         if (retract > 0.0) {
            for (int i = 0; i < 96; i++) {
               double clock = t - i * 0.3125;
               if (!(clock <= 0.0)) {
                  double cycle = Math.floor(clock / 30.0);
                  double life = clock - cycle * 30.0;
                  double seed = hash(i, (int)cycle + 40);
                  double ang = i * 2.399963 + cycle * 0.7;
                  double rr = 43.2 + life * 4.5;
                  double size = 5.0 + seed * 7.0 + life * 0.4;
                  Vec3 c = base.add(Math.cos(ang) * rr, size * 0.6 + seed * 8.0, Math.sin(ang) * rr);
                  double al = retract * smooth(life / 3.0) * (1.0 - smooth((life - 22.0) / 8.0));
                  puff(v, m, c, size * 1.2, 2757648, al * 0.12, false);
                  puff(v, m, c, size, seed < 0.5 ? 16738848 : 16752704, al * 0.1, true);
               }
            }
         }

         for (int i = 0; i < 160; i++) {
            double life = (i * 0.618034 + age * 0.012) % 1.0;
            double a = i * 2.399963;
            double rr = 6.0 + i * 7.3 % 46.0;
            Vec3 p = base.add(Math.cos(a + life) * rr, life * life * 120.0, Math.sin(a + life) * rr);
            spark(v, m, p, 0.25 + 0.35 * (1.0 - life), (1.0 - life) * (0.4 + 0.6 * retract));
         }
      }
   }

   private static boolean layer(boolean glow) {
      return pass < 0 || pass == 1 == glow;
   }

   static void strand(VertexConsumer v, Matrix4f m, Vec3[] p, double[] w, double[] a, int n, int color, boolean glow) {
      strand(v, m, p, w, a, n, color, glow, true);
   }

   static void strand(VertexConsumer v, Matrix4f m, Vec3[] p, double[] w, double[] a, int n, int color, boolean glow, boolean torn) {
      if (layer(glow) && n >= 2) {
         Vec3[] s = new Vec3[n];

         for (int i = 0; i < n; i++) {
            Vec3 tangent = p[Math.min(n - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            Vec3 c = tangent.cross(p[i]);
            s[i] = (c.lengthSqr() < 1.0E-12 ? side(tangent) : c.normalize()).scale(w[i]);
         }

         float[] u = new float[n];
         double run = -flow * 0.09;
         double widest = 0.0;

         for (int i = 0; i < n; i++) {
            if (i > 0) {
               run += p[i].distanceTo(p[i - 1]) * 0.45;
            }

            u[i] = (float)run;
            widest = Math.max(widest, w[i]);
         }

         boolean soft = !torn || widest < 0.22;
         float edge = soft ? 0.9375F : 0.0078125F;
         float spine = soft ? 0.9375F : 0.4375F;
         float far = soft ? 0.9375F : 0.8671875F;
         double rim = soft ? 0.0 : 0.9;

         for (int i = 0; i + 1 < n; i++) {
            vertex(v, m, p[i].add(s[i]), color, a[i] * rim, u[i], edge);
            vertex(v, m, p[i], color, a[i], u[i], spine);
            vertex(v, m, p[i + 1], color, a[i + 1], u[i + 1], spine);
            vertex(v, m, p[i + 1].add(s[i + 1]), color, a[i + 1] * rim, u[i + 1], edge);
            vertex(v, m, p[i], color, a[i], u[i], spine);
            vertex(v, m, p[i].subtract(s[i]), color, a[i] * rim, u[i], far);
            vertex(v, m, p[i + 1].subtract(s[i + 1]), color, a[i + 1] * rim, u[i + 1], far);
            vertex(v, m, p[i + 1], color, a[i + 1], u[i + 1], spine);
         }
      }
   }

   private static void line(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double wa, double wb, int color, boolean glow, double alpha) {
      strand(v, m, new Vec3[]{a, a.lerp(b, 0.5), b}, new double[]{wa, (wa + wb) * 0.5, wb}, new double[]{alpha, alpha, alpha * 0.6}, 3, color, glow, false);
   }

   private static void spark(VertexConsumer v, Matrix4f m, Vec3 p, double size, double alpha) {
      dot(v, m, p, size * 1.2, 16770736, alpha, true);
   }

   private static void fireBody(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, double bend, double age, double gain, double seed) {
      Vec3 along = direction(b.subtract(a));
      Vec3 u = side(along);
      Vec3 w = along.cross(u).normalize();
      int n = 18;
      Vec3[] p = new Vec3[n];
      double[] r = new double[n];
      double[] al = new double[n];
      int[] colors = new int[]{12065283, 14825990, 16738834, 16751150, 16762458, 16770984, 16775398};

      for (int k = 0; k < colors.length; k++) {
         double phase = seed + k * 1.7;
         double spread = (k < 3 ? 0.55 : (k < 5 ? 0.32 : 0.1)) * width;
         double thick = (k < 3 ? 0.75 : (k < 5 ? 0.38 : 0.16)) * width;

         for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0);
            double envelope = Math.sin(Math.PI * t);
            double soft = Math.pow(envelope, 0.55);
            double wobble = Math.sin(t * (5.0 + k * 0.7) - age * (0.11 + k * 0.013) + phase);
            double twist = Math.cos(t * (4.0 + k * 0.5) - age * (0.09 + k * 0.011) + phase * 1.3);
            p[i] = a.lerp(b, t)
               .add(u.scale(wobble * spread * soft))
               .add(w.scale(twist * spread * 0.7 * soft))
               .add(0.0, envelope * bend * (1.0 + 0.3 * Math.sin(age * 0.07 + phase)), 0.0);
            r[i] = thick * (0.3 + 0.7 * soft) * (0.55 + 0.45 * noise(t * 6.0 - age * 0.16, k * 3.1 + seed));
            al[i] = gain * (k < 3 ? 0.85 : (k < 5 ? 0.7 : 0.8)) * Math.min(1.0, soft * 1.6);
         }

         strand(v, m, p, r, al, n, colors[k], k >= 5);
      }
   }

   private static void licks(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double size, double age, double gain, int count, double seed, Vec3 drift) {
      licks(v, m, a, b, size, age, gain, count, seed, drift, 15747594, 16764786, false);
   }

   private static void licks(
      VertexConsumer v,
      Matrix4f m,
      Vec3 a,
      Vec3 b,
      double size,
      double age,
      double gain,
      int count,
      double seed,
      Vec3 drift,
      int body,
      int core,
      boolean glowBody
   ) {
      if (!(size <= 0.0) && !(gain <= 0.0)) {
         Vec3 u = side(direction(b.subtract(a)));
         Vec3 flow = direction(drift);
         int n = 8;
         Vec3[] p = new Vec3[n];
         double[] r = new double[n];
         double[] al = new double[n];
         double[] rc = new double[n];
         double[] ac = new double[n];

         for (int j = 0; j < count; j++) {
            double clock = j * 0.6180339 + age * (0.028 + 0.006 * (j % 3)) + seed;
            double life = clock - Math.floor(clock);
            double cycle = Math.floor(clock);
            double at = (j * 0.37 + cycle * 0.618034) % 1.0;
            double turn = (j + (int)cycle & 1) == 0 ? 1.0 : -1.0;
            double swell = Math.sin(Math.PI * Math.min(1.0, life * 1.3));
            double length = size * (1.5 + 1.4 * noise(cycle, j + seed)) * swell;
            Vec3 root = a.lerp(b, 0.1 + 0.8 * at);

            for (int i = 0; i < n; i++) {
               double q = i / (n - 1.0);
               p[i] = root.add(flow.scale(q * length))
                  .add(u.scale(turn * Math.sin(q * 3.4 + age * 0.25 + j) * size * 0.8 * q))
                  .add(0.0, q * q * size * 0.3, 0.0);
               r[i] = size * 0.85 * Math.pow(1.0 - q, 0.7) * (0.45 + 0.55 * swell);
               al[i] = gain * (1.0 - life) * (1.0 - q * 0.5);
               rc[i] = r[i] * 0.42;
               ac[i] = al[i] * 0.85;
            }

            strand(v, m, p, r, al, n, body, glowBody, false);
            strand(v, m, p, rc, ac, n, core, true, false);
         }
      }
   }

   private static void ellipsoid(
      VertexConsumer v, Matrix4f m, Vec3 center, double horizontal, double vertical, double age, int color, double alpha, boolean glow
   ) {
      if (layer(glow)) {
         for (int band = 0; band < 12; band++) {
            for (int s = 0; s < 24; s++) {
               Vec3[] p = new Vec3[4];

               for (int c = 0; c < 4; c++) {
                  double theta = (band + (c >= 2 ? 1 : 0)) * Math.PI / 12.0 - (Math.PI / 2);
                  double a = (s + (c != 1 && c != 2 ? 0 : 1)) * (Math.PI * 2) / 24.0;
                  double r = 1.0 + 0.07 * Math.sin(a * 7.0 + theta * 9.0 + age * 0.15) + 0.04 * Math.sin(a * 13.0 - theta * 5.0 - age * 0.23);
                  p[c] = center.add(
                     Math.cos(a) * Math.cos(theta) * horizontal * r, Math.sin(theta) * vertical * r, Math.sin(a) * Math.cos(theta) * horizontal * r
                  );
               }

               quad(v, m, p[0], p[1], p[2], p[3], color, alpha);
            }
         }
      }
   }

   private static void ring(VertexConsumer v, Matrix4f m, Vec3 base, double radius, double width, int color, double alpha, boolean glow) {
      ring(v, m, base, radius, width, color, alpha, glow, 0.0);
   }

   private static void ring(VertexConsumer v, Matrix4f m, Vec3 base, double radius, double width, int color, double alpha, boolean glow, double turn) {
      ring(v, m, base, radius, width, color, alpha, glow, turn, 64);
   }

   private static void ring(VertexConsumer v, Matrix4f m, Vec3 base, double radius, double width, int color, double alpha, boolean glow, double turn, int n) {
      if (layer(glow) && !(alpha <= 0.0)) {
         double drift = radius * 0.17;
         Vec3[] in = new Vec3[n + 1];
         Vec3[] mid = new Vec3[n + 1];
         Vec3[] out = new Vec3[n + 1];
         double[] k = new double[n + 1];

         for (int i = 0; i <= n; i++) {
            double a = i * (Math.PI * 2) / n;
            double c = Math.cos(a + turn);
            double s = Math.sin(a + turn);
            double w = width * (0.45 + 0.9 * noise(a * 7.0 + drift, 3.1));
            double torn = noise(a * 4.0 - drift * 0.6, radius * 0.05 + 1.7);
            k[i] = alpha * Math.min(1.0, 0.15 + 1.4 * torn * torn);
            in[i] = base.add(c * (radius - w), 0.0, s * (radius - w));
            mid[i] = base.add(c * radius, 0.0, s * radius);
            out[i] = base.add(c * (radius + w * 1.3), 0.0, s * (radius + w * 1.3));
         }

         for (int i = 0; i < n; i++) {
            float ua = (float)(i * 8.0 / n + drift * 0.3);
            float ub = (float)((i + 1) * 8.0 / n + drift * 0.3);
            vertex(v, m, in[i], color, 0.0, ua, 0.9375F);
            vertex(v, m, in[i + 1], color, 0.0, ub, 0.9375F);
            vertex(v, m, mid[i + 1], color, k[i + 1], ub, 0.9375F);
            vertex(v, m, mid[i], color, k[i], ua, 0.9375F);
            vertex(v, m, mid[i], color, k[i], ua, 0.9375F);
            vertex(v, m, mid[i + 1], color, k[i + 1], ub, 0.9375F);
            vertex(v, m, out[i + 1], color, 0.0, ub, 0.9375F);
            vertex(v, m, out[i], color, 0.0, ua, 0.9375F);
         }

         int hot = hot(color);

         for (int f = 0; f < 2; f++) {
            double off = (f == 0 ? -0.35 : 0.45) * width;
            double thin = width * 0.08 + 0.02;

            for (int i = 0; i < n; i++) {
               double a = i * (Math.PI * 2) / n;
               double b = (i + 1) * (Math.PI * 2) / n;
               double ka = k[i] * noise(a * 11.0 + drift * 1.3, f * 5 + 9);
               double kb = k[i + 1] * noise(b * 11.0 + drift * 1.3, f * 5 + 9);
               quad2(v, m, base, radius + off, thin, a + turn, b + turn, hot, ka, kb);
            }
         }
      }
   }

   private static void quad2(VertexConsumer v, Matrix4f m, Vec3 base, double r, double w, double a, double b, int color, double ka, double kb) {
      vertex(v, m, base.add(Math.cos(a) * (r - w), 0.0, Math.sin(a) * (r - w)), color, ka);
      vertex(v, m, base.add(Math.cos(b) * (r - w), 0.0, Math.sin(b) * (r - w)), color, kb);
      vertex(v, m, base.add(Math.cos(b) * (r + w), 0.0, Math.sin(b) * (r + w)), color, kb);
      vertex(v, m, base.add(Math.cos(a) * (r + w), 0.0, Math.sin(a) * (r + w)), color, ka);
   }

   private static int hot(int c) {
      return ((c >> 16 & 0xFF) + 255) / 2 << 16 | ((c >> 8 & 0xFF) + 255) / 2 << 8 | ((c & 0xFF) + 255) / 2;
   }

   private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, double alpha) {
      vertex(v, m, a, color, alpha);
      vertex(v, m, b, color, alpha);
      vertex(v, m, c, color, alpha);
      vertex(v, m, d, color, alpha);
   }

   private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int color, double alpha) {
      vertex(v, m, p, color, alpha, 0.5F, 0.9375F);
   }

   private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int color, double alpha, float u, float tv) {
      int opacity = (int)(Math.max(0.0, Math.min(1.0, alpha)) * 255.0);
      v.vertex(m, (float)p.x, (float)p.y, (float)p.z).uv(u, tv).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, opacity).endVertex();
   }

   private static double hash(int x, int y) {
      int h = x * 374761393 + y * 668265263;
      h = (h ^ h >>> 13) * 1274126177;
      return ((h ^ h >>> 16) & 65535) / 65535.0;
   }

   static double noise(double x, double y) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      double fx = x - ix;
      double fy = y - iy;
      fx = fx * fx * (3.0 - 2.0 * fx);
      fy = fy * fy * (3.0 - 2.0 * fy);
      double a = hash(ix, iy);
      double b = hash(ix + 1, iy);
      double c = hash(ix, iy + 1);
      double d = hash(ix + 1, iy + 1);
      return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
   }

   private static double easeOut(double t) {
      t = Math.max(0.0, Math.min(1.0, t));
      return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
   }

   private static Vec3 direction(Vec3 d) {
      return d.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : d.normalize();
   }

   private static Vec3 side(Vec3 d) {
      return direction(d.cross(Math.abs(d.y) > 0.98 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)));
   }

   private static double smooth(double t) {
      t = Math.max(0.0, Math.min(1.0, t));
      return t * t * (3.0 - 2.0 * t);
   }

   private static boolean finite(double... values) {
      for (double v : values) {
         if (!Double.isFinite(v)) {
            return false;
         }
      }

      return true;
   }

   static {
      for (int i = 0; i < 14; i++) {
         PUFF_COS[i] = Math.cos(i * (Math.PI * 2) / 14.0);
         PUFF_SIN[i] = Math.sin(i * (Math.PI * 2) / 14.0);
      }
   }

   record Aftermath(Vec3 center, long at, long cast) {
   }

   private record Clock(long token, int phase, double age, double at) {
   }

   private record Deferred(Matrix4f pose, Matrix4f modelView, Matrix4f projection, Camera camera, float partial) {
   }

   private static final class FlameTexture extends DynamicTexture {
      FlameTexture(NativeImage pixels) {
         super(pixels);
         this.setFilter(true, false);
      }
   }

   public record Frame(int casterId, long startedAt, int phase, double age, Vec3 origin, Vec3 target) {
      public Vec3 center() {
         return this.origin;
      }

      public Vec3 arrowPosition() {
         Vec3 travel = this.target.subtract(this.origin);
         double distance = travel.length();
         return distance < 1.0E-4 ? this.origin : this.origin.add(travel.scale(Math.min(distance, FugaSequence.flightDistance(this.age)) / distance));
      }
   }

   private record Glow(int caster, long token, long phaseAt, Vec3 center) {
   }
}

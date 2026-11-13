package com.flash.godwand;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class FugaManager {
   private static final Map<UUID, FugaManager.Task> TASKS = new LinkedHashMap<>();
   private static final List<FugaManager.Settling> SETTLING = new ArrayList<>();
   static final int FORCE_PER_TICK = 2;

   private FugaManager() {
   }

   public static boolean beginCharge(ServerPlayer player) {
      if (!player.isAlive()
         || player.isSpectator()
         || player.isPassenger()
         || !GodWandConfig.SERVER.isDimensionAllowed(player.level().dimension().location().toString())
         || TASKS.containsKey(player.getUUID())
         || TASKS.values().stream().filter(t -> t.world == player.level()).count() >= 4L) {
         return false;
      } else if (true) {
         FugaManager.Task task = new FugaManager.Task(player);
         TASKS.put(player.getUUID(), task);
         broadcast(task, 0);
         return true;
      } else {
         return false;
      }
   }

   public static boolean movementLocked(ServerPlayer player) {
      FugaManager.Task task = TASKS.get(player.getUUID());
      return task != null && FugaSequence.movementLocked(task.phase) && player.isAlive() && !player.isSpectator() && task.world == player.serverLevel();
   }

   public static boolean invulnerable(ServerPlayer player) {
      FugaManager.Task task = TASKS.get(player.getUUID());
      return task != null && !invalid(task);
   }

   public static void cancelCharge(ServerPlayer player) {
      FugaManager.Task task = TASKS.remove(player.getUUID());
      if (task != null) {
         broadcast(task, 3);
      }
   }

   public static void releaseCharge(ServerPlayer player) {
      FugaManager.Task task = TASKS.get(player.getUUID());
      if (task != null && task.phase == 0) {
         if (!invalid(task) && FugaSequence.ready(task.world.getGameTime(), task.started)) {
            task.origin = task.position = player.getEyePosition();
            task.target = FugaSequence.launchTarget(task.origin, player.getLookAngle());
            task.phase = 1;
            task.phaseAt = task.world.getGameTime();
            broadcast(task, 1);
            Vec3 kick = player.getLookAngle().scale(-0.55);
            player.setDeltaMovement(kick.x, Math.max(0.12, kick.y * 0.3), kick.z);
            player.hurtMarked = true;
         } else {
            cancelCharge(player);
         }
      }
   }

   private static boolean invalid(FugaManager.Task task) {
      return !task.caster.isAlive()
         || task.caster.hasDisconnected()
         || task.caster.isSpectator()
         || task.caster.level() != task.world
         || !com.flash.yas.YasPowers.has(task.caster);
   }

   public static void tick(ServerLevel world) {
      SpellDamage.poolExperience(world);
      int budget = 32768;
      long deadline = System.nanoTime() + 8000000L;
      Iterator<FugaManager.Task> iterator = TASKS.values().iterator();

      while (iterator.hasNext()) {
         FugaManager.Task task = iterator.next();
         if (task.world == world) {
            if (!invalid(task) && (task.phase != 0 || GodWandSkills.charging(task.caster, 6))) {
               if (FugaSequence.movementLocked(task.phase)) {
                  task.caster.setDeltaMovement(Vec3.ZERO);
                  task.caster.fallDistance = 0.0F;
                  if (task.caster.position().distanceToSqr(task.anchor) > 1.0E-4) {
                     task.caster.connection.teleport(task.anchor.x, task.anchor.y, task.anchor.z, task.caster.getYRot(), task.caster.getXRot());
                  }
               }

               if (task.phase == 1) {
                  advance(task);
               }

               if (task.phase == 2) {
                  forceSome(world, task.toForce, task.forced, 2);
                  long age = world.getGameTime() - task.phaseAt;
                  if (age < 80L && world.getGameTime() % 10L == 0L) {
                     slow(task);
                  }

                  if (age >= 305L) {
                     broadcast(task, 4);
                     iterator.remove();
                     if (task.crater != null && !task.crater.finished() && task.terrain) {
                        SETTLING.add(new FugaManager.Settling(world, task.crater, task.phaseAt, task.forced));
                     } else {
                        release(world, task.forced);
                     }
                  } else {
                     if (age >= 80L) {
                        damage(task, age);
                        outflow(task, age);
                     }

                     if (age >= 8L && age < 72L && budget > 0 && task.crater != null && task.terrain) {
                        budget -= task.crater.upheave(world, budget, age);
                     }

                     if (age >= 44L && age < 84L && budget > 0 && task.crater != null && task.terrain) {
                        budget -= task.crater.press(world, budget, age);
                     }

                     if (FugaSequence.terrainActive(age) && budget > 0 && task.terrain) {
                        budget -= task.crater.carve(world, budget, deadline, age);
                     }
                  }
               }
            } else {
               broadcast(task, 3);
               release(task.world, task.forced);
               iterator.remove();
            }
         }
      }

      settle(world, budget, deadline);
   }

   private static void settle(ServerLevel world, int budget, long deadline) {
      Iterator<FugaManager.Settling> iterator = SETTLING.iterator();

      while (iterator.hasNext()) {
         FugaManager.Settling s = iterator.next();
         if (s.world() == world) {
            double age = world.getGameTime() - s.impactAt();
            if (s.crater().finished() || !FugaSequence.terrainActive(age)) {
               release(s.world(), s.forced());
               iterator.remove();
            } else if (budget > 0) {
               budget -= s.crater().carve(world, budget, deadline, age);
            }
         }
      }
   }

   private static void advance(FugaManager.Task task) {
      Vec3 from = task.position;
      Vec3 desired = FugaSequence.arrowPosition(task.origin, task.target, task.world.getGameTime() - task.phaseAt);
      Vec3 end = loadedEndpoint(task.world, from, desired);
      boolean boundary = end.distanceToSqr(desired) > 1.0E-12;
      BlockHitResult block = task.world.clip(new ClipContext(from, end, Block.COLLIDER, Fluid.NONE, task.caster));
      boolean collided = block.getType() != Type.MISS;
      if (collided) {
         end = block.getLocation();
      }

      double nearest = from.distanceToSqr(end);

      for (Entity entity : task.world.getEntities(task.caster, new AABB(from, end).inflate(1.0), e -> e.isAlive() && e.isPickable())) {
         AABB box = entity.getBoundingBox().inflate(0.5);
         Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, end);
         if (hit.isPresent() && from.distanceToSqr(hit.get()) <= nearest) {
            end = hit.get();
            nearest = from.distanceToSqr(end);
            collided = true;
         }
      }

      task.position = end;
      if (collided || boundary || end.distanceToSqr(task.target) < 1.0E-6) {
         LevelChunk ground = task.world.getChunkSource().getChunkNow(Mth.floor(end.x) >> 4, Mth.floor(end.z) >> 4);
         if (ground != null) {
            int surface = ground.getHeight(Types.WORLD_SURFACE, Mth.floor(end.x) & 15, Mth.floor(end.z) & 15) + 1;
            end = new Vec3(end.x, Math.max(task.world.getMinBuildHeight(), surface), end.z);
            task.position = end;
         }

         task.origin = end;
         task.phase = 2;
         task.phaseAt = task.world.getGameTime() + 14L;
         task.crater = new FugaCrater(BlockPos.containing(end), GodWandConfig.SERVER.fugaTerrainRadius);
         if (task.terrain) {
            task.toForce.addAll(forceOrder(BlockPos.containing(end), FugaCrater.reach(GodWandConfig.SERVER.fugaTerrainRadius)));
         }

         broadcast(task, 2);
      }
   }

   static Vec3 loadedEndpoint(ServerLevel world, Vec3 from, Vec3 to) {
      if (!loaded(world, from)) {
         return from;
      }

      TreeSet<Double> crossings = new TreeSet<>();
      crossings.add(0.0);
      crossings.add(1.0);
      double[] starts = new double[]{from.x, from.y, from.z};
      double[] ends = new double[]{to.x, to.y, to.z};

      for (int axis = 0; axis < 3; axis++) {
         double delta = ends[axis] - starts[axis];
         if (!(Math.abs(delta) < 1.0E-12)) {
            int first = (int)Math.floor(Math.min(starts[axis], ends[axis]) / 16.0) + 1;
            int last = (int)Math.floor(Math.max(starts[axis], ends[axis]) / 16.0);

            for (int plane = first; plane <= last; plane++) {
               double t = (plane * 16.0 - starts[axis]) / delta;
               if (t > 0.0 && t < 1.0) {
                  crossings.add(t);
               }
            }
         }
      }

      double previous = 0.0;

      for (double next : crossings) {
         if (!(next <= previous)) {
            if (!loaded(world, from.lerp(to, (previous + next) * 0.5))) {
               return from.lerp(to, Math.max(0.0, previous - 1.0E-8));
            }

            previous = next;
         }
      }

      return loaded(world, to) ? to : from.lerp(to, 0.99999999);
   }

   private static boolean loaded(ServerLevel world, Vec3 point) {
      BlockPos pos = BlockPos.containing(point);
      return world.isInWorldBounds(pos) && world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
   }

   private static void damage(FugaManager.Task task, long age) {
      double radius = FugaSequence.columnRadius(age);
      if (!(radius <= 0.0)) {
         Vec3 p = task.position;
         AABB area = new AABB(p.x - radius, task.world.getMinBuildHeight(), p.z - radius, p.x + radius, p.y + 384.0, p.z + radius);

         for (LivingEntity entity : task.world
            .getEntitiesOfClass(
               LivingEntity.class, area, e -> e != task.caster && e.isAlive() && !(e instanceof Player player && (player.isCreative() || player.isSpectator()))
            )) {
            hit(task, entity, radius);
         }

         for (Entity entity : SpellDamage.extraTargets(task.world, area)) {
            hit(task, entity, radius);
         }
      }
   }

   private static void slow(FugaManager.Task task) {
      double r = 57.599999999999994;
      Vec3 p = task.position;

      for (LivingEntity entity : task.world
         .getEntitiesOfClass(
            LivingEntity.class,
            new AABB(p.x - r, p.y - 16.0, p.z - r, p.x + r, p.y + 48.0, p.z + r),
            e -> e != task.caster && e.isAlive() && !(e instanceof Player player && (player.isCreative() || player.isSpectator()))
         )) {
         entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, false));
      }
   }

   private static void hit(FugaManager.Task task, Entity entity, double radius) {
      UUID id = SpellDamage.identity(entity);
      if (!(Math.hypot(entity.getX() - task.position.x, entity.getZ() - task.position.z) > radius) && !task.struck.contains(id)) {
         float amount = GodWandConfig.SERVER.fugaDamage * GodWandConfig.SERVER.damageMultiplier;
         if (SpellDamage.hurt(entity, task.world.damageSources().indirectMagic(task.caster, task.caster), amount)) {
            task.struck.add(id);
         }
      }
   }

   private static void broadcast(FugaManager.Task task, int phase) {
      Vec3 point = task.origin;
      GodWandNet.near(
            task.world,
            point,
            256.0,
            new FugaFxPayload(
               task.caster.getId(),
               task.started,
               point.x,
               point.y,
               point.z,
               task.target.x,
               task.target.y,
               task.target.z,
               phase,
               phase != 3 && phase != 4 ? task.phaseAt : task.world.getGameTime()
            )
         );
   }

   public static void clearAllTasks() {
      for (FugaManager.Task task : TASKS.values()) {
         release(task.world, task.forced);
      }

      for (FugaManager.Settling s : SETTLING) {
         release(s.world(), s.forced());
      }

      TASKS.clear();
      SETTLING.clear();
   }

   static List<Long> forceOrder(BlockPos center, int radius) {
      ArrayList<long[]> keys = new ArrayList<>();
      int r = (radius >> 4) + 1;
      int cx = center.getX() >> 4;
      int cz = center.getZ() >> 4;

      for (int x = -r; x <= r; x++) {
         for (int z = -r; z <= r; z++) {
            if (x * x + z * z <= r * r + 1) {
               keys.add(new long[]{x * x + z * z, (long)(cx + x) << 32 | cz + z & 4294967295L});
            }
         }
      }

      keys.sort(Comparator.comparingLong(kx -> kx[0]));
      ArrayList<Long> order = new ArrayList<>();

      for (long[] k : keys) {
         order.add(k[1]);
      }

      return order;
   }

   private static void forceSome(ServerLevel world, ArrayDeque<Long> queue, List<Long> forced, int count) {
      for (int i = 0; i < count && !queue.isEmpty(); i++) {
         long key = queue.poll();
         if (world.setChunkForced((int)(key >> 32), (int)key, true)) {
            forced.add(key);
         }
      }
   }

   private static void release(ServerLevel world, List<Long> forced) {
      for (long key : forced) {
         world.setChunkForced((int)(key >> 32), (int)key, false);
      }

      forced.clear();
   }

   private static void outflow(FugaManager.Task task, long age) {
      double visual = FugaSequence.visualAge(age);
      if (!(visual < 40.0) && !(visual >= 150.0)) {
         Vec3 p = task.position;
         double inner = 28.799999999999997;
         double reach = Math.min(230.0, 46.0 + (visual - 40.0) * 7.0);
         if (age % 10L == 0L) {
            AABB area = new AABB(p.x - reach, p.y - 6.0, p.z - reach, p.x + reach, p.y + 40.0, p.z + reach);
            float amount = GodWandConfig.SERVER.fugaDamage * GodWandConfig.SERVER.damageMultiplier * 0.08F;

            for (LivingEntity entity : task.world
               .getEntitiesOfClass(
                  LivingEntity.class,
                  area,
                  e -> e != task.caster && e.isAlive() && !(e instanceof Player player && (player.isCreative() || player.isSpectator()))
               )) {
               double dx = entity.getX() - p.x;
               double dz = entity.getZ() - p.z;
               double d = Math.hypot(dx, dz);
               if (!(d < inner) && !(d > reach)) {
                  SpellDamage.hurt(entity, task.world.damageSources().indirectMagic(task.caster, task.caster), amount);
                  entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100));
                  entity.setDeltaMovement(entity.getDeltaMovement().add(dx / Math.max(1.0, d) * 0.6, 0.15, dz / Math.max(1.0, d) * 0.6));
                  entity.hurtMarked = true;
               }
            }
         }

         if (task.terrain) {
            RandomSource random = task.world.getRandom();

            for (int i = 0; i < 6; i++) {
               double a = random.nextDouble() * Math.PI * 2.0;
               double r = reach - random.nextDouble() * 14.0;
               int x = Mth.floor(p.x + Math.cos(a) * r);
               int z = Mth.floor(p.z + Math.sin(a) * r);
               LevelChunk chunk = task.world.getChunkSource().getChunkNow(x >> 4, z >> 4);
               if (chunk != null) {
                  int y = chunk.getHeight(Types.WORLD_SURFACE, x & 15, z & 15);
                  BlockPos top = new BlockPos(x, y, z);
                  BlockState state = chunk.getBlockState(top);
                  if (state.ignitedByLava()) {
                     BlockPos at = state.canBeReplaced() ? top : top.above();
                     BlockState there = chunk.getBlockState(at);
                     if (there.canBeReplaced() && SpatialDisplacementManager.canDisplace(task.world, at, there)) {
                        task.world.setBlock(at, BaseFireBlock.getState(task.world, at), 3);
                     }
                  }
               }
            }
         }
      }
   }

   private record Settling(ServerLevel world, FugaCrater crater, long impactAt, List<Long> forced) {
   }

   private static final class Task {
      final ServerPlayer caster;
      final ServerLevel world;
      final boolean terrain;
      final long started;
      final Vec3 anchor;
      Vec3 origin;
      Vec3 target;
      Vec3 position;
      long phaseAt;
      int phase = 0;
      FugaCrater crater;
      final Set<UUID> struck = new HashSet<>();
      List<Long> forced = new ArrayList<>();
      final ArrayDeque<Long> toForce = new ArrayDeque<>();

      Task(ServerPlayer player) {
         this.caster = player;
         this.world = player.serverLevel();
         this.terrain = GodWandSkills.destroys(player);
         this.started = this.phaseAt = this.world.getGameTime();
         this.anchor = this.origin = this.position = player.position();
         this.target = FugaSequence.launchTarget(player.getEyePosition(), player.getLookAngle());
      }
   }
}

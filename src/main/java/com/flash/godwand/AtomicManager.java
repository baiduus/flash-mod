package com.flash.godwand;

import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AtomicManager {
   private static final Map<UUID, AtomicManager.Task> TASKS = new LinkedHashMap<>();

   public static boolean invulnerable(ServerPlayer player) {
      AtomicManager.Task task = TASKS.get(player.getUUID());
      return task != null
         && player.isAlive()
         && task.world == player.level()
         && AtomicSequence.protectedDuringCast(AtomicSequence.castAge(task.world.getGameTime(), task.started, task.releasedAt));
   }

   public static boolean movementLocked(ServerPlayer player) {
      AtomicManager.Task task = TASKS.get(player.getUUID());
      return task != null
         && player.isAlive()
         && !player.isSpectator()
         && task.world == player.level()
         && !task.movementReleased
         && AtomicSequence.movementLocked(AtomicSequence.castAge(task.world.getGameTime(), task.started, task.releasedAt));
   }

   public static boolean releaseMovement(ServerPlayer player, long startedAt) {
      AtomicManager.Task task = TASKS.get(player.getUUID());
      if (task != null && task.started == startedAt && task.world == player.level() && player.isAlive() && !player.isSpectator() && task.releasedAt >= 0L) {
         double age = AtomicSequence.castAge(task.world.getGameTime(), task.started, task.releasedAt);
         if (!(age < 414.0) && !(age >= 834.0)) {
            task.movementReleased = true;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean beginCharge(ServerPlayer player) {
      if (player.isPassenger() || !GodWandConfig.SERVER.isDimensionAllowed(player.level().dimension().location().toString())) {
         return false;
      } else if (!TASKS.containsKey(player.getUUID()) && TASKS.values().stream().filter(t -> t.world == player.level()).count() < 4L) {
         AtomicManager.Task task = new AtomicManager.Task(player);
         TASKS.put(player.getUUID(), task);
         broadcast(task, 0);
         return true;
      } else {
         return false;
      }
   }

   public static boolean arm(ServerPlayer player) {
      AtomicManager.Task task = TASKS.get(player.getUUID());
      if (task != null && !task.armed && task.world == player.level()) {
         task.center = player.position();
         task.armed = true;
         task.started = task.world.getGameTime() - 380L;
         task.crater = new AtomicCrater(
            BlockPos.containing(task.center).below(),
            GodWandSkills.destroys(player) ? AtomicCrater.terrainRadius(GodWandConfig.SERVER.atomicTerrainRadius) : 0
         );
         broadcast(task, 1);
         return true;
      } else {
         return false;
      }
   }

   public static void cancelCharge(ServerPlayer player) {
      AtomicManager.Task task = TASKS.get(player.getUUID());
      if (task != null && !task.exploded) {
         broadcast(task, 3);
         TASKS.remove(player.getUUID());
      }
   }

   public static void releaseCharge(ServerPlayer player) {
      AtomicManager.Task task = TASKS.get(player.getUUID());
      if (task != null && task.world == player.level() && task.releasedAt < 0L) {
         if (!task.armed) {
            cancelCharge(player);
         } else {
            task.releasedAt = AtomicSequence.releaseTime(task.world.getGameTime(), task.started);
            broadcast(task, 4);
         }
      }
   }

   public static void tick(ServerLevel world) {
      int terrainBudget = 32768;
      long terrainTime = 8000000L;
      Iterator<AtomicManager.Task> iterator = TASKS.values().iterator();

      while (iterator.hasNext()) {
         AtomicManager.Task task = iterator.next();
         if (task.world == world) {
            long age = (long)AtomicSequence.castAge(world.getGameTime(), task.started, task.releasedAt);
            boolean invalidCaster = !task.caster.isAlive()
               || task.caster.hasDisconnected()
               || task.caster.level() != world
               || task.caster.isSpectator()
               || !com.flash.yas.YasPowers.has(task.caster)
            || task.armed && task.releasedAt < 0L && !GodWandSkills.charging(task.caster, 5);
            if (!task.exploded && invalidCaster || age >= 834L) {
               broadcast(task, 3);
               iterator.remove();
            } else {
               if (task.exploded && age >= 430L) {
                  task.movementReleased = true;
               }
               if (movementLocked(task.caster)) {
                  task.caster.setDeltaMovement(Vec3.ZERO);
                  task.caster.fallDistance = 0.0F;
                  if (task.caster.position().distanceToSqr(task.center) > 1.0E-4) {
                     task.caster.connection.teleport(task.center.x, task.center.y, task.center.z, task.caster.getYRot(), task.caster.getXRot());
                  }
               }

               if (!task.armed) {
                  if (GodWandSkills.charging(task.caster, 5)) {
                     task.center = task.caster.position();
                  } else {
                     broadcast(task, 3);
                     iterator.remove();
                  }
               } else {
                  if (!task.exploded && age >= 414L) {
                     task.exploded = true;
                     broadcast(task, 2);
                  }

                  if (task.exploded) {
                     float radius = AtomicSequence.shockRadius(age);
                     AABB area = new AABB(
                        task.center.x - radius,
                        task.center.y - 14.0,
                        task.center.z - radius,
                        task.center.x + radius,
                        task.center.y + 1024.0,
                        task.center.z + radius
                     );

                     for (LivingEntity entity : world.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        e -> AtomicSequence.beamRadius(age) > 0.0F
                           && !SpellDamage.isCaster(e, task.caster)
                           && e.isAlive()
                           && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                     )) {
                        double distance = Math.hypot(entity.getX() - task.center.x, entity.getZ() - task.center.z);
                        float hit = damage(distance);
                        if (!(distance > AtomicSequence.beamRadius(age))
                           && !(hit <= 0.0F)
                           && !task.struck.contains(entity.getUUID())
                           && SpellDamage.hurt(entity, world.damageSources().indirectMagic(task.caster, task.caster), hit)) {
                           task.struck.add(entity.getUUID());
                           Vec3 push = entity.position().subtract(task.center).normalize().scale(1.8 * (1.0 - distance / 98.0));
                           entity.push(push.x, Math.min(0.8, push.length() * 0.4), push.z);
                           entity.hurtMarked = true;
                        }
                     }

                     for (Entity part : SpellDamage.extraTargets(world, area)) {
                        double distance = Math.hypot(part.getX() - task.center.x, part.getZ() - task.center.z);
                        UUID id = SpellDamage.identity(part);
                        if (!(distance > AtomicSequence.beamRadius(age))
                           && !task.struck.contains(id)
                           && SpellDamage.hurt(part, world.damageSources().indirectMagic(task.caster, task.caster), damage(distance))) {
                           task.struck.add(id);
                        }
                     }

                     if (AtomicSequence.pressure(age) > 0.15F
                        && GodWandConfig.SERVER.atomicShockwaveDamage > 0.0F
                        && GodWandConfig.SERVER.damageMultiplier > 0.0F) {
                        double reach = 130.0;
                        AABB groundFront = new AABB(
                           task.center.x - reach, task.center.y - 2.0, task.center.z - reach, task.center.x + reach, task.center.y + 3.0, task.center.z + reach
                        );

                        for (Entity part : SpellDamage.extraTargets(world, groundFront)) {
                           UUID id = SpellDamage.identity(part);
                           if (age - task.pressureHits.getOrDefault(id, -100L) >= 10L) {
                              double distance = Math.hypot(part.getX() - task.center.x, part.getZ() - task.center.z);

                              for (int wave = 0; wave < 4; wave++) {
                                 if (AtomicSequence.pressureCrosses(age, wave, distance)) {
                                    if (SpellDamage.hurt(
                                       part,
                                       world.damageSources().indirectMagic(task.caster, task.caster),
                                       GodWandConfig.SERVER.atomicShockwaveDamage * GodWandConfig.SERVER.damageMultiplier
                                    )) {
                                       task.pressureHits.put(id, age);
                                    }
                                    break;
                                 }
                              }
                           }
                        }

                        for (LivingEntity entity : world.getEntitiesOfClass(Mob.class, groundFront, LivingEntity::isAlive)) {
                           if (age - task.pressureHits.getOrDefault(entity.getUUID(), -100L) >= 10L) {
                              double distance = Math.hypot(entity.getX() - task.center.x, entity.getZ() - task.center.z);

                              for (int wave = 0; wave < 4; wave++) {
                                 if (AtomicSequence.pressureCrosses(age, wave, distance)) {
                                    task.pressureHits.put(entity.getUUID(), age);
                                    if (SpellDamage.hurt(
                                       entity,
                                       world.damageSources().indirectMagic(task.caster, task.caster),
                                       GodWandConfig.SERVER.atomicShockwaveDamage * GodWandConfig.SERVER.damageMultiplier
                                    )) {
                                       Vec3 outward = new Vec3(entity.getX() - task.center.x, 0.0, entity.getZ() - task.center.z).normalize().scale(0.45);
                                       entity.push(outward.x, 0.12, outward.z);
                                       entity.hurtMarked = true;
                                    }
                                    break;
                                 }
                              }
                           }
                        }
                     }

                     if (age < 574L && AtomicSequence.beamRadius(age) > 0.0F && terrainBudget > 0 && terrainTime > 0L) {
                        long began = System.nanoTime();
                        terrainBudget -= task.crater.carve(world, terrainBudget, AtomicSequence.beamRadius(age), began + terrainTime);
                        terrainTime -= System.nanoTime() - began;
                     }
                  }
               }
            }
         }
      }
   }

   static float damage(double distance) {
      return (float)Math.max(0.0, GodWandConfig.SERVER.atomicDamage * GodWandConfig.SERVER.damageMultiplier * (1.0 - Mth.clamp(distance, 0.0, 98.0) / 98.0));
   }

   private static void broadcast(AtomicManager.Task task, int phase) {
      GodWandNet.near(
            task.world,
            task.center,
            256.0,
            new AtomicFxPayload(task.caster.getId(), task.started, task.center.x, task.center.y, task.center.z, phase, task.releasedAt)
         );
   }

   public static void clearAllTasks() {
      TASKS.clear();
   }

   private static final class Task {
      final ServerLevel world;
      final ServerPlayer caster;
      long started;
      long releasedAt = -1L;
      Vec3 center;
      boolean armed;
      boolean exploded;
      boolean movementReleased;
      final Set<UUID> struck = new HashSet<>();
      final Map<UUID, Long> pressureHits = new HashMap<>();
      AtomicCrater crater;

      Task(ServerPlayer player) {
         this.world = player.serverLevel();
         this.caster = player;
         this.started = this.world.getGameTime();
         this.center = player.position();
      }
   }
}

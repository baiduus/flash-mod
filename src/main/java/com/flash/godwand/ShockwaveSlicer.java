package com.flash.godwand;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public class ShockwaveSlicer {
   private static final Queue<ShockwaveSlicer.SingularityTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

   private static boolean isExposedSurfaceBlock(ServerLevel world, BlockPos pos) {
      for (Direction dir : Direction.values()) {
         BlockState neighbor = world.getBlockState(pos.relative(dir));
         if (neighbor.isAir() || !neighbor.isSolidRender(world, pos.relative(dir))) {
            return true;
         }
      }

      return false;
   }

   public static void queueCalamityDetonation(ServerLevel world, BlockPos center, double maxRadius) {
      queueCalamityDetonation(world, center, maxRadius, null);
   }

   public static void queueCalamityDetonation(ServerLevel world, BlockPos center, double maxRadius, ServerPlayer caster) {
      Vec3 orbCenter = Vec3.atCenterOf(center).add(0.0, 3.8 + maxRadius * 0.35, 0.0);
      ShockwaveSlicer.SingularityTask task = new ShockwaveSlicer.SingularityTask(world, center, maxRadius, orbCenter, caster);
      int intR = (int)Math.ceil(maxRadius);
      double maxRadiusSq = maxRadius * maxRadius;
      MutableBlockPos mutable = new MutableBlockPos();
      List<BlockPos> surfaceCandidates = new ArrayList<>();
      List<BlockPos> deepCandidates = new ArrayList<>();

      for (int x = -intR; x <= intR; x++) {
         for (int y = -intR; y <= intR; y++) {
            int targetY = center.getY() + y;
            if (targetY >= world.getMinBuildHeight() && targetY < (world.getMaxBuildHeight() - 1) + 1) {
               for (int z = -intR; z <= intR; z++) {
                  double dSq = x * x + y * y + z * z;
                  if (!(dSq > maxRadiusSq)) {
                     mutable.set(center.getX() + x, targetY, center.getZ() + z);
                     BlockState state = world.getBlockState(mutable);
                     if (SpatialDisplacementManager.canDisplace(world, mutable, state)) {
                        BlockPos imm = mutable.immutable();
                        if (isExposedSurfaceBlock(world, imm)) {
                           surfaceCandidates.add(imm);
                        } else {
                           deepCandidates.add(imm);
                        }
                     }
                  }
               }
            }
         }
      }

      Random rng = new Random(center.asLong());
      Collections.shuffle(surfaceCandidates, rng);
      Collections.shuffle(deepCandidates, rng);
      List<BlockPos> candidatePositions = new ArrayList<>(surfaceCandidates.size() + deepCandidates.size());
      candidatePositions.addAll(surfaceCandidates);
      candidatePositions.addAll(deepCandidates);
      int maxDisplays = Math.min(600, candidatePositions.size());

      for (int i = 0; i < candidatePositions.size(); i++) {
         BlockPos pos = candidatePositions.get(i);
         boolean isDisplay = i < maxDisplays;
         if (isDisplay) {
            float hashVal = (float)SpatialMath.hash01(pos);
            float hashVal2 = (float)SpatialMath.hash01(pos.above());
            double dx = pos.getX() + 0.5 - (center.getX() + 0.5);
            double dz = pos.getZ() + 0.5 - (center.getZ() + 0.5);
            double baseAzimuth = Math.atan2(dz, dx) + (hashVal - 0.5) * 0.22;
            double phi = (hashVal2 * 1.45 - 0.55) * (Math.PI / 2);
            double expandRadius = maxRadius * (1.15 + hashVal * 0.95);
            Vec3 sphereDir = new Vec3(Math.cos(phi) * Math.cos(baseAzimuth), Math.sin(phi), Math.cos(phi) * Math.sin(baseAzimuth)).normalize();
            Vec3 targetWorldPos = orbCenter.add(sphereDir.scale(expandRadius));
            double minHoverY = center.getY() + 1.0 + hashVal2 * 2.5;
            if (targetWorldPos.y < minHoverY) {
               targetWorldPos = new Vec3(targetWorldPos.x, minHoverY, targetWorldPos.z);
            }

            Vec3 fullExpansionVec = targetWorldPos.subtract(Vec3.atCenterOf(pos));
            float scale = 0.92F + hashVal * 0.48F;
            Quaternionf rot = new Quaternionf().rotateY((float)(hashVal * Math.PI * 2.0)).rotateX((float)Math.toRadians(25.0 + hashVal * 35.0));
            SpatialDisplacementManager.displaceBlock(
               world,
               pos,
               new Vector3f(0.0F, 0.0F, 0.0F),
               rot,
               new Vector3f(scale, scale, scale),
               140,
               0,
               true,
               DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
               null,
               Vec3.atCenterOf(center),
               maxRadius
            );
            task.displayedPositions.add(pos);
            task.fullExpansionMap.put(pos, fullExpansionVec);
            task.baseRotationMap.put(pos, rot);
            task.blockScaleMap.put(pos, scale);
            task.lastOffsetMap.put(pos, Vec3.ZERO);
            task.lastRotationMap.put(pos, rot);
         } else {
            SpatialDisplacementManager.displaceBlock(
               world,
               pos,
               new Vector3f(0.0F, 0.0F, 0.0F),
               new Quaternionf(),
               new Vector3f(1.0F, 1.0F, 1.0F),
               140,
               0,
               false,
               DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
               null,
               Vec3.atCenterOf(center),
               maxRadius
            );
            task.interiorPositions.add(pos);
         }
      }

      world.sendParticles(ParticleTypes.FLASH, orbCenter.x, orbCenter.y, orbCenter.z, 4, 0.2, 0.2, 0.2, 0.0);
      world.sendParticles(ParticleTypes.SONIC_BOOM, orbCenter.x, orbCenter.y, orbCenter.z, 2, 0.1, 0.1, 0.1, 0.0);
      world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 1.0, 1.0, 1.0, 0.0);
      world.sendParticles(
         new DustParticleOptions(new Vector3f(0.0F, 0.95F, 1.0F), 1.8F), orbCenter.x, orbCenter.y, orbCenter.z, 60, 1.5, 1.5, 1.5, 0.45
      );
      world.playSound(null, center.getX(), center.getY(), center.getZ(), GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 3.5F, 0.6F);
      world.playSound(null, center.getX(), center.getY(), center.getZ(), GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.PLAYERS, 3.0F, 0.8F);
      world.playSound(null, center.getX(), center.getY(), center.getZ(), GodWandSounds.ev(SoundEvents.TRIDENT_THUNDER), SoundSource.PLAYERS, 2.8F, 0.7F);
      AABB blastBox = new AABB(center).inflate(maxRadius * 1.25);

      for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, blastBox, ent -> true)) {
         Vec3 delta = e.position().subtract(Vec3.atCenterOf(center));
         double dist = delta.length();
         if (dist < maxRadius * 1.25 && dist > 0.1) {
            double force = (1.0 - dist / (maxRadius * 1.25)) * 2.2;
            Vec3 push = delta.normalize().scale(force).add(0.0, 0.55, 0.0);
            e.push(push.x, push.y, push.z);
            e.hurtMarked = true;
         }
      }

      ImpactFxPayload detPayload = new ImpactFxPayload(task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 0, 132, (float)Math.max(1.0, maxRadius / 10.0));
      CinematicFxPayload detCinematic = new CinematicFxPayload(0, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 0.65F, 16);
      GodWandNet.near(world, task.orbCenter, 96.0, detPayload);
      GodWandNet.near(world, task.orbCenter, 96.0, detCinematic);
      ACTIVE_TASKS.add(task);
      SkillCooldowns.sync(task.caster, 1);
      if (caster != null) {
         caster.displayClientMessage(Component.translatable("message.godwand.singularity_deployed"), true);
      }
   }

   private static void spawnThreeOrbitalParticleRings(ServerLevel world, Vec3 center, int ageTicks, double radius) {
      double baseSpeed = ageTicks * 0.28;
      int pointsPerRing = 6;

      for (int ring = 0; ring < 3; ring++) {
         double rotX = ring == 0 ? Math.toRadians(38.0) : (ring == 1 ? Math.toRadians(-42.0) : Math.toRadians(15.0));
         double rotZ = ring == 0 ? Math.toRadians(22.0) : (ring == 1 ? Math.toRadians(35.0) : Math.toRadians(-55.0));
         double dir = ring == 1 ? -1.35 : (ring == 2 ? 1.15 : 1.0);

         Vector3f ringColor = switch (ring) {
            case 0 -> new Vector3f(0.0F, 0.96F, 1.0F);
            case 1 -> new Vector3f(0.9F, 0.98F, 1.0F);
            default -> new Vector3f(0.68F, 0.22F, 1.0F);
         };

         for (int i = 0; i < pointsPerRing; i++) {
            double theta = baseSpeed * dir + i * 2.0 * Math.PI / pointsPerRing;
            double lx = Math.cos(theta) * radius;
            double lz = Math.sin(theta) * radius;
            double ly = 0.0;
            double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
            double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
            double z1 = lz;
            double fx = x1;
            double fy = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
            double fz = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);
            world.sendParticles(
               new DustParticleOptions(ringColor, 1.35F), center.x + fx, center.y + fy, center.z + fz, 1, 0.02, 0.02, 0.02, 0.0
            );
            if (i % 2 == 0) {
               world.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + fx, center.y + fy, center.z + fz, 1, 0.03, 0.03, 0.03, 0.02);
            }
         }
      }
   }

   public static boolean hasActiveTask(ServerPlayer player) {
      for (ShockwaveSlicer.SingularityTask task : ACTIVE_TASKS) {
         if (!task.restored && !task.earlySnapped && (task.caster == null || task.caster == player || task.caster.getUUID().equals(player.getUUID()))) {
            return true;
         }
      }

      return false;
   }

   public static boolean triggerEarlySnap(ServerPlayer player) {
      for (ShockwaveSlicer.SingularityTask task : ACTIVE_TASKS) {
         if (!task.restored && !task.earlySnapped && (task.caster == null || task.caster == player || task.caster.getUUID().equals(player.getUUID()))) {
            snapTask(task);
            return true;
         }
      }

      return false;
   }

   private static void snapTask(ShockwaveSlicer.SingularityTask task) {
      task.earlySnapped = true;
      SkillCooldowns.sync(task.caster, 1);
      task.earlySnapTick = task.ageTicks;
      task.snappedBack = true;
      sendThreadFx(task, 4);
      ServerLevel world = task.world;
      world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.BLOCKS, 0.4F, 1.8F);
      world.playSound(
         null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_DEPLETE), SoundSource.BLOCKS, 0.45F, 1.6F
      );
      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 0.65F, 0.8F);
      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.NETHERITE_BLOCK_PLACE), SoundSource.BLOCKS, 0.45F, 0.7F);
      AABB snapBox = new AABB(task.center).inflate(task.maxRadius * 1.15);
      SpellDamage.hurtExtras(
         world,
         snapBox,
         task.caster != null ? world.damageSources().fallingBlock(task.caster) : world.damageSources().inWall(),
         e -> e.position().distanceTo(Vec3.atCenterOf(task.center)) <= task.maxRadius * 1.15
            ? 2.0
               * Math.max(45.0, (1.0 - e.position().distanceTo(Vec3.atCenterOf(task.center)) / (task.maxRadius * 1.15)) * 80.0 + 45.0)
               * GodWandConfig.skillDamageMultiplier(1, task.chargeLevel)
            : 0.0
      );

      for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, snapBox, ent -> !SpellDamage.isCaster(ent, task.caster) && ent.isAlive())) {
         Vec3 diff = Vec3.atCenterOf(task.center).subtract(e.position());
         double dist = diff.length();
         if (dist <= task.maxRadius * 1.15) {
            float dmg = (float)Math.max(45.0, (1.0 - dist / (task.maxRadius * 1.15)) * 80.0 + 45.0) * GodWandConfig.skillDamageMultiplier(1, task.chargeLevel);
            SpellDamage.hurt(e, world.damageSources().fallingBlock(task.caster), (e instanceof Mob ? 2.0F : 1.0F) * dmg);
            Vec3 pull = dist > 0.01 ? diff.normalize().scale(1.9).add(0.0, -0.9, 0.0) : new Vec3(0.0, -1.0, 0.0);
            e.setDeltaMovement(pull);
            e.hurtMarked = true;
         }
      }

      for (BlockPos pos : task.displayedPositions) {
         DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
         if (record != null && record.displayEntityUuid != null) {
            BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display != null && display.isAlive()) {
               GodWandDisplays.delay(display, 0);
               GodWandDisplays.duration(display, 4);
               updateBeam(task, pos, Vec3.ZERO, 4);
               GodWandDisplays.transform(display, SpatialMath.createPivotTransform(Vec3.ZERO, new Quaternionf(), 1.0F));
            }
         }
      }

      ImpactFxPayload snapFx = new ImpactFxPayload(task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 7, 10, 1.45F);
      CinematicFxPayload snapShake = new CinematicFxPayload(0, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 0.95F, 14);
      CinematicFxPayload snapFov = new CinematicFxPayload(1, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 0.85F, 6);
      GodWandNet.near(world, task.orbCenter, 96.0, snapFx);
      GodWandNet.near(world, task.orbCenter, 96.0, snapShake);
      GodWandNet.near(world, task.orbCenter, 96.0, snapFov);
   }

   public static void tick(ServerLevel world) {
      Iterator<ShockwaveSlicer.SingularityTask> it = ACTIVE_TASKS.iterator();

      while (it.hasNext()) {
         ShockwaveSlicer.SingularityTask task = it.next();
         if (task.world == world) {
            task.ageTicks++;
            if (task.earlySnapped) {
               int ticksSinceSnap = task.ageTicks - task.earlySnapTick;
               if (ticksSinceSnap < 4) {
                  animateReturn(task, ticksSinceSnap / 3.0F);
               }

               if (ticksSinceSnap < 4) {
                  world.sendParticles(
                     ParticleTypes.REVERSE_PORTAL, task.center.getX() + 0.5, task.center.getY() + 1.0, task.center.getZ() + 0.5, 16, 1.5, 1.0, 1.5, 0.2
                  );
                  world.sendParticles(
                     new DustParticleOptions(new Vector3f(0.0F, 0.95F, 1.0F), 1.5F),
                     task.orbCenter.x,
                     task.orbCenter.y,
                     task.orbCenter.z,
                     12,
                     0.8,
                     0.8,
                     0.8,
                     0.3
                  );
               } else if (!task.restored) {
                  executeRestoration(task);
                  it.remove();
               }
            } else {
               if (task.ageTicks <= 105 && task.ageTicks % 20 == 0) {
                  sendThreadFx(task, 0);
               }

               if (task.ageTicks <= 112) {
                  double ringR = task.ageTicks <= 105 ? 1.55 : Math.max(0.2, 1.55 * (1.0 - (task.ageTicks - 105) / 7.0));
                  spawnThreeOrbitalParticleRings(world, task.orbCenter, task.ageTicks, ringR);
               }

               if (task.ageTicks <= 22) {
                  if (task.ageTicks % 2 == 0) {
                     float t = task.ageTicks / 22.0F;
                     double p = 1.0 - Math.pow(1.0 - t, 3.0);
                     updateBlockPositions(task, p, 2);
                     double outProg = task.ageTicks / 22.0;
                     double shockOutR = 0.8 + Math.pow(outProg, 0.65) * (task.maxRadius * 1.35);
                     int outPoints = 36;

                     for (int pt = 0; pt < outPoints; pt++) {
                        double ang = (double)pt / outPoints * Math.PI * 2.0;
                        double px = task.orbCenter.x + Math.cos(ang) * shockOutR;
                        double pz = task.orbCenter.z + Math.sin(ang) * shockOutR;
                        double py = task.orbCenter.y;
                        double vx = Math.cos(ang) * 0.45;
                        double vz = Math.sin(ang) * 0.45;
                        world.sendParticles(
                           new DustParticleOptions(new Vector3f(0.08F, 0.92F, 1.0F), 1.6F), px, py, pz, 1, vx, 0.05, vz, 0.15
                        );
                        if (pt % 4 == 0) {
                           world.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, vx, 0.1, vz, 0.05);
                        }
                     }
                  }
               } else if (task.ageTicks > 105) {
                  if (task.ageTicks >= 106 && task.ageTicks <= 130 && task.ageTicks % 4 == 0) {
                     for (int k = 0; k < 6; k++) {
                        if (!task.displayedPositions.isEmpty()) {
                           BlockPos rPos = task.displayedPositions.get(world.getRandom().nextInt(task.displayedPositions.size()));
                           Vec3 curOff = task.lastOffsetMap.getOrDefault(rPos, Vec3.ZERO);
                           Vec3 bPos = Vec3.atCenterOf(rPos).add(curOff);
                           world.sendParticles(ParticleTypes.ELECTRIC_SPARK, bPos.x, bPos.y, bPos.z, 2, 0.05, 0.05, 0.05, 0.04);
                           world.sendParticles(
                              new DustParticleOptions(new Vector3f(0.0F, 0.96F, 1.0F), 1.2F),
                              bPos.x,
                              bPos.y,
                              bPos.z,
                              1,
                              0.0,
                              0.02,
                              0.0,
                              0.01
                           );
                        }
                     }
                  }

                  if (task.ageTicks == 106) {
                     world.playSound(
                        null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_CHARGE), SoundSource.BLOCKS, 0.55F, 1.5F
                     );

                     for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record != null && record.displayEntityUuid != null) {
                           BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                           if (display != null && display.isAlive()) {
                              Vec3 curOff = task.lastOffsetMap.getOrDefault(pos, task.fullExpansionMap.getOrDefault(pos, Vec3.ZERO));
                              Quaternionf curRot = task.lastRotationMap.getOrDefault(pos, task.baseRotationMap.getOrDefault(pos, new Quaternionf()));
                              float scale = task.blockScaleMap.getOrDefault(pos, 1.0F);
                              GodWandDisplays.delay(display, 0);
                              GodWandDisplays.duration(display, 5);
                              updateBeam(task, pos, curOff.scale(1.04), 5);
                              task.lastOffsetMap.put(pos, curOff.scale(1.04));
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(curOff.scale(1.04), curRot, scale));
                           }
                        }
                     }
                  }

                  if (task.ageTicks == 112 && !task.snappedBack) {
                     task.snappedBack = true;
                     sendThreadFx(task, 18);
                     world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.BLOCKS, 0.9F, 1.6F);
                     world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, GodWandSounds.ev(SoundEvents.BEACON_DEACTIVATE), SoundSource.BLOCKS, 0.45F, 1.8F);

                     for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record != null && record.displayEntityUuid != null) {
                           BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                           if (display != null && display.isAlive()) {
                              GodWandDisplays.delay(display, 0);
                              GodWandDisplays.duration(display, 18);
                              updateBeam(task, pos, Vec3.ZERO, 18);
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(Vec3.ZERO, new Quaternionf(), 1.0F));
                           }
                        }
                     }
                  }

                  if (task.ageTicks >= 112 && task.ageTicks <= 130) {
                     animateReturn(task, (task.ageTicks - 112) / 16.0F);
                  }

                  if (task.ageTicks >= 131 && !task.restored) {
                     executeRestoration(task);
                     it.remove();
                  }
               }
            }
         }
      }
   }

   public static float returnProgress(float progress) {
      float t = Mth.clamp(progress, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static void animateReturn(ShockwaveSlicer.SingularityTask task, float progress) {
      float t = returnProgress(progress);

      for (BlockPos pos : task.displayedPositions) {
         DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.dimension(), pos);
         if (record != null && record.displayEntityUuid != null) {
            BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display != null && display.isAlive()) {
               Vec3 offset = task.lastOffsetMap.getOrDefault(pos, Vec3.ZERO).scale(1.0F - t);
               Quaternionf rotation = new Quaternionf((Quaternionfc)task.lastRotationMap.getOrDefault(pos, new Quaternionf())).slerp(new Quaternionf(), t);
               float scale = Mth.lerp(t, task.blockScaleMap.getOrDefault(pos, 1.0F), 1.0F);
               GodWandDisplays.delay(display, 0);
               GodWandDisplays.duration(display, 1);
               GodWandDisplays.transform(display, SpatialMath.createPivotTransform(offset, rotation, scale));
               updateBeam(task, pos, offset, 1);
            }
         }
      }
   }

   private static void executeRestoration(ShockwaveSlicer.SingularityTask task) {
      clearBeams(task);
      task.restored = true;
      SkillCooldowns.sync(task.caster, 1);
      ServerLevel world = task.world;
      List<DisplacedBlockRecord> toRestore = new ArrayList<>();

      for (BlockPos pos : task.displayedPositions) {
         DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
         if (rec != null) {
            toRestore.add(rec);
         }
      }

      for (BlockPos pos : task.interiorPositions) {
         DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
         if (rec != null) {
            toRestore.add(rec);
         }
      }

      if (!toRestore.isEmpty()) {
         SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
      }

      AABB entombBox = new AABB(task.center).inflate(task.maxRadius * 1.05);
      SpellDamage.hurtExtras(
         world,
         entombBox,
         task.caster != null ? world.damageSources().fallingBlock(task.caster) : world.damageSources().inWall(),
         e -> e.position().distanceTo(Vec3.atCenterOf(task.center)) <= task.maxRadius * 1.05
            ? 70.0F * GodWandConfig.skillDamageMultiplier(1, task.chargeLevel)
            : 0.0
      );

      for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, entombBox, ent -> !SpellDamage.isCaster(ent, task.caster) && ent.isAlive())) {
         BlockPos feetPos = BlockPos.containing(e.position());
         BlockPos eyePos = BlockPos.containing(e.getEyePosition());
         BlockState feetState = world.getBlockState(feetPos);
         BlockState eyeState = world.getBlockState(eyePos);
         boolean inSolid = feetState.isSolidRender(world, feetPos) || eyeState.isSolidRender(world, eyePos);
         if (inSolid) {
            SpellDamage.hurt(
               e,
               task.caster != null ? world.damageSources().fallingBlock(task.caster) : world.damageSources().inWall(),
               (e instanceof Mob ? 2.0F : 1.0F) * 35.0F * GodWandConfig.skillDamageMultiplier(1, task.chargeLevel)
            );
            BlockState blockToBreak = feetState.isSolidRender(world, feetPos) ? feetState : eyeState;
            world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockToBreak), e.getX(), e.getY() + 0.5, e.getZ(), 24, 0.3, 0.5, 0.3, 0.15);
         }
      }

      if (task.caster != null) {
         task.caster.displayClientMessage(Component.translatable("message.godwand.collapsed"), true);
      }

      ImpactFxPayload snapFx = new ImpactFxPayload(task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 7, 10, 1.35F);
      CinematicFxPayload snapShake = new CinematicFxPayload(0, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 0.82F, 12);
      CinematicFxPayload snapFov = new CinematicFxPayload(1, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 0.9F, 6);
      GodWandNet.near(world, task.orbCenter, 96.0, snapFx);
      GodWandNet.near(world, task.orbCenter, 96.0, snapShake);
      GodWandNet.near(world, task.orbCenter, 96.0, snapFov);
      world.sendParticles(ParticleTypes.FLASH, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 4, 0.4, 0.4, 0.4, 0.0);
      world.sendParticles(ParticleTypes.SONIC_BOOM, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 1, 0.0, 0.0, 0.0, 0.0);

      for (int s = 0; s < 54; s++) {
         double ang = s / 54.0 * Math.PI * 2.0;
         double dist = task.maxRadius * (0.35 + world.getRandom().nextDouble() * 0.75);
         double px = task.center.getX() + 0.5 + Math.cos(ang) * dist;
         double pz = task.center.getZ() + 0.5 + Math.sin(ang) * dist;
         world.sendParticles(ParticleTypes.FLASH, px, task.center.getY() + 1.05, pz, 1, 0.0, 0.0, 0.0, 0.0);
         world.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, task.center.getY() + 1.1, pz, 2, 0.1, 0.1, 0.1, 0.05);
         world.sendParticles(
            new DustParticleOptions(new Vector3f(0.0F, 0.95F, 1.0F), 1.5F), px, task.center.getY() + 1.1, pz, 1, 0.0, 0.05, 0.0, 0.02
         );
      }

      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.NETHERITE_BLOCK_PLACE), SoundSource.BLOCKS, 3.5F, 1.05F);
      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.ANVIL_LAND), SoundSource.BLOCKS, 3.0F, 1.4F);
      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.BLOCKS, 3.2F, 1.7F);
      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.TRIDENT_THUNDER), SoundSource.BLOCKS, 3.0F, 1.35F);
      world.playSound(null, task.center, GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_DEPLETE), SoundSource.BLOCKS, 2.8F, 1.8F);
   }

   private static void updateBlockPositions(ShockwaveSlicer.SingularityTask task, double progress, int duration) {
      int previousBeamCount = task.beams.size();

      for (BlockPos pos : task.displayedPositions) {
         DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.dimension(), pos);
         if (record != null && record.displayEntityUuid != null) {
            BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display != null && display.isAlive()) {
               Vec3 fullExp = task.fullExpansionMap.getOrDefault(pos, Vec3.ZERO);
               Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
               float scale = task.blockScaleMap.getOrDefault(pos, 1.0F);
               Vec3 curOffset = fullExp.scale(progress);
               Quaternionf currentRot = rot;
               int index = task.displayedPositions.indexOf(pos);
               int beamStride = Math.max(1, (task.displayedPositions.size() + 143) / 144);
               if (index % beamStride == 0) {
                  BlockDisplay beam = task.beams.get(pos);
                  if (beam == null) {
                     beam = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, task.world);
                     beam.addTag("godwand_displaced");
                     beam.addTag("godwand_managed_displacement");
                     beam.setPos(task.orbCenter);
                     GodWandDisplays.blockState(beam, Blocks.AMETHYST_BLOCK.defaultBlockState());
                     GodWandDisplays.brightness(beam, new Brightness(15, 15));
                     beam.setGlowingTag(false);
                     GodWandDisplays.glowColor(beam, -1);
                     task.world.addFreshEntity(beam);
                     task.beams.put(pos, beam);
                     SpatialDisplacementManager.trackEntity(beam.getUUID(), beam);
                  }

                  updateBeam(task, pos, curOffset, duration);
               }

               task.lastOffsetMap.put(pos, curOffset);
               task.lastRotationMap.put(pos, currentRot);
               GodWandDisplays.delay(display, 0);
               GodWandDisplays.duration(display, duration);
               GodWandDisplays.transform(display, SpatialMath.createPivotTransform(curOffset, currentRot, scale));
            }
         }
      }

      if (task.beams.size() != previousBeamCount) {
         sendThreadFx(task, 0);
      }
   }

   private static void sendThreadFx(ShockwaveSlicer.SingularityTask task, int recallTicks) {
      if (!task.beams.isEmpty()) {
         List<Integer> ids = task.beams
            .values()
            .stream()
            .map(beam -> SpatialDisplacementManager.getActiveEntity(beam.getUUID()))
            .filter(Objects::nonNull)
            .filter(Entity::isAlive)
            .<Integer>map(Entity::getId)
            .toList();
         SingularityThreadsPayload payload = new SingularityThreadsPayload(ids, recallTicks);
         GodWandNet.near(task.world, task.orbCenter, 96.0, payload);
      }
   }

   public static int getActiveTaskCount() {
      return ACTIVE_TASKS.size();
   }

   private static void updateBeam(ShockwaveSlicer.SingularityTask task, BlockPos pos, Vec3 offset, int duration) {
      BlockDisplay beam = task.beams.get(pos);
      if (beam != null) {
         beam = SpatialDisplacementManager.getActiveEntity(beam.getUUID());
         if (beam != null) {
            GodWandDisplays.delay(beam, 0);
            GodWandDisplays.duration(beam, duration);
            GodWandDisplays.transform(beam, SpatialMath.createThinBeamTransform(task.orbCenter, Vec3.atCenterOf(pos).add(offset), 0.012F));
         }
      }
   }

   private static void clearBeams(ShockwaveSlicer.SingularityTask task) {
      for (BlockDisplay original : task.beams.values()) {
         BlockDisplay loaded = SpatialDisplacementManager.getActiveEntity(original.getUUID());
         SpatialDisplacementManager.untrackEntity(original.getUUID());
         (loaded != null ? loaded : original).discard();
      }

      task.beams.clear();
   }

   public static void clearAllTasks() {
      ACTIVE_TASKS.forEach(ShockwaveSlicer::clearBeams);
      ACTIVE_TASKS.clear();
   }

   public static class SingularityTask {
      public final ServerLevel world;
      public final BlockPos center;
      public final double maxRadius;
      public final int chargeLevel;
      public final Vec3 orbCenter;
      public final ServerPlayer caster;
      public final List<BlockPos> displayedPositions = new ArrayList<>();
      public final Map<BlockPos, BlockDisplay> beams = new HashMap<>();
      public final List<BlockPos> interiorPositions = new ArrayList<>();
      public final Map<BlockPos, Vec3> fullExpansionMap = new HashMap<>();
      public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
      public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();
      public final Map<BlockPos, Vec3> lastOffsetMap = new HashMap<>();
      public final Map<BlockPos, Quaternionf> lastRotationMap = new HashMap<>();
      public int ageTicks = 0;
      public final int maxLifetimeTicks = 132;
      public boolean snappedBack = false;
      public boolean restored = false;
      public boolean earlySnapped = false;
      public int earlySnapTick = -1;

      public SingularityTask(ServerLevel world, BlockPos center, double maxRadius, Vec3 orbCenter) {
         this(world, center, maxRadius, orbCenter, null);
      }

      public SingularityTask(ServerLevel world, BlockPos center, double maxRadius, Vec3 orbCenter, ServerPlayer caster) {
         this.world = world;
         this.center = center.immutable();
         this.maxRadius = maxRadius;
         this.chargeLevel = maxRadius <= 5.0 ? 1 : (maxRadius <= 10.0 ? 2 : 3);
         this.orbCenter = orbCenter;
         this.caster = caster;
      }
   }
}

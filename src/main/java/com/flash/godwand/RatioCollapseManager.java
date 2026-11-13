package com.flash.godwand;

import net.minecraft.world.entity.Display;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RatioCollapseManager {
   public static final int ASSEMBLY_END = 88;
   public static final int SPIN_END = 158;
   public static final int IMPACT_TICK = 167;
   public static final int SEQUENCE_END = 176;
   private static final Queue<RatioCollapseManager.RatioCollapseTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

   public static void cast(ServerPlayer player) {
      cast(player, 1);
   }

   public static void cast(ServerPlayer player, int chargeLevel) {
      ServerLevel world = player.serverLevel();

      double reach = switch (chargeLevel) {
         case 2 -> 45.0;
         case 3 -> 65.0;
         case 4 -> 85.0;
         default -> 28.0;
      };
      HitResult hit = player.pick(reach, 0.0F, false);
      BlockPos targetPos;
      if (hit.getType() == Type.BLOCK && hit instanceof BlockHitResult bHit) {
         targetPos = bHit.getBlockPos();
      } else {
         Vec3 targetVec = player.getEyePosition().add(player.getViewVector(1.0F).scale(reach * 0.7));
         targetPos = BlockPos.containing(targetVec);

         while (targetPos.getY() > world.getMinBuildHeight() && world.getBlockState(targetPos).isAir()) {
            targetPos = targetPos.below();
         }
      }
      double radius = switch (chargeLevel) {
         case 2 -> 13.5;
         case 3 -> 19.0;
         case 4 -> 24.0;
         default -> 9.0;
      };

      double boulderRadius = switch (chargeLevel) {
         case 2 -> 3.6;
         case 3 -> 5.0;
         case 4 -> 6.5;
         default -> 2.5;
      };

      int maxBlocks = switch (chargeLevel) {
         case 2 -> 200;
         case 3 -> 300;
         case 4 -> 420;
         default -> 159;
      };

      int craterRadius = switch (chargeLevel) {
         case 2 -> 13;
         case 3 -> 18;
         case 4 -> 23;
         default -> 8;
      };

      int craterDepth = switch (chargeLevel) {
         case 2 -> 28;
         case 3 -> 41;
         case 4 -> 54;
         default -> 19;
      };

      float damageMult = switch (chargeLevel) {
         case 2 -> 1.8F;
         case 3 -> 2.8F;
         case 4 -> 3.8F;
         default -> 1.2F;
      };
      launch(world, player, targetPos, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult, chargeLevel);
   }

   public static void launch(ServerLevel world, ServerPlayer player, BlockPos center, double radius) {
      launch(world, player, center, radius, 2.5, 159, 8, 16, 1.2F);
   }

   public static void launch(
      ServerLevel world,
      ServerPlayer player,
      BlockPos center,
      double radius,
      double boulderRadius,
      int maxBlocks,
      int craterRadius,
      int craterDepth,
      float damageMult
   ) {
      launch(world, player, center, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult, 1);
   }

   private static void launch(
      ServerLevel world,
      ServerPlayer player,
      BlockPos center,
      double radius,
      double boulderRadius,
      int maxBlocks,
      int craterRadius,
      int craterDepth,
      float damageMult,
      int chargeLevel
   ) {
      RatioCollapseManager.RatioCollapseTask task = new RatioCollapseManager.RatioCollapseTask(
         world, player, center, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult
      );
      task.chargeLevel = chargeLevel;
      CinematicFxPayload castShake = new CinematicFxPayload(0, player.getX(), player.getY(), player.getZ(), Math.min(1.0F, 0.65F * damageMult), 14);
      GodWandNet.near(world, player.position(), 48.0, castShake);
      ImpactFxPayload gravityCorePayload = new ImpactFxPayload(task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 6, 159, (float)(boulderRadius / 2.5));
      GodWandNet.near(world, task.meteorCore, 160.0, gravityCorePayload);
      world.sendParticles(ParticleTypes.SONIC_BOOM, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 1, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.ELECTRIC_SPARK, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 20, 0.4, 0.4, 0.4, 0.1);
      int intR = (int)Math.ceil(task.radius);
      MutableBlockPos mutable = new MutableBlockPos();
      List<BlockPos> validPositions = new ArrayList<>();

      for (int x = -intR; x <= intR; x++) {
         for (int z = -intR; z <= intR; z++) {
            double dSq = x * x + z * z;
            if (!(dSq > task.radius * task.radius)) {
               int startY = center.getY() + 3;
               mutable.set(center.getX() + x, startY, center.getZ() + z);

               while (mutable.getY() > center.getY() - 6 && world.getBlockState(mutable).isAir()) {
                  mutable.move(0, -1, 0);
               }

               BlockState state = world.getBlockState(mutable);
               if (task.blockLift && SpatialDisplacementManager.canDisplace(world, mutable, state)) {
                  validPositions.add(mutable.immutable());
               }
            }
         }
      }

      Collections.shuffle(validPositions, new Random(center.asLong()));
      int count = Math.min(task.maxBlocks, validPositions.size());

      for (int i = 0; i < count; i++) {
         BlockPos pos = validPositions.get(i);
         float hash = (float)SpatialMath.hash01(pos);
         Quaternionf rot = new Quaternionf()
            .rotateX((float)Math.toRadians(25.0 * Math.sin(hash * 6.28)))
            .rotateY((float)Math.toRadians(45.0 * Math.cos(hash * 6.28)));
         SpatialDisplacementManager.displaceBlock(
            world,
            pos,
            new Vector3f(0.0F, 0.0F, 0.0F),
            rot,
            new Vector3f(1.0F, 1.0F, 1.0F),
            160,
            0,
            true,
            DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
            player.getUUID(),
            null,
            0.0,
            task.terrainDamage
         );
         double dx = pos.getX() + 0.5 - task.meteorCore.x;
         double dz = pos.getZ() + 0.5 - task.meteorCore.z;
         double initAngle = Math.atan2(dz, dx);
         double initRad = Math.max(task.boulderRadius * 1.8, Math.sqrt(dx * dx + dz * dz));
         double yBand = (hash - 0.5) * (task.boulderRadius * 1.6);
         task.displacedPositions.add(pos);
         task.blockIndexMap.put(pos, i);
         task.initialOrbitAngle.put(pos, initAngle);
         task.initialOrbitRadius.put(pos, initRad);
         task.hoverHeightOffset.put(pos, yBand);
         task.baseRotation.put(pos, rot);
         task.sphereClusterOffset.put(pos, computeBoulderClusterOffset(i, count, task.boulderRadius));
      }

      AABB vortexBox = new AABB(center).inflate(task.radius * 1.25);
      SpellDamage.hurtExtras(
         world,
         vortexBox,
         world.damageSources().indirectMagic(player, player),
         ex -> 8.0F * task.damageMult * GodWandConfig.skillDamageMultiplier(2, task.chargeLevel)
      );

      for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, vortexBox, ent -> ent != player)) {
         e.push(0.0, 0.48, 0.0);
         e.hurtMarked = true;
         SpellDamage.hurt(
            e,
            world.damageSources().indirectMagic(player, player),
            (e instanceof Mob ? 2.0F : 1.0F) * (4.0F * task.damageMult * GodWandConfig.skillDamageMultiplier(2, task.chargeLevel))
         );
      }

      ACTIVE_TASKS.add(task);
   }

   public static Vec3 computeBoulderClusterOffset(int index, int total, double radius) {
      if (total <= 1) {
         return Vec3.ZERO;
      }

      double phi = Math.acos(1.0 - 2.0 * (index + 0.5) / total);
      double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * index;
      double r = radius * (0.65 + 0.35 * Math.sqrt((index + 0.5) / total));
      return new Vec3(r * Math.sin(phi) * Math.cos(theta), r * Math.cos(phi), r * Math.sin(phi) * Math.sin(theta));
   }

   public static Vec3 computeSpiralDrillOffset(int index, int total, double boulderRadius, float spinAngle) {
      if (total <= 1) {
         return Vec3.ZERO;
      }

      double h = (index + 0.5) / total;
      double drillLength = boulderRadius * 3.2;
      double localY = (h - 0.62) * drillLength;
      double maxConeR = boulderRadius * 1.18;
      double coneR = 0.12 + maxConeR * Math.pow(h, 0.72);
      int flute = index % 3;
      double spiralTwist = h * Math.PI * 5.0;
      double angle = flute * (Math.PI * 2.0 / 3.0) + spiralTwist + spinAngle;
      double radialLayer = index % 5 == 0 ? 0.45 : 1.0;
      double r = coneR * radialLayer;
      return new Vec3(Math.cos(angle) * r, localY, Math.sin(angle) * r);
   }

   public static void tick(ServerLevel world) {
      Iterator<RatioCollapseManager.RatioCollapseTask> it = ACTIVE_TASKS.iterator();

      while (it.hasNext()) {
         RatioCollapseManager.RatioCollapseTask task = it.next();
         if (task.world == world) {
            task.ageTicks++;
            int totalBlocks = Math.max(1, task.displacedPositions.size());
            if (task.ageTicks <= 158) {
               double coreR = Math.max(0.38, task.boulderRadius * 0.26);

               for (int f = 0; f < 3; f++) {
                  double ang = world.getRandom().nextDouble() * Math.PI * 2.0;
                  double elev = (world.getRandom().nextDouble() - 0.5) * Math.PI;
                  double fx = task.meteorCore.x + Math.cos(elev) * Math.cos(ang) * coreR;
                  double fy = task.meteorCore.y + Math.sin(elev) * coreR;
                  double fz = task.meteorCore.z + Math.cos(elev) * Math.sin(ang) * coreR;
                  world.sendParticles(
                     new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.12F), 1.6F), fx, fy, fz, 1, 0.0, 0.02, 0.0, 0.04
                  );
                  if (f % 2 == 0) {
                     world.sendParticles(
                        new DustParticleOptions(new Vector3f(0.9F, 0.0F, 0.18F), 1.3F), fx, fy, fz, 1, 0.0, 0.01, 0.0, 0.02
                     );
                     world.sendParticles(ParticleTypes.ELECTRIC_SPARK, fx, fy, fz, 1, 0.01, 0.01, 0.01, 0.02);
                  }
               }

               for (int s = 0; s < 4; s++) {
                  double spawnDist = 2.8 + world.getRandom().nextDouble() * 4.5;
                  double sTheta = world.getRandom().nextDouble() * Math.PI * 2.0;
                  double sPhi = (world.getRandom().nextDouble() - 0.5) * Math.PI;
                  double sx = task.meteorCore.x + Math.cos(sPhi) * Math.cos(sTheta) * spawnDist;
                  double sy = task.meteorCore.y + Math.sin(sPhi) * spawnDist;
                  double sz = task.meteorCore.z + Math.cos(sPhi) * Math.sin(sTheta) * spawnDist;
                  Vec3 toCenter = task.meteorCore.subtract(sx, sy, sz).normalize().scale(0.4);
                  world.sendParticles(
                     new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.22F), 1.2F),
                     sx,
                     sy,
                     sz,
                     0,
                     toCenter.x,
                     toCenter.y,
                     toCenter.z,
                     0.4
                  );
                  if (s % 2 == 0) {
                     world.sendParticles(ParticleTypes.REVERSE_PORTAL, sx, sy, sz, 1, toCenter.x * 0.35, toCenter.y * 0.35, toCenter.z * 0.35, 0.06);
                  }
               }
            }

            if (task.ageTicks <= 38) {
               float t = task.ageTicks / 38.0F;
               float smoothT = 0.5F - 0.5F * (float)Math.cos(t * Math.PI);
               float spinSpeed1 = 0.12F * (t * t);
               task.orbitAngle += spinSpeed1;
               if (task.ageTicks % 2 == 0) {
                  for (BlockPos pos : task.displacedPositions) {
                     DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                     if (record != null && record.displayEntityUuid != null) {
                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display != null && display.isAlive()) {
                           int idx = task.blockIndexMap.getOrDefault(pos, 0);
                           double initAngle = task.initialOrbitAngle.getOrDefault(pos, 0.0);
                           double initRad = task.initialOrbitRadius.getOrDefault(pos, 5.0);
                           double hoverY = task.hoverHeightOffset.getOrDefault(pos, 0.0);
                           double curAngle = initAngle + task.orbitAngle;
                           Vec3 targetHoverWorld = new Vec3(
                              task.meteorCore.x + Math.cos(curAngle) * initRad, task.meteorCore.y + hoverY, task.meteorCore.z + Math.sin(curAngle) * initRad
                           );
                           Vec3 curDisp = targetHoverWorld.subtract(Vec3.atCenterOf(pos)).scale(smoothT);
                           Quaternionf baseRot = task.baseRotation.getOrDefault(pos, new Quaternionf());
                           Quaternionf rot = new Quaternionf(baseRot).rotateY(-task.orbitAngle);
                           GodWandDisplays.delay(display, 0);
                           GodWandDisplays.duration(display, 2);
                           GodWandDisplays.transform(display, SpatialMath.createPivotTransform(curDisp, rot, 1.0F));
                           if (idx % Math.max(1, totalBlocks / 14) == 0) {
                              Vec3 blockWorld = Vec3.atCenterOf(pos).add(curDisp);
                              Vec3 thread = blockWorld.subtract(task.meteorCore);
                              double dLen = thread.length();
                              if (dLen > 0.8) {
                                 int steps = Math.min(8, Math.max(3, (int)(dLen * 1.2)));

                                 for (int st = 1; st < steps; st++) {
                                    double fr = (double)st / steps;
                                    world.sendParticles(
                                       new DustParticleOptions(new Vector3f(1.0F, 0.22F, 0.38F), 0.35F),
                                       task.meteorCore.x + thread.x * fr,
                                       task.meteorCore.y + thread.y * fr,
                                       task.meteorCore.z + thread.z * fr,
                                       1,
                                       0.0,
                                       0.0,
                                       0.0,
                                       0.0
                                    );
                                 }

                                 world.sendParticles(ParticleTypes.ELECTRIC_SPARK, blockWorld.x, blockWorld.y, blockWorld.z, 1, 0.01, 0.01, 0.01, 0.01);
                              }
                           }
                        }
                     }
                  }
               }
            } else if (task.ageTicks <= 88) {
               float p2 = (task.ageTicks - 38) / 50.0F;
               float smoothP2 = p2 * p2 * (3.0F - 2.0F * p2);
               float currentSpinSpeed = 0.12F + p2 * 0.28F;
               task.orbitAngle += currentSpinSpeed;
               if (task.ageTicks % 2 == 0) {
                  for (BlockPos pos : task.displacedPositions) {
                     DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                     if (record != null && record.displayEntityUuid != null) {
                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display != null && display.isAlive()) {
                           int idx = task.blockIndexMap.getOrDefault(pos, 0);
                           double initAngle = task.initialOrbitAngle.getOrDefault(pos, 0.0);
                           double initRad = task.initialOrbitRadius.getOrDefault(pos, 5.0);
                           double hoverY = task.hoverHeightOffset.getOrDefault(pos, 0.0);
                           double curAngle = initAngle + task.orbitAngle;
                           double curOrbitRad = Mth.lerp(smoothP2, initRad, task.boulderRadius * 1.4);
                           Vec3 swirlingOrbitOffset = new Vec3(
                              Math.cos(curAngle) * curOrbitRad, hoverY * (1.0 - smoothP2 * 0.5), Math.sin(curAngle) * curOrbitRad
                           );
                           Vec3 drillTargetOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);
                           Vec3 localOff = swirlingOrbitOffset.lerp(drillTargetOff, smoothP2);
                           Vec3 finalDisp = task.meteorCore.add(localOff).subtract(Vec3.atCenterOf(pos));
                           Quaternionf baseRot = task.baseRotation.getOrDefault(pos, new Quaternionf());
                           Quaternionf targetDrillRot = new Quaternionf().rotateY(-task.orbitAngle).rotateX((float)Math.toRadians(35.0));
                           Quaternionf startRot = new Quaternionf(baseRot).rotateY(-task.orbitAngle);
                           Quaternionf drillRot = new Quaternionf(startRot).slerp(targetDrillRot, smoothP2);
                           GodWandDisplays.delay(display, 0);
                           GodWandDisplays.duration(display, 2);
                           GodWandDisplays.transform(display, SpatialMath.createPivotTransform(finalDisp, drillRot, 1.0F));
                           if (idx % Math.max(1, totalBlocks / 14) == 0) {
                              Vec3 blockWorld = task.meteorCore.add(localOff);
                              Vec3 thread = blockWorld.subtract(task.meteorCore);
                              double dLen = thread.length();
                              if (dLen > 0.8) {
                                 int steps = Math.min(8, Math.max(3, (int)(dLen * 1.2)));

                                 for (int st = 1; st < steps; st++) {
                                    double fr = (double)st / steps;
                                    world.sendParticles(
                                       new DustParticleOptions(new Vector3f(1.0F, 0.22F, 0.38F), 0.35F),
                                       task.meteorCore.x + thread.x * fr,
                                       task.meteorCore.y + thread.y * fr,
                                       task.meteorCore.z + thread.z * fr,
                                       1,
                                       0.0,
                                       0.0,
                                       0.0,
                                       0.0
                                    );
                                 }

                                 world.sendParticles(ParticleTypes.ELECTRIC_SPARK, blockWorld.x, blockWorld.y, blockWorld.z, 1, 0.01, 0.01, 0.01, 0.01);
                              }
                           }
                        }
                     }
                  }
               }

               if (task.ageTicks % 6 == 0) {
                  AABB suctionBox = new AABB(task.center).inflate(task.radius * 1.2);

                  for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, suctionBox, ent -> !SpellDamage.isCaster(ent, task.caster))) {
                     Vec3 pull = task.meteorCore.subtract(e.position()).normalize().scale(0.24);
                     e.push(pull.x, pull.y, pull.z);
                     e.hurtMarked = true;
                  }
               }
            } else if (task.ageTicks <= 158) {
               float drillProg = (task.ageTicks - 88) / 70.0F;
               float drillSpinSpeed = 0.4F + drillProg * 0.45F;
               task.orbitAngle += drillSpinSpeed;
               if (drillSpinSpeed >= 0.5F) {
                  sendDrillWind(task, task.meteorCore, drillSpinSpeed);
               }

               for (BlockPos pos : task.displacedPositions) {
                  DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                  if (record != null && record.displayEntityUuid != null) {
                     BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                     if (display != null && display.isAlive()) {
                        int idx = task.blockIndexMap.getOrDefault(pos, 0);
                        if (drillHeat(0.4F + Math.min(1.0F, (task.ageTicks - 88) / 70.0F) * 0.45F) > 0.2F + idx % 11 * 0.06F) {
                           GodWandDisplays.blockState(display, Blocks.MAGMA_BLOCK.defaultBlockState());
                        }

                        Vec3 drillOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);
                        Vec3 finalDisp = task.meteorCore.add(drillOff).subtract(Vec3.atCenterOf(pos));
                        Quaternionf drillRot = new Quaternionf().rotateY(-task.orbitAngle).rotateX((float)Math.toRadians(35.0));
                        GodWandDisplays.delay(display, 0);
                        GodWandDisplays.duration(display, 1);
                        GodWandDisplays.transform(display, SpatialMath.createPivotTransform(finalDisp, drillRot, 1.0F));
                     }
                  }
               }

               double drillLen = task.boulderRadius * 3.2;

               for (int k = 0; k < 12; k++) {
                  double h = Math.random();
                  double coneR = 0.15 + task.boulderRadius * 1.2 * Math.pow(h, 0.72);
                  double ang = task.orbitAngle + k * (Math.PI / 6) + h * 8.0;
                  double px = task.meteorCore.x + Math.cos(ang) * coneR;
                  double py = task.meteorCore.y + (h - 0.62) * drillLen;
                  double pz = task.meteorCore.z + Math.sin(ang) * coneR;
                  world.sendParticles(
                     new DustParticleOptions(new Vector3f(1.0F, 0.02F, 0.05F), 2.2F), px, py, pz, 1, 0.05, 0.05, 0.05, 0.0
                  );
                  if (task.ageTicks >= 112 && k % 2 == 0) {
                     world.sendParticles(ParticleTypes.FLAME, px, py, pz, 0, -Math.sin(ang) * 0.3, 0.1, Math.cos(ang) * 0.3, 0.6);
                  }

                  if (k % 2 == 0) {
                     world.sendParticles(
                        new DustParticleOptions(new Vector3f(0.85F, 0.0F, 0.12F), 1.6F),
                        px,
                        py,
                        pz,
                        1,
                        -Math.sin(ang) * 0.25,
                        0.05,
                        Math.cos(ang) * 0.25,
                        0.02
                     );
                     world.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, 0.04, 0.04, 0.04, 0.03);
                  }
               }
            } else if (task.ageTicks > 176) {
               if (task.craterCursor == null || task.craterCursor.finished()) {
                  it.remove();
               }
            } else {
               float boreProg = (task.ageTicks - 158) / 10.0F;
               float clampedBore = Mth.clamp(boreProg, 0.0F, 1.0F);
               task.orbitAngle += 0.95F;
               Vec3 drillTargetCenter = Vec3.atCenterOf(task.center).subtract(0.0, task.craterDepth * 0.6, 0.0);
               Vec3 currentDrillCenter = task.meteorCore.lerp(drillTargetCenter, clampedBore * clampedBore);
               sendDrillWind(task, currentDrillCenter, 0.95F);
               if (task.ageTicks <= 169) {
                  for (BlockPos pos : task.displacedPositions) {
                     DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                     if (record != null && record.displayEntityUuid != null) {
                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display != null && display.isAlive()) {
                           int idx = task.blockIndexMap.getOrDefault(pos, 0);
                           if (drillHeat(0.4F + Math.min(1.0F, (task.ageTicks - 88) / 70.0F) * 0.45F) > 0.2F + idx % 11 * 0.06F) {
                              GodWandDisplays.blockState(display, Blocks.MAGMA_BLOCK.defaultBlockState());
                           }

                           Vec3 drillOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);
                           Vec3 slamDisp = currentDrillCenter.add(drillOff).subtract(Vec3.atCenterOf(pos));
                           Quaternionf drillRot = new Quaternionf().rotateY(-task.orbitAngle).rotateX((float)Math.toRadians(35.0));
                           GodWandDisplays.delay(display, 0);
                           GodWandDisplays.duration(display, 1);
                           GodWandDisplays.transform(display, SpatialMath.createPivotTransform(slamDisp, drillRot, 1.0F));
                        }
                     }
                  }

                  AABB drillPenetrateBox = AABB.ofSize(currentDrillCenter, task.boulderRadius * 2.2, task.boulderRadius * 3.6, task.boulderRadius * 2.2);
                  SpellDamage.hurtExtras(
                     world,
                     drillPenetrateBox,
                     world.damageSources().fallingBlock(task.caster),
                     e -> 20.0F * task.damageMult * GodWandConfig.skillDamageMultiplier(2, task.chargeLevel)
                  );

                  for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, drillPenetrateBox, ent -> !SpellDamage.isCaster(ent, task.caster))) {
                     SpellDamage.hurt(
                        e,
                        world.damageSources().fallingBlock(task.caster),
                        (e instanceof Mob ? 2.0F : 1.0F) * (10.0F * task.damageMult * GodWandConfig.skillDamageMultiplier(2, task.chargeLevel))
                     );
                     e.push(0.0, -0.45, 0.0);
                     e.hurtMarked = true;
                  }

                  world.sendParticles(
                     new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.05F), 2.5F),
                     currentDrillCenter.x,
                     currentDrillCenter.y,
                     currentDrillCenter.z,
                     25,
                     task.boulderRadius * 0.6,
                     1.5,
                     task.boulderRadius * 0.6,
                     0.1
                  );
               }

               if (task.ageTicks == 167 && !task.slammed) {
                  task.slammed = true;
                  AABB slamBox = new AABB(
                     task.center.getX() - task.radius * 1.45,
                     task.center.getY() - task.craterDepth - 4.0,
                     task.center.getZ() - task.radius * 1.45,
                     task.center.getX() + task.radius * 1.45,
                     task.center.getY() + task.radius * 0.8,
                     task.center.getZ() + task.radius * 1.45
                  );
                  SpellDamage.hurtExtras(
                     world,
                     slamBox,
                     world.damageSources().fallingBlock(task.caster),
                     e -> 2.0
                        * Math.max(35.0, ((1.0 - e.position().distanceTo(Vec3.atCenterOf(task.center)) / (task.radius * 1.45)) * 75.0 + 25.0) * task.damageMult)
                        * GodWandConfig.skillDamageMultiplier(2, task.chargeLevel)
                  );

                  for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, slamBox, ent -> !SpellDamage.isCaster(ent, task.caster))) {
                     double dist = e.position().distanceTo(Vec3.atCenterOf(task.center));
                     float dmg = (float)Math.max(35.0, ((1.0 - dist / (task.radius * 1.45)) * 75.0 + 25.0) * task.damageMult)
                        * GodWandConfig.skillDamageMultiplier(2, task.chargeLevel);
                     SpellDamage.hurt(e, world.damageSources().fallingBlock(task.caster), (e instanceof Mob ? 2.0F : 1.0F) * dmg);
                     Vec3 diff = e.position().subtract(Vec3.atCenterOf(task.center)).normalize().scale(2.2 * task.damageMult).add(0.0, 0.85, 0.0);
                     e.push(diff.x, diff.y, diff.z);
                     e.hurtMarked = true;
                  }

                  ImpactFxPayload slamPayload = new ImpactFxPayload(
                     task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 5, 34, 2.2F * task.damageMult
                  );
                  CinematicFxPayload slamCinematic = new CinematicFxPayload(
                     4, task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, Math.min(1.0F, 1.0F * task.damageMult), 26
                  );
                  GodWandNet.near(world, Vec3.atCenterOf(task.center), 96.0, slamPayload);
                  GodWandNet.near(world, Vec3.atCenterOf(task.center), 96.0, slamCinematic);
                  task.craterCursor = task.terrainDamage ? new SupernovaCraterCursor(task.craterRadius) : null;
               }

               if (task.ageTicks == 172 && !task.terrainDamage) {
                  SpatialDisplacementManager.restorePositions(world, task.displacedPositions);
               }

               if (task.ageTicks == 172 && task.terrainDamage) {
                  Random rng = new Random(task.center.asLong());
                  int flingCount = 0;
                  int maxFling = Math.min(65, task.maxBlocks / 3);

                  for (BlockPos pos : task.displacedPositions) {
                     DisplacedBlockRecord record = SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                     if (record != null && flingCount < maxFling && rng.nextFloat() < 0.55F) {
                        flingCount++;
                        double fAngle = rng.nextDouble() * Math.PI * 2.0;
                        double fSpeed = 0.5 + rng.nextDouble() * 0.85;
                        double spawnX = task.center.getX() + 0.5 + Math.cos(fAngle) * 3.0;
                        double spawnY = task.center.getY() + 1.5;
                        double spawnZ = task.center.getZ() + 0.5 + Math.sin(fAngle) * 3.0;
                        BlockState flungState = rng.nextFloat() < 0.35F ? Blocks.MAGMA_BLOCK.defaultBlockState() : record.originalState;
                        FallingBlockEntity falling = FallingBlockEntity.fall(world, BlockPos.containing(spawnX, spawnY, spawnZ), flungState);
                        falling.setDeltaMovement(Math.cos(fAngle) * fSpeed, 0.55 + rng.nextDouble() * 0.55, Math.sin(fAngle) * fSpeed);
                        falling.hurtMarked = true;
                     }
                  }
               }
            }

            if (task.ageTicks > 167 && task.craterCursor != null && !task.craterCursor.finished()) {
               carveCrater(task);
            }
         }
      }
   }

   private static void carveCrater(RatioCollapseManager.RatioCollapseTask task) {
      if (task.terrainDamage) {
         ServerLevel world = task.world;
         MutableBlockPos pos = new MutableBlockPos();
         boolean changedRecords = false;

         for (int column = 0; column < 96 && !task.craterCursor.finished(); column++) {
            int x = task.craterCursor.x();
            int z = task.craterCursor.z();
            task.craterCursor.advance();
            if (x * x + z * z <= task.craterRadius * task.craterRadius) {
               pos.set(task.center.getX() + x, task.center.getY(), task.center.getZ() + z);
               if (world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                  double distance = Math.hypot(x, z);
                  double groove = Math.sin(Math.atan2(z, x) * 3.0 + distance * 0.85);
                  int depth = drillCraterDepth(x, z, task.craterRadius, task.craterDepth);

                  for (int y = 3; y >= -depth; y--) {
                     pos.setY(task.center.getY() + y);
                     if (world.isInWorldBounds(pos) && world.getWorldBorder().isWithinBounds(pos)) {
                        BlockState state = world.getBlockState(pos);
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record != null) {
                           state = record.originalState;
                        }

                        if (!state.isAir() && !(state.getDestroySpeed(world, pos) < 0.0F)) {
                           if (record != null) {
                              SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                              changedRecords = true;
                           }

                           BlockState replacement = Blocks.AIR.defaultBlockState();
                           if (y == -depth) {
                              double heat = SpatialMath.hash01(pos);
                              replacement = (groove > 0.15
                                    ? Blocks.MAGMA_BLOCK
                                    : (heat < 0.45 ? Blocks.BLACKSTONE : (heat < 0.86 ? Blocks.COBBLESTONE : Blocks.OBSIDIAN)))
                                 .defaultBlockState();
                           }

                           world.setBlock(pos, replacement, 50);
                        }
                     }
                  }
               }
            }
         }

         if (changedRecords) {
            DisplacementPersistentState.getServerState(world).setDirty();
         }
      }
   }

   public static int drillCraterDepth(int x, int z, int radius, int maxDepth) {
      double distance = Math.hypot(x, z);
      double edge = Math.max(0.0, 1.0 - distance / radius);
      return (int)Math.round(Math.pow(edge, 1.55) * maxDepth + edge * Math.sin(Math.atan2(z, x) * 3.0 + distance * 0.85) * 1.5);
   }

   public static double drillHoverHeight(double radius) {
      return 18.0 + radius * 2.2 + Math.max(0.0, radius - 5.0) * 2.0;
   }

   public static float drillWindVisibility(float speed) {
      float t = Mth.clamp((speed - 0.5F) / 0.1F, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   public static float drillHeat(float speed) {
      return Mth.clamp((speed - 0.5F) / 0.35F, 0.0F, 1.0F);
   }

   private static void sendDrillWind(RatioCollapseManager.RatioCollapseTask task, Vec3 center, float speed) {
      DrillWindPayload payload = new DrillWindPayload(task.center.asLong(), center.x, center.y, center.z, (float)task.boulderRadius, task.orbitAngle, speed);
      GodWandNet.near(task.world, center, 128.0, payload);
   }

   public static int getActiveTaskCount() {
      return ACTIVE_TASKS.size();
   }

   public static void clearAllTasks() {
      ACTIVE_TASKS.clear();
   }

   public static class RatioCollapseTask {
      public final ServerLevel world;
      public final boolean terrainDamage;
      public final boolean blockLift = GodWandConfig.SERVER.earthDrillBlockLift;
      public final ServerPlayer caster;
      public final BlockPos center;
      public final Vec3 meteorCore;
      public final double radius;
      public final double boulderRadius;
      public final int maxBlocks;
      public final int craterRadius;
      public final int craterDepth;
      public final float damageMult;
      public int chargeLevel = 1;
      public int ageTicks = 0;
      public float orbitAngle = 0.0F;
      public boolean slammed = false;
      private SupernovaCraterCursor craterCursor;
      public final List<BlockPos> displacedPositions = new ArrayList<>();
      public final Map<BlockPos, Integer> blockIndexMap = new HashMap<>();
      public final Map<BlockPos, Double> initialOrbitAngle = new HashMap<>();
      public final Map<BlockPos, Double> initialOrbitRadius = new HashMap<>();
      public final Map<BlockPos, Double> hoverHeightOffset = new HashMap<>();
      public final Map<BlockPos, Vec3> sphereClusterOffset = new HashMap<>();
      public final Map<BlockPos, Quaternionf> baseRotation = new HashMap<>();

      public RatioCollapseTask(
         ServerLevel world,
         ServerPlayer caster,
         BlockPos center,
         double radius,
         double boulderRadius,
         int maxBlocks,
         int craterRadius,
         int craterDepth,
         float damageMult
      ) {
         this.world = world;
         this.caster = caster;
         this.terrainDamage = GodWandSkills.destroys(caster);
         this.center = center.immutable();
         this.radius = radius;
         this.boulderRadius = boulderRadius;
         this.maxBlocks = maxBlocks;
         this.craterRadius = craterRadius;
         this.craterDepth = craterDepth;
         this.damageMult = damageMult;
         this.meteorCore = Vec3.atCenterOf(center).add(0.0, RatioCollapseManager.drillHoverHeight(boulderRadius), 0.0);
      }
   }
}

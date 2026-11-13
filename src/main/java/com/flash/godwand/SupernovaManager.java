package com.flash.godwand;

import net.minecraft.world.entity.Display;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public class SupernovaManager {
   public static final int DETONATION_TICK = 348;
   public static final int IMPACT_TICK = 344;
   public static final int TERRAIN_START_TICK = 352;
   public static final int MAX_LIFETIME_TICKS = 628;
   public static final int MAX_DISPLAYS_CAP = 1800;
   private static final Queue<SupernovaManager.SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();
   private static final Map<ServerPlayer, BlockPos> CHARGE_TARGETS = new WeakHashMap<>();

   public static boolean isFoliageOrNonSolid(ServerLevel world, BlockPos pos, BlockState state) {
      return state.is(BlockTags.LEAVES)
         || state.is(BlockTags.FLOWERS)
         || state.is(BlockTags.CROPS)
         || state.is(BlockTags.SAPLINGS)
         || state.getBlock() instanceof LeavesBlock
         || !state.isCollisionShapeFullBlock(world, pos)
         || !state.canOcclude();
   }

   static Vec3 mobPullVelocity(Vec3 current, Vec3 towardCore, double progress) {
      double distance = towardCore.length();
      double charge = Mth.clamp(progress, 0.0, 1.0);
      double speed = Math.min(distance * 0.18, 0.9 + charge * 2.4);
      Vec3 desired = distance < 1.0E-6 ? Vec3.ZERO : towardCore.scale(speed / distance);
      Vec3 result = current.lerp(desired, 0.3 + charge * 0.2);
      double limit = Math.min(3.3, distance * 0.65);
      return result.lengthSqr() > limit * limit ? result.normalize().scale(limit) : result;
   }

   public static BlockPos findTarget(Player player) {
      Level world = player.level();
      HitResult hit = player.pick(180.0, 1.0F, false);
      BlockPos groundCenter;
      if (hit.getType() == Type.BLOCK && hit instanceof BlockHitResult bHit) {
         groundCenter = bHit.getBlockPos();
      } else {
         Vec3 forward = player.getEyePosition().add(player.getViewVector(1.0F).scale(120.0));
         groundCenter = BlockPos.containing(forward);
         if (world.isClientSide() && !world.getChunkSource().hasChunk(groundCenter.getX() >> 4, groundCenter.getZ() >> 4)) {
            return null;
         }

         groundCenter = groundCenter.atY(Math.min(groundCenter.getY(), (world.getMaxBuildHeight() - 1)));

         while (groundCenter.getY() > world.getMinBuildHeight() && world.getBlockState(groundCenter).isAir()) {
            groundCenter = groundCenter.below();
         }
      }

      return groundCenter.immutable();
   }

   public static void beginCharge(ServerPlayer player) {
      CHARGE_TARGETS.put(player, findTarget(player));
   }

   public static void cancelCharge(ServerPlayer player) {
      CHARGE_TARGETS.remove(player);
   }

   public static void cast(ServerPlayer player) {
      ServerLevel world = player.level();
      BlockPos groundCenter = CHARGE_TARGETS.remove(player);
      if (groundCenter == null) {
         groundCenter = findTarget(player);
      }

      Vec3 corePos = Vec3.atCenterOf(groundCenter).add(0.0, 20.0, 0.0);
      SupernovaManager.SupernovaTask task = new SupernovaManager.SupernovaTask(world, player, groundCenter, corePos);
      CinematicFxPayload castShake = new CinematicFxPayload(0, corePos.x, corePos.y, corePos.z, 0.18F, 8);
      GodWandNet.near(world, corePos, 120.0, castShake);
      ACTIVE_TASKS.add(task);
      ImpactFxPayload blackHolePayload = new ImpactFxPayload(corePos.x, corePos.y, corePos.z, 4, 344, 1.0F);
      GodWandNet.near(world, corePos, 250.0, blackHolePayload);
      if (player.isAlive()) {
         GodWandNet.to(player, blackHolePayload);
      }
   }

   private static void prepareTerrain(SupernovaManager.SupernovaTask task) {
      if (task.blockLift) {
         ServerLevel world = task.world;
         int groundY = task.groundCenter.getY();
         int maxY = Math.min((world.getMaxBuildHeight() - 1), groundY + 54);
         MutableBlockPos pos = new MutableBlockPos();

         for (int columns = 0; columns < 64 && !task.candidateColumns.finished(); columns++) {
            int x = task.candidateColumns.x();
            int z = task.candidateColumns.z();
            task.candidateColumns.advance();
            double distance = Math.hypot(x, z);
            int colX = task.groundCenter.getX() + x;
            int colZ = task.groundCenter.getZ() + z;
            if (!(distance > task.maxRadius) && world.getChunkSource().hasChunk(colX >> 4, colZ >> 4)) {
               double bowlDepth = 15.0 * Math.pow(1.0 - distance / task.maxRadius, 1.35);
               double noise = (SpatialMath.hash01(new BlockPos(colX * 2, 0, colZ * 2)) - 0.5) * 6.5;
               noise += Math.sin(colX * 0.4) * Math.cos(colZ * 0.4) * 2.5;
               int floorY = Math.max(world.getMinBuildHeight() + 4, (int)Math.round(groundY - bowlDepth + noise));
               int surfaceY = world.getHeight(Types.WORLD_SURFACE, colX, colZ) - 1;

               for (int y = Math.min(maxY, surfaceY + 4); y >= floorY; y--) {
                  pos.set(colX, y, colZ);
                  BlockState state = world.getBlockState(pos);
                  if (SpatialDisplacementManager.canDisplace(world, pos, state)) {
                     task.candidatePositions.add(pos.immutable());
                  }
               }
            }
         }
      }
   }

   public static void tick(ServerLevel world) {
      Iterator<SupernovaManager.SupernovaTask> it = ACTIVE_TASKS.iterator();

      while (it.hasNext()) {
         SupernovaManager.SupernovaTask task = it.next();
         if (task.world == world) {
            task.ageTicks++;
            if (task.ageTicks < 250) {
               prepareTerrain(task);
            }

            if (task.ageTicks >= 348 && task.ageTicks < 408) {
               carveSphericalCraterShell(task, task.ageTicks - 348, 60);
            }

            if (task.ageTicks < 344) {
               if (task.ageTicks == 334) {
                  world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z, GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.PLAYERS, 2.6F, 1.35F);
                  world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z, GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_DEPLETE), SoundSource.PLAYERS, 2.0F, 0.7F);
                  world.sendParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 1, 0.0, 0.0, 0.0, 0.0);
                  world.sendParticles(ParticleTypes.ELECTRIC_SPARK, task.corePos.x, task.corePos.y, task.corePos.z, 32, 2.5, 1.0, 2.5, 0.35);
                  GodWandNet.near(world, task.corePos, 200.0, new CinematicFxPayload(0, task.corePos.x, task.corePos.y, task.corePos.z, 0.22F, 8));
               }

               double currentPeelRadius = 2.5;
               if (task.ageTicks >= 250) {
                  currentPeelRadius = task.maxRadius;
               } else {
                  double progress = task.ageTicks / 250.0;
                  double bhScale = Math.pow(progress, 1.25);
                  currentPeelRadius = 2.5 + (task.maxRadius - 2.5) * bhScale;
                  double currentRadiusSq = currentPeelRadius * currentPeelRadius;
                  int seedDisplays = Math.min(35, task.displayLimit);
                  int maxDisplaysNow = seedDisplays + (int)((task.displayLimit - seedDisplays) * Math.pow(progress, 1.35));
                  int peeledThisTick = 0;
                  int maxPeelPerTick = 80;
                  int size = task.candidatePositions.size();

                  for (int i = task.candidateScanIndex; i < size; i++) {
                     BlockPos pos = task.candidatePositions.get(i);
                     if (world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        if (task.peeledPositions.contains(pos)) {
                           if (i == task.candidateScanIndex) {
                              task.candidateScanIndex++;
                           }
                        } else {
                           double dx = pos.getX() - task.groundCenter.getX();
                           double dz = pos.getZ() - task.groundCenter.getZ();
                           if (dx * dx + dz * dz <= currentRadiusSq) {
                              task.peeledPositions.add(pos);
                              task.peelTickMap.put(pos, task.ageTicks);
                              peeledThisTick++;
                              BlockState state = world.getBlockState(pos);
                              boolean canSpawn = !isFoliageOrNonSolid(world, pos, state);
                              if (canSpawn && task.displayedPositions.size() < maxDisplaysNow && task.displayedPositions.size() < 1800) {
                                 float hash = (float)SpatialMath.hash01(pos);
                                 Quaternionf rot = new Quaternionf().rotateY((float)(hash * Math.PI * 2.0)).rotateX((float)Math.toRadians(20.0 + hash * 25.0));
                                 float scale = 0.85F + hash * 0.55F;
                                 SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0.0F, 0.0F, 0.0F),
                                    rot,
                                    new Vector3f(scale, scale, scale),
                                    550,
                                    0,
                                    true,
                                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                                    task.caster.getUUID(),
                                    null,
                                    0.0,
                                    task.terrainDamage
                                 );
                                 task.displayedPositions.add(pos);
                                 DisplacedBlockRecord lifted = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                                 BlockDisplay display = lifted == null ? null : SpatialDisplacementManager.getActiveEntity(lifted.displayEntityUuid);
                                 if (display != null) {
                                    GodWandDisplays.brightness(display, null);
                                    display.setGlowingTag(false);
                                    GodWandDisplays.glowColor(display, -1);
                                 }

                                 task.baseRotationMap.put(pos, rot);
                                 task.blockScaleMap.put(pos, scale);
                                 if (peeledThisTick <= 6) {
                                    world.sendParticles(
                                       ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.0, 0.08, 0.0, 0.02
                                    );
                                 }
                              } else {
                                 SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0.0F, 0.0F, 0.0F),
                                    new Quaternionf(),
                                    new Vector3f(1.0F, 1.0F, 1.0F),
                                    550,
                                    0,
                                    false,
                                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                                    null,
                                    null,
                                    0.0,
                                    task.terrainDamage
                                 );
                                 task.interiorPositions.add(pos);
                              }

                              if (peeledThisTick >= maxPeelPerTick) {
                                 break;
                              }
                           }
                        }
                     }
                  }
               }

               double totalProgress = task.ageTicks / 344.0;
               float angularSpeed = 0.008F + (float)Math.pow(totalProgress, 2.2) * 0.38F;
               task.swirlAngle += angularSpeed;
               if (task.ageTicks % 2 == 0) {
                  for (BlockPos pos : task.displayedPositions) {
                     float collapse = SupernovaSequence.swallow(task.ageTicks, SpatialMath.hash01(pos));
                     DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                     if (record != null && record.displayEntityUuid != null) {
                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display != null && display.isAlive()) {
                           int peelTick = task.peelTickMap.getOrDefault(pos, task.ageTicks);
                           int ticksSincePeel = task.ageTicks - peelTick;
                           float entryProgress = Mth.clamp(ticksSincePeel / 30.0F, 0.0F, 1.0F);
                           Vec3 orbitOffset = SpatialMath.computeDynamicBlackHoleOrbit(
                              pos, task.corePos, currentPeelRadius, task.swirlAngle, entryProgress, 1.0
                           );
                           Vec3 singularityCenter = task.corePos.subtract(Vec3.atCenterOf(pos));
                           Vec3 dynamicOffset = collapse > 0.0F ? orbitOffset.lerp(singularityCenter, Math.min(1.0, collapse)) : orbitOffset;
                           Quaternionf rot = new Quaternionf((Quaternionfc)task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                              .rotateY(task.swirlAngle * 1.3F);
                           float baseScale = task.blockScaleMap.getOrDefault(pos, 1.0F);
                           float scale = collapse > 0.0F ? baseScale * (float)Math.max(0.0, 1.0 - collapse) : baseScale;
                           GodWandDisplays.delay(display, 0);
                           GodWandDisplays.duration(display, 2);
                           if (collapse > 0.0F && scale > 0.001F) {
                              Vec3 radial = Vec3.atCenterOf(pos).add(dynamicOffset).subtract(task.corePos).normalize();
                              Quaternionf tidalRotation = new Quaternionf()
                                 .rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float)radial.x, (float)radial.y, (float)radial.z));
                              Vector3f tidalScale = new Vector3f(scale * 0.38F, scale * (1.0F + collapse * 6.0F), scale * 0.38F);
                              Vector3f pivot = new Vector3f(0.5F).mul(tidalScale).rotate(tidalRotation);
                              Vector3f translation = new Vector3f((float)dynamicOffset.x + 0.5F, (float)dynamicOffset.y + 0.5F, (float)dynamicOffset.z + 0.5F)
                                 .sub(pivot);
                              GodWandDisplays.transform(display, new Transformation(translation, tidalRotation, tidalScale, null));
                           } else {
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(dynamicOffset, rot, scale));
                           }
                        }
                     }
                  }
               }

               AABB suctionBox = new AABB(task.groundCenter).inflate(currentPeelRadius * 1.4);
               if (task.ageTicks % 4 == 0) {
                  SpellDamage.hurtExtras(
                     world,
                     suctionBox,
                     world.damageSources().explosion(null, task.caster),
                     e -> e.position().distanceToSqr(task.corePos) <= 12.25 ? 15.0F * GodWandConfig.skillDamageMultiplier(3, 1) : 0.0
                  );
               }

               for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, suctionBox, ent -> !SpellDamage.isCaster(ent, task.caster))) {
                  double distToCoreSq = e.distanceToSqr(task.corePos);
                  if (distToCoreSq <= 12.25 && GodWandConfig.skillDamageMultiplier(3, 1) > 0.0F) {
                     DamageSource source = world.damageSources().fellOutOfWorld();
                     if (e.getMaxHealth() <= GodWandConfig.SERVER.supernovaInstantKillHealthThreshold) {
                        SpellDamage.hurt(e, source, Float.MAX_VALUE);
                        if (e.isAlive()) {
                           e.setHealth(0.0F);
                           e.die(source);
                        }

                        world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                     } else {
                        if (task.ageTicks % 4 == 0) {
                           SpellDamage.hurt(e, source, 15.0F * GodWandConfig.skillDamageMultiplier(3, 1));
                        }

                        world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                        Vec3 swirl = task.corePos.subtract(e.position()).scale(0.25);
                        e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(swirl));
                        e.hurtMarked = true;
                     }
                  } else if (e instanceof Mob) {
                     e.setDeltaMovement(mobPullVelocity(e.getDeltaMovement(), task.corePos.subtract(e.position()), totalProgress));
                     e.hurtMarked = true;
                  } else if (task.ageTicks % 2 == 0) {
                     Vec3 pull = task.corePos.subtract(e.position()).normalize().scale(0.4 + totalProgress * 0.7);
                     e.setDeltaMovement(e.getDeltaMovement().lerp(pull, 0.35));
                     e.hurtMarked = true;
                  }
               }
            } else if (task.ageTicks == 344) {
               ImpactFxPayload impact = new ImpactFxPayload(task.corePos.x, task.corePos.y, task.corePos.z, 8, 4, 1.0F);
               GodWandNet.near(world, task.corePos, 250.0, impact);
               if (task.caster != null && task.caster.isAlive() && task.caster.level() == world) {
                  GodWandNet.to(task.caster, impact);
               }
            } else if (task.ageTicks == 348 && !task.exploded) {
               task.exploded = true;
               broadcastDetonation(task);
               world.sendParticles(ParticleTypes.ELECTRIC_SPARK, task.corePos.x, task.corePos.y, task.corePos.z, 28, 8.0, 8.0, 8.0, 0.45);
               world.sendParticles(ParticleTypes.LAVA, task.corePos.x, task.corePos.y, task.corePos.z, 16, 6.0, 6.0, 6.0, 0.55);
               AABB blastBox = new AABB(task.groundCenter).inflate(96.0 + 10.0);
               SpellDamage.hurtExtras(
                  world,
                  blastBox,
                  world.damageSources().explosion(null, task.caster),
                  e -> e.position().distanceTo(task.corePos) <= 96.0 + 8.0
                     ? Math.max(80.0, (1.0 - e.position().distanceTo(task.corePos) / 96.0) * 380.0 + 80.0) * GodWandConfig.skillDamageMultiplier(3, 1)
                     : 0.0
               );

               for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, blastBox, ent -> !SpellDamage.isCaster(ent, task.caster))) {
                  double dist = e.position().distanceTo(task.corePos);
                  if (dist <= 96.0 + 8.0) {
                     float dmg = (float)Math.max(80.0, (1.0 - dist / 96.0) * 380.0 + 80.0) * GodWandConfig.skillDamageMultiplier(3, 1);
                     DamageSource source = world.damageSources().explosion(null, task.caster);
                     if (e instanceof Mob
                        && e.getMaxHealth() <= GodWandConfig.SERVER.supernovaInstantKillHealthThreshold
                        && GodWandConfig.skillDamageMultiplier(3, 1) > 0.0F) {
                        SpellDamage.hurt(e, source, Float.MAX_VALUE);
                        if (e.isAlive()) {
                           e.setHealth(0.0F);
                           e.die(source);
                        }
                     } else {
                        SpellDamage.hurt(e, source, dmg);
                     }

                     Vec3 push = e.position().subtract(task.corePos).normalize().scale(5.8).add(0.0, 2.2, 0.0);
                     e.push(push.x, push.y, push.z);
                     e.hurtMarked = true;
                  }
               }

               if (!task.terrainDamage) {
                  SpatialDisplacementManager.restorePositions(world, task.displayedPositions);
                  SpatialDisplacementManager.restorePositions(world, task.interiorPositions);
               } else {
                  Random rng = new Random(task.groundCenter.asLong());
                  int flingDisplayCount = Math.min(650, task.displayedPositions.size());

                  for (int i = 0; i < task.displayedPositions.size(); i++) {
                     BlockPos pos = task.displayedPositions.get(i);
                     if (i < flingDisplayCount) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record != null && record.displayEntityUuid != null) {
                           BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                           if (display != null && display.isAlive()) {
                              double fAngle = rng.nextDouble() * Math.PI * 2.0;
                              double fDist = 95.0 + rng.nextDouble() * 45.0;
                              double targetX = task.corePos.x + Math.cos(fAngle) * fDist;
                              double targetY = task.groundCenter.getY() + 2.0 + rng.nextDouble() * 16.0;
                              double targetZ = task.corePos.z + Math.sin(fAngle) * fDist;
                              Vec3 flingDisp = new Vec3(targetX, targetY, targetZ).subtract(Vec3.atCenterOf(pos));
                              Quaternionf flingRot = new Quaternionf()
                                 .rotateY((float)(rng.nextDouble() * Math.PI * 2.0))
                                 .rotateX((float)(rng.nextDouble() * Math.PI));
                              GodWandDisplays.delay(display, 0);
                              GodWandDisplays.duration(display, 44);
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(flingDisp, flingRot, 0.9F));
                              task.blastFlingPositions.add(pos);
                              continue;
                           }
                        }
                     }

                     SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                  }

                  for (BlockPos pos : task.interiorPositions) {
                     SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                  }
               }
            } else if (task.ageTicks > 348) {
               if (task.ageTicks <= 360 && task.ageTicks % 4 == 0) {
                  GodWandNet.near(world, task.corePos, 160.0, new CinematicFxPayload(0, task.corePos.x, task.corePos.y, task.corePos.z, 0.16F, 8));
               }

               if (task.ageTicks == 396) {
                  for (BlockPos pos : task.blastFlingPositions) {
                     SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                  }

                  task.blastFlingPositions.clear();
               }

               if (task.ageTicks >= 628) {
                  it.remove();
               }
            }
         }
      }
   }

   private static void broadcastDetonation(SupernovaManager.SupernovaTask task) {
      ImpactFxPayload novaPayload = new ImpactFxPayload(task.corePos.x, task.corePos.y, task.corePos.z, 3, 280, 3.5F);
      CinematicFxPayload burstCinematic = new CinematicFxPayload(6, task.corePos.x, task.corePos.y, task.corePos.z, 1.0F, 110);
      GodWandNet.near(task.world, task.corePos, 250.0, novaPayload);
      GodWandNet.near(task.world, task.corePos, 250.0, burstCinematic);
      if (task.caster != null && task.caster.isAlive() && task.caster.level() == task.world) {
         GodWandNet.to(task.caster, novaPayload);
         GodWandNet.to(task.caster, burstCinematic);
      }
   }

   private static void carveSphericalCraterShell(SupernovaManager.SupernovaTask task, int step, int totalSteps) {
      if (task.terrainDamage) {
         ServerLevel world = task.world;
         MutableBlockPos pos = new MutableBlockPos();
         double maxR = task.terrainRadius;
         double maxDepth = 52.0;
         double rMin = step == 0 ? 0.0 : maxR * ((double)step / totalSteps);
         double rMax = maxR * ((double)(step + 1) / totalSteps);
         double rMinSq = rMin * rMin;
         double rMaxSq = rMax * rMax;
         int cx = task.groundCenter.getX();
         int groundY = task.groundCenter.getY();
         int cz = task.groundCenter.getZ();
         int intR = (int)Math.ceil(rMax);
         boolean removedRestorable = false;

         for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
               double d2Sq = x * x + z * z;
               if (!(d2Sq < rMinSq) && !(d2Sq > rMaxSq)) {
                  int wx = cx + x;
                  int wz = cz + z;
                  LevelChunk chunk = world.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
                  if (chunk != null) {
                     double dist = Math.sqrt(d2Sq);
                     double normDist = dist / maxR;
                     double depth = maxDepth * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist));
                     int topY = Math.min((world.getMaxBuildHeight() - 1), chunk.getHeight(Types.WORLD_SURFACE, wx & 15, wz & 15));
                     int surfaceY = topY;
                     int floorY = Math.max(world.getMinBuildHeight() + 2, (int)Math.round(Math.min(groundY, surfaceY) - depth));

                     for (int y = topY; y >= floorY; y--) {
                        pos.set(wx, y, wz);
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir() || !(state.getDestroySpeed(world, pos) < 0.0F)) {
                           DisplacedBlockRecord displaced = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                           if (displaced != null) {
                              SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                              removedRestorable = true;
                           }

                           if (!state.isAir()) {
                              world.setBlock(pos, Blocks.AIR.defaultBlockState(), 50);
                           }
                        }
                     }
                  }
               }
            }
         }

         if (removedRestorable) {
            DisplacementPersistentState.getServerState(world).setDirty();
         }
      }
   }

   public static int getActiveTaskCount() {
      return ACTIVE_TASKS.size();
   }

   public static void clearAllTasks() {
      ACTIVE_TASKS.clear();
      CHARGE_TARGETS.clear();
   }

   public static class SupernovaTask {
      public final ServerLevel world;
      public final boolean terrainDamage;
      public final boolean blockLift = GodWandConfig.SERVER.supernovaBlockLift;
      public final ServerPlayer caster;
      public final BlockPos groundCenter;
      public final Vec3 corePos;
      public final double terrainRadius = GodWandConfig.SERVER.supernovaTerrainRadius;
      public final int displayLimit = GodWandConfig.SERVER.supernovaMaxDisplays;
      public final double maxRadius = Math.min(55.0, this.terrainRadius);
      public final double blastRadius = 96.0;
      public int ageTicks = 0;
      public float swirlAngle = 0.0F;
      public boolean exploded = false;
      public final List<BlockPos> candidatePositions = new ArrayList<>();
      public final Set<BlockPos> peeledPositions = new HashSet<>();
      public final Map<BlockPos, Integer> peelTickMap = new HashMap<>();
      public int candidateScanIndex = 0;
      public final List<BlockPos> displayedPositions = new ArrayList<>();
      public final List<BlockPos> interiorPositions = new ArrayList<>();
      public final List<BlockPos> blastFlingPositions = new ArrayList<>();
      public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
      public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();
      final SupernovaCraterCursor candidateColumns = new SupernovaCraterCursor((int)Math.ceil(this.maxRadius));

      public SupernovaTask(ServerLevel world, ServerPlayer caster, BlockPos groundCenter, Vec3 corePos) {
         this.world = world;
         this.caster = caster;
         this.terrainDamage = GodWandSkills.destroys(caster);
         this.groundCenter = groundCenter.immutable();
         this.corePos = corePos;
      }
   }
}

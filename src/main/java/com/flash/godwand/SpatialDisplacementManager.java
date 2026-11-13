package com.flash.godwand;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SpatialDisplacementManager {
   private static final Map<DimensionPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
   private static final Map<DimensionPos, DisplacedBlockRecord> BRIDGE_DESTINATIONS = new ConcurrentHashMap<>();
   private static final Map<UUID, BlockDisplay> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> LAST_PATH_OPEN_SOUND = new HashMap<>();
   private static final Map<UUID, Long> LAST_PATH_RETURN_SOUND = new HashMap<>();

   static boolean shouldPlayPathSound(UUID owner, long tick, boolean returning) {
      if (owner == null) {
         return false;
      }

      Map<UUID, Long> times = returning ? LAST_PATH_RETURN_SOUND : LAST_PATH_OPEN_SOUND;
      Long previous = times.get(owner);
      if (previous != null && tick >= previous && tick - previous < (returning ? 8 : 6)) {
         return false;
      }

      times.put(owner, tick);
      return true;
   }

   public static boolean isPositionDisplaced(ResourceKey<Level> worldKey, BlockPos pos) {
      return ACTIVE_RECORDS.containsKey(new DimensionPos(worldKey, pos));
   }

   public static boolean isPositionDisplaced(BlockPos pos) {
      return isPositionDisplaced(Level.OVERWORLD, pos);
   }

   public static boolean canDisplace(ServerLevel world, BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return false;
      } else {
         BlockPos immutablePos = pos.immutable();
         if (state.getDestroySpeed(world, immutablePos) < 0.0F) {
            return false;
         } else if (!state.is(Blocks.BEDROCK) && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.BARRIER)) {
            return !BRIDGE_DESTINATIONS.isEmpty() && BRIDGE_DESTINATIONS.containsKey(new DimensionPos(world.dimension(), immutablePos))
               ? false
               : ACTIVE_RECORDS.isEmpty() || !isPositionDisplaced(world.dimension(), immutablePos);
         } else {
            return false;
         }
      }
   }

   public static void clearRuntimeState() {
      ACTIVE_RECORDS.clear();
      ACTIVE_ENTITIES.clear();
      BRIDGE_DESTINATIONS.clear();
      LAST_PATH_OPEN_SOUND.clear();
      LAST_PATH_RETURN_SOUND.clear();
   }

   public static void recoverBridge(DisplacedBlockRecord record) {
      ACTIVE_RECORDS.put(new DimensionPos(record.worldKey, record.pos), record);
      if (record.bridgeDestination != null) {
         BRIDGE_DESTINATIONS.put(new DimensionPos(record.worldKey, record.bridgeDestination), record);
      }
   }

   public static void moveBridge(DisplacedBlockRecord record, BlockPos target) {
      if (record.bridgeDestination != null) {
         BRIDGE_DESTINATIONS.remove(new DimensionPos(record.worldKey, record.bridgeDestination));
      }

      record.bridgeDestination = target == null ? null : target.immutable();
      if (target != null) {
         BRIDGE_DESTINATIONS.put(new DimensionPos(record.worldKey, target), record);
      }
   }

   public static int getActiveRecordCount() {
      return ACTIVE_RECORDS.size();
   }

   public static int getActiveEntityCount() {
      return ACTIVE_ENTITIES.size();
   }

   public static DisplacedBlockRecord getActiveRecord(ResourceKey<Level> worldKey, BlockPos pos) {
      return ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
   }

   public static BlockDisplay getActiveEntity(UUID uuid) {
      BlockDisplay display = uuid != null ? ACTIVE_ENTITIES.get(uuid) : null;
      if (display != null && !display.isAlive() && display.level() instanceof ServerLevel world && world.getEntity(uuid) instanceof BlockDisplay loaded) {
         ACTIVE_ENTITIES.put(uuid, loaded);
         return loaded;
      } else {
         return display;
      }
   }

   /** A display of ours loaded back with its chunk: the record keeps it; one nobody keeps is a leftover (gone). */
   public static boolean reattach(BlockDisplay display) {
      UUID id = display.getUUID();
      if (ACTIVE_ENTITIES.containsKey(id)) {
         ACTIVE_ENTITIES.put(id, display);
         return true;
      }
      return false;
   }

   public static void tickManagedDisplay(BlockDisplay display) {
      if (display.level() instanceof ServerLevel && display.getTags().contains("godwand_managed_displacement")) {
         UUID id = display.getUUID();
         if (ACTIVE_ENTITIES.containsKey(id)) {
            ACTIVE_ENTITIES.put(id, display);
         } else {
            display.discard();
         }
      }
   }

   public static void trackEntity(UUID uuid, BlockDisplay entity) {
      if (uuid != null && entity != null) {
         ACTIVE_ENTITIES.put(uuid, entity);
      }
   }

   public static void untrackEntity(UUID uuid) {
      if (uuid != null) {
         ACTIVE_ENTITIES.remove(uuid);
      }
   }

   public static DisplacedBlockRecord removeActiveRecord(ResourceKey<Level> worldKey, BlockPos pos) {
      DisplacedBlockRecord rec = ACTIVE_RECORDS.remove(new DimensionPos(worldKey, pos));
      if (rec != null && rec.bridgeDestination != null) {
         BRIDGE_DESTINATIONS.remove(new DimensionPos(rec.worldKey, rec.bridgeDestination));
      }

      if (rec != null && rec.displayEntityUuid != null) {
         BlockDisplay ent = getActiveEntity(rec.displayEntityUuid);
         ACTIVE_ENTITIES.remove(rec.displayEntityUuid);
         if (ent != null && ent.isAlive()) {
            ent.discard();
         }
      }

      return rec;
   }

   public static List<DisplacedBlockRecord> getActiveRecordsForWorld(ResourceKey<Level> worldKey) {
      List<DisplacedBlockRecord> list = new ArrayList<>();

      for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
         if (r.worldKey.equals(worldKey) && !r.permanentDestruction) {
            list.add(r);
         }
      }

      return list;
   }

   public static void refreshDisplacement(ResourceKey<Level> worldKey, BlockPos pos, int lifetime) {
      DisplacedBlockRecord record = ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
      if (record != null) {
         record.interruptRestoration();
         record.maxLifetimeTicks = lifetime;
      }
   }

   public static BlockPos getMatchingDoubleBlockHalf(ServerLevel world, BlockPos pos, BlockState state) {
      if (state.getBlock() instanceof DoorBlock) {
         if (state.hasProperty(DoorBlock.HALF)) {
            DoubleBlockHalf half = (DoubleBlockHalf)state.getValue(DoorBlock.HALF);
            BlockPos target = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState targetState = world.getBlockState(target);
            if (targetState.is(state.getBlock()) && targetState.hasProperty(DoorBlock.HALF) && targetState.getValue(DoorBlock.HALF) != half) {
               return target;
            }
         }
      } else if (state.getBlock() instanceof DoublePlantBlock) {
         if (state.hasProperty(DoublePlantBlock.HALF)) {
            DoubleBlockHalf half = (DoubleBlockHalf)state.getValue(DoublePlantBlock.HALF);
            BlockPos target = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState targetState = world.getBlockState(target);
            if (targetState.is(state.getBlock()) && targetState.hasProperty(DoublePlantBlock.HALF) && targetState.getValue(DoublePlantBlock.HALF) != half) {
               return target;
            }
         }
      } else if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
         BedPart part = (BedPart)state.getValue(BedBlock.PART);
         Direction facing = (Direction)state.getValue(BedBlock.FACING);
         BlockPos target = part == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
         BlockState targetState = world.getBlockState(target);
         if (targetState.is(state.getBlock()) && targetState.hasProperty(BedBlock.PART) && targetState.getValue(BedBlock.PART) != part) {
            return target;
         }
      }

      return null;
   }

   public static void displaceBlock(ServerLevel world, BlockPos pos, Vector3f targetDisplacement, Quaternionf rotation, int lifetime, int glowColor) {
      displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0F, 1.0F, 1.0F), lifetime, glowColor, true);
   }

   public static void displaceBlock(
      ServerLevel world, BlockPos pos, Vector3f targetDisplacement, Quaternionf rotation, int lifetime, int glowColor, boolean spawnDisplay
   ) {
      displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0F, 1.0F, 1.0F), lifetime, glowColor, spawnDisplay);
   }

   public static void displaceBlock(
      ServerLevel world, BlockPos pos, Vector3f targetDisplacement, Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay
   ) {
      displaceBlock(
         world,
         pos,
         targetDisplacement,
         rotation,
         scale,
         lifetime,
         glowColor,
         spawnDisplay,
         DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
         null,
         null,
         0.0
      );
   }

   public static void displaceBlock(
      ServerLevel world,
      BlockPos pos,
      Vector3f targetDisplacement,
      Quaternionf rotation,
      Vector3f scale,
      int lifetime,
      int glowColor,
      boolean spawnDisplay,
      DisplacedBlockRecord.DisplacementMode mode,
      UUID boundPlayerUuid,
      Vec3 blastCenter,
      double blastRadius
   ) {
      displaceBlockInternal(
         world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true, false
      );
   }

   public static void displaceBlock(
      ServerLevel world,
      BlockPos pos,
      Vector3f targetDisplacement,
      Quaternionf rotation,
      Vector3f scale,
      int lifetime,
      int glowColor,
      boolean spawnDisplay,
      DisplacedBlockRecord.DisplacementMode mode,
      UUID boundPlayerUuid,
      Vec3 blastCenter,
      double blastRadius,
      boolean permanent
   ) {
      displaceBlockInternal(
         world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true, permanent
      );
   }

   public static void restorePositions(ServerLevel world, Collection<BlockPos> positions) {
      Set<DisplacedBlockRecord> records = new LinkedHashSet<>();

      for (BlockPos pos : positions) {
         DisplacedBlockRecord record = getActiveRecord(world.dimension(), pos);
         if (record != null && !record.permanentDestruction) {
            records.add(record);
            BlockState state = record.originalState;
            BlockPos other = null;
            if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof DoublePlantBlock) {
               other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.above();
            } else if (state.getBlock() instanceof BedBlock) {
               other = pos.relative(
                  state.getValue(BedBlock.PART) == BedPart.FOOT
                     ? (Direction)state.getValue(BedBlock.FACING)
                     : ((Direction)state.getValue(BedBlock.FACING)).getOpposite()
               );
            }

            DisplacedBlockRecord pair = other == null ? null : getActiveRecord(world.dimension(), other);
            if (pair != null
               && !pair.permanentDestruction
               && pair.originalState.is(state.getBlock())
               && Objects.equals(pair.boundPlayerUuid, record.boundPlayerUuid)) {
               records.add(pair);
            }
         }
      }

      if (!records.isEmpty()) {
         executeTwoPassRestoration(world, new ArrayList<>(records));
      }
   }

   public static void displaceBlockPermanently(
      ServerLevel world,
      BlockPos pos,
      Vector3f targetDisplacement,
      Quaternionf rotation,
      Vector3f scale,
      int lifetime,
      int glowColor,
      boolean spawnDisplay,
      DisplacedBlockRecord.DisplacementMode mode,
      UUID boundPlayerUuid,
      Vec3 blastCenter,
      double blastRadius
   ) {
      displaceBlockInternal(
         world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay, mode, boundPlayerUuid, blastCenter, blastRadius, true, true
      );
   }

   private static void displaceBlockInternal(
      ServerLevel world,
      BlockPos pos,
      Vector3f targetDisplacement,
      Quaternionf rotation,
      Vector3f scale,
      int lifetime,
      int glowColor,
      boolean spawnDisplay,
      DisplacedBlockRecord.DisplacementMode mode,
      UUID boundPlayerUuid,
      Vec3 blastCenter,
      double blastRadius,
      boolean checkDoubleBlock,
      boolean permanent
   ) {
      BlockPos immutablePos = pos.immutable();
      BlockState state = world.getBlockState(immutablePos);
      if (canDisplace(world, immutablePos, state)) {
         BlockPos otherHalf = checkDoubleBlock ? getMatchingDoubleBlockHalf(world, immutablePos, state) : null;
         if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
            spawnDisplay = false;
         }

         BlockEntity be = world.getBlockEntity(immutablePos);
         CompoundTag nbt = null;
         if (be != null) {
            nbt = be.saveWithFullMetadata();
            world.removeBlockEntity(immutablePos);
         }

         boolean isFull = state.isCollisionShapeFullBlock(world, immutablePos);
         DisplacedBlockRecord record = new DisplacedBlockRecord(
            immutablePos, world.dimension(), state, nbt, targetDisplacement, rotation, scale, lifetime, isFull, mode, boundPlayerUuid
         );
         record.permanentDestruction = permanent;
         record.blastCenter = blastCenter;
         record.blastRadius = blastRadius;
         if (!world.setBlock(immutablePos, Blocks.AIR.defaultBlockState(), 50)) {
            if (be != null) {
               be.clearRemoved();
               world.setBlockEntity(be);
            }
         } else {
            if (be != null) {
               if (be instanceof Clearable clearable) {
                  clearable.clearContent();
               }

               for (ServerPlayer player : world.players()) {
                  if (!player.containerMenu.stillValid(player)) {
                     player.closeContainer();
                  }
               }
            }

            ACTIVE_RECORDS.put(new DimensionPos(world.dimension(), immutablePos), record);
            DisplacementPersistentState.getServerState(world).setDirty();
            if (spawnDisplay) {
               BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world);
               display.addTag("godwand_displaced");
               display.addTag("godwand_managed_displacement");
               display.setPos(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
               GodWandDisplays.blockState(display, state);
               display.setGlowingTag(false);
               GodWandDisplays.glowColor(display, -1);
               GodWandDisplays.delay(display, 0);
               GodWandDisplays.duration(display, 0);
               GodWandDisplays.transform(display, Transformation.identity());
               world.addFreshEntity(display);
               record.displayEntityUuid = display.getUUID();
               ACTIVE_ENTITIES.put(display.getUUID(), display);
               if (mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER && shouldPlayPathSound(boundPlayerUuid, world.getGameTime(), false)) {
               }

               if (mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER && (immutablePos.asLong() & 3L) == 0L) {
                  world.sendParticles(
                     new BlockParticleOption(ParticleTypes.BLOCK, state),
                     immutablePos.getX() + 0.5,
                     immutablePos.getY() + 0.5,
                     immutablePos.getZ() + 0.5,
                     3,
                     0.15,
                     0.15,
                     0.15,
                     0.08
                  );
                  if (!permanent && (immutablePos.asLong() & 31L) == 0L) {
                     world.playSound(null, immutablePos, GodWandSounds.ev(SoundEvents.BASALT_BREAK), SoundSource.BLOCKS, 0.6F, 0.85F + 0.25F * (float)Math.random());
                  }
               }
            }

            if (otherHalf != null && canDisplace(world, otherHalf, world.getBlockState(otherHalf))) {
               displaceBlockInternal(
                  world,
                  otherHalf,
                  targetDisplacement,
                  rotation,
                  scale,
                  lifetime,
                  glowColor,
                  spawnDisplay,
                  mode,
                  boundPlayerUuid,
                  blastCenter,
                  blastRadius,
                  false,
                  permanent
               );
            }
         }
      }
   }

   public static void tick(ServerLevel world) {
      List<DisplacedBlockRecord> toRemove = new ArrayList<>();

      for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
         if (record.worldKey.equals(world.dimension()) && record.bridgeDestination == null) {
            record.ageTicks++;
            BlockDisplay display = record.displayEntityUuid != null ? ACTIVE_ENTITIES.get(record.displayEntityUuid) : null;
            if (record.permanentDestruction && record.ageTicks >= record.maxLifetimeTicks + 20) {
               removeActiveRecord(record.worldKey, record.pos);
            } else {
               if (record.isRestoring()) {
                  if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER || record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {
                     Vec3 blockCenter = Vec3.atCenterOf(record.pos);

                     for (ServerPlayer p : world.players()) {
                        if (SpatialPathWalker.isActive(p)) {
                           Vec3 lv = p.getViewVector(1.0F);
                           Vec3 lh = new Vec3(lv.x, 0.0, lv.z);
                           lh = lh.lengthSqr() < 0.001 ? new Vec3(1.0, 0.0, 0.0) : lh.normalize();
                           double lead = SpatialPathWalker.getPlayerForwardLead(p);
                           double holdingLead = Math.max(lead, 5.5);
                           double spineDist = SpatialMath.distanceToPathAura(blockCenter, p.position().add(0.0, 1.1, 0.0), lh, holdingLead);
                           if (spineDist <= 4.2) {
                              record.interruptRestoration();
                              if (display != null && display.isAlive()) {
                                 Vec3 rebound = SpatialMath.computeDynamicAuraOffset(record.pos, p.position(), lh, 2.1, record.ageTicks);
                                 record.currentOffset.set((float)rebound.x, (float)rebound.y, (float)rebound.z);
                                 Quaternionf reboundRot = SpatialMath.computeArchwayRotation(record.pos, p.position(), lh, record.ageTicks);
                                 record.currentRotation.set(reboundRot);
                                 GodWandDisplays.delay(display, 0);
                                 GodWandDisplays.duration(display, 5);
                                 GodWandDisplays.transform(display, SpatialMath.createPivotTransform(rebound, reboundRot, record.targetScale.x));
                              }
                              break;
                           }
                        }
                     }
                  }

                  if (record.isRestoring()) {
                     if (display != null && display.isAlive()) {
                        if (record.restoreTick == 1) {
                           Vec3 spiralPreSnap = SpatialMath.computeSpiralReturnOffset(record.getCurrentOffset(), record.pos);
                           record.currentOffset.set((float)spiralPreSnap.x, (float)spiralPreSnap.y, (float)spiralPreSnap.z);
                           Quaternionf nearZeroRot = new Quaternionf().slerp(record.currentRotation, 0.18F);
                           record.currentRotation.set(nearZeroRot);
                           GodWandDisplays.delay(display, 0);
                           GodWandDisplays.duration(display, 9);
                           GodWandDisplays.transform(display, SpatialMath.createPivotTransform(spiralPreSnap, nearZeroRot, 0.96F));
                        } else if (record.restoreTick == 10) {
                           record.currentOffset.set(0.0F, 0.0F, 0.0F);
                           GodWandDisplays.delay(display, 0);
                           GodWandDisplays.duration(display, 4);
                           GodWandDisplays.transform(display, Transformation.identity());
                           if (record.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER && (record.pos.asLong() & 31L) == 0L) {
                              world.playSound(null, record.pos, GodWandSounds.ev(SoundEvents.IRON_TRAPDOOR_OPEN), SoundSource.BLOCKS, 0.25F, 1.35F);
                           }
                        }
                     }

                     if (record.restoreTick == 14 && record.originalState.isCollisionShapeFullBlock(world, record.pos)) {
                        if (restoreRecordDirectly(world, record, display)) {
                           toRemove.add(record);
                        }
                     } else if (record.restoreTick >= 15 && restoreRecordDirectly(world, record, display)) {
                        toRemove.add(record);
                     }

                     record.restoreTick++;
                     continue;
                  }
               }

               if (record.mode == DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED) {
                  if (record.ageTicks >= record.maxLifetimeTicks + 20) {
                     record.beginRestoration();
                  }
               } else if (record.spawnTickDelay > 0) {
                  record.spawnTickDelay--;
                  if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                     GodWandDisplays.delay(display, 0);
                     int openDuration = 8;
                     if (record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER) {
                        double playerSpeed = 0.0;
                        double distToPlayer = 3.0;
                        if (record.boundPlayerUuid != null) {
                           ServerPlayer p = world.getServer().getPlayerList().getPlayer(record.boundPlayerUuid);
                           if (p != null) {
                              playerSpeed = SpatialPathWalker.getPlayerSpeed(p);
                              distToPlayer = Vec3.atCenterOf(record.pos).distanceTo(p.position());
                           }
                        }

                        if (playerSpeed >= 0.15) {
                           openDuration = 5;
                        } else if (distToPlayer <= 2.0) {
                           openDuration = 5;
                        } else {
                           float tDist = (float)Mth.clamp((distToPlayer - 2.0) / 2.0, 0.0, 1.0);
                           openDuration = (int)Mth.lerp(tDist, 6.0F, 9.0F);
                        }
                     }

                     record.openDurationTicks = openDuration;
                     GodWandDisplays.duration(display, openDuration);
                     GodWandDisplays.transform(display, SpatialMath.createPivotTransform(record.getCurrentOffset(), record.currentRotation, record.targetScale.x));
                     record.animationStage = 1;
                     record.transformed = true;
                  }
               } else {
                  int minHoldTicks = record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER ? 3 : 9;
                  if (record.ageTicks >= minHoldTicks) {
                     if (record.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER
                        && record.mode != DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {
                        if (record.mode == DisplacedBlockRecord.DisplacementMode.CANOPY_VORTEX
                           || record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE && record.blastCenter != null) {
                           if (record.ageTicks >= record.maxLifetimeTicks) {
                              record.beginRestoration();
                           } else if (display != null && display.isAlive() && (display.getId() + record.ageTicks) % 3 == 0) {
                              Vec3 blockCenter = Vec3.atCenterOf(record.pos);
                              Vec3 vortexOffset = SpatialMath.computeCanopyVortexOffset(record.pos, record.blastCenter, record.blastRadius, record.ageTicks);
                              Vec3 actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                              Player closestPlayer = world.getNearestPlayer(actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false);
                              if (closestPlayer != null) {
                                 Vec3 evasion = SpatialMath.computeInteractiveEvasion(actualDebrisWorldPos, closestPlayer.position().add(0.0, 1.1, 0.0), 3.4);
                                 if (evasion.lengthSqr() > 0.001) {
                                    vortexOffset = vortexOffset.add(evasion);
                                    record.hoverYaw += 0.35F;
                                 }
                              }

                              Vec3 smoothedVortex = record.getCurrentOffset().lerp(vortexOffset, 0.45);
                              record.currentOffset.set((float)smoothedVortex.x, (float)smoothedVortex.y, (float)smoothedVortex.z);
                              record.hoverYaw += 0.07F;
                              Quaternionf rot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                              record.currentRotation.set(rot);
                              GodWandDisplays.delay(display, 0);
                              GodWandDisplays.duration(display, 3);
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(smoothedVortex, rot, record.targetScale.x));
                           }
                        } else if (record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE) {
                           if (record.ageTicks >= record.maxLifetimeTicks) {
                              record.beginRestoration();
                           } else if (display != null && display.isAlive() && record.ageTicks % 6 == 0) {
                              record.hoverYaw += 0.08F;
                              double hashPhase = SpatialMath.hash01(record.pos) * Math.PI * 2.0;
                              float tremorY = (float)(Math.sin(hashPhase + record.ageTicks / 6.0) * 0.14);
                              Vector3f hoverDisp = new Vector3f(record.baseDisplacement.x, record.baseDisplacement.y + tremorY, record.baseDisplacement.z);
                              record.currentOffset.set(hoverDisp);
                              Quaternionf hoverRot = new Quaternionf(record.targetRotation).rotateY(record.hoverYaw);
                              record.currentRotation.set(hoverRot);
                              GodWandDisplays.delay(display, 0);
                              GodWandDisplays.duration(display, 6);
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(hoverDisp, hoverRot, record.targetScale.x));
                           }
                        }
                     } else {
                        Vec3 blockCenter = Vec3.atCenterOf(record.pos);
                        ServerPlayer activeOwner = null;
                        double minSpineDist = Double.MAX_VALUE;

                        for (ServerPlayer p : world.players()) {
                           if (SpatialPathWalker.isActive(p)) {
                              Vec3 lv = p.getViewVector(1.0F);
                              Vec3 lh = new Vec3(lv.x, 0.0, lv.z);
                              lh = lh.lengthSqr() < 0.001 ? new Vec3(1.0, 0.0, 0.0) : lh.normalize();
                              double lead = SpatialPathWalker.getPlayerForwardLead(p);
                              double holdingLead = Math.max(lead, 5.5);
                              double d = SpatialMath.distanceToPathAura(blockCenter, p.position().add(0.0, 1.1, 0.0), lh, holdingLead);
                              if (d < minSpineDist) {
                                 minSpineDist = d;
                                 activeOwner = p;
                              }
                           }
                        }

                        if (activeOwner != null && minSpineDist <= 4.2) {
                           record.resetRestoreTick();
                           if (display != null && display.isAlive() && (record.ageTicks + Math.abs(record.pos.hashCode())) % 3 == 0) {
                              Vec3 lookVec = activeOwner.getViewVector(1.0F);
                              Vec3 lookHoriz = new Vec3(lookVec.x, 0.0, lookVec.z);
                              lookHoriz = lookHoriz.lengthSqr() < 0.001 ? new Vec3(1.0, 0.0, 0.0) : lookHoriz.normalize();
                              Vec3 desiredAuraOffset = SpatialMath.computeDynamicAuraOffset(record.pos, activeOwner.position(), lookHoriz, 2.1, record.ageTicks);
                              Vec3 actualDebrisWorldPos = blockCenter.add(record.getCurrentOffset());
                              Player closestToDebris = world.getNearestPlayer(
                                 actualDebrisWorldPos.x, actualDebrisWorldPos.y, actualDebrisWorldPos.z, 3.8, false
                              );
                              if (closestToDebris != null) {
                                 Vec3 evasion = SpatialMath.computeInteractiveEvasion(actualDebrisWorldPos, closestToDebris.position().add(0.0, 1.1, 0.0), 3.4);
                                 if (evasion.lengthSqr() > 0.001) {
                                    desiredAuraOffset = desiredAuraOffset.add(evasion);
                                    record.hoverYaw += 0.38F;
                                 }
                              }

                              Vec3 smoothedOffset = record.getCurrentOffset().lerp(desiredAuraOffset, 0.42);
                              record.currentOffset.set((float)smoothedOffset.x, (float)smoothedOffset.y, (float)smoothedOffset.z);
                              record.hoverYaw += 0.09F;
                              Quaternionf rot = SpatialMath.computeArchwayRotation(record.pos, activeOwner.position(), lookHoriz, record.ageTicks);
                              record.currentRotation.set(rot);
                              GodWandDisplays.delay(display, 0);
                              GodWandDisplays.duration(display, 3);
                              GodWandDisplays.transform(display, SpatialMath.createPivotTransform(smoothedOffset, rot, record.targetScale.x));
                           }
                        } else {
                           record.beginRestoration();
                        }
                     }
                  }
               }
            }
         }
      }

      for (DisplacedBlockRecord r : toRemove) {
         ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
         DisplacementPersistentState.getServerState(world).setDirty();
      }
   }

   private static boolean restoreRecordDirectly(ServerLevel world, DisplacedBlockRecord r, BlockDisplay display) {
      BlockState current = world.getBlockState(r.pos);
      if (r.permanentDestruction || current.isAir() || current.canBeReplaced() && !current.equals(r.originalState)
         || current.equals(r.originalState) && r.blockEntityNbt == null) {
         if (r.bridgeDestination != null) {
            if (!world.getChunkSource().hasChunk(r.bridgeDestination.getX() >> 4, r.bridgeDestination.getZ() >> 4)
               || !world.getChunkSource().hasChunk(r.pos.getX() >> 4, r.pos.getZ() >> 4)) {
               return false;
            }

            if (!world.getBlockState(r.bridgeDestination).equals(r.originalState)) {
               removeActiveRecord(r.worldKey, r.pos);
               return true;
            }

            if (!world.removeBlock(r.bridgeDestination, false)) {
               return false;
            }

            BRIDGE_DESTINATIONS.remove(new DimensionPos(r.worldKey, r.bridgeDestination));
            r.bridgeDestination = null;
         }

         if (r.permanentDestruction) {
            removeActiveRecord(r.worldKey, r.pos);
            return true;
         }

         if (!world.setBlock(r.pos, r.originalState, 18) && !world.getBlockState(r.pos).equals(r.originalState)) {
            return false;
         }

         if (r.blockEntityNbt != null) {
            BlockEntity be = world.getBlockEntity(r.pos);
            if (be != null) {
               be.load(r.blockEntityNbt);
               be.setChanged();
            }
         }

         BlockDisplay toDiscard = display;
         if (toDiscard == null && r.displayEntityUuid != null) {
            toDiscard = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
         }

         if (toDiscard != null) {
            ACTIVE_ENTITIES.remove(r.displayEntityUuid);
            toDiscard.discard();
         }

         if (r.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER) {
            world.sendParticles(
               new BlockParticleOption(ParticleTypes.BLOCK, r.originalState),
               r.pos.getX() + 0.5,
               r.pos.getY() + 0.5,
               r.pos.getZ() + 0.5,
               4,
               0.15,
               0.15,
               0.15,
               0.05
            );
         }

         if (r.mode != DisplacedBlockRecord.DisplacementMode.PATH_WALKER && (r.pos.asLong() & 31L) == 0L) {
            world.playSound(null, r.pos, GodWandSounds.ev(SoundEvents.STONE_PLACE), SoundSource.BLOCKS, 0.3F, 1.0F);
         } else if (r.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER) {
            shouldPlayPathSound(r.boundPlayerUuid, world.getGameTime(), true);
         }

         return true;
      } else {
         return false;
      }
   }

   public static void collapsePlayerAura(UUID playerUuid) {
      for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
         if (playerUuid != null && playerUuid.equals(record.boundPlayerUuid) && isPathRecord(record)) {
            record.beginRestoration();
         }
      }
   }

   private static boolean isPathRecord(DisplacedBlockRecord record) {
      return record.mode == DisplacedBlockRecord.DisplacementMode.PATH_WALKER
         || record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA
         || record.bridgeDestination != null;
   }

   public static void restorePathForPlayer(UUID playerUuid, ServerLevel preferredLevel) {
      restoreForPlayer(playerUuid, preferredLevel, true);
   }

   public static void restoreAllForPlayer(UUID playerUuid, ServerLevel preferredLevel) {
      restoreForPlayer(playerUuid, preferredLevel, false);
   }

   private static void restoreForPlayer(UUID playerUuid, ServerLevel preferredLevel, boolean pathOnly) {
      if (playerUuid != null) {
         List<DisplacedBlockRecord> toRestore = new ArrayList<>();

         for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (playerUuid.equals(r.boundPlayerUuid) && (!pathOnly || isPathRecord(r))) {
               if (r.permanentDestruction) {
                  removeActiveRecord(r.worldKey, r.pos);
               } else {
                  toRestore.add(r);
               }
            }
         }

         if (!toRestore.isEmpty()) {
            Map<ResourceKey<Level>, List<DisplacedBlockRecord>> byWorld = new HashMap<>();

            for (DisplacedBlockRecord r : toRestore) {
               byWorld.computeIfAbsent(r.worldKey, k -> new ArrayList<>()).add(r);
            }

            MinecraftServer server = preferredLevel != null ? preferredLevel.getServer() : null;

            for (Entry<ResourceKey<Level>, List<DisplacedBlockRecord>> entry : byWorld.entrySet()) {
               ServerLevel targetLevel = preferredLevel;
               if (server != null && (targetLevel == null || !targetLevel.dimension().equals(entry.getKey()))) {
                  targetLevel = server.getLevel(entry.getKey());
               }

               if (targetLevel != null) {
                  executeTwoPassRestoration(targetLevel, entry.getValue());
               }
            }
         }
      }
   }

   public static void emergencyRestoreAll(ServerLevel world) {
      List<DisplacedBlockRecord> matching = new ArrayList<>();

      for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
         if (r.worldKey.equals(world.dimension())) {
            if (r.permanentDestruction) {
               ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
               if (r.displayEntityUuid != null) {
                  BlockDisplay ent = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
                  if (ent != null && ent.isAlive()) {
                     ent.discard();
                  }
               }
            } else {
               matching.add(r);
            }
         }
      }

      executeTwoPassRestoration(world, matching);
   }

   public static void executeTwoPassRestoration(ServerLevel world, List<DisplacedBlockRecord> records) {
      for (DisplacedBlockRecord r : records) {
         if (r.isFullCube) {
            BlockDisplay d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
            if (restoreRecordDirectly(world, r, d)) {
               ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
            }
         }
      }

      List<DisplacedBlockRecord> delicate = new ArrayList<>();

      for (DisplacedBlockRecord r : records) {
         if (!r.isFullCube) {
            delicate.add(r);
         }
      }

      delicate.sort(Comparator.comparingInt(rx -> rx.pos.getY()));

      for (DisplacedBlockRecord r : delicate) {
         BlockDisplay d = r.displayEntityUuid != null ? ACTIVE_ENTITIES.get(r.displayEntityUuid) : null;
         if (restoreRecordDirectly(world, r, d)) {
            ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
         }
      }

      DisplacementPersistentState.getServerState(world).setDirty();
   }

   public static void collapseNearbyDisplacements(ResourceKey<Level> worldKey, BlockPos center, double radius) {
      double rSq = radius * radius;

      for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
         if (record.worldKey.equals(worldKey)) {
            double dSq = record.pos.distSqr(center);
            if (dSq <= rSq) {
               record.beginRestoration();
            }
         }
      }
   }
}

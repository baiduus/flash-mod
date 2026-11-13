package com.flash.godwand;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SpatialPathWalker {
   private static final Map<UUID, Integer> LAST_SHIELD_SOUND = new ConcurrentHashMap<>();
   private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Double> LAST_GROUND_Y = new ConcurrentHashMap<>();
   private static final Map<UUID, Vec3> LAST_PLAYER_POS = new ConcurrentHashMap<>();
   private static final Map<UUID, Double> CURRENT_FORWARD_LEAD = new ConcurrentHashMap<>();
   private static final Map<UUID, Double> CURRENT_PLAYER_SPEED = new ConcurrentHashMap<>();

   public static boolean isActive(Player player) {
      return ACTIVE_PLAYERS.contains(player.getUUID());
   }

   public static void toggle(ServerPlayer player) {
      if (SkillCooldowns.ready(player, 0)) {
         SkillCooldowns.start(player, 0, GodWandConfig.skillCooldownTicks(0, 1));
         ServerLevel world = player.serverLevel();
         if (ACTIVE_PLAYERS.contains(player.getUUID())) {
            ACTIVE_PLAYERS.remove(player.getUUID());
            SpatialBridge.disable(player.getUUID());
            LAST_GROUND_Y.remove(player.getUUID());
            LAST_PLAYER_POS.remove(player.getUUID());
            CURRENT_FORWARD_LEAD.remove(player.getUUID());
            CURRENT_PLAYER_SPEED.remove(player.getUUID());
            LAST_SHIELD_SOUND.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.godwand.path_disabled"), true);
            world.playSound(
               null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_DEPLETE), SoundSource.PLAYERS, 0.45F, 0.9F
            );
            world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.BEACON_DEACTIVATE), SoundSource.PLAYERS, 0.55F, 1.2F);

            for (LivingEntity e : world.getEntitiesOfClass(
               LivingEntity.class,
               player.getBoundingBox().inflate(5.0),
               ent -> ent != player
                  && (ent instanceof Mob || ent instanceof Player)
                  && ent.isAlive()
                  && !ent.isSpectator()
                  && !ent.isPassengerOfSameVehicle(player)
            )) {
               Vec3 diff = e.position().subtract(player.position()).normalize().scale(0.95).add(0.0, 0.28, 0.0);
               e.push(diff.x, diff.y, diff.z);
               e.hurtMarked = true;
            }

            SpatialDisplacementManager.collapsePlayerAura(player.getUUID());
            SpatialDisplacementManager.collapseNearbyDisplacements(world.dimension(), player.blockPosition(), 14.0);
            ImpactFxPayload colPayload = new ImpactFxPayload(player.getX(), player.getEyeY(), player.getZ(), 2, 8, 1.0F);
            GodWandNet.near(world, player.position(), 48.0, colPayload);
         } else {
            ACTIVE_PLAYERS.add(player.getUUID());
            repelMobs(player);
            deflectProjectiles(player);
            player.displayClientMessage(Component.translatable("message.godwand.path_enabled"), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.BEACON_ACTIVATE), SoundSource.PLAYERS, 0.75F, 1.25F);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_CHARGE), SoundSource.PLAYERS, 0.55F, 1.4F);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.PLAYERS, 0.65F, 1.15F);
            CinematicFxPayload actShake = new CinematicFxPayload(0, player.getX(), player.getY(), player.getZ(), 0.45F, 10);
            CinematicFxPayload actFov = new CinematicFxPayload(1, player.getX(), player.getY(), player.getZ(), 1.08F, 10);
            GodWandNet.to(player, actShake);
            GodWandNet.to(player, actFov);
            world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.7, 0.5, 0.15);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.6, 0.8, 0.6, 0.22);
         }

         syncAura(player);
      }
   }

   /** Yas keeps the path while she is Yas (it is hers, not a wand's). */
   public static boolean hasGodWand(Player player) {
      return player != null && com.flash.yas.YasPowers.has(player);
   }

   public static void resetRuntime() {
      ACTIVE_PLAYERS.clear();
      LAST_GROUND_Y.clear();
      LAST_PLAYER_POS.clear();
      CURRENT_FORWARD_LEAD.clear();
      CURRENT_PLAYER_SPEED.clear();
      LAST_SHIELD_SOUND.clear();
   }

   public static void deactivate(Player player) {
      SpatialBridge.disable(player.getUUID());
      ACTIVE_PLAYERS.remove(player.getUUID());
      LAST_GROUND_Y.remove(player.getUUID());
      LAST_PLAYER_POS.remove(player.getUUID());
      CURRENT_FORWARD_LEAD.remove(player.getUUID());
      CURRENT_PLAYER_SPEED.remove(player.getUUID());
      LAST_SHIELD_SOUND.remove(player.getUUID());
      if (player instanceof ServerPlayer serverPlayer) {
         SpatialDisplacementManager.restorePathForPlayer(player.getUUID(), serverPlayer.serverLevel());
         syncAura(serverPlayer);
      } else {
         SpatialDisplacementManager.restorePathForPlayer(player.getUUID(), null);
      }
   }

   private static void syncAura(ServerPlayer player) {
      SpatialAuraPayload payload = new SpatialAuraPayload(player.getId(), isActive(player));
      GodWandNet.near(player.level(), player.position(), 96.0, payload);
   }

   static boolean shouldPlayShieldSound(UUID player, int tick) {
      Integer previous = LAST_SHIELD_SOUND.get(player);
      if (previous != null && tick >= previous && tick - previous < 6) {
         return false;
      }

      LAST_SHIELD_SOUND.put(player, tick);
      return true;
   }

   private static void sendAuraHit(ServerPlayer player, Vec3 normal) {
      SpatialAuraPayload payload = new SpatialAuraPayload(player.getId(), true, normal);
      GodWandNet.near(player.level(), player.position(), 96.0, payload);
   }

   private static void deflectProjectiles(ServerPlayer player) {
      ServerLevel world = player.serverLevel();
      Vec3 center = player.position().add(0.0, 1.5, 0.0);

      for (Projectile arrow : world.getEntitiesOfClass(
         Projectile.class, player.getBoundingBox().inflate(16.0), e -> e.getOwner() != player && e.getDeltaMovement().lengthSqr() > 1.0E-4
      )) {
         Vec3 velocity = arrow.getDeltaMovement();
         Vec3 hit = SpatialMath.auraArrowIntersection(arrow.position(), arrow.position().add(velocity), center);
         if (hit == null) {
            Vec3 offset = arrow.position().subtract(center);
            if (offset.lengthSqr() < 25.0 && velocity.dot(offset) < 0.0) {
               arrow.setDeltaMovement(velocity.scale(0.85));
               arrow.hurtMarked = true;
            }
         } else {
            BlockHitResult obstruction = world.clip(new ClipContext(arrow.position(), hit, Block.COLLIDER, Fluid.NONE, arrow));
            if (obstruction.getType() == Type.MISS) {
               Vec3 normal = hit.subtract(center).normalize();
               if (normal.lengthSqr() < 0.01) {
                  normal = velocity.normalize().reverse();
               }

               arrow.setPos(hit.add(normal.scale(0.05)));
               arrow.setDeltaMovement(SpatialMath.deflectAuraArrow(velocity, normal));
               if (arrow instanceof AbstractHurtingProjectile explosive) {
                  explosive.xPower *= 0.35;
                  explosive.yPower *= 0.35;
                  explosive.zPower *= 0.35;
               }

               arrow.hurtMarked = true;
               sendAuraHit(player, normal);
               if (shouldPlayShieldSound(player.getUUID(), player.tickCount)) {
                  world.playSound(null, hit.x, hit.y, hit.z, GodWandSounds.ev(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.PLAYERS, 0.35F, 1.6F);
               }
            }
         }
      }
   }

   static void repelMobs(ServerPlayer player) {
      Vec3 center = player.position().add(0.0, 1.5, 0.0);

      for (LivingEntity mob : player.level()
         .getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(5.0),
            e -> e != player && (e instanceof Mob || e instanceof Player) && e.isAlive() && !e.isSpectator() && !e.isPassengerOfSameVehicle(player)
         )) {
         Vec3 offset = mob.getBoundingBox().getCenter().subtract(center);
         double distance = offset.length();
         double reach = 3.3 + mob.getBbWidth() * 0.5;
         if (!(distance > reach + 0.8)) {
            Vec3 normal;
            if (distance < 0.05) {
               double angle = (mob.getUUID().getLeastSignificantBits() & 1023L) * Math.PI * 2.0 / 1024.0;
               normal = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            } else {
               normal = offset.normalize();
               if (mob.onGround()) {
                  normal = new Vec3(normal.x, Math.max(0.0, normal.y), normal.z).normalize();
                  if (normal.lengthSqr() < 0.01) {
                     normal = new Vec3(1.0, 0.0, 0.0);
                  }
               }
            }

            mob.setDeltaMovement(SpatialMath.auraMobVelocity(mob.getDeltaMovement(), normal, distance - mob.getBbWidth() * 0.5));
            mob.hurtMarked = true;
            if (distance <= reach && (player.tickCount + mob.getId()) % 12 == 0) {
               sendAuraHit(player, normal);
            }
         }
      }
   }

   public static double updatePlayerForwardLead(ServerPlayer player) {
      Vec3 currentPos = player.position();
      Vec3 lastPos = LAST_PLAYER_POS.getOrDefault(player.getUUID(), currentPos);
      LAST_PLAYER_POS.put(player.getUUID(), currentPos);
      Vec3 deltaHoriz = new Vec3(currentPos.x - lastPos.x, 0.0, currentPos.z - lastPos.z);
      double speed = deltaHoriz.length();
      CURRENT_PLAYER_SPEED.put(player.getUUID(), speed);
      double boost = speed > 0.02 && speed < 4.0 ? Math.min(3.2, speed * 7.0) : 0.0;
      if (player.isSprinting() && boost < 1.2) {
         boost = 1.2;
      }

      double lead = 1.0 + boost;
      CURRENT_FORWARD_LEAD.put(player.getUUID(), lead);
      return lead;
   }

   public static double getPlayerSpeed(ServerPlayer player) {
      return CURRENT_PLAYER_SPEED.getOrDefault(player.getUUID(), 0.0);
   }

   public static double getPlayerForwardLead(ServerPlayer player) {
      return CURRENT_FORWARD_LEAD.getOrDefault(player.getUUID(), 1.0);
   }

   public static void tickPlayer(ServerPlayer player) {
      if (isActive(player)) {
         if (player.isAlive()
            && !player.isSpectator()
            && GodWandConfig.SERVER.isDimensionAllowed(player.level().dimension().location().toString())
            && hasGodWand(player)) {
            ServerLevel world = player.serverLevel();
            SpatialBridge.tickPlayer(player);
            if (player.tickCount % 20 == 0) {
               syncAura(player);
            }

            if (player.tickCount % 30 == 0) {
               world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.BEACON_AMBIENT), SoundSource.PLAYERS, 0.4F, 1.2F);
               world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_AMBIENT), SoundSource.PLAYERS, 0.3F, 1.25F);
            }

            deflectProjectiles(player);
            repelMobs(player);
            Vec3 playerPos = player.position();
            Vec3 chestPos = playerPos.add(0.0, 1.1, 0.0);
            Vec3 lookVec = player.getViewVector(1.0F);
            Vec3 lookDirHoriz = new Vec3(lookVec.x, 0.0, lookVec.z);
            if (lookDirHoriz.lengthSqr() < 0.001) {
               lookDirHoriz = new Vec3(1.0, 0.0, 0.0);
            } else {
               lookDirHoriz = lookDirHoriz.normalize();
            }

            boolean allowDown = player.getAbilities().flying && player.getXRot() > 45.0F;
            if (player.onGround()) {
               LAST_GROUND_Y.put(player.getUUID(), player.getY());
            }

            double baseGroundY = player.onGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUUID(), player.getY());
            int floorY = (int)Math.floor(baseGroundY);
            double forwardLead = updatePlayerForwardLead(player);
            double auraRadius = 2.1;
            double maxRange = 3.3 + forwardLead + 0.8;
            int minX = Mth.floor(playerPos.x - maxRange);
            int maxX = Mth.floor(playerPos.x + maxRange);
            int minY = allowDown ? Mth.floor(playerPos.y - 2.2) : floorY;
            int maxY = Mth.floor(playerPos.y + 4.8);
            int minZ = Mth.floor(playerPos.z - maxRange);
            int maxZ = Mth.floor(playerPos.z + maxRange);

            for (FallingBlockEntity falling : world.getEntitiesOfClass(
               FallingBlockEntity.class, player.getBoundingBox().inflate(maxRange + 1.0), e -> e.isAlive()
            )) {
               if (!(falling.getY() < baseGroundY) && !(SpatialMath.distanceToPathAura(falling.position(), chestPos, lookDirHoriz, forwardLead) > 3.9)) {
                  falling.setDeltaMovement(SpatialMath.pathLooseBlockVelocity(falling.position(), chestPos, lookDirHoriz));
                  falling.hurtMarked = true;
               }
            }

            MutableBlockPos mutablePos = new MutableBlockPos();

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  for (int y = minY; y <= maxY; y++) {
                     mutablePos.set(x, y, z);
                     if (SpatialMath.canOpenPathBlock(y, baseGroundY, allowDown)) {
                        BlockState state = world.getBlockState(mutablePos);
                        if (!allowDown) {
                           VoxelShape shape = state.getCollisionShape(world, mutablePos, CollisionContext.of(player));
                           if (!shape.isEmpty() && y + shape.max(Axis.Y) <= baseGroundY + 0.125) {
                              continue;
                           }

                           boolean supportsFeet = false;

                           for (AABB box : shape.toAabbs()) {
                              if (SpatialMath.supportsPathFeet(box.move(mutablePos), player.getBoundingBox())) {
                                 supportsFeet = true;
                                 break;
                              }
                           }

                           if (supportsFeet) {
                              continue;
                           }
                        }

                        Vec3 blockCenter = Vec3.atCenterOf(mutablePos);
                        double spineDist = SpatialMath.distanceToPathAura(blockCenter, chestPos, lookDirHoriz, forwardLead);
                        if (!(spineDist > 3.3)) {
                           if (SpatialDisplacementManager.isPositionDisplaced(world.dimension(), mutablePos)) {
                              SpatialDisplacementManager.refreshDisplacement(world.dimension(), mutablePos, 100);
                              if (!state.isAir()
                                 && state.getFluidState().isEmpty()
                                 && !state.hasBlockEntity()
                                 && state.getDestroySpeed(world, mutablePos) >= 0.0F) {
                                 FallingBlockEntity loose = FallingBlockEntity.fall(world, mutablePos.immutable(), state);
                                 loose.setDeltaMovement(SpatialMath.pathLooseBlockVelocity(blockCenter, chestPos, lookDirHoriz));
                                 loose.hurtMarked = true;
                              }
                           } else if (SpatialDisplacementManager.canDisplace(world, mutablePos, state)) {
                              Vec3 auraDisp = SpatialMath.computeDynamicAuraOffset(mutablePos, playerPos, lookDirHoriz, auraRadius, 0);
                              Vector3f targetDisp = new Vector3f((float)auraDisp.x, (float)auraDisp.y, (float)auraDisp.z);
                              Quaternionf rotation = SpatialMath.computeArchwayRotation(mutablePos, playerPos, lookDirHoriz, 0);
                              float blockScale = 0.78F + (float)(SpatialMath.hash01(mutablePos) * 0.14);
                              Vector3f scale = new Vector3f(blockScale, blockScale, blockScale);
                              int glowColor = (x + y + z & 1) == 0 ? 62975 : 11032055;
                              SpatialDisplacementManager.displaceBlock(
                                 world,
                                 mutablePos.immutable(),
                                 targetDisp,
                                 rotation,
                                 scale,
                                 100,
                                 glowColor,
                                 true,
                                 DisplacedBlockRecord.DisplacementMode.PATH_WALKER,
                                 player.getUUID(),
                                 null,
                                 0.0
                              );
                           }
                        }
                     }
                  }
               }
            }
         } else {
            deactivate(player);
         }
      }
   }
}

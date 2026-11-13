package com.flash.godwand;

import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StoneCannonManager {
   public static int calculateChargeLevel(int chargeTicks) {
      if (chargeTicks >= 80) {
         return 4;
      } else if (chargeTicks >= 50) {
         return 3;
      } else {
         return chargeTicks >= 25 ? 2 : 1;
      }
   }

   public static float calculateDirectDamage(int level) {
      return switch (level) {
         case 1 -> 65.0F;
         case 2 -> 120.0F;
         case 3 -> 185.0F;
         default -> 260.0F;
      };
   }

   public static double calculateCraterRadius(int level) {
      return switch (level) {
         case 1 -> 3.0;
         case 2 -> 4.5;
         case 3 -> 6.0;
         default -> 8.0;
      };
   }

   public static double calculateSpeed(int level) {
      return switch (level) {
         case 1 -> 2.8;
         case 2 -> 3.6;
         case 3 -> 4.5;
         default -> 5.4;
      };
   }

   public static double calculateRecoil(int level) {
      return switch (level) {
         case 1 -> 0.4;
         case 2 -> 0.75;
         case 3 -> 1.15;
         default -> 1.65;
      };
   }

   public static boolean insideDrillSweep(Vec3 point, Vec3 start, Vec3 end, double radius, double length) {
      Vec3 travel = end.subtract(start);
      double distance = travel.length();
      if (distance < 1.0E-6) {
         return false;
      } else {
         Vec3 axis = travel.scale(1.0 / distance);
         Vec3 relative = point.subtract(start);
         double axial = relative.dot(axis);
         if (!(axial < -length) && !(axial > distance)) {
            double coneRadius = radius * Math.min(1.0, (distance - axial) / length);
            return relative.lengthSqr() - axial * axial <= coneRadius * coneRadius;
         } else {
            return false;
         }
      }
   }

   private static Vec3 excavateImpact(StoneCannonProjectileEntity projectile, Vec3 start, Vec3 end) {
      if (!(projectile.getOwner() instanceof ServerPlayer owner) || !GodWandSkills.destroys(owner)) {
         return start;
      }

      ServerLevel world = (ServerLevel)projectile.level();
      Vec3 travel = end.subtract(start);
      int steps = Math.max(1, (int)Math.ceil(travel.length() * 4.0));

      for (int i = 0; i <= steps; i++) {
         Vec3 sample = start.lerp(end, (double)i / steps);
         BlockPos pos = BlockPos.containing(sample);
         if (!world.hasChunkAt(pos) || !world.isInWorldBounds(pos) || world.getBlockState(pos).getDestroySpeed(world, pos) < 0.0F) {
            end = start.lerp(end, (double)Math.max(0, i - 1) / steps);
            break;
         }
      }

      BlockHitResult barrier = world.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, projectile) {
         public VoxelShape getBlockShape(BlockState state, BlockGetter view, BlockPos pos) {
            return state.getDestroySpeed(view, pos) < 0.0F ? Shapes.block() : Shapes.empty();
         }
      });
      if (barrier.getType() != Type.MISS) {
         end = start.lerp(barrier.getLocation(), 0.9999);
      }

      if (start.distanceToSqr(end) < 1.0E-6) {
         return start;
      }

      double radius = 0.65 + projectile.getChargeLevel() * 0.28;
      double length = radius * 3.2;
      Vec3 axis = travel.normalize();
      Vec3 tail = start.subtract(axis.scale(length));
      AABB bounds = new AABB(tail, end).inflate(radius + 0.9);
      int debris = 0;
      boolean removedDisplacement = false;

      for (BlockPos pos : BlockPos.betweenClosed(
         (int)Math.floor(bounds.minX),
         (int)Math.floor(bounds.minY),
         (int)Math.floor(bounds.minZ),
         (int)Math.ceil(bounds.maxX),
         (int)Math.ceil(bounds.maxY),
         (int)Math.ceil(bounds.maxZ)
      )) {
         if (world.isInWorldBounds(pos)
            && world.hasChunkAt(pos)
            && insideDrillSweep(Vec3.atCenterOf(pos), start, end.add(axis.scale(0.87)), radius + 0.87, length)) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getDestroySpeed(world, pos) < 0.0F)) {
               DisplacedBlockRecord displaced = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
               if (displaced != null) {
                  SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                  removedDisplacement = true;
               }

               if (!state.isAir()) {
                  world.destroyBlock(pos, false, projectile.getOwner(), 0);
                  if (debris++ < 6) {
                     world.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.06
                     );
                  }
               }
            }
         }
      }

      if (removedDisplacement) {
         DisplacementPersistentState.getServerState(world).setDirty();
      }

      return end;
   }

   public static float chargeStoneVolume(int ticks) {
      return 0.28F + 0.14F * Mth.clamp(ticks / 80.0F, 0.0F, 1.0F);
   }

   public static void tickCharge(ServerPlayer player, int chargeTicks, int level) {
      ServerLevel sw = player.serverLevel();
      Vec3 look = player.getViewVector(1.0F);
      Vec3 focusPos = player.getEyePosition().add(look.scale(2.0)).add(0.0, -0.22, 0.0);
      if (chargeTicks == 0) {
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.NETHERITE_BLOCK_PLACE), SoundSource.PLAYERS, 1.1F, 0.75F);
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.DEEPSLATE_BREAK), SoundSource.PLAYERS, 1.0F, 0.65F);
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.ANVIL_LAND), SoundSource.PLAYERS, 0.55F, 1.6F);
      }

      if (chargeTicks == 3) {
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.BASALT_PLACE), SoundSource.PLAYERS, 0.95F, 1.25F);
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.DEEPSLATE_PLACE), SoundSource.PLAYERS, 0.9F, 1.15F);
      } else if (chargeTicks == 6) {
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.STONE_PLACE), SoundSource.PLAYERS, 0.95F, 1.35F);
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.ANVIL_PLACE), SoundSource.PLAYERS, 0.45F, 1.7F);
      } else if (chargeTicks == 10) {
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.NETHERITE_BLOCK_STEP), SoundSource.PLAYERS, 1.0F, 1.3F);
         sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.BASALT_BREAK), SoundSource.PLAYERS, 0.85F, 1.4F);
      }

      if (chargeTicks >= 0 && chargeTicks % 12 == 0) {
         sw.playSound(
            null,
            player.blockPosition(),
            GodWandSounds.ev(SoundEvents.GRINDSTONE_USE),
            SoundSource.PLAYERS,
            chargeStoneVolume(chargeTicks),
            0.65F + Math.min(chargeTicks, 80) * 0.008F
         );
         if (chargeTicks < 50) {
            sw.playSound(null, player.blockPosition(), GodWandSounds.ev(SoundEvents.BASALT_HIT), SoundSource.PLAYERS, 0.28F, 0.8F + chargeTicks * 0.006F);
         }
      }

      if (chargeTicks % 2 == 0) {
         for (int i = 0; i < 3; i++) {
            double groundR = 3.5 + sw.getRandom().nextDouble() * 5.0;
            double groundAng = sw.getRandom().nextDouble() * Math.PI * 2.0;
            double gx = player.getX() + Math.cos(groundAng) * groundR;
            double gz = player.getZ() + Math.sin(groundAng) * groundR;
            double gy = player.getY() + 0.1;
            BlockPos gPos = BlockPos.containing(gx, gy, gz);
            BlockState gState = sw.getBlockState(gPos.below());
            BlockState blockToSparks = gState.isAir() ? Blocks.STONE.defaultBlockState() : gState;
            Vec3 toFocus = focusPos.subtract(gx, gy, gz).normalize().scale(0.45 + level * 0.12);
            sw.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockToSparks), gx, gy, gz, 2, toFocus.x, toFocus.y, toFocus.z, 0.15);
         }
      }

      if (level >= 3 && chargeTicks % 2 == 0) {
         sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, focusPos.x, focusPos.y, focusPos.z, 3, 0.15, 0.15, 0.15, 0.05);
      }

      if (chargeTicks == 25 || chargeTicks == 50 || chargeTicks == 80) {
         sw.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.RESPAWN_ANCHOR_CHARGE), SoundSource.PLAYERS, 0.65F, 0.85F + level * 0.15F);
      }

      if (chargeTicks % 4 == 0) {
         float trauma = (float)(0.04 + level * 0.05);
         CinematicFxPayload shake = new CinematicFxPayload(0, player.getX(), player.getY(), player.getZ(), trauma, 4);
         GodWandNet.to(player, shake);
      }
   }

   public static void fire(ServerPlayer player, int chargeDuration) {
      int level = calculateChargeLevel(chargeDuration);
      double speed = calculateSpeed(level);
      ServerLevel sw = player.serverLevel();
      Vec3 look = player.getViewVector(1.0F);
      Vec3 spawnPos = player.getEyePosition().add(look.scale(1.5));
      StoneCannonProjectileEntity cannon = new StoneCannonProjectileEntity(player.level(), player, level);
      cannon.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
      cannon.setDeltaMovement(look.scale(speed));
      player.level().addFreshEntity(cannon);
      double recoil = calculateRecoil(level);
      player.push(-look.x * recoil, -look.y * recoil * 0.4 + 0.12, -look.z * recoil);
      player.hurtMarked = true;
      sw.sendParticles(ParticleTypes.SONIC_BOOM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.0, 0.0, 0.0, 0.0);
      sw.sendParticles(ParticleTypes.FLASH, spawnPos.x, spawnPos.y, spawnPos.z, 2, 0.1, 0.1, 0.1, 0.0);
      sw.sendParticles(ParticleTypes.SWEEP_ATTACK, spawnPos.x, spawnPos.y, spawnPos.z, 3, 0.2, 0.2, 0.2, 0.05);
      sw.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.PLAYERS, 1.15F, 1.4F);
      sw.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 0.85F, 0.8F);
      sw.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 0.65F, 1.4F);
      player.displayClientMessage(Component.translatable("message.godwand.cannon_fired"), true);
   }

   public static void onProjectileImpact(StoneCannonProjectileEntity projectile, HitResult hit) {
      int level = projectile.getChargeLevel();
      Vec3 hitPos = hit.getLocation();
      ServerLevel sw = (ServerLevel)projectile.level();
      Vec3 direction = projectile.getDeltaMovement().normalize();
      Vec3 surfaceNormal = hit instanceof BlockHitResult blockHit ? Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()) : direction.reverse();
      CannonPressurePayload pressure = new CannonPressurePayload(hitPos, direction, level, surfaceNormal);
      GodWandNet.near(sw, hitPos, 96.0, pressure);
      excavateImpact(projectile, hitPos, hitPos.add(direction.scale(3.0 + level * 1.5)));
      if (hit instanceof EntityHitResult entityHit
         && entityHit.getEntity() instanceof LivingEntity victim
         && victim != projectile.getOwner()
         && projectile.markHit(victim)) {
         SpellDamage.hurt(
            victim,
            sw.damageSources().fallingBlock(projectile.getOwner()),
            calculateDirectDamage(level) * GodWandConfig.skillDamageMultiplier(4, level) * (victim instanceof Mob ? 2.0F : 1.0F)
         );
      }

      double blastRadius = calculateCraterRadius(level) * 1.25;
      AABB aoeBox = new AABB(
         hitPos.x - blastRadius, hitPos.y - blastRadius, hitPos.z - blastRadius, hitPos.x + blastRadius, hitPos.y + blastRadius, hitPos.z + blastRadius
      );
      SpellDamage.hurtExtras(
         sw,
         aoeBox,
         sw.damageSources().fallingBlock(projectile.getOwner()),
         e -> e.position().distanceTo(hitPos) <= blastRadius
            ? 2.0
               * Math.max(30.0, (1.0 - e.position().distanceTo(hitPos) / blastRadius) * (calculateDirectDamage(level) * 0.75) + 30.0)
               * GodWandConfig.skillDamageMultiplier(4, level)
            : 0.0
      );

      for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, aoeBox, ent -> ent != projectile.getOwner() && ent.isAlive())) {
         double dist = e.position().distanceTo(hitPos);
         if (dist <= blastRadius && projectile.markHit(e)) {
            float splashDmg = (float)Math.max(30.0, (1.0 - dist / blastRadius) * (calculateDirectDamage(level) * 0.75) + 30.0);
            SpellDamage.hurt(
               e,
               sw.damageSources().fallingBlock(projectile.getOwner()),
               (e instanceof Mob ? 2.0F : 1.0F) * (splashDmg * GodWandConfig.skillDamageMultiplier(4, level))
            );
            Vec3 blastDir = e.position().subtract(hitPos);
            Vec3 push = blastDir.length() > 0.01 ? blastDir.normalize().scale(1.8).add(0.0, 0.65, 0.0) : new Vec3(0.0, 1.2, 0.0);
            e.setDeltaMovement(push);
            e.hurtMarked = true;
         }
      }

      ImpactFxPayload impactPayload = new ImpactFxPayload(hitPos.x, hitPos.y, hitPos.z, 5, 12, (float)Math.max(1.0, level * 0.8));
      CinematicFxPayload traumaShake = new CinematicFxPayload(0, hitPos.x, hitPos.y, hitPos.z, Math.min(1.0F, 0.65F + level * 0.12F), 16);
      CinematicFxPayload fovPunch = new CinematicFxPayload(1, hitPos.x, hitPos.y, hitPos.z, 0.88F, 6);
      GodWandNet.near(sw, hitPos, 96.0, impactPayload);
      GodWandNet.near(sw, hitPos, 96.0, traumaShake);
      GodWandNet.near(sw, hitPos, 96.0, fovPunch);
      sw.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hitPos.x, hitPos.y, hitPos.z, 4, 1.0, 1.0, 1.0, 0.0);
      sw.sendParticles(ParticleTypes.SONIC_BOOM, hitPos.x, hitPos.y, hitPos.z, 2, 0.2, 0.2, 0.2, 0.0);
      sw.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 8, 1.2, 0.8, 1.2, 0.1);
      sw.playSound(null, hitPos.x, hitPos.y, hitPos.z, GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 1.4F, 0.6F);
      sw.playSound(null, hitPos.x, hitPos.y, hitPos.z, GodWandSounds.ev(SoundEvents.WARDEN_SONIC_BOOM), SoundSource.PLAYERS, 0.7F, 1.2F);
      sw.playSound(null, hitPos.x, hitPos.y, hitPos.z, GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 0.85F, 0.8F);
   }
}

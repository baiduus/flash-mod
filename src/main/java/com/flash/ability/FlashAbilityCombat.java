package com.flash.ability;

import com.flash.power.FlashPowers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side helpers shared by the abilities. All damage, pushing and block
 * destruction happens here on the server; clients only receive particles and
 * the short-lived beam packets.
 */
public final class FlashAbilityCombat {

    private FlashAbilityCombat() {
    }

    public static List<LivingEntity> targetsInFront(ServerPlayer player, double range,
                                                    double minDot, int limit) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(range + 1.0D),
                entity -> entity != player && entity.isAlive()
                        && !(entity instanceof net.minecraft.world.entity.player.Player other
                        && other.isSpectator())
        );

        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity entity : candidates) {
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(eye);
            double distance = delta.length();
            if (distance > range || distance < 0.01D) {
                continue;
            }
            double dot = delta.normalize().dot(look);
            if (dot < minDot) {
                continue;
            }
            result.add(entity);
        }
        result.sort(Comparator.comparingDouble(entity ->
                entity.getBoundingBox().getCenter().distanceToSqr(eye)));
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    public static double meleeDamage(ServerPlayer player) {
        return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    }

    public static boolean hit(ServerPlayer player, LivingEntity target, float damage) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        // Rapid abilities must produce individual hits instead of being
        // swallowed by the vanilla invulnerability window.
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(player.damageSources().playerAttack(player), damage);
        if (hurt) {
            target.setLastHurtByPlayer(player);
        }
        return hurt;
    }

    public static Optional<EntityHitResult> pickEntity(ServerPlayer player, Vec3 start, Vec3 end) {
        AABB search = new AABB(start, end).inflate(2.0D);
        EntityHitResult closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class, search,
                entity -> entity != player && entity.isAlive()
                        && !(entity instanceof net.minecraft.world.entity.player.Player other
                        && other.isSpectator()))) {
            Optional<Vec3> clip = entity.getBoundingBox().inflate(0.35D).clip(start, end);
            if (clip.isPresent()) {
                double distance = clip.get().distanceToSqr(start);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = new EntityHitResult(entity, clip.get());
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    public static void spawnElectricBurst(ServerLevel level, Vec3 position, int count) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                position.x, position.y, position.z, count,
                0.25D, 0.35D, 0.25D, 0.04D);
    }

    public static void spawnImpact(ServerLevel level, Vec3 position, float power) {
        int flashes = Math.max(1, (int) (power * 2.0F));
        level.sendParticles(ParticleTypes.FLASH,
                position.x, position.y, position.z, flashes,
                0.05D, 0.05D, 0.05D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                position.x, position.y, position.z, Math.max(8, (int) (power * 12.0F)),
                0.55D, 0.55D, 0.55D, 0.06D);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                position.x, position.y, position.z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
    }

    public static void playThunder(ServerLevel level, Vec3 position) {
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.2F, 0.8F);
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.8F, 0.9F);
    }

    /**
     * Breaks a limited cone of blocks through the same server-side break path
     * used by vanilla, so claim/protection plugins that cancel the break event
     * are respected.
     */
    public static int breakCone(ServerPlayer player, Vec3 direction, double range,
                                double radius, int limit) {
        if (!FlashPowers.isDestroyBlocksEnabled(player)) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 look = direction.normalize();
        int broken = 0;

        for (double distance = 1.0D; distance <= range && broken < limit; distance += 0.8D) {
            Vec3 center = origin.add(look.scale(distance));
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r && broken < limit; dx++) {
                for (int dy = -r; dy <= r && broken < limit; dy++) {
                    for (int dz = -r; dz <= r && broken < limit; dz++) {
                        Vec3 point = center.add(dx * 0.8D, dy * 0.8D, dz * 0.8D);
                        if (point.distanceToSqr(center) > radius * radius) {
                            continue;
                        }
                        BlockPos pos = BlockPos.containing(point);
                        if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                            continue;
                        }
                        if (player.gameMode.destroyBlock(pos)) {
                            broken++;
                        }
                    }
                }
            }
        }
        return broken;
    }
}

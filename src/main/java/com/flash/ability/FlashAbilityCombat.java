package com.flash.ability;

import com.flash.power.FlashPowers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

    /**
     * Ability damage of the Flash, buffed x4 (at most 36
     * per hit; hits that were already stronger, like the infinite mass punch, stay as they are).
     */
    static float buffed(float damage) {
        return Math.min(damage * 4.0F, Math.max(damage, 36.0F));
    }

    public static boolean hit(ServerPlayer player, LivingEntity target, float damage) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        // Rapid abilities must produce individual hits instead of being
        // swallowed by the vanilla invulnerability window.
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(player.damageSources().playerAttack(player), buffed(damage));
        if (hurt) {
            target.setLastHurtByPlayer(player);
        }
        return hurt;
    }

    /** Result of {@link #aim}: where the shot lands and what (if anything) it hit. */
    public record AimResult(Vec3 point, Entity entity, BlockHitResult block) {
    }

    /** Short and tall grass never stop an aimed shot. */
    private static boolean isGrass(BlockState state) {
        return state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS);
    }

    /**
     * Ray cast against the real outline of every block (flowers, torches, glass,
     * water surface...) except grass.
     */
    public static BlockHitResult pickBlock(ServerPlayer player, Vec3 start, Vec3 end) {
        Level level = player.level();
        ClipContext context = new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player);
        return BlockGetter.traverseBlocks(start, end, context, (ctx, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || isGrass(state)) {
                return null;
            }
            VoxelShape shape = state.getShape(level, pos, CollisionContext.empty());
            BlockHitResult result = shape.isEmpty() ? null : shape.clip(start, end, pos);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                BlockHitResult fluidHit = fluid.getShape(level, pos).clip(start, end, pos);
                if (fluidHit != null && (result == null
                        || start.distanceToSqr(fluidHit.getLocation())
                        < start.distanceToSqr(result.getLocation()))) {
                    result = fluidHit;
                }
            }
            return result;
        }, ctx -> {
            Vec3 delta = ctx.getFrom().subtract(ctx.getTo());
            return BlockHitResult.miss(ctx.getTo(), Direction.getNearest(delta.x, delta.y, delta.z),
                    BlockPos.containing(ctx.getTo()));
        });
    }

    /**
     * Forgiving aiming for the lightning: any entity inside a cylinder around the
     * line of sight (it widens with distance) counts as hit, as long as no block
     * is in the way. Without an entity the shot lands on the first block, or at
     * the end of the range.
     */
    public static AimResult aim(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = start.add(look.scale(range));

        BlockHitResult block = pickBlock(player, start, end);
        boolean blockHit = block.getType() != HitResult.Type.MISS;
        double blockDistance = blockHit ? block.getLocation().distanceTo(start) : range;

        Entity best = null;
        double bestAlong = Double.MAX_VALUE;
        Vec3 bestPoint = null;
        for (Entity entity : player.level().getEntities(player,
                new AABB(start, end).inflate(4.0D),
                e -> !e.isSpectator() && e != player.getVehicle() && !e.isPassengerOfSameVehicle(player))) {
            AABB box = entity.getBoundingBox();
            Vec3 center = box.getCenter();
            Vec3 delta = center.subtract(start);
            double along = delta.dot(look);
            if (along < 0.3D || along > blockDistance + 0.6D) {
                continue;
            }
            double sideways = delta.subtract(look.scale(along)).length();
            double reach = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5D
                    + 0.35D + along * 0.03D;
            if (sideways > reach || along >= bestAlong) {
                continue;
            }
            best = entity;
            bestAlong = along;
            Vec3 point = box.clip(start, end).orElse(center);
            bestPoint = box.contains(start) ? center : point;
        }

        if (best != null) {
            return new AimResult(bestPoint, best, blockHit ? block : null);
        }
        return new AimResult(blockHit ? block.getLocation() : end, null, blockHit ? block : null);
    }

    /** Damages any kind of entity (living or not). */
    public static boolean hitAny(ServerPlayer player, Entity target, float damage) {
        if (target instanceof LivingEntity living) {
            return hit(player, living, damage);
        }
        return target.hurt(player.damageSources().playerAttack(player), buffed(damage));
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

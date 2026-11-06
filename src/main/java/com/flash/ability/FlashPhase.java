package com.flash.ability;

import com.flash.power.FlashPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Molecular-vibration phasing. Collisions are switched off on both sides while
 * the key is held, so walls in the way are crossed, but the physics stay normal:
 * gravity, jumping and falling work as usual. The player never sinks: the
 * ground under the feet (or the floor height where the vibration started, if
 * the ground drops away) holds them, see {@link #floor} / {@link #holdFloor}.
 *
 * <p>The ability stores the last position that was not inside a block. If the
 * player releases the key inside a wall, or falls below the world, a safe
 * position is searched around the current location and the player is moved
 * there before collisions are restored.</p>
 */
public final class FlashPhase {

    private static final int MAX_PHASING_TICKS = 240;
    private static final double MAX_SAFE_DROP = 40.0D;

    private FlashPhase() {
    }

    public static void start(ServerPlayer player, FlashAbilityManager.PlayerState state) {
        state.phasing = true;
        state.phasingTicks = 0;
        state.lastSafe = player.position();
        player.noPhysics = true;
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        player.setAirSupply(300);
        player.displayClientMessage(
                Component.translatable("message.flash.phasing_started"), true);
        player.playNotifySound(SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.7F);
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                24, 0.35D, 0.8D, 0.35D, 0.08D);
    }

    public static boolean tick(ServerPlayer player, FlashAbilityManager.PlayerState state) {
        if (!FlashPowers.hasPowers(player)
                || FlashPowers.getSelectedLevel(player) < 1) {
            return false;
        }
        state.phasingTicks++;
        if (state.phasingTicks > MAX_PHASING_TICKS) {
            return false;
        }

        ServerLevel level = (ServerLevel) player.level();
        player.fallDistance = 0.0F;
        player.setAirSupply(300);
        player.noPhysics = true;
        player.setNoGravity(false);
        // the movement itself comes from the client (normal physics, see holdFloor)

        if (player.getY() < level.getMinBuildHeight() + 2.0D
                || player.getY() < state.lastSafe.y - MAX_SAFE_DROP) {
            Vec3 safe = findSafe(player, state.lastSafe);
            teleport(player, safe);
            state.lastSafe = safe;
        } else if (!isInsideBlock(player, player.position())
                && state.phasingTicks % 8 == 0) {
            state.lastSafe = player.position();
        }

        if (player.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    6, 0.28D, 0.65D, 0.28D, 0.05D);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 0.9D, player.getZ(),
                    2, 0.25D, 0.45D, 0.25D, 0.03D);
        }
        if (state.phasingTicks % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
        return true;
    }

    public static void stop(ServerPlayer player, FlashAbilityManager.PlayerState state,
                            boolean moveToSafety) {
        state.phasing = false;
        state.phasingTicks = 0;
        restore(player);
        if (moveToSafety && isInsideBlock(player, player.position())) {
            Vec3 safe = findSafe(player, state.lastSafe);
            teleport(player, safe);
            state.lastSafe = safe;
        }
        player.displayClientMessage(
                Component.translatable("message.flash.phasing_stopped"), true);
        player.playNotifySound(SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 0.9F);
    }

    // ------------------------------------------------------------------
    // Shared floor rule (client and server)
    // ------------------------------------------------------------------

    /**
     * Height the feet may not go below: the top of the solid block right under the
     * feet (only blocks the feet are above count, so walls being crossed are
     * ignored), and never lower than {@code startFloor}, the ground where the
     * vibration began.
     */
    public static double floor(Player player, double startFloor) {
        Level level = player.level();
        double feet = player.getY();
        int x = (int) Math.floor(player.getX());
        int z = (int) Math.floor(player.getZ());
        int top = (int) Math.floor(feet + 0.05D);
        for (int y = top; y >= top - 3; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            net.minecraft.world.phys.shapes.VoxelShape shape =
                    level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double surface = y + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (surface <= feet + 0.05D) {
                return Math.max(startFloor, surface);
            }
        }
        return startFloor;
    }

    /** Ground where a vibration starting now stands (searches down if the player is in the air). */
    public static double startFloor(Player player) {
        if (player.onGround()) {
            return player.getY();
        }
        Level level = player.level();
        int x = (int) Math.floor(player.getX());
        int z = (int) Math.floor(player.getZ());
        for (int y = (int) Math.floor(player.getY()); y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            net.minecraft.world.phys.shapes.VoxelShape shape =
                    level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double surface = y + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (surface <= player.getY() + 0.05D) {
                    return surface;
                }
            }
        }
        return player.getY();
    }

    /** End of the tick: stand on the floor instead of sinking; in the air, fall normally. */
    public static void holdFloor(Player player, double floor) {
        player.fallDistance = 0.0F;
        if (player.getY() <= floor + 0.001D) {
            player.setPos(player.getX(), floor, player.getZ());
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < 0.0D) {
                player.setDeltaMovement(motion.x, 0.0D, motion.z);
            }
            player.setOnGround(true);
        } else {
            player.setOnGround(false);
        }
    }

    public static void restore(ServerPlayer player) {
        player.noPhysics = false;
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    private static void teleport(ServerPlayer player, Vec3 safe) {
        player.connection.teleport(safe.x, safe.y, safe.z,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    /**
     * Looks for a free 1.8-block tall space, preferring a position with solid
     * ground underneath so the player does not end up falling into a cave or
     * the void after leaving a wall.
     */
    private static Vec3 findSafe(ServerPlayer player, Vec3 fallback) {
        if (isFreeAndSupported(player, player.position())) {
            return player.position();
        }
        for (int dy = 1; dy <= 16; dy++) {
            Vec3 candidate = player.position().add(0.0D, dy, 0.0D);
            if (isFreeAndSupported(player, candidate)) {
                return candidate;
            }
        }

        Vec3 base = player.position();
        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 4; dy >= -3; dy--) {
                        Vec3 candidate = base.add(dx, dy, dz);
                        if (isFreeAndSupported(player, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        for (int dy = 0; dy <= 20; dy++) {
            Vec3 candidate = fallback.add(0.0D, dy, 0.0D);
            if (isFreeAndSupported(player, candidate)) {
                return candidate;
            }
        }

        // Last resort: place the player on top of the height map so they can
        // never remain stuck inside a wall or fall forever.
        int bx = (int) Math.floor(player.getX());
        int bz = (int) Math.floor(player.getZ());
        int surface = player.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                bx, bz);
        return new Vec3(player.getX(), Math.max(surface, player.level().getMinBuildHeight() + 2), player.getZ());
    }

    private static boolean isFreeAndSupported(ServerPlayer player, Vec3 position) {
        if (!isFree(player, position)) {
            return false;
        }
        Level level = player.level();
        BlockPos below = BlockPos.containing(position.x, position.y - 0.05D, position.z);
        BlockState ground = level.getBlockState(below);
        if (ground.getCollisionShape(level, below, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()) {
            // No floor directly underneath: still allow it when there is a
            // floor within three blocks, otherwise prefer another candidate.
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos deeper = below.below(dy);
                if (!level.getBlockState(deeper).getCollisionShape(
                        level, deeper, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()) {
                    return true;
                }
            }
            return position.y <= level.getMinBuildHeight() + 3.0D;
        }
        return true;
    }

    private static boolean isFree(ServerPlayer player, Vec3 position) {
        AABB box = new AABB(position.x - 0.3D, position.y,
                position.z - 0.3D, position.x + 0.3D,
                position.y + 1.8D, position.z + 0.3D);
        return player.level().noCollision(player, box);
    }

    private static boolean isInsideBlock(ServerPlayer player, Vec3 position) {
        AABB box = new AABB(position.x - 0.28D, position.y + 0.05D,
                position.z - 0.28D, position.x + 0.28D,
                position.y + 1.75D, position.z + 0.28D);
        return !player.level().noCollision(player, box);
    }
}

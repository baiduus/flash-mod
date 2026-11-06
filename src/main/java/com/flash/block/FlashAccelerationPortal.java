package com.flash.block;

import com.flash.ability.FlashDimensionTravel;
import com.flash.network.FlashSyncPacket;
import com.flash.registry.ModBlocks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side state of the circular Acceleration Force portal.
 *
 * <p>The old 2x3 wall of portal blocks is gone. A single invisible marker block
 * now stores the owner, orientation and lifetime, while the circular energy
 * ring, vortex, orbital ribbons, lightning and ground glow are drawn by the
 * client renderer. The server still owns the entry/exit validation and the
 * 1:15 teleport, so a modified client cannot create or use a portal.</p>
 */
public final class FlashAccelerationPortal {

    public static final double DEFAULT_RADIUS = 2.8D;
    private static final long LIFETIME_TICKS = 200L;
    private static final double MIN_GROUND_OFFSET = 1.2D;
    private static final double MAX_GROUND_OFFSET = 8.0D;

    private static final Map<UUID, Portal> PORTALS = new ConcurrentHashMap<>();

    private FlashAccelerationPortal() {
    }

    /**
     * One active portal. The marker block is always at the exact centre cell;
     * {@link #center()} is therefore the block centre used by both the renderer
     * and the crossing test.
     */
    public record Portal(UUID owner, ResourceKey<Level> dimension, BlockPos marker,
                         float yaw, boolean exit, long createdAt, double radius,
                         double groundOffset, int seed) {

        public Vec3 center() {
            return new Vec3(this.marker.getX() + 0.5D,
                    this.marker.getY() + 0.5D,
                    this.marker.getZ() + 0.5D);
        }

        public Vec3 normal() {
            double radians = Math.toRadians(this.yaw);
            return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }

        public Vec3 right() {
            Vec3 normal = this.normal();
            return new Vec3(-normal.z, 0.0D, normal.x);
        }
    }

    public static boolean open(ServerPlayer player, boolean exit) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (exit != FlashDimensionTravel.isInAccelerationForce(player)) {
            return false;
        }

        remove(player);

        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontal = horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);

        double[] distances = {4.0D, 3.5D, 4.5D, 5.0D, 3.0D};
        for (double distance : distances) {
            for (int dy = 0; dy >= -2; dy--) {
                Vec3 candidate = findPortalCentre(level, player, horizontal, distance, dy);
                if (candidate == null) {
                    continue;
                }
                BlockPos marker = BlockPos.containing(candidate);
                if (!level.isLoaded(marker)) {
                    continue;
                }
                BlockState existing = level.getBlockState(marker);
                if (!existing.isAir() && !existing.canBeReplaced()) {
                    continue;
                }
                Vec3 center = new Vec3(marker.getX() + 0.5D,
                        marker.getY() + 0.5D, marker.getZ() + 0.5D);
                if (!hasClearRing(level, center, horizontal, right, DEFAULT_RADIUS)) {
                    continue;
                }
                int ground = groundHeight(level, center.x, center.z);
                double groundOffset = center.y - ground;
                if (groundOffset < MIN_GROUND_OFFSET
                        || groundOffset > MAX_GROUND_OFFSET) {
                    continue;
                }

                BlockState portalState = ModBlocks.ACCELERATION_PORTAL.get()
                        .defaultBlockState()
                        .setValue(AccelerationPortalBlock.AXIS,
                                Math.abs(horizontal.x) > Math.abs(horizontal.z)
                                        ? Direction.Axis.Z : Direction.Axis.X);
                level.setBlock(marker, portalState, Block.UPDATE_ALL);
                int seed = player.getRandom().nextInt();
                if (level.getBlockEntity(marker) instanceof AccelerationPortalBlockEntity portal) {
                    portal.configure(player.getUUID(), exit, level.getGameTime(),
                            player.getYRot(), DEFAULT_RADIUS, groundOffset, seed);
                    portal.setNegative(com.flash.power.FlashPowers.isNegative(player)
                            || level.dimension().equals(
                                    FlashDimensionTravel.NEGATIVE_ACCELERATION_FORCE));
                }
                long now = level.getGameTime();
                Portal portal = new Portal(player.getUUID(), level.dimension(),
                        marker, player.getYRot(), exit, now,
                        DEFAULT_RADIUS, groundOffset, seed);
                PORTALS.put(player.getUUID(), portal);

                level.playSound(null, center.x, center.y, center.z,
                        SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 0.7F);
                level.sendParticles(ParticleTypes.FLASH,
                        center.x, center.y, center.z, 2,
                        0.4D, 0.8D, 0.4D, 0.0D);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        center.x, center.y, center.z, 60,
                        1.2D, 1.8D, 1.2D, 0.12D);
                player.displayClientMessage(Component.translatable(
                        exit ? "message.flash.portal_opened_exit"
                                : "message.flash.portal_opened"), true);
                FlashSyncPacket.send(player);
                return true;
            }
        }
        return false;
    }

    /**
     * Chooses a free marker cell in front of the player, floats the centre
     * above the local terrain and returns the exact block centre.
     */
    private static Vec3 findPortalCentre(ServerLevel level, ServerPlayer player,
                                         Vec3 look, double distance, int dyOffset) {
        double x = player.getX() + look.x * distance;
        double z = player.getZ() + look.z * distance;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        level.getChunk(bx >> 4, bz >> 4);
        int ground = groundHeight(level, x, z);
        if (ground <= level.getMinBuildHeight() + 1) {
            return null;
        }

        // Keep the bottom of the 5.6-block ring just above the terrain. The
        // trigger is slightly generous so walking through the lower half still
        // counts even when the marker snaps to a block centre.
        double desiredY = Math.max(ground + DEFAULT_RADIUS + 0.35D,
                player.getY() + 1.2D) + dyOffset;
        BlockPos base = BlockPos.containing(x, desiredY, z);
        for (int up = 0; up <= 5; up++) {
            BlockPos candidate = base.above(up);
            if (!level.isLoaded(candidate)) {
                return null;
            }
            BlockState state = level.getBlockState(candidate);
            if (state.isAir() || state.canBeReplaced()) {
                return new Vec3(candidate.getX() + 0.5D,
                        candidate.getY() + 0.5D, candidate.getZ() + 0.5D);
            }
        }
        return null;
    }

    public static boolean remove(ServerPlayer player) {
        Portal portal = PORTALS.remove(player.getUUID());
        if (portal == null) {
            return false;
        }
        MinecraftServerHolder holder = new MinecraftServerHolder(player);
        ServerLevel level = holder.level(portal.dimension());
        if (level != null) {
            removeMarker(level, portal.marker());
            level.sendParticles(ParticleTypes.FLASH,
                    portal.center().x, portal.center().y, portal.center().z,
                    6, 0.8D, 1.4D, 0.8D, 0.02D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    portal.center().x, portal.center().y, portal.center().z,
                    30, 1.0D, 1.8D, 1.0D, 0.08D);
        }
        return true;
    }

    public static void removePortal(Portal portal) {
        if (portal == null) {
            return;
        }
        PORTALS.remove(portal.owner(), portal);
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(portal.dimension());
        if (level != null) {
            removeMarker(level, portal.marker());
        }
    }

    public static void removeAt(Level level, BlockPos pos) {
        PORTALS.entrySet().removeIf(entry ->
                entry.getValue().marker().equals(pos)
                        && entry.getValue().dimension().equals(level.dimension()));
        removeMarker(level, pos);
    }

    private static void removeMarker(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof AccelerationPortalBlock) {
            level.removeBlock(pos, false);
        }
    }

    public static void register(AccelerationPortalBlockEntity portal) {
        if (portal == null || portal.owner() == null || portal.getLevel() == null
                || portal.getLevel().isClientSide) {
            return;
        }
        PORTALS.put(portal.owner(), new Portal(
                portal.owner(),
                portal.getLevel().dimension(),
                portal.getBlockPos(),
                portal.yaw(),
                portal.isExit(),
                portal.createdAt(),
                portal.radius(),
                portal.groundOffset(),
                portal.seed()
        ));
    }

    public static void unregister(UUID owner, BlockPos pos) {
        if (owner == null) {
            return;
        }
        Portal portal = PORTALS.get(owner);
        if (portal != null && portal.marker().equals(pos)) {
            PORTALS.remove(owner, portal);
        }
    }

    public static Portal get(UUID owner) {
        return PORTALS.get(owner);
    }

    public static Portal get(ServerPlayer player) {
        return PORTALS.get(player.getUUID());
    }

    public static void clear() {
        PORTALS.clear();
    }

    /**
     * Server-side crossing test. It checks the segment between the previous and
     * current body centres, so a player running at full speed cannot skip the
     * portal plane between two ticks. The trigger is the central opening, not
     * the outer lightning ring.
     */
    public static void checkCrossing(ServerPlayer player, Vec3 previousPosition) {
        Portal portal = get(player);
        if (portal == null
                || portal.exit() != FlashDimensionTravel.isInAccelerationForce(player)
                || !portal.dimension().equals(player.level().dimension())) {
            return;
        }

        Vec3 center = portal.center();
        Vec3 normal = portal.normal();
        Vec3 right = portal.right();
        Vec3 previous = previousPosition.add(0.0D, 0.9D, 0.0D);
        Vec3 current = player.position().add(0.0D, 0.9D, 0.0D);

        double previousDistance = previous.subtract(center).dot(normal);
        double currentDistance = current.subtract(center).dot(normal);
        boolean crossed = (previousDistance <= 0.0D && currentDistance >= 0.0D)
                || (previousDistance >= 0.0D && currentDistance <= 0.0D);

        Vec3 point;
        if (crossed && Math.abs(previousDistance - currentDistance) > 1.0E-5D) {
            double t = previousDistance / (previousDistance - currentDistance);
            point = previous.lerp(current, Mth.clamp(t, 0.0D, 1.0D));
        } else if (Math.abs(currentDistance) <= 0.75D) {
            point = current;
        } else {
            return;
        }

        Vec3 delta = point.subtract(center);
        double side = delta.dot(right);
        double vertical = delta.y;
        double limit = portal.radius() - 0.05D;
        if (side * side + vertical * vertical <= limit * limit) {
            FlashDimensionTravel.tryUsePortal(player, portal);
        }
    }

    private static boolean hasClearRing(ServerLevel level, Vec3 center, Vec3 look,
                                        Vec3 right, double radius) {
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0D / 16.0D);
            Vec3 point = center
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(0.0D, Math.sin(angle) * radius, 0.0D);
            BlockPos pos = BlockPos.containing(point);
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.REPLACEABLE_BY_TREES)) {
                continue;
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int groundHeight(ServerLevel level, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        level.getChunk(bx >> 4, bz >> 4);
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (height <= level.getMinBuildHeight() + 1) {
            return level.getMinBuildHeight();
        }
        return height;
    }

    /**
     * Tiny helper that keeps the server lookup out of the public record and
     * makes the null handling explicit.
     */
    private record MinecraftServerHolder(ServerPlayer player) {
        ServerLevel level(ResourceKey<Level> key) {
            net.minecraft.server.MinecraftServer server = this.player.getServer();
            return server == null ? null : server.getLevel(key);
        }
    }
}

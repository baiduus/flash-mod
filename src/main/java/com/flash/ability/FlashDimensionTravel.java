package com.flash.ability;

import com.flash.FlashMod;
import com.flash.block.FlashAccelerationPortal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Handles entry/exit and the 1:15 relative coordinate conversion of the
 * Acceleration Force dimension.
 *
 * <p>The entry position is stored when the player enters. On exit the delta
 * travelled inside the dimension is multiplied by 15 on both X and Z and added
 * to the original world position. This works at any coordinate and direction
 * and never depends on the absolute dimension position.</p>
 *
 * <p>The circular portal is not made of blocks: the server checks whether the
 * player's body crossed the portal plane and falls inside the central opening.
 * The test uses the previous tick position, so a player running at level 10
 * speed cannot skip the trigger.</p>
 */
public final class FlashDimensionTravel {

    public static final ResourceKey<Level> ACCELERATION_FORCE = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(FlashMod.MODID, "acceleration_force"));

    /** Red twin of the Acceleration Force, entered by players with the Negative force. */
    public static final ResourceKey<Level> NEGATIVE_ACCELERATION_FORCE = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(FlashMod.MODID, "negative_acceleration_force"));

    private static final String ROOT = "flash_dimension";
    private static final String ACTIVE = "active";
    private static final String ORIGIN_DIMENSION = "originDimension";
    private static final String ORIGIN_X = "originX";
    private static final String ORIGIN_Y = "originY";
    private static final String ORIGIN_Z = "originZ";
    private static final String ENTRY_X = "entryX";
    private static final String ENTRY_Z = "entryZ";
    private static final String COOLDOWN_UNTIL = "cooldownUntil";
    private static final int PORTAL_COOLDOWN_TICKS = 60;

    private static final Map<UUID, Vec3> LAST_POSITIONS = new ConcurrentHashMap<>();

    private FlashDimensionTravel() {
    }

    /** True in either the Positive or the Negative Acceleration Force dimension. */
    public static boolean isInAccelerationForce(ServerPlayer player) {
        ResourceKey<Level> key = player.level().dimension();
        return key.equals(ACCELERATION_FORCE) || key.equals(NEGATIVE_ACCELERATION_FORCE);
    }

    public static void tryUsePortal(ServerPlayer player,
                                    FlashAccelerationPortal.Portal portal) {
        if (player.level().isClientSide() || portal == null
                || !portal.owner().equals(player.getUUID())
                || !portal.dimension().equals(player.level().dimension())) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        if (portal.createdAt() > 0L && now - portal.createdAt() < 5L) {
            return;
        }
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        if (now < data.getLong(COOLDOWN_UNTIL)) {
            return;
        }

        boolean inDimension = isInAccelerationForce(player);
        if (portal.exit() != inDimension) {
            return;
        }

        FlashAccelerationPortal.removePortal(portal);
        LAST_POSITIONS.remove(player.getUUID());
        if (inDimension) {
            exit(player);
        } else {
            enter(player);
        }
    }

    public static boolean enter(ServerPlayer player) {
        if (isInAccelerationForce(player)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel target = server.getLevel(
                com.flash.power.FlashPowers.isNegative(player)
                        ? NEGATIVE_ACCELERATION_FORCE : ACCELERATION_FORCE);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("message.flash.dimension_missing"), true);
            return false;
        }

        ServerLevel originLevel = (ServerLevel) player.level();
        long now = originLevel.getGameTime();
        CompoundTag data = new CompoundTag();
        data.putBoolean(ACTIVE, true);
        data.putString(ORIGIN_DIMENSION, originLevel.dimension().location().toString());
        data.putDouble(ORIGIN_X, player.getX());
        data.putDouble(ORIGIN_Y, player.getY());
        data.putDouble(ORIGIN_Z, player.getZ());
        data.putDouble(ENTRY_X, player.getX());
        data.putDouble(ENTRY_Z, player.getZ());
        data.putLong(COOLDOWN_UNTIL, now + PORTAL_COOLDOWN_TICKS);
        player.getPersistentData().put(ROOT, data);

        double y = safeY(target, player.getX(), player.getZ());
        teleport(player, target, player.getX(), y, player.getZ());
        player.displayClientMessage(
                Component.translatable("message.flash.dimension_entered"), true);
        target.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0F, 0.7F);
        target.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                40, 0.8D, 1.2D, 0.8D, 0.12D);
        return true;
    }

    public static boolean exit(ServerPlayer player) {
        if (!isInAccelerationForce(player)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        double originX = data.contains(ORIGIN_X) ? data.getDouble(ORIGIN_X) : 0.0D;
        double originZ = data.contains(ORIGIN_Z) ? data.getDouble(ORIGIN_Z) : 0.0D;
        double entryX = data.contains(ENTRY_X) ? data.getDouble(ENTRY_X) : player.getX();
        double entryZ = data.contains(ENTRY_Z) ? data.getDouble(ENTRY_Z) : player.getZ();

        ResourceKey<Level> originKey = Level.OVERWORLD;
        if (data.contains(ORIGIN_DIMENSION)) {
            try {
                originKey = ResourceKey.create(Registries.DIMENSION,
                        new ResourceLocation(data.getString(ORIGIN_DIMENSION)));
            } catch (RuntimeException ignored) {
                originKey = Level.OVERWORLD;
            }
        }
        ServerLevel target = server.getLevel(originKey);
        if (target == null) {
            target = server.overworld();
        }

        double exitX = originX + (player.getX() - entryX) * 15.0D;
        double exitZ = originZ + (player.getZ() - entryZ) * 15.0D;
        exitX = Mth.clamp(exitX, target.getWorldBorder().getMinX() + 1.0D,
                target.getWorldBorder().getMaxX() - 1.0D);
        exitZ = Mth.clamp(exitZ, target.getWorldBorder().getMinZ() + 1.0D,
                target.getWorldBorder().getMaxZ() - 1.0D);
        double y = safeY(target, exitX, exitZ);

        player.getPersistentData().remove(ROOT);
        teleport(player, target, exitX, y, exitZ);
        player.displayClientMessage(
                Component.translatable("message.flash.dimension_exited"), true);
        target.playSound(null, exitX, y, exitZ,
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0F, 0.8F);
        target.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                exitX, y + 1.0D, exitZ, 40, 0.8D, 1.2D, 0.8D, 0.12D);
        return true;
    }

    public static void tick(ServerPlayer player) {
        Vec3 previous = LAST_POSITIONS.getOrDefault(player.getUUID(), player.position());
        FlashAccelerationPortal.checkCrossing(player, previous);
        LAST_POSITIONS.put(player.getUUID(), player.position());

        if (!isInAccelerationForce(player)) {
            CompoundTag data = player.getPersistentData().getCompound(ROOT);
            if (data.getBoolean(ACTIVE)) {
                player.getPersistentData().remove(ROOT);
            }
            return;
        }

        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        if (!data.getBoolean(ACTIVE)) {
            initializeFallback(player);
            data = player.getPersistentData().getCompound(ROOT);
        }

        ServerLevel level = (ServerLevel) player.level();
        if (player.getY() < level.getMinBuildHeight() + 5.0D
                || player.getY() < -40.0D) {
            double x = player.getX();
            double z = player.getZ();
            double y = safeY(level, x, z);
            teleport(player, level, x, y, z);
            player.displayClientMessage(
                    Component.translatable("message.flash.dimension_safety"), true);
        }
    }

    public static void onLogin(ServerPlayer player) {
        LAST_POSITIONS.remove(player.getUUID());
        if (isInAccelerationForce(player)
                && !player.getPersistentData().getCompound(ROOT).getBoolean(ACTIVE)) {
            initializeFallback(player);
        }
    }

    private static void initializeFallback(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        CompoundTag data = new CompoundTag();
        data.putBoolean(ACTIVE, true);
        data.putString(ORIGIN_DIMENSION, Level.OVERWORLD.location().toString());
        data.putDouble(ORIGIN_X, spawn.getX() + 0.5D);
        data.putDouble(ORIGIN_Y, spawn.getY());
        data.putDouble(ORIGIN_Z, spawn.getZ() + 0.5D);
        data.putDouble(ENTRY_X, player.getX());
        data.putDouble(ENTRY_Z, player.getZ());
        data.putLong(COOLDOWN_UNTIL, 0L);
        player.getPersistentData().put(ROOT, data);
    }

    private static void teleport(ServerPlayer player, ServerLevel target,
                                 double x, double y, double z) {
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_POSITIONS.remove(player.getUUID());
    }

    public static double safeY(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        level.getChunk(bx >> 4, bz >> 4);
        int min = level.getMinBuildHeight();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (y <= min + 1) {
            y = Math.max(min + 2, 80);
        }
        for (int i = 0; i < 24; i++) {
            AABB box = new AABB(x - 0.3D, y, z - 0.3D,
                    x + 0.3D, y + 1.8D, z + 0.3D);
            if (level.noCollision(box)) {
                return y;
            }
            y++;
        }
        return y;
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(ROOT);
        LAST_POSITIONS.remove(player.getUUID());
    }
}

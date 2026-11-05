package com.flash.block;

import com.flash.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Owner/mode data and render state of the circular Acceleration Force portal.
 *
 * <p>Only one invisible marker block is placed in the world. The actual
 * circular portal is drawn by {@code AccelerationPortalRenderer}; this block
 * entity carries the orientation, radius, ground height and owner so every
 * nearby client can draw the exact same portal.</p>
 */
public class AccelerationPortalBlockEntity extends BlockEntity {

    private static final String OWNER = "Owner";
    private static final String EXIT = "Exit";
    private static final String CREATED = "CreatedAt";
    private static final String YAW = "Yaw";
    private static final String RADIUS = "Radius";
    private static final String GROUND_OFFSET = "GroundOffset";
    private static final String SEED = "Seed";
    private static final String NEGATIVE = "Negative";

    /** Ticks the portal stays open; the renderer closes it during the last 15. */
    public static final long LIFETIME_TICKS = 1200L;

    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.80F, 0.10F), 0.9F);
    private static final DustParticleOptions BLUE_DUST =
            new DustParticleOptions(new Vector3f(0.30F, 0.60F, 1.0F), 0.9F);
    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(0.95F, 0.08F, 0.10F), 0.9F);
    private static final DustParticleOptions DARK_RED_DUST =
            new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.05F), 0.9F);

    private UUID owner;
    private boolean exit;
    private long createdAt;
    private float yaw;
    private double radius = 3.0D;
    private double groundOffset = 2.4D;
    private int seed;
    private boolean negative;

    public AccelerationPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATION_PORTAL.get(), pos, state);
    }

    public void configure(UUID owner, boolean exit, long createdAt, float yaw,
                          double radius, double groundOffset, int seed) {
        this.owner = owner;
        this.exit = exit;
        this.createdAt = createdAt;
        this.yaw = yaw;
        this.radius = Math.max(2.5D, Math.min(3.5D, radius));
        this.groundOffset = Math.max(1.0D, groundOffset);
        this.seed = seed;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(),
                    this.getBlockState(), 3);
        }
    }

    /** True when the portal belongs to the Negative Acceleration Force (drawn in red). */
    public boolean isNegative() {
        return this.negative;
    }

    public void setNegative(boolean negative) {
        this.negative = negative;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(),
                    this.getBlockState(), 3);
        }
    }

    public UUID owner() {
        return this.owner;
    }

    public boolean isExit() {
        return this.exit;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public float yaw() {
        return this.yaw;
    }

    public double radius() {
        return this.radius;
    }

    public double groundOffset() {
        return this.groundOffset;
    }

    public int seed() {
        return this.seed;
    }

    public boolean isReady() {
        return this.owner != null && this.level != null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  AccelerationPortalBlockEntity portal) {
        if (portal.owner == null) {
            if (level.getGameTime() % 20L == 0L) {
                level.removeBlock(pos, false);
            }
            return;
        }
        if (portal.createdAt > 0L
                && level.getGameTime() - portal.createdAt > LIFETIME_TICKS) {
            FlashAccelerationPortal.removeAt(level, pos);
            return;
        }
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % 3L == 0L) {
            portal.spawnParticles(serverLevel);
        }
    }

    /** A few sparks around the ring, in the portal plane. Kept small so the circle stays readable. */
    private void spawnParticles(ServerLevel level) {
        double radians = Math.toRadians(this.yaw);
        double rx = -Math.cos(radians);
        double rz = -Math.sin(radians);
        double cx = this.worldPosition.getX() + 0.5D;
        double cy = this.worldPosition.getY() + 0.5D;
        double cz = this.worldPosition.getZ() + 0.5D;
        for (int i = 0; i < 4; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double r = this.radius * (0.85D + level.random.nextDouble() * 0.45D);
            double side = Math.cos(angle) * r;
            boolean blue = i == 0;
            level.sendParticles(this.negative ? (blue ? RED_DUST : DARK_RED_DUST)
                    : (blue ? BLUE_DUST : GOLD_DUST),
                    cx + rx * side, cy + Math.sin(angle) * r, cz + rz * side,
                    1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double side = Math.cos(angle) * this.radius;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                cx + rx * side, cy + Math.sin(angle) * this.radius, cz + rz * side,
                1, 0.1D, 0.1D, 0.1D, 0.04D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.owner != null) {
            tag.putUUID(OWNER, this.owner);
        }
        tag.putBoolean(EXIT, this.exit);
        tag.putLong(CREATED, this.createdAt);
        tag.putFloat(YAW, this.yaw);
        tag.putDouble(RADIUS, this.radius);
        tag.putDouble(GROUND_OFFSET, this.groundOffset);
        tag.putInt(SEED, this.seed);
        tag.putBoolean(NEGATIVE, this.negative);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.owner = tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
        this.exit = tag.getBoolean(EXIT);
        this.createdAt = tag.getLong(CREATED);
        this.yaw = tag.getFloat(YAW);
        this.radius = tag.contains(RADIUS) ? tag.getDouble(RADIUS) : 3.0D;
        this.groundOffset = tag.contains(GROUND_OFFSET)
                ? tag.getDouble(GROUND_OFFSET) : 2.4D;
        this.seed = tag.getInt(SEED);
        this.negative = tag.getBoolean(NEGATIVE);
        if (this.owner != null && this.level != null && !this.level.isClientSide) {
            FlashAccelerationPortal.register(this);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (this.owner != null && !level.isClientSide) {
            FlashAccelerationPortal.register(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        double r = this.radius * 2.4D + 1.0D;
        double down = Math.max(r, this.groundOffset + 1.0D);
        return new net.minecraft.world.phys.AABB(
                this.worldPosition.getX() + 0.5D - r,
                this.worldPosition.getY() + 0.5D - down,
                this.worldPosition.getZ() + 0.5D - r,
                this.worldPosition.getX() + 0.5D + r,
                this.worldPosition.getY() + 0.5D + r,
                this.worldPosition.getZ() + 0.5D + r);
    }
}

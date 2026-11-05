package com.flash.block;

import com.flash.registry.ModBlockEntities;
import java.util.UUID;
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

    private static final long LIFETIME_TICKS = 1200L;

    private UUID owner;
    private boolean exit;
    private long createdAt;
    private float yaw;
    private double radius = 3.0D;
    private double groundOffset = 2.4D;
    private int seed;

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
        }
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
        double r = this.radius + 1.5D;
        return new net.minecraft.world.phys.AABB(
                this.worldPosition.getX() + 0.5D - r,
                this.worldPosition.getY() + 0.5D - r,
                this.worldPosition.getZ() + 0.5D - r,
                this.worldPosition.getX() + 0.5D + r,
                this.worldPosition.getY() + 0.5D + r,
                this.worldPosition.getZ() + 0.5D + r);
    }
}

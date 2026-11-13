package com.flash.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The red energy of chaos that comes to a player without powers (ScarletChaos) and asks whether he gives in to it.
 * It floats before him, turning; it goes when he answers (or after a minute).
 */
public class ChaosEnergy extends Entity {

    public static final int LIFE = 60 * 20;
    private static final EntityDataAccessor<Optional<UUID>> FOR = SynchedEntityData.defineId(ChaosEnergy.class,
            EntityDataSerializers.OPTIONAL_UUID);

    public ChaosEnergy(EntityType<? extends ChaosEnergy> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FOR, Optional.empty());
    }

    public void setFor(UUID player) {
        this.entityData.set(FOR, Optional.ofNullable(player));
    }

    public UUID target() {
        return this.entityData.get(FOR).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > LIFE) {
            this.discard();
        }
        // a slow bob
        this.setPos(this.getX(), this.getY() + Math.sin(this.tickCount * 0.08D) * 0.004D, this.getZ());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

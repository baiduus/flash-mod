package com.flash.godwand;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Stone Cannon's boulder: flies straight, bores into the land where it hits (StoneCannonManager). */
public class StoneCannonProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Integer> CHARGE_LEVEL =
            SynchedEntityData.defineId(StoneCannonProjectileEntity.class, EntityDataSerializers.INT);
    private final Set<UUID> hitEntities = new HashSet<>();

    public StoneCannonProjectileEntity(EntityType<? extends Projectile> entityType, Level world) {
        super(entityType, world);
        this.noPhysics = false;
    }

    public StoneCannonProjectileEntity(Level world, LivingEntity owner, int chargeLevel) {
        this(com.flash.registry.ModEntities.STONE_CANNON.get(), world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
    }

    public boolean markHit(LivingEntity entity) {
        return this.hitEntities.add(entity.getUUID());
    }

    public void setChargeLevel(int level) {
        this.entityData.set(CHARGE_LEVEL, Math.max(1, Math.min(4, level)));
    }

    public int getChargeLevel() {
        return this.entityData.get(CHARGE_LEVEL);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CHARGE_LEVEL, 1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("ChargeLevel", this.getChargeLevel());
        nbt.putInt("Age", this.tickCount);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setChargeLevel(nbt.contains("ChargeLevel") ? nbt.getInt("ChargeLevel") : 1);
        this.tickCount = nbt.getInt("Age");
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 end = this.position().add(velocity);
        if (!this.level().isClientSide()) {
            if (this.tickCount > 240 || velocity.lengthSqr() < 0.001) {
                this.discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.setPos(hit.getLocation());
                StoneCannonManager.onProjectileImpact(this, hit);
                this.discard();
                return;
            }
        }
        this.setPos(end);
        if (velocity.length() > 0.05 && !this.level().isClientSide()) {
            ServerLevel sw = (ServerLevel) this.level();
            sw.sendParticles(new DustParticleOptions(new Vector3f(0.82F, 0.78F, 0.65F), 1.0F), this.getX(), this.getY(),
                    this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
            if (this.getChargeLevel() >= 3 && this.tickCount % 2 == 0) {
                sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 2, 0.08, 0.08,
                        0.08, 0.02);
            }
        }
    }
}

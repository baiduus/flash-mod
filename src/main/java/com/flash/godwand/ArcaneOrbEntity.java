package com.flash.godwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Singularity's orb: flies straight, and where it hits the land opens into a sphere (ShockwaveSlicer). */
public class ArcaneOrbEntity extends Projectile {

    private static final EntityDataAccessor<Integer> CHARGE_LEVEL =
            SynchedEntityData.defineId(ArcaneOrbEntity.class, EntityDataSerializers.INT);

    public ArcaneOrbEntity(EntityType<? extends Projectile> entityType, Level world) {
        super(entityType, world);
        this.noPhysics = false;
    }

    public ArcaneOrbEntity(Level world, LivingEntity owner, int chargeLevel) {
        this(com.flash.registry.ModEntities.ARCANE_ORB.get(), world);
        this.setOwner(owner);
        this.setChargeLevel(chargeLevel);
    }

    public void setChargeLevel(int level) {
        this.entityData.set(CHARGE_LEVEL, Mth.clamp(level, 1, 3));
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
        this.tickCount++;
        Vec3 velocity = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
            return;
        }
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
        if (this.level().isClientSide()) {
            this.level().addParticle(new DustParticleOptions(new Vector3f(0.0F, 0.94F, 1.0F), 1.3F), this.getX(),
                    this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        } else {
            ServerLevel sw = (ServerLevel) this.level();
            sw.sendParticles(new DustParticleOptions(new Vector3f(0.0F, 0.94F, 1.0F), 1.2F), this.getX(), this.getY(),
                    this.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
            sw.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
            if (this.tickCount > 200) {
                this.discard();
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return (entity != this.getOwner() || this.tickCount >= 5) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            double radius = switch (this.getChargeLevel()) {
                case 1 -> 5.0;
                case 2 -> 10.0;
                default -> 16.0;
            };
            BlockPos center = hitResult instanceof BlockHitResult bhr ? bhr.getBlockPos() : this.blockPosition();
            ServerPlayer caster = this.getOwner() instanceof ServerPlayer spe ? spe : null;
            ShockwaveSlicer.queueCalamityDetonation((ServerLevel) this.level(), center, radius, caster);
            this.discard();
        }
    }
}

package com.flash.infinity.entity;

import com.flash.infinity.InfinityEntities;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * The Power Stone's shots (the original's EnergyBlast and PowerRocket): an energy blast (straight, damages the first
 * thing it hits) or a rocket (homing on the nearest enemy ahead, exploding where it hits, never hurting its shooter).
 */
public class PowerShot extends AbstractHurtingProjectile {

    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(PowerShot.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ROCKET = SynchedEntityData.defineId(PowerShot.class,
            EntityDataSerializers.BOOLEAN);
    private float damage = 3.0F;
    /** The Energy Blast seeks its prey too (as the rockets), but bursts without breaking anything. */
    public boolean homing;
    private LivingEntity prey;

    public PowerShot(EntityType<? extends PowerShot> type, Level level) {
        super(type, level);
    }

    public static PowerShot shoot(LivingEntity owner, Vec3 from, Vec3 dir, float speed, float damage, int color,
                                  boolean rocket) {
        PowerShot s = new PowerShot(InfinityEntities.POWER_SHOT.get(), owner.level());
        s.setOwner(owner);
        s.moveTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        Vec3 v = dir.normalize().scale(speed);
        s.setDeltaMovement(v);
        s.xPower = 0.0D;
        s.yPower = 0.0D;
        s.zPower = 0.0D;
        s.damage = damage;
        s.entityData.set(COLOR, color);
        s.entityData.set(ROCKET, rocket);
        owner.level().addFreshEntity(s);
        return s;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COLOR, 0xFF00FF);
        this.entityData.define(ROCKET, false);
    }

    public int color() {
        return this.entityData.get(COLOR);
    }

    public boolean rocket() {
        return this.entityData.get(ROCKET);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > 600) {
            this.discard();
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int c = color();
        level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255.0F, ((c >> 8) & 255)
                / 255.0F, (c & 255) / 255.0F), rocket() ? 1.0F : 1.5F), this.getX(), this.getY(), this.getZ(), 2,
                0.05D, 0.05D, 0.05D, 0.0D);
        if ((rocket() || this.homing) && this.tickCount > 3) {
            if (this.prey == null || !this.prey.isAlive()) {
                Entity owner = this.getOwner();
                List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox()
                        .inflate(20.0D), e -> e != owner && e.isAlive() && !(e instanceof StoneClone));
                double best = Double.MAX_VALUE;
                Vec3 dir = this.getDeltaMovement().normalize();
                for (LivingEntity e : near) {
                    Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(this.position());
                    if (to.normalize().dot(dir) > 0.3D && to.lengthSqr() < best) {
                        best = to.lengthSqr();
                        this.prey = e;
                    }
                }
            }
            if (this.prey != null) {
                Vec3 want = this.prey.position().add(0.0D, this.prey.getBbHeight() * 0.5D, 0.0D)
                        .subtract(this.position()).normalize().scale(this.getDeltaMovement().length());
                this.setDeltaMovement(this.getDeltaMovement().lerp(want, 0.25D));
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != this.getOwner() && !(e instanceof PowerShot);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity owner = this.getOwner();
        if (hit.getEntity() instanceof LivingEntity l && this.damage > 0.0F) {
            l.hurt(this.damageSources().indirectMagic(this, owner), this.damage);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!this.level().isClientSide) {
            if (rocket()) {
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), 1.5F,
                        Level.ExplosionInteraction.NONE);
            } else if (this.level() instanceof ServerLevel level) {
                // the blast bursts: a ring of purple light and a push
                level.sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.2F, 1.0F), 2.0F), this.getX(),
                        this.getY(), this.getZ(), 40, 0.6D, 0.6D, 0.6D, 0.1D);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, this.getX(), this.getY(),
                        this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.5F);
                Entity owner = this.getOwner();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.5D),
                        e -> e != owner && e.isAlive())) {
                    Vec3 push = e.position().subtract(this.position()).normalize().scale(0.9D);
                    e.push(push.x, 0.35D, push.z);
                    e.hurtMarked = true;
                }
            }
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float a) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putInt("Color", color());
        tag.putBoolean("Rocket", rocket());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        this.entityData.set(COLOR, tag.getInt("Color"));
        this.entityData.set(ROCKET, tag.getBoolean("Rocket"));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

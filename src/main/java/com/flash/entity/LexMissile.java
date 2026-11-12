package com.flash.entity;

import com.flash.registry.ModEntities;
import com.flash.registry.ModItems;
import com.flash.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A missile of Lex Luthor's suit: flies fast, turns toward its target (if it has one) and blows up on impact. */
public class LexMissile extends ThrowableItemProjectile {

    private static final double SPEED = 1.6D;
    private int target = -1;
    private float damage = 6.0F;

    public LexMissile(EntityType<? extends LexMissile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public LexMissile(Level level, LivingEntity owner, Vec3 from, Vec3 dir, Entity target, float damage) {
        this(ModEntities.LEX_MISSILE.get(), level);
        this.setOwner(owner);
        this.setPos(from.x, from.y, from.z);
        this.setDeltaMovement(dir.normalize().scale(SPEED));
        this.target = target == null ? -1 : target.getId();
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.LEX_MISSILE.get();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            Entity t = this.target >= 0 ? this.level().getEntity(this.target) : null;
            Vec3 v = this.getDeltaMovement();
            if (t != null && t.isAlive() && this.tickCount > 3) {
                Vec3 to = t.position().add(0.0D, t.getBbHeight() * 0.5D, 0.0D).subtract(this.position()).normalize();
                v = v.normalize().scale(0.75D).add(to.scale(0.25D));
            }
            this.setDeltaMovement(v.normalize().scale(SPEED));
            if (this.tickCount > 100) {
                this.explode(this.position());
                return;
            }
            if (this.tickCount % 10 == 1) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.LEX_MISSILE_FLY.get(),
                        SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        } else {
            Vec3 back = this.position().subtract(this.getDeltaMovement().scale(0.5D));
            this.level().addParticle(ParticleTypes.SMOKE, back.x, back.y, back.z, 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ParticleTypes.SMALL_FLAME, back.x, back.y, back.z, 0.0D, 0.0D, 0.0D);
        }
        super.tick();
        if (!this.level().isClientSide) {
            // the air does not slow a missile down
            this.setDeltaMovement(this.getDeltaMovement().normalize().scale(SPEED));
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != this.getOwner();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level().isClientSide) {
            return;
        }
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity living) {
            living.invulnerableTime = 0;
            living.hurt(this.damageSources().explosion(this, this.getOwner()), this.damage);
        }
        this.explode(hit.getLocation());
    }

    private void explode(Vec3 at) {
        ServerLevel level = (ServerLevel) this.level();
        // a small blast that hurts and throws, without breaking blocks
        level.explode(this, this.damageSources().explosion(this, this.getOwner()), null, at.x, at.y, at.z, 1.3F, false,
                Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3D, 0.3D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 12, 0.4D, 0.4D, 0.4D, 0.03D);
        level.playSound(null, at.x, at.y, at.z, ModSounds.LEX_MISSILE_BOOM.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
        this.discard();
    }
}

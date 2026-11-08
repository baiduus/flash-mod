package com.flash.entity;

import com.flash.registry.ModEntities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A rock thrown by the Hulk: it smashes and hurts everything where it lands. */
public class HulkBoulder extends ThrowableItemProjectile {

    private float damage = 14.0F;
    /** Size of the rock (1 = normal): the model and the explosion grow with it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SIZE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(HulkBoulder.class,
                    net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SIZE, 1.0F);
    }

    public void setSize(float size) {
        this.entityData.set(SIZE, Math.max(0.3F, Math.min(3.0F, size)));
    }

    public float getSize() {
        return this.entityData.get(SIZE);
    }

    /**
     * Blast of the rock (TNT = 4): about 2 TNTs for the smallest rock (level 1) up to the
     * force of 15 TNTs (60) for the biggest one (level 10).
     */
    public static float blastPower(float size) {
        float min = sizeFor(1);
        float max = sizeFor(10);
        float t = Math.max(0.0F, Math.min(1.0F, (size - min) / (max - min)));
        // 3 (a small rock) up to 8 (the biggest, two TNTs): the push of a blast reaches twice its power
        return 3.0F + 5.0F * t;
    }

    /** Rock size from the Hulk's active level: small at level 1, twice as big at level 10. */
    public static float sizeFor(int activeLevel) {
        return 0.6F + 0.14F * Math.max(1, Math.min(10, activeLevel));
    }

    public HulkBoulder(EntityType<? extends HulkBoulder> type, Level level) {
        super(type, level);
    }

    public HulkBoulder(Level level, LivingEntity thrower) {
        super(ModEntities.HULK_BOULDER.get(), thrower, level);
    }

    /** Force bar of the throw (0..1): the blast grows with it (server only). */
    private float charge = 1.0F;

    public void setCharge(float charge) {
        this.charge = charge;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    protected float getGravity() {
        return 0.04F;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        float size = this.getSize();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.0D + 3.5D * size),
                e -> e != this.getOwner() && e.isAlive())) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurt(this.damageSources().thrown(this, this.getOwner()), this.damage);
            e.setDeltaMovement(this.getDeltaMovement().normalize().scale(1.2D).add(0.0D, 0.5D, 0.0D));
            e.hurtMarked = true;
        }
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(),
                this.getZ(), (int) (25 * size), 0.4D * size, 0.4D * size, 0.4D * size, 0.2D);
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.0D);
        if (this.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) {
            // the rock bursts on impact (the Hulk is left out of the blast)
            // small rock, small blast; big rock, big blast
            // the blast pushes and hurts out to twice its power: kept in step with the crater it opens
            float power = blastPower(size) * (0.2F + 0.8F * this.charge);
            level.explode(this, this.getX(), this.getY(), this.getZ(), power,
                    com.flash.hulk.HulkPowers.destroyBlocks(owner)
                            ? net.minecraft.world.level.Level.ExplosionInteraction.TNT
                            : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            // the visible blast as wide as its reach
            int n = Math.max(1, Math.round(power * 1.5F));
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(),
                    Math.max(1, n / 4), power * 0.35D, power * 0.15D, power * 0.35D, 0.0D);
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), n * 2,
                    power * 0.6D, power * 0.25D, power * 0.6D, 0.0D);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.0F, 0.8F);
        this.discard();
    }
}

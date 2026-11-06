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

    public HulkBoulder(EntityType<? extends HulkBoulder> type, Level level) {
        super(type, level);
    }

    public HulkBoulder(Level level, LivingEntity thrower) {
        super(ModEntities.HULK_BOULDER.get(), thrower, level);
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
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.5D),
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
                this.getZ(), 40, 0.5D, 0.5D, 0.5D, 0.2D);
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.0D);
        if (this.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) {
            // the rock bursts on impact (the Hulk is left out of the blast)
            level.explode(this, this.getX(), this.getY(), this.getZ(), 3.0F,
                    com.flash.hulk.HulkPowers.destroyBlocks(owner)
                            ? net.minecraft.world.level.Level.ExplosionInteraction.TNT
                            : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.0F, 0.8F);
        this.discard();
    }
}

package com.flash.ultron;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A drone of Ultron's Legion: a smaller Ultron body flying on its thrusters (UltronAbilities, the Legion). It flies
 * around him, goes for whatever he fights or whatever hits him and hunts the monsters near him: it circles its prey
 * shooting orange bolts from its palm and dives on it to strike. It lasts a minute and a half, then falls apart; it is
 * never saved and drops nothing.
 */
public class UltronSentry extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(UltronSentry.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private int life = 1800;
    private int shot = 30;
    private int dive;

    public UltronSentry(EntityType<? extends UltronSentry> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D).add(Attributes.FLYING_SPEED, 0.7D)
                .add(Attributes.FOLLOW_RANGE, 40.0D).add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 10.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    public static UltronSentry create(ServerLevel level, ServerPlayer owner) {
        UltronSentry s = new UltronSentry(UltronRegistry.SENTRY.get(), level);
        s.entityData.set(OWNER, Optional.of(owner.getUUID()));
        return s;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    public UUID owner() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public Player ownerPlayer() {
        UUID id = owner();
        return id == null ? null : this.level().getPlayerByUUID(id);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Hunt(this));
        this.goalSelector.addGoal(2, new Escort(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        Player o = ownerPlayer();
        if (--this.life <= 0 || o == null || !o.isAlive() || !UltronPowers.has(o)) {
            fallApart();
            return;
        }
        LivingEntity t = this.getTarget();
        if (t != null && (!t.isAlive() || UltronAbilities.friend(o.getUUID(), t))) {
            this.setTarget(null);
        }
        if (this.getTarget() == null && this.tickCount % 10 == 0) {
            this.setTarget(pickTarget(o));
        }
        if (this.distanceToSqr(o) > 48.0D * 48.0D && this.level() == o.level()) {
            this.teleportTo(o.getX(), o.getY() + 2.0D, o.getZ());
        }
    }

    private LivingEntity pickTarget(Player o) {
        LivingEntity foe = o.getLastHurtByMob();
        if (foe == null || o.tickCount - o.getLastHurtByMobTimestamp() > 200) {
            foe = o.getLastHurtMob();
            if (foe != null && o.tickCount - o.getLastHurtMobTimestamp() > 200) {
                foe = null;
            }
        }
        if (foe != null && foe.isAlive() && !UltronAbilities.friend(o.getUUID(), foe)) {
            return foe;
        }
        LivingEntity near = null;
        double best = 20.0D * 20.0D;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(20.0D),
                e -> e instanceof Enemy && e.isAlive() && !UltronAbilities.friend(o.getUUID(), e))) {
            double d = e.distanceToSqr(this);
            if (d < best) {
                best = d;
                near = e;
            }
        }
        return near;
    }

    /** A bolt from the palm. */
    private void shoot(LivingEntity t) {
        Player o = ownerPlayer();
        if (o == null) {
            return;
        }
        Vec3 a = this.position().add(0.0D, this.getBbHeight() * 0.6D, 0.0D);
        Vec3 b = t.position().add(0.0D, t.getBbHeight() * 0.5D, 0.0D);
        UltronAbilities.fx(this, UltronAbilities.FX_SHOT, a, b, 1.0F, t.getId());
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 0.6F, 1.7F);
        UltronAbilities.hurt(this, o, t, 5.0F);
    }

    public void fallApart() {
        pieces();
        this.discard();
    }

    private void pieces() {
        if (this.level() instanceof ServerLevel level) {
            UltronAbilities.fx(this, UltronAbilities.FX_HIT, this.position().add(0.0D, 0.7D, 0.0D), Vec3.ZERO, 0.6F,
                    -1);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, this.getX(), this.getY() + 0.7D,
                    this.getZ(), 15, 0.3D, 0.5D, 0.3D, 0.02D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_DEATH,
                    SoundSource.HOSTILE, 0.6F, 1.6F);
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Player o = ownerPlayer();
        DamageSource src = o != null ? this.damageSources().indirectMagic(this, o)
                : this.damageSources().mobAttack(this);
        return target.hurt(src, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @Override
    public boolean canAttack(LivingEntity e) {
        UUID id = owner();
        return (id == null || !UltronAbilities.friend(id, e)) && super.canAttack(e);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        UUID id = owner();
        if (id != null && source.getEntity() != null && UltronAbilities.friend(id, source.getEntity())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide) {
            pieces();
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public int getExperienceReward() {
        return 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    /** Circles its prey a few blocks up, shooting; now and then it dives on it and strikes. */
    private static final class Hunt extends Goal {
        private final UltronSentry s;

        Hunt(UltronSentry s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.s.getTarget() != null && this.s.getTarget().isAlive();
        }

        @Override
        public void tick() {
            LivingEntity t = this.s.getTarget();
            if (t == null) {
                return;
            }
            this.s.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (this.s.dive > 0) {
                this.s.dive--;
                this.s.getMoveControl().setWantedPosition(t.getX(), t.getY() + t.getBbHeight() * 0.5D, t.getZ(), 1.8D);
                if (this.s.distanceToSqr(t) < 2.6D * 2.6D) {
                    this.s.doHurtTarget(t);
                    this.s.dive = 0;
                }
                return;
            }
            double a = (this.s.tickCount + this.s.getId() * 37) * 0.06D;
            double r = 4.5D;
            this.s.getMoveControl().setWantedPosition(t.getX() + Math.cos(a) * r, t.getY() + 2.5D + Math.sin(a * 0.7D),
                    t.getZ() + Math.sin(a) * r, 1.2D);
            if (--this.s.shot <= 0 && this.s.hasLineOfSight(t)) {
                this.s.shot = 22 + this.s.random.nextInt(12);
                this.s.shoot(t);
                if (this.s.random.nextInt(4) == 0) {
                    this.s.dive = 20;
                }
            }
        }
    }

    /** Flies around Ultron's shoulders when there is nothing to fight. */
    private static final class Escort extends Goal {
        private final UltronSentry s;

        Escort(UltronSentry s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.s.getTarget() == null && this.s.ownerPlayer() != null;
        }

        @Override
        public void tick() {
            Player o = this.s.ownerPlayer();
            if (o == null) {
                return;
            }
            double a = (this.s.tickCount + this.s.getId() * 53) * 0.04D;
            double r = 2.5D + (this.s.getId() % 3) * 0.6D;
            this.s.getMoveControl().setWantedPosition(o.getX() + Math.cos(a) * r, o.getY() + 2.2D
                    + (this.s.getId() % 3) * 0.5D, o.getZ() + Math.sin(a) * r, 1.3D);
            this.s.getLookControl().setLookAt(o.getX() + o.getLookAngle().x * 8.0D, o.getEyeY(),
                    o.getZ() + o.getLookAngle().z * 8.0D);
        }
    }
}

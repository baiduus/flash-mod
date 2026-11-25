package com.flash.ultron;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A sentry of Ultron's Legion: a smaller Ultron body built by him (UltronAbilities, the Legion). It follows him,
 * fights whatever he fights or whatever hits him, and hunts the monsters near him: punches up close, a red laser
 * from the eyes from afar. It lasts a minute, then falls apart; it is never saved and drops nothing.
 */
public class UltronSentry extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(UltronSentry.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final DustParticleOptions RED = new DustParticleOptions(UltronPowers.RED, 0.9F);
    private int life = 1200;
    private int laser = 20;

    public UltronSentry(EntityType<? extends UltronSentry> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D).add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D).add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    public static UltronSentry create(ServerLevel level, ServerPlayer owner) {
        UltronSentry s = new UltronSentry(UltronRegistry.SENTRY.get(), level);
        s.entityData.set(OWNER, Optional.of(owner.getUUID()));
        return s;
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
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(3, new FollowOwner(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
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
            t = null;
        }
        if (t == null && this.tickCount % 10 == 0) {
            this.setTarget(pickTarget(o));
            t = this.getTarget();
        }
        // the eye laser at a distance
        if (t != null && --this.laser <= 0 && this.distanceToSqr(t) < 18.0D * 18.0D && this.distanceToSqr(t) > 9.0D
                && this.hasLineOfSight(t)) {
            this.laser = 30 + this.random.nextInt(10);
            ServerLevel level = (ServerLevel) this.level();
            Vec3 a = this.getEyePosition();
            Vec3 b = t.getEyePosition().add(0.0D, -0.3D, 0.0D);
            UltronAbilities.line(level, a, b, RED, 0.4D);
            level.sendParticles(ParticleTypes.SMALL_FLAME, b.x, b.y, b.z, 3, 0.1D, 0.1D, 0.1D, 0.01D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE,
                    0.6F, 1.8F);
            t.invulnerableTime = 0;
            t.hurt(this.damageSources().indirectMagic(this, o), 4.0F);
        }
        if (this.distanceToSqr(o) > 40.0D * 40.0D && this.level() == o.level()) {
            this.teleportTo(o.getX(), o.getY(), o.getZ());
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
        double best = 16.0D * 16.0D;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(16.0D),
                e -> e instanceof Enemy && e.isAlive() && !UltronAbilities.friend(o.getUUID(), e))) {
            double d = e.distanceToSqr(this);
            if (d < best) {
                best = d;
                near = e;
            }
        }
        return near;
    }

    public void fallApart() {
        pieces();
        this.discard();
    }

    private void pieces() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(RED, this.getX(), this.getY() + 0.8D, this.getZ(), 25, 0.3D, 0.6D, 0.3D, 0.0D);
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.8D, this.getZ(), 15, 0.3D, 0.6D,
                    0.3D, 0.02D);
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

    /** Follows Ultron as a tamed wolf does. */
    private static final class FollowOwner extends Goal {
        private final UltronSentry s;

        FollowOwner(UltronSentry s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player o = this.s.ownerPlayer();
            return o != null && this.s.getTarget() == null && this.s.distanceToSqr(o) > 36.0D;
        }

        @Override
        public boolean canContinueToUse() {
            Player o = this.s.ownerPlayer();
            return o != null && this.s.getTarget() == null && this.s.distanceToSqr(o) > 6.0D;
        }

        @Override
        public void tick() {
            Player o = this.s.ownerPlayer();
            if (o == null) {
                return;
            }
            this.s.getLookControl().setLookAt(o, 10.0F, this.s.getMaxHeadXRot());
            this.s.getNavigation().moveTo(o, 1.2D);
        }
    }
}

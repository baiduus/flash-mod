package com.flash.infinity.entity;

import com.flash.infinity.InfinityEntities;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * An illusion of the Mind Stone (the original's Clone): looks exactly like its player (skin, armour, held items, name),
 * follows them as a tamed wolf would, defends itself and them, attacks whatever they hit, and vanishes after its
 * lifetime or when they die. Drops nothing, never saved.
 */
public class StoneClone extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(StoneClone.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private int life = 1200;

    public StoneClone(EntityType<? extends StoneClone> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    public static StoneClone create(ServerLevel level, Player owner, int life, float health) {
        StoneClone c = new StoneClone(InfinityEntities.CLONE.get(), level);
        c.entityData.set(OWNER, Optional.of(owner.getUUID()));
        c.life = life;
        c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        c.setHealth(health);
        c.copyEquipment(owner);
        c.setCustomName(owner.getDisplayName());
        c.setCustomNameVisible(true);
        return c;
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

    public void copyEquipment(LivingEntity from) {
        for (EquipmentSlot s : EquipmentSlot.values()) {
            this.setItemSlot(s, from.getItemBySlot(s).copy());
            this.setDropChance(s, 0.0F);
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new FollowOwner(this, 1.1D, 6.0F, 2.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerTarget(this, true));
        this.targetSelector.addGoal(2, new OwnerTarget(this, false));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        Player o = ownerPlayer();
        if (--this.life <= 0 || o == null || !o.isAlive()) {
            vanish();
            return;
        }
        // the owner's held items, as they change
        if (this.tickCount % 10 == 0) {
            this.setItemSlot(EquipmentSlot.MAINHAND, o.getMainHandItem().copy());
            this.setItemSlot(EquipmentSlot.OFFHAND, o.getOffhandItem().copy());
        }
    }

    public void vanish() {
        if (this.level() instanceof ServerLevel level) {
            poof(level, this.getX(), this.getY() + 0.5D, this.getZ());
        }
        this.discard();
    }

    public static void poof(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.POOF, x, y, z, 20, 0.3D, 0.5D, 0.3D, 0.02D);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        Player o = ownerPlayer();
        float dmg = o == null ? 2.0F : (float) o.getAttributeValue(Attributes.ATTACK_DAMAGE);
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return target.hurt(this.damageSources().mobAttack(this), Math.max(1.0F, dmg));
    }

    @Override
    public boolean canAttack(LivingEntity e) {
        Player o = ownerPlayer();
        return e != o && !(e instanceof StoneClone c && c.owner() != null && c.owner().equals(owner()))
                && super.canAttack(e);
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

    /** Follows its player as a tamed wolf does. */
    private static final class FollowOwner extends Goal {
        private final StoneClone c;
        private final double speed;
        private final float start;
        private final float stop;

        FollowOwner(StoneClone c, double speed, float start, float stop) {
            this.c = c;
            this.speed = speed;
            this.start = start;
            this.stop = stop;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player o = this.c.ownerPlayer();
            return o != null && this.c.getTarget() == null && this.c.distanceToSqr(o) > this.start * this.start;
        }

        @Override
        public boolean canContinueToUse() {
            Player o = this.c.ownerPlayer();
            return o != null && this.c.getTarget() == null && this.c.distanceToSqr(o) > this.stop * this.stop;
        }

        @Override
        public void tick() {
            Player o = this.c.ownerPlayer();
            if (o == null) {
                return;
            }
            this.c.getLookControl().setLookAt(o, 10.0F, this.c.getMaxHeadXRot());
            if (this.c.distanceToSqr(o) > 24.0D * 24.0D) {
                this.c.teleportTo(o.getX(), o.getY(), o.getZ());
            } else {
                this.c.getNavigation().moveTo(o, this.speed);
            }
        }
    }

    /** Defends its player (who hurt them) and attacks what they hit. */
    private static final class OwnerTarget extends TargetGoal {
        private final boolean byOwner;
        private LivingEntity target;
        private int stamp;

        OwnerTarget(StoneClone c, boolean byOwner) {
            super(c, false);
            this.byOwner = byOwner;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player o = ((StoneClone) this.mob).ownerPlayer();
            if (o == null) {
                return false;
            }
            this.target = this.byOwner ? o.getLastHurtMob() : o.getLastHurtByMob();
            int t = this.byOwner ? o.getLastHurtMobTimestamp() : o.getLastHurtByMobTimestamp();
            return t != this.stamp && this.target != null && this.canAttack(this.target, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.target);
            Player o = ((StoneClone) this.mob).ownerPlayer();
            if (o != null) {
                this.stamp = this.byOwner ? o.getLastHurtMobTimestamp() : o.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }
}

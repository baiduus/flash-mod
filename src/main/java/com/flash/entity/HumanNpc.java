package com.flash.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A plain human (Steve or Alex), what the Manipulation of Reality makes of a skeleton or a zombie (YasReality). It
 * wanders, looks at the players and runs when hurt. Made from a skeleton its flesh and skin grow back over the bones
 * first ({@link #GROW_TICKS}, HumanNpcRenderer), from a zombie the rot fades away; it stands still meanwhile.
 */
public class HumanNpc extends PathfinderMob {

    public static final int GROW_TICKS = 70;
    /** 0 Steve, 1 Alex. */
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(HumanNpc.class,
            EntityDataSerializers.INT);
    /** 0 nothing, 1 from bones, 2 from rot. */
    private static final EntityDataAccessor<Integer> FROM = SynchedEntityData.defineId(HumanNpc.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GROWN = SynchedEntityData.defineId(HumanNpc.class,
            EntityDataSerializers.INT);

    public HumanNpc(EntityType<? extends HumanNpc> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SKIN, 0);
        this.entityData.define(FROM, 0);
        this.entityData.define(GROWN, GROW_TICKS);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4D));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public boolean alex() {
        return this.entityData.get(SKIN) == 1;
    }

    public void setAlex(boolean alex) {
        this.entityData.set(SKIN, alex ? 1 : 0);
    }

    public int from() {
        return this.entityData.get(FROM);
    }

    /** It starts growing back (from bones 1, from rot 2). */
    public void grow(int from) {
        this.entityData.set(FROM, from);
        this.entityData.set(GROWN, 0);
    }

    /** 0..1 of the growth (1: a whole human). */
    public float growth(float partial) {
        int g = this.entityData.get(GROWN);
        return g >= GROW_TICKS ? 1.0F : Math.min(1.0F, (g + partial) / GROW_TICKS);
    }

    public boolean growing() {
        return this.entityData.get(GROWN) < GROW_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && growing()) {
            this.entityData.set(GROWN, this.entityData.get(GROWN) + 1);
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || growing();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Alex", alex());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAlex(tag.getBoolean("Alex"));
    }
}

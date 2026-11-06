package com.flash.entity;

import com.flash.ability.FlashDimensionTravel;
import com.flash.registry.ModEntities;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ghost of the Acceleration Force: a decayed speedster in a black suit with red
 * lightning (inspired by the Black Flash). It follows whoever enters the
 * Acceleration Force and hunts them there. 100 health, very fast, drops a nether
 * star and fragments of its suit.
 */
public class AccelerationPhantom extends Monster {

    private static final DustParticleOptions RED_TRAIL =
            new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.06F), 1.0F);

    public AccelerationPhantom(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.setMaxUpStep(1.25F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            Vec3 motion = this.getDeltaMovement();
            boolean moving = motion.x * motion.x + motion.z * motion.z > 0.002D;
            if (moving || this.random.nextInt(4) == 0) {
                for (int i = 0; i < (moving ? 3 : 1); i++) {
                    this.level().addParticle(RED_TRAIL,
                            this.getRandomX(0.6D), this.getRandomY(), this.getRandomZ(0.6D), 0.0D, 0.0D, 0.0D);
                }
                if (this.random.nextInt(3) == 0) {
                    this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    /**
     * A player just arrived in the Acceleration Force: a ghost appears nearby and
     * hunts them (only one ghost at a time around the same player).
     */
    public static void huntOnArrival(ServerPlayer player, ServerLevel level) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        List<AccelerationPhantom> existing = level.getEntitiesOfClass(AccelerationPhantom.class,
                player.getBoundingBox().inflate(128.0D));
        if (!existing.isEmpty()) {
            existing.forEach(ghost -> ghost.setTarget(player));
            return;
        }
        AccelerationPhantom ghost = ModEntities.ACCELERATION_PHANTOM.get().create(level);
        if (ghost == null) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double x = player.getX() + Math.cos(angle) * 14.0D;
        double z = player.getZ() + Math.sin(angle) * 14.0D;
        double y = FlashDimensionTravel.safeY(level, x, z);
        float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - z, player.getX() - x)) - 90.0F;
        ghost.moveTo(x, y, z, yaw, 0.0F);
        ghost.setTarget(player);
        ghost.setPersistenceRequired();
        level.addFreshEntity(ghost);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.0D, z, 40, 0.5D, 1.0D, 0.5D, 0.15D);
        level.sendParticles(RED_TRAIL, x, y + 1.0D, z, 40, 0.5D, 1.0D, 0.5D, 0.0D);
        level.playSound(null, x, y, z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("message.flash.phantom_arrives"), false);
    }
}

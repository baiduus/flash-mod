package com.flash.entity;

import com.flash.ability.FlashAbility;
import com.flash.ability.FlashAbilityCombat;
import com.flash.ability.FlashDimensionTravel;
import com.flash.network.FlashBeamPacket;
import com.flash.power.FlashSpeedLevels;
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
import net.minecraft.world.entity.LivingEntity;
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
 * Acceleration Force and hunts them there. 50 health, very fast, drops a nether
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
                .add(Attributes.MAX_HEALTH, 50.0D)
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

    // ------------------------------------------------------------------
    // Speedster brain: level 10 speed and the Flash abilities (same cooldowns as a player)
    // ------------------------------------------------------------------

    /** Blocks per tick of a level-10 speedster at full sprint. */
    private static final double LEVEL10_STEP =
            FlashSpeedLevels.totalMultiplier(FlashSpeedLevels.BASE_MAX_LEVEL) * 0.2158D * 1.3D;

    private enum Action { NONE, BARRAGE, TORNADO, LIGHTNING }

    private final int[] cooldowns = new int[FlashAbility.values().length];
    private Action action = Action.NONE;
    private int actionTicks;
    private Vec3 actionCenter = Vec3.ZERO;
    private double actionAngle;

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        for (int i = 0; i < this.cooldowns.length; i++) {
            if (this.cooldowns[i] > 0) {
                this.cooldowns[i]--;
            }
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()
                || (target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            this.action = Action.NONE;
            return;
        }
        if (this.action != Action.NONE) {
            this.tickAction(target);
            return;
        }
        double dist = this.distanceTo(target);
        boolean sees = this.hasLineOfSight(target);
        if (this.tickCount % 10 == 0 && this.chooseAbility(target, dist, sees)) {
            return;
        }
        if (dist > 3.0D && sees) {
            // straight run at level-10 speed, stopping right next to the target
            Vec3 to = target.position().subtract(this.position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double len = flat.length();
            if (len > 0.01D) {
                double step = Math.min(len - 2.2D, LEVEL10_STEP);
                Vec3 dir = flat.scale(1.0D / len);
                double y = this.getDeltaMovement().y;
                if (this.horizontalCollision && this.onGround()) {
                    y = 0.42D;
                }
                this.getNavigation().stop();
                this.setDeltaMovement(dir.x * step, y, dir.z * step);
                this.face(target);
            }
        } else if (!sees) {
            this.getNavigation().moveTo(target, 1.6D);
        }
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    private boolean ready(FlashAbility ability) {
        return this.cooldowns[ability.ordinal()] <= 0;
    }

    private void use(FlashAbility ability) {
        this.cooldowns[ability.ordinal()] = ability.cooldownTicks();
    }

    private boolean chooseAbility(LivingEntity target, double dist, boolean sees) {
        if (dist <= 3.6D && ready(FlashAbility.BARRAGE) && this.random.nextInt(2) == 0) {
            use(FlashAbility.BARRAGE);
            start(Action.BARRAGE);
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.6F);
            return true;
        }
        if (dist <= 8.0D && ready(FlashAbility.SPEED_TORNADO) && this.random.nextInt(3) == 0) {
            use(FlashAbility.SPEED_TORNADO);
            start(Action.TORNADO);
            this.actionCenter = target.position();
            return true;
        }
        if (sees && dist > 6.0D && dist <= 40.0D && ready(FlashAbility.LIGHTNING_STRIKE)) {
            use(FlashAbility.LIGHTNING_STRIKE);
            start(Action.LIGHTNING);
            this.actionCenter = this.position();
            this.playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.4F);
            return true;
        }
        if (sees && dist > 6.0D && dist <= 24.0D && ready(FlashAbility.DASH_PUNCH)) {
            use(FlashAbility.DASH_PUNCH);
            dashPunch(target);
            return true;
        }
        return false;
    }

    private void start(Action next) {
        this.action = next;
        this.actionTicks = 0;
        this.actionAngle = 0.0D;
        this.getNavigation().stop();
    }

    private void tickAction(LivingEntity target) {
        ServerLevel level = (ServerLevel) this.level();
        this.actionTicks++;
        switch (this.action) {
            case BARRAGE -> {
                // 20 ticks of punches, one every 2 ticks, at whoever stays in front
                this.face(target);
                if (this.actionTicks % 2 == 0 && this.distanceTo(target) <= 3.6D) {
                    target.invulnerableTime = 0;
                    if (target.hurt(this.damageSources().mobAttack(this), 3.0F)) {
                        FlashAbilityCombat.spawnElectricBurst(level, target.getBoundingBox().getCenter(), 5);
                    }
                    this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                }
                if (this.actionTicks >= 20) {
                    this.action = Action.NONE;
                }
            }
            case TORNADO -> {
                // runs in circles around the target: it is lifted and hit
                this.actionAngle += 0.9D;
                Vec3 c = this.actionCenter;
                double x = c.x + Math.cos(this.actionAngle) * 2.5D;
                double z = c.z + Math.sin(this.actionAngle) * 2.5D;
                this.teleportTo(x, FlashDimensionTravel.safeY(level, x, z), z);
                this.face(target);
                if (target.distanceToSqr(c) < 36.0D) {
                    Vec3 pull = c.subtract(target.position()).scale(0.15D);
                    target.setDeltaMovement(pull.x, 0.32D, pull.z);
                    target.hurtMarked = true;
                    target.fallDistance = 0.0F;
                    if (this.actionTicks % 10 == 0) {
                        target.invulnerableTime = 0;
                        target.hurt(this.damageSources().mobAttack(this), 4.0F);
                    }
                }
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1.0D, c.z, 6, 1.6D, 1.2D, 1.6D, 0.05D);
                level.sendParticles(RED_TRAIL, x, this.getY() + 1.0D, z, 3, 0.2D, 0.5D, 0.2D, 0.0D);
                if (this.actionTicks >= 60) {
                    this.action = Action.NONE;
                }
            }
            case LIGHTNING -> {
                // spins up for 12 ticks, then throws the lightning at the target
                this.actionAngle += 0.8D;
                Vec3 c = this.actionCenter;
                double x = c.x + Math.cos(this.actionAngle) * 1.5D;
                double z = c.z + Math.sin(this.actionAngle) * 1.5D;
                this.teleportTo(x, FlashDimensionTravel.safeY(level, x, z), z);
                if (this.actionTicks >= 12) {
                    this.face(target);
                    Vec3 start = this.getEyePosition();
                    Vec3 end = target.getBoundingBox().getCenter();
                    target.invulnerableTime = 0;
                    target.hurt(this.damageSources().indirectMagic(this, this), 25.0F);
                    target.setSecondsOnFire(8);
                    FlashBeamPacket.sendFrom(this, start, end, 0xFFFF2020, 16, 1.25F, true);
                    FlashAbilityCombat.spawnImpact(level, end, 2.5F);
                    FlashAbilityCombat.playThunder(level, end);
                    this.action = Action.NONE;
                }
            }
            default -> this.action = Action.NONE;
        }
    }

    /** Punch rush: appears right next to the target in a blink and hits hard. */
    private void dashPunch(LivingEntity target) {
        ServerLevel level = (ServerLevel) this.level();
        Vec3 from = this.position();
        Vec3 back = from.subtract(target.position());
        Vec3 flat = new Vec3(back.x, 0.0D, back.z);
        if (flat.lengthSqr() < 0.01D) {
            flat = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 spot = target.position().add(flat.normalize().scale(1.4D));
        this.teleportTo(spot.x, FlashDimensionTravel.safeY(level, spot.x, spot.z), spot.z);
        this.face(target);
        target.invulnerableTime = 0;
        target.hurt(this.damageSources().mobAttack(this), 15.0F);
        Vec3 push = target.position().subtract(this.position()).normalize();
        target.setDeltaMovement(push.x * 1.2D, 0.4D, push.z * 1.2D);
        target.hurtMarked = true;
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.sendParticles(RED_TRAIL, from.x, from.y + 1.0D, from.z, 30, 0.3D, 0.6D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spot.x, spot.y + 1.0D, spot.z, 20, 0.4D, 0.6D, 0.4D, 0.1D);
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.8F);
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
    private static final String DUE = "flash_phantom_due";

    /** Called on arrival: the ghost shows up a few seconds later (the chunks are loaded by then). */
    public static void scheduleHunt(ServerPlayer player) {
        player.getPersistentData().putLong(DUE, player.level().getGameTime() + 60L);
    }

    /** Every player tick: spawns the scheduled ghost while the player is still in the dimension. */
    public static void tickHunt(ServerPlayer player) {
        if (!player.getPersistentData().contains(DUE)
                || player.level().getGameTime() < player.getPersistentData().getLong(DUE)) {
            return;
        }
        player.getPersistentData().remove(DUE);
        if (FlashDimensionTravel.isInAccelerationForce(player) && player.level() instanceof ServerLevel level) {
            huntOnArrival(player, level);
        }
    }

    public static void huntOnArrival(ServerPlayer player, ServerLevel level) {
        if (player.isSpectator()) {
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

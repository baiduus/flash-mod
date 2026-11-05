package com.flash.ability;

import com.flash.block.FlashAccelerationPortal;
import com.flash.network.FlashAbilitySyncPacket;
import com.flash.network.FlashBeamPacket;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import com.flash.power.FlashPowers;
import com.flash.power.FlashSpeed;
import com.flash.power.FlashStatusEffects;
import com.flash.power.FlashStep;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

/**
 * Server-authoritative coordinator for every progressive skill.
 *
 * <p>All activation requests pass through {@link #onPress(ServerPlayer,
 * FlashAbility)}. The manager verifies unlock level and cooldown, owns the
 * active state, applies damage/pushes on the server and sends only short-lived
 * visual packets. Hold abilities are stopped on release, logout, death or
 * dimension changes.</p>
 */
public final class FlashAbilityManager {

    private static final int MAX_WIND_TICKS = 400;
    private static final int MAX_TORNADO_TICKS = 120;
    private static final int LIGHTNING_SPIN_TICKS = 12;
    private static final int MASS_CHARGE_TICKS = 30;
    private static final int PORTAL_CHANNEL_TICKS = 40;
    private static final int TEMPORARY_DURATION_TICKS = 6000;
    private static final double MASS_CIRCLE_RADIUS = 7.5D;

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<FlashAbility, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    /** Players whose cooldowns were switched off with /flash cooldowns off (testing aid). */
    private static final Set<UUID> NO_COOLDOWN = ConcurrentHashMap.newKeySet();

    private FlashAbilityManager() {
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public static void onPress(ServerPlayer player, FlashAbility ability) {
        if (ability == null) {
            return;
        }
        if (!FlashPowers.hasPowers(player)) {
            player.displayClientMessage(Component.translatable("message.flash.no_powers"), true);
            return;
        }
        if (!ability.isUnlocked(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.flash.ability_locked",
                    ability.displayName(),
                    ability.requiredLevel()), true);
            return;
        }

        long now = gameTime(player);
        long end = cooldownEnd(player, ability);
        if (end > now) {
            int seconds = (int) Math.ceil((end - now) / 20.0D);
            player.displayClientMessage(Component.translatable(
                    "message.flash.ability_cooldown", ability.displayName(), seconds), true);
            sendSync(player, ability, false);
            return;
        }

        PlayerState state = state(player);
        if (isBusy(state)) {
            player.displayClientMessage(Component.translatable("message.flash.ability_busy"), true);
            return;
        }

        boolean started = switch (ability) {
            case BARRAGE -> {
                state.barrageTicks = 0;
                player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 0.9F, 1.6F);
                yield true;
            }
            case WIND_HANDS -> {
                state.windTicks = 0;
                state.windDamaged.clear();
                player.playNotifySound(SoundEvents.PHANTOM_FLAP,
                        SoundSource.PLAYERS, 0.8F, 1.5F);
                player.displayClientMessage(
                        Component.translatable("message.flash.wind_started"), true);
                yield true;
            }
            case PHASING -> {
                FlashPhase.start(player, state);
                yield true;
            }
            case SPEED_TORNADO -> {
                state.tornadoTicks = 0;
                state.tornadoCenter = player.position();
                state.tornadoAngle = Math.atan2(player.getZ() - state.tornadoCenter.z,
                        player.getX() - state.tornadoCenter.x);
                player.displayClientMessage(
                        Component.translatable("message.flash.ability_tornado_started"), true);
                yield true;
            }
            case LIGHTNING_STRIKE -> {
                state.lightningTicks = 0;
                state.lightningCenter = player.position();
                state.lightningAngle = Math.atan2(player.getZ() - state.lightningCenter.z,
                        player.getX() - state.lightningCenter.x);
                player.displayClientMessage(
                        Component.translatable("message.flash.ability_lightning_charge"), true);
                player.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE,
                        SoundSource.PLAYERS, 1.0F, 1.4F);
                yield true;
            }
            case MASS_PUNCH -> {
                state.massTicks = 0;
                state.massCenter = player.position();
                state.massAngle = 0.0D;
                player.displayClientMessage(
                        Component.translatable("message.flash.ability_mass_charge"), true);
                player.playNotifySound(SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.2F, 1.5F);
                yield true;
            }
            case SPEED_MANIPULATION -> grantSpeedToTarget(player);
            case DASH_PUNCH -> {
                state.dash = FlashDashPunch.start(player);
                if (state.dash == null) {
                    player.displayClientMessage(
                            Component.translatable("message.flash.dash_no_targets"), true);
                    yield false;
                }
                yield true;
            }
            case TIME_SLOW -> {
                state.slow = FlashTimeSlow.start(player);
                player.displayClientMessage(
                        Component.translatable("message.flash.time_slow_started"), true);
                yield true;
            }
            case ACCELERATION_FORCE -> {
                state.portalTicks = 0;
                state.portalExit = FlashDimensionTravel.isInAccelerationForce(player);
                player.displayClientMessage(Component.translatable(
                        state.portalExit
                                ? "message.flash.portal_opening_exit"
                                : "message.flash.portal_opening"), true);
                player.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE,
                        SoundSource.PLAYERS, 1.4F, 0.8F);
                yield true;
            }
        };

        if (!started) {
            return;
        }

        // Hold abilities start their cooldown when the player releases them,
        // so holding the key is never punished by a cooldown that expires
        // before the ability ends.
        if (!ability.isHold() && ability.cooldownTicks() > 0
                && !NO_COOLDOWN.contains(player.getUUID())) {
            cooldowns(player).put(ability, now + ability.cooldownTicks());
        }
        sendSync(player, ability, true);
    }

    public static void onRelease(ServerPlayer player, FlashAbility ability) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (ability == FlashAbility.WIND_HANDS && state.windTicks >= 0) {
            endWind(player, state, true);
        } else if (ability == FlashAbility.PHASING && state.phasing) {
            FlashPhase.stop(player, state, true);
            startHoldCooldown(player, ability);
            sendSync(player, ability, false);
        } else if (ability == FlashAbility.SPEED_TORNADO && state.tornadoTicks >= 0) {
            state.tornadoTicks = -1;
            releaseDebris(state);
            startHoldCooldown(player, ability);
            sendSync(player, ability, false);
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || !FlashPowers.hasPowers(player)) {
            reset(player);
            return;
        }

        PlayerState state = state(player);

        if (state.barrageTicks >= 0) {
            if ((state.barrageTicks & 1) == 0) {
                barragePulse(player);
            }
            state.barrageTicks++;
            if (state.barrageTicks > 20) {
                state.barrageTicks = -1;
                sendSync(player, FlashAbility.BARRAGE, false);
            }
        }

        if (state.windTicks >= 0) {
            if ((state.windTicks & 1) == 0) {
                windPulse(player, state);
            }
            state.windTicks++;
            if (state.windTicks > MAX_WIND_TICKS) {
                endWind(player, state, true);
            }
        }

        if (state.phasing) {
            if (!FlashPhase.tick(player, state)) {
                FlashPhase.stop(player, state, true);
                startHoldCooldown(player, FlashAbility.PHASING);
                sendSync(player, FlashAbility.PHASING, false);
            }
        }

        if (state.tornadoTicks >= 0) {
            tornadoTick(player, state);
            state.tornadoTicks++;
            if (state.tornadoTicks > MAX_TORNADO_TICKS) {
                state.tornadoTicks = -1;
                releaseDebris(state);
                startHoldCooldown(player, FlashAbility.SPEED_TORNADO);
                sendSync(player, FlashAbility.SPEED_TORNADO, false);
            }
        }

        if (state.lightningTicks >= 0) {
            if (state.lightningTicks < LIGHTNING_SPIN_TICKS) {
                lightningSpin(player, state);
                state.lightningTicks++;
            } else {
                fireLightning(player);
                state.lightningTicks = -1;
                sendSync(player, FlashAbility.LIGHTNING_STRIKE, false);
            }
        }

        if (state.massTicks >= 0) {
            if (state.massTicks < MASS_CHARGE_TICKS) {
                massChargeTick(player, state);
                state.massTicks++;
            } else {
                massPunch(player);
                state.massTicks = -1;
                sendSync(player, FlashAbility.MASS_PUNCH, false);
            }
        }

        if (state.portalTicks >= 0) {
            portalChannelTick(player, state);
            state.portalTicks++;
            if (state.portalTicks > PORTAL_CHANNEL_TICKS) {
                boolean opened = FlashAccelerationPortal.open(player, state.portalExit);
                state.portalTicks = -1;
                if (!opened) {
                    player.displayClientMessage(
                            Component.translatable("message.flash.portal_failed"), true);
                }
                sendSync(player, FlashAbility.ACCELERATION_FORCE, false);
            }
        }

        if (state.dash != null && !FlashDashPunch.tick(player, state.dash)) {
            state.dash = null;
            sendSync(player, FlashAbility.DASH_PUNCH, false);
        }

        if (state.slow != null && !FlashTimeSlow.tick(player, state.slow)) {
            state.slow = null;
            sendSync(player, FlashAbility.TIME_SLOW, false);
        }

        FlashWaterWalk.tick(player);
        FlashDimensionTravel.tick(player);
    }

    public static void reset(ServerPlayer player) {
        FlashWaterWalk.release(player);
        PlayerState state = STATES.remove(player.getUUID());
        if (state != null) {
            releaseDebris(state);
            if (state.slow != null) {
                FlashTimeSlow.end(player, state.slow);
            }
        }
        if (state != null && state.phasing) {
            FlashPhase.restore(player);
            FlashAbilitySyncPacket.send(player, FlashAbility.PHASING, false, 0);
        }
        COOLDOWNS.remove(player.getUUID());
    }

    public static void clear() {
        STATES.clear();
        COOLDOWNS.clear();
        NO_COOLDOWN.clear();
    }

    /** Switches the cooldowns of a player on or off (testing command). */
    public static void setCooldownsEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            NO_COOLDOWN.remove(player.getUUID());
        } else {
            NO_COOLDOWN.add(player.getUUID());
            COOLDOWNS.remove(player.getUUID());
        }
        for (FlashAbility ability : FlashAbility.values()) {
            FlashAbilitySyncPacket.send(player, ability, false, 0);
        }
    }

    public static boolean areCooldownsEnabled(ServerPlayer player) {
        return !NO_COOLDOWN.contains(player.getUUID());
    }

    public static boolean isPhasing(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.phasing;
    }

    public static boolean isAnyAbilityActive(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        return state != null && isBusy(state);
    }

    public static int getRemainingCooldown(ServerPlayer player, FlashAbility ability) {
        long remaining = cooldownEnd(player, ability) - gameTime(player);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    /**
     * Bitmask of the abilities currently being channelled. It is sent to
     * nearby clients so they can reproduce the arm pose and other visual
     * state of the ability.
     */
    public static int activeMask(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null) {
            return 0;
        }
        int mask = 0;
        if (state.barrageTicks >= 0) {
            mask |= 1 << FlashAbility.BARRAGE.ordinal();
        }
        if (state.windTicks >= 0) {
            mask |= 1 << FlashAbility.WIND_HANDS.ordinal();
        }
        if (state.phasing) {
            mask |= 1 << FlashAbility.PHASING.ordinal();
        }
        if (state.tornadoTicks >= 0) {
            mask |= 1 << FlashAbility.SPEED_TORNADO.ordinal();
        }
        if (state.lightningTicks >= 0) {
            mask |= 1 << FlashAbility.LIGHTNING_STRIKE.ordinal();
        }
        if (state.massTicks >= 0) {
            mask |= 1 << FlashAbility.MASS_PUNCH.ordinal();
        }
        if (state.portalTicks >= 0) {
            mask |= 1 << FlashAbility.ACCELERATION_FORCE.ordinal();
        }
        if (state.dash != null) {
            mask |= 1 << FlashAbility.DASH_PUNCH.ordinal();
        }
        if (state.slow != null) {
            mask |= 1 << FlashAbility.TIME_SLOW.ordinal();
        }
        return mask;
    }

    // ------------------------------------------------------------------
    // Barrage
    // ------------------------------------------------------------------

    private static void barragePulse(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        List<LivingEntity> targets = FlashAbilityCombat.targetsInFront(player, 3.4D, 0.25D, 3);
        float damage = (float) FlashAbilityCombat.meleeDamage(player);

        for (LivingEntity target : targets) {
            if (FlashAbilityCombat.hit(player, target, damage)) {
                FlashAbilityCombat.spawnElectricBurst(
                        (ServerLevel) player.level(),
                        target.getBoundingBox().getCenter(), 7);
            }
        }

        player.swing(InteractionHand.MAIN_HAND, true);
        player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.55F, 1.65F);
    }

    // ------------------------------------------------------------------
    // Wind hands (level 2, hold)
    // ------------------------------------------------------------------

    private static void windPulse(ServerPlayer player, PlayerState state) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 eye = player.getEyePosition();
        double time = (player.tickCount + state.windTicks) * 0.42D;

        for (int side = -1; side <= 1; side += 2) {
            Vec3 origin = eye.add(look.scale(0.55D))
                    .add(right.scale(side * 0.42D))
                    .add(0.0D, -0.22D, 0.0D);

            // Visuals are drawn client-side by FlashAbilityEffectsRenderer.
        }

        List<LivingEntity> targets = FlashAbilityCombat.targetsInFront(player, 7.2D, 0.42D, 20);
        for (LivingEntity target : targets) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            double distance = delta.length();
            if (distance > 7.2D || distance < 0.01D) {
                continue;
            }
            if (delta.normalize().dot(look) < 0.42D) {
                continue;
            }
            if (state.windDamaged.add(target.getUUID())) {
                FlashAbilityCombat.hit(player, target, 1.5F);
            }
            double swirl = Math.sin(time + target.getId());
            Vec3 push = look.scale(0.95D)
                    .add(right.scale(swirl * 0.38D))
                    .add(0.0D, 0.22D, 0.0D);
            target.setDeltaMovement(target.getDeltaMovement().add(push));
            target.hurtMarked = true;
            if (!(target instanceof net.minecraft.world.entity.player.Player)) {
                target.move(MoverType.SELF, push.scale(0.35D));
            }
        }

        if (state.windTicks % 8 == 0) {
            player.playNotifySound(SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        // A subtle arm movement keeps the third-person animation alive while
        // the cone is being channelled.
        if (state.windTicks % 10 == 0) {
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    private static void endWind(ServerPlayer player, PlayerState state, boolean cooldown) {
        state.windTicks = -1;
        state.windDamaged.clear();
        if (cooldown) {
            startHoldCooldown(player, FlashAbility.WIND_HANDS);
        }
        sendSync(player, FlashAbility.WIND_HANDS, false);
        player.playNotifySound(SoundEvents.PHANTOM_FLAP,
                SoundSource.PLAYERS, 0.4F, 0.9F);
    }

    // ------------------------------------------------------------------
    // Speed tornado (level 4, hold)
    // ------------------------------------------------------------------

    private static void tornadoTick(ServerPlayer player, PlayerState state) {
        ServerLevel level = (ServerLevel) player.level();
        state.tornadoAngle += 0.55D;
        double radius = 1.5D;
        Vec3 center = state.tornadoCenter;
        double x = center.x + Math.cos(state.tornadoAngle) * radius;
        double z = center.z + Math.sin(state.tornadoAngle) * radius;
        float yaw = (float) (Math.toDegrees(Math.atan2(-(x - player.getX()),
                z - player.getZ())));
        player.connection.teleport(x, player.getY(), z, yaw, player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(7.0D),
                entity -> entity != player && entity.isAlive())) {
            Vec3 delta = center.subtract(entity.position());
            double distanceSqr = delta.lengthSqr();
            if (distanceSqr > 49.0D || distanceSqr < 0.001D) {
                continue;
            }
            Vec3 pull = delta.normalize().scale(0.095D);
            Vec3 tangent = new Vec3(-delta.z, 0.0D, delta.x).normalize().scale(0.045D);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.90D)
                    .add(pull).add(tangent));
            entity.hurtMarked = true;
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
                entity.move(MoverType.SELF, pull.scale(1.4D));
            }
        }

        // Effective damage: stronger than the old 2.0 every second, applied
        // twice per second around the tornado core.
        if (state.tornadoTicks % 10 == 0) {
            float damage = 5.0F + (float) FlashAbilityCombat.meleeDamage(player) * 0.5F;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(3.4D),
                    entity -> entity != player && entity.isAlive())) {
                if (entity.position().distanceToSqr(center) <= 3.4D * 3.4D) {
                    FlashAbilityCombat.hit(player, entity, damage);
                }
            }
        }

        // Lift nearby blocks as debris when block destruction is enabled.
        if (FlashPowers.isDestroyBlocksEnabled(player)
                && state.tornadoTicks % 12 == 0) {
            liftDebris(player, level, center, state);
        }
        holdDebrisAtTop(state, center);

        // Visual funnel: about 9.5 blocks tall, wider at the top, with two
        // particles per layer every other tick (bounded for performance).
        if ((state.tornadoTicks & 1) == 0) {
            int layers = 14;
            for (int i = 0; i < layers; i++) {
                double height = i * 0.68D;
                double ring = 0.22D + i * 0.17D;
                for (int j = 0; j < 2; j++) {
                    double angle = state.tornadoTicks * 0.62D + j * Math.PI
                            + i * 0.36D;
                    double px = center.x + Math.cos(angle) * ring;
                    double pz = center.z + Math.sin(angle) * ring;
                    level.sendParticles(ParticleTypes.CLOUD,
                            px, center.y + height, pz, 1,
                            0.05D, 0.06D, 0.05D, 0.01D);
                    if (i % 3 == 0 && j == 0) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                px, center.y + height, pz, 1,
                                0.03D, 0.03D, 0.03D, 0.02D);
                    }
                }
            }
        }
        // Soft wind loop, only while the key is held (this method stops running
        // the moment the ability is released).
        if (state.tornadoTicks % 16 == 0) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.18F, 0.9F);
        }
    }

    /**
     * Debris stays at the top of the funnel, hopping up and down while it
     * orbits, until the ability ends; {@link #releaseDebris} then drops it.
     */
    private static void holdDebrisAtTop(PlayerState state, Vec3 center) {
        state.debris.removeIf(block -> !block.isAlive());
        int count = state.debris.size();
        for (int i = 0; i < count; i++) {
            FallingBlockEntity block = state.debris.get(i);
            double angle = state.tornadoTicks * 0.22D + i * (Mth.TWO_PI / Math.max(1, count));
            double hop = Math.abs(Math.sin(state.tornadoTicks * 0.16D + i * 1.7D)) * 1.1D;
            double radius = 2.0D + 0.6D * Math.sin(i * 2.1D);
            double tx = center.x + Math.cos(angle) * radius;
            double ty = center.y + 9.2D + hop;
            double tz = center.z + Math.sin(angle) * radius;
            block.setNoGravity(true);
            block.time = 1;
            block.setDeltaMovement(
                    (tx - block.getX()) * 0.22D,
                    (ty - block.getY()) * 0.22D,
                    (tz - block.getZ()) * 0.22D);
            block.hurtMarked = true;
        }
    }

    private static void releaseDebris(PlayerState state) {
        for (FallingBlockEntity block : state.debris) {
            if (block.isAlive()) {
                block.setNoGravity(false);
                block.setDeltaMovement(block.getDeltaMovement().scale(0.2D));
                block.hurtMarked = true;
            }
        }
        state.debris.clear();
    }

    private static void liftDebris(ServerPlayer player, ServerLevel level, Vec3 center,
                                   PlayerState ps) {
        if (ps.debris.size() >= 12) {
            return;
        }

        int lifted = 0;
        BlockPos origin = BlockPos.containing(center);
        for (int attempt = 0; attempt < 14 && lifted < 2; attempt++) {
            int dx = player.getRandom().nextInt(7) - 3;
            int dz = player.getRandom().nextInt(7) - 3;
            int dy = player.getRandom().nextInt(3) - 1;
            if (dx * dx + dz * dz > 9) {
                continue;
            }
            BlockPos pos = origin.offset(dx, dy, dz);
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F
                    || state.getDestroySpeed(level, pos) > 25.0F) {
                continue;
            }
            if (!level.removeBlock(pos, false)) {
                continue;
            }

            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
            falling.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            falling.setDeltaMovement(
                    (level.random.nextDouble() - 0.5D) * 0.18D,
                    0.55D + level.random.nextDouble() * 0.35D,
                    (level.random.nextDouble() - 0.5D) * 0.18D);
            falling.hurtMarked = true;
            falling.dropItem = false;
            ps.debris.add(falling);
            lifted++;
        }
        if (lifted > 0) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.15F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // Lightning strike (level 5)
    // ------------------------------------------------------------------

    private static void lightningSpin(ServerPlayer player, PlayerState state) {
        state.lightningAngle += Math.PI * 0.5D;
        Vec3 center = state.lightningCenter;
        double x = center.x + Math.cos(state.lightningAngle) * 1.0D;
        double z = center.z + Math.sin(state.lightningAngle) * 1.0D;
        player.connection.teleport(x, player.getY(), z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                8, 0.4D, 0.6D, 0.4D, 0.05D);
        if (state.lightningTicks % 4 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    private static void fireLightning(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        // Forgiving aim: any entity near the line of sight, or the first block
        // (flowers, glass and water included; grass is ignored).
        FlashAbilityCombat.AimResult aim = FlashAbilityCombat.aim(player, 64.0D);
        Vec3 targetPoint = aim.point();

        // Quick synchronized air slap: the arm swing is broadcast by vanilla,
        // while the sweep fan makes the gesture readable at a distance.
        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 slapCenter = start.add(look.scale(1.7D));
        for (int i = -2; i <= 2; i++) {
            Vec3 point = slapCenter.add(right.scale(i * 0.42D));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    point.x, point.y - 0.15D, point.z, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z, 3,
                    0.12D, 0.12D, 0.12D, 0.04D);
        }
        player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.3F, 1.25F);

        if (aim.entity() != null) {
            FlashAbilityCombat.hitAny(player, aim.entity(), 25.0F);
            aim.entity().setSecondsOnFire(8);
        }

        if (FlashPowers.isDestroyBlocksEnabled(player) && aim.block() != null) {
            FlashAbilityCombat.breakCone(player, look,
                    targetPoint.distanceTo(start) + 1.0D, 1.6D, 18);
            BlockPos firePos = BlockPos.containing(targetPoint).above();
            if (level.getBlockState(firePos).isAir()
                    && !level.getBlockState(firePos.below()).isAir()) {
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
            }
        }

        FlashAbilityCombat.spawnImpact(level, targetPoint, 2.5F);
        FlashAbilityCombat.playThunder(level, targetPoint);
        FlashBeamPacket.send(player, start, targetPoint, 0xFFFFC400, 16, 1.25F);
    }

    // ------------------------------------------------------------------
    // Mass punch (level 8)
    // ------------------------------------------------------------------

    private static void massChargeTick(ServerPlayer player, PlayerState state) {
        ServerLevel level = (ServerLevel) player.level();
        state.massAngle += 0.33D;
        double angle = state.massAngle;
        Vec3 center = state.massCenter;
        double x = center.x + Math.cos(angle) * MASS_CIRCLE_RADIUS;
        double z = center.z + Math.sin(angle) * MASS_CIRCLE_RADIUS;
        double y = FlashDimensionTravel.safeY(level, x, z);

        AABB target = player.getBoundingBox().move(
                x - player.getX(), y - player.getY(), z - player.getZ());
        if (level.noCollision(player, target)) {
            float yaw = (float) Math.toDegrees(angle);
            player.connection.teleport(x, y, z, yaw, 0.0F);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
        }

        // Speed trails and a visible circular preparation area.
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                4, 0.18D, 0.5D, 0.18D, 0.05D);
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.4D, player.getZ(),
                2, 0.25D, 0.1D, 0.25D, 0.02D);
        if (state.massTicks % 3 == 0) {
            for (int i = 0; i < 10; i++) {
                double a = state.massTicks * 0.12D + i * (Math.PI * 2.0D / 10.0D);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        center.x + Math.cos(a) * MASS_CIRCLE_RADIUS,
                        center.y + 0.15D,
                        center.z + Math.sin(a) * MASS_CIRCLE_RADIUS,
                        1, 0.02D, 0.02D, 0.02D, 0.01D);
            }
        }
        if (state.massTicks % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    private static void massPunch(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.scale(15.0D));

        player.swing(InteractionHand.MAIN_HAND, true);

        List<LivingEntity> targets = FlashAbilityCombat.targetsInFront(player, 15.0D, 0.45D, 32);
        for (LivingEntity target : targets) {
            FlashAbilityCombat.hit(player, target, 100.0F);
            target.setDeltaMovement(look.scale(2.0D).add(0.0D, 0.45D, 0.0D));
            target.hurtMarked = true;
        }

        FlashAbilityCombat.breakCone(player, look, 15.0D, 2.2D, 80);
        FlashAbilityCombat.spawnImpact(level, end, 4.0F);
        FlashAbilityCombat.playThunder(level, end);
        FlashBeamPacket.send(player, start, end, 0xFFFFD24A, 20, 2.2F);

        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        for (int i = -2; i <= 2; i++) {
            Vec3 point = start.add(look.scale(2.0D)).add(right.scale(i * 0.55D));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    point.x, point.y - 0.2D, point.z, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }
        player.playNotifySound(SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.8F, 0.7F);
    }

    // ------------------------------------------------------------------
    // Speed manipulation (level 9)
    // ------------------------------------------------------------------

    private static boolean grantSpeedToTarget(ServerPlayer player) {
        ServerPlayer target = null;
        HitResult hit = player.pick(12.0D, 1.0F, false);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayer other
                && other != player) {
            target = other;
        }
        if (target == null) {
            Vec3 look = player.getLookAngle();
            double best = -1.0D;
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other == player) {
                    continue;
                }
                Vec3 delta = other.position().add(0.0D, 1.0D, 0.0D)
                        .subtract(player.getEyePosition());
                double distance = delta.length();
                if (distance > 12.0D) {
                    continue;
                }
                double dot = delta.normalize().dot(look);
                if (dot > 0.75D && dot > best) {
                    best = dot;
                    target = other;
                }
            }
        }

        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("message.flash.ability_no_target"), true);
            return false;
        }
        if (FlashPowers.hasPermanentPowers(target)) {
            player.displayClientMessage(
                    Component.translatable("message.flash.ability_target_has_powers"), true);
            return false;
        }
        if (FlashPowers.isTemporary(target)) {
            player.displayClientMessage(
                    Component.translatable("message.flash.ability_target_temporary"), true);
            return false;
        }

        // Clear an expired grant before applying the new one.
        if (FlashPowers.tickTemporary(target)) {
            FlashSpeed.remove(target);
            FlashStep.remove(target);
            FlashStatusEffects.reset(target);
        }

        if (!FlashPowers.grantTemporary(target, 5, TEMPORARY_DURATION_TICKS)) {
            player.displayClientMessage(
                    Component.translatable("message.flash.ability_target_has_powers"), true);
            return false;
        }

        FlashSpeed.apply(target);
        FlashStep.apply(target);
        FlashStatusEffects.onLevelChanged(target);
        FlashSyncPacket.send(target);
        FlashVisualSyncPacket.send(target);
        target.displayClientMessage(
                Component.translatable("message.flash.ability_temp_received", player.getDisplayName()), false);
        player.displayClientMessage(
                Component.translatable("message.flash.ability_temp_granted", target.getDisplayName()), true);

        FlashBeamPacket.send(player, player.getEyePosition(),
                target.getEyePosition(), 0xFFFFC400, 12, 0.9F);
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0D, target.getZ(),
                30, 0.5D, 0.8D, 0.5D, 0.08D);
        player.playNotifySound(SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.2F, 1.5F);
        return true;
    }

    // ------------------------------------------------------------------
    // Acceleration Force portal channel
    // ------------------------------------------------------------------

    private static void portalChannelTick(ServerPlayer player, PlayerState state) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 center = player.getEyePosition().add(look.scale(3.0D));
        double angle = state.portalTicks * 0.7D;

        for (int i = 0; i < 10; i++) {
            double a = angle + i * (Math.PI * 2.0D / 10.0D);
            Vec3 point = center.add(right.scale(Math.cos(a) * 0.9D))
                    .add(0.0D, Math.sin(a) * 0.9D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z, 1,
                    0.04D, 0.04D, 0.04D, 0.02D);
        }
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z,
                6, 0.5D, 0.5D, 0.5D, 0.1D);
        if (state.portalTicks % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 1.6F);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void startHoldCooldown(ServerPlayer player, FlashAbility ability) {
        if (ability.cooldownTicks() <= 0 || NO_COOLDOWN.contains(player.getUUID())) {
            return;
        }
        cooldowns(player).put(ability, gameTime(player) + ability.cooldownTicks());
    }

    private static PlayerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
    }

    private static boolean isBusy(PlayerState state) {
        return state.barrageTicks >= 0
                || state.windTicks >= 0
                || state.phasing
                || state.tornadoTicks >= 0
                || state.lightningTicks >= 0
                || state.massTicks >= 0
                || state.portalTicks >= 0
                || state.dash != null;
    }

    private static long gameTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    private static Map<FlashAbility, Long> cooldowns(ServerPlayer player) {
        return COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
    }

    private static long cooldownEnd(ServerPlayer player, FlashAbility ability) {
        if (NO_COOLDOWN.contains(player.getUUID())) {
            return 0L;
        }
        Map<FlashAbility, Long> map = COOLDOWNS.get(player.getUUID());
        return map == null ? 0L : map.getOrDefault(ability, 0L);
    }

    private static void sendSync(ServerPlayer player, FlashAbility ability, boolean active) {
        long now = gameTime(player);
        long end = cooldownEnd(player, ability);
        int remaining = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, end - now));
        FlashAbilitySyncPacket.send(player, ability, active, remaining);
        FlashVisualSyncPacket.send(player);
    }

    static final class PlayerState {
        int barrageTicks = -1;
        int windTicks = -1;
        final Set<UUID> windDamaged = new HashSet<>();
        boolean phasing;
        int phasingTicks;
        Vec3 lastSafe = Vec3.ZERO;
        int tornadoTicks = -1;
        final List<FallingBlockEntity> debris = new java.util.ArrayList<>();
        Vec3 tornadoCenter = Vec3.ZERO;
        double tornadoAngle;
        int lightningTicks = -1;
        Vec3 lightningCenter = Vec3.ZERO;
        double lightningAngle;
        int massTicks = -1;
        Vec3 massCenter = Vec3.ZERO;
        double massAngle;
        int portalTicks = -1;
        boolean portalExit;
        FlashDashPunch.Run dash;
        FlashTimeSlow.Zone slow;
    }
}

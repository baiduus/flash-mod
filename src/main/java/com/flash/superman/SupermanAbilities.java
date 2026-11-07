package com.flash.superman;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.SupermanAnimPacket;
import com.flash.registry.ModEffects;
import com.flash.registry.ModParticles;
import com.flash.registry.ModSounds;
import com.flash.network.SupermanFxPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * What every Superman ability does (server side). Keys arrive as press /
 * release; hold abilities (heat vision, breaths) and switches (flight, super
 * speed, slow time) run every tick and drain solar energy.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SupermanAbilities {

    public static final int CHARGE_TICKS = 40;
    public static final int ULTIMATE_TICKS = 100;
    private static final double HEAT_RANGE = 64.0D;
    private static final double SLOW_RADIUS = 32.0D;
    private static final double SLOW_FACTOR = 0.05D;
    private static final String SLOW_TAG = "superman_slow_until";

    private SupermanAbilities() {
    }

    private static final class Session {
        BlockPos heatPos;
        int heatOnBlock;
        long chargeStart = -1L;
        int grabbed = -1;
        int comboTarget = -1;
        int comboTicks = -1;
        int ultTicks = -1;
        long clapAt = -1L;
        long launchAt = -1L;
        long throwAt = -1L;
        Vec3 dwellAt;
        int dwellTicks;
        final Map<UUID, Held> slowed = new HashMap<>();
        boolean slowing;
    }

    private record Held(Entity entity, Vec3 velocity, boolean noGravity) {
    }

    private record Thrown(UUID thrower, ServerLevel level, float damage, int[] ticks) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<Integer, Thrown> THROWN = new HashMap<>();

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    public static void key(ServerPlayer player, SupermanAbility ability, boolean pressed) {
        if (!SupermanPowers.has(player) || player.isSpectator()) {
            return;
        }
        if (!SupermanPowers.isSuperman(player)) {
            if (pressed) {
                player.displayClientMessage(Component.translatable("message.flash.superman_need_form"), true);
            }
            return;
        }
        if (SupermanPowers.level(player) < ability.level()) {
            if (pressed) {
                player.displayClientMessage(Component.translatable("message.flash.superman_locked", ability.level()), true);
            }
            return;
        }
        Session s = session(player);
        switch (ability.kind()) {
            case TOGGLE -> {
                if (pressed) {
                    toggle(player, s, ability);
                }
            }
            case HOLD -> hold(player, ability, pressed);
            case CHARGE -> {
                if (pressed) {
                    if (cooling(player, ability)) {
                        return;
                    }
                    s.chargeStart = player.level().getGameTime();
                    SupermanPowers.setFlag(player, SupermanPowers.F_CHARGE, true);
                    sound(player, ModSounds.CHARGE.get(), 0.9F, 1.1F);
                } else if (s.chargeStart >= 0L) {
                    float charge = Math.min(1.0F, (player.level().getGameTime() - s.chargeStart) / (float) CHARGE_TICKS);
                    s.chargeStart = -1L;
                    SupermanPowers.setFlag(player, SupermanPowers.F_CHARGE, false);
                    chargedPunch(player, charge);
                }
            }
            case PRESS -> {
                if (pressed) {
                    press(player, s, ability);
                }
            }
        }
    }

    private static boolean cooling(ServerPlayer player, SupermanAbility ability) {
        int cd = SupermanPowers.cooldown(player, ability);
        if (cd > 0) {
            player.displayClientMessage(Component.translatable("message.flash.superman_cooldown",
                    ability.displayName(), (cd + 19) / 20), true);
            return true;
        }
        return false;
    }

    private static boolean pay(ServerPlayer player, float amount) {
        if (SupermanPowers.spend(player, amount)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("message.flash.superman_no_energy"), true);
        return false;
    }

    private static void toggle(ServerPlayer player, Session s, SupermanAbility ability) {
        switch (ability) {
            case FLIGHT -> {
                boolean on = !SupermanPowers.flag(player, SupermanPowers.F_FLIGHT);
                if (on && player.isShiftKeyDown() && player.onGround()) {
                    // charged takeoff: coil down, then explode into the sky
                    if (s.launchAt < 0L) {
                        s.launchAt = player.level().getGameTime() + 22L;
                        anim(player, "charge_takeoff9");
                        sound(player, ModSounds.CHARGE.get(), 1.0F, 0.8F);
                    }
                    return;
                }
                if (on) {
                    SupermanPowers.setFlag(player, SupermanPowers.F_FLIGHT, true);
                    SupermanPowers.startFlight(player);
                    sound(player, ModSounds.TAKEOFF.get(), 1.0F, 1.0F);
                    // the takeoff pose comes from the clients (they see the flight start)
                    if (player.level() instanceof ServerLevel level) {
                        groundPuff(level, player.position(), 1.5D, 16);
                    }
                } else {
                    SupermanPowers.stopFlight(player);
                    SupermanPowers.sync(player);
                }
                player.displayClientMessage(Component.translatable(on ? "message.flash.superman_flight_on"
                        : "message.flash.superman_flight_off"), true);
            }
            case SUPER_SPEED -> {
                boolean on = !SupermanPowers.flag(player, SupermanPowers.F_SPEED);
                SupermanPowers.setFlag(player, SupermanPowers.F_SPEED, on);
                player.displayClientMessage(Component.translatable(on ? "message.flash.superman_speed_on"
                        : "message.flash.superman_speed_off"), true);
            }
            case TIME_SLOW -> {
                boolean on = !SupermanPowers.flag(player, SupermanPowers.F_SLOW);
                if (on && SupermanPowers.energy(player) <= 1.0F) {
                    pay(player, 1000.0F);
                    return;
                }
                SupermanPowers.setFlag(player, SupermanPowers.F_SLOW, on);
                if (on) {
                    s.slowing = true;
                    sound(player, SoundEvents.BEACON_DEACTIVATE, 1.6F, 0.5F);
                } else {
                    endSlow(player, s);
                }
            }
            default -> {
                // x-ray: purely visual, handled by the client
            }
        }
    }

    private static void hold(ServerPlayer player, SupermanAbility ability, boolean pressed) {
        int flag = switch (ability) {
            case HEAT_VISION -> SupermanPowers.F_HEAT;
            case FREEZE_BREATH -> SupermanPowers.F_FREEZE;
            default -> SupermanPowers.F_BREATH;
        };
        if (pressed) {
            if (SupermanPowers.energy(player) < ability.energy()) {
                pay(player, 1000.0F);
                return;
            }
            // one at a time: the eyes or the mouth
            int all = SupermanPowers.F_HEAT | SupermanPowers.F_FREEZE | SupermanPowers.F_BREATH;
            if ((SupermanPowers.flags(player) & all & ~flag) != 0) {
                return;
            }
            boolean laser = ability == SupermanAbility.HEAT_VISION && player.isShiftKeyDown();
            SupermanPowers.setFlag(player, flag | (laser ? SupermanPowers.F_LASER : 0), true);
            if (ability == SupermanAbility.HEAT_VISION) {
                sound(player, ModSounds.HEAT_START.get(), 1.0F, laser ? 0.8F : 1.0F);
            }
        } else {
            SupermanPowers.setFlag(player, flag | (ability == SupermanAbility.HEAT_VISION ? SupermanPowers.F_LASER : 0),
                    false);
            session(player).heatPos = null;
        }
    }

    private static void press(ServerPlayer player, Session s, SupermanAbility ability) {
        if (ability == SupermanAbility.GRAB && s.grabbed >= 0) {
            // the throw lands on the swing of the animation (tick 6)
            if (s.throwAt < 0L) {
                s.throwAt = player.level().getGameTime() + 5L;
                anim(player, "grab_throw");
            }
            return;
        }
        if (cooling(player, ability)) {
            return;
        }
        if (SupermanPowers.energy(player) < ability.energy() && !SupermanPowers.infiniteEnergy(player)) {
            pay(player, 1000.0F);
            return;
        }
        switch (ability) {
            case GRAB -> grab(player, s);
            case THUNDERCLAP -> {
                if (s.clapAt >= 0L || !pay(player, ability.energy())) {
                    return;
                }
                anim(player, "thunderclap");
                s.clapAt = player.level().getGameTime() + 8L;
                SupermanPowers.setCooldown(player, ability);
            }
            case SUPER_COMBO -> startCombo(player, s);
            case SOLAR_ULTIMATE -> {
                if (s.ultTicks >= 0) {
                    return;
                }
                s.ultTicks = 0;
                SupermanPowers.setFlag(player, SupermanPowers.F_ULTIMATE, true);
                sound(player, ModSounds.SOLAR_CHARGE.get(), 2.0F, 1.0F);
                player.setNoGravity(true);
                player.setDeltaMovement(0.0D, 0.9D, 0.0D);
                player.hurtMarked = true;
                SupermanPowers.setCooldown(player, ability);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (!SupermanPowers.isSuperman(player)) {
            if (s != null) {
                stopAll(player);
            }
            return;
        }
        ServerLevel level = player.serverLevel();
        int flags = SupermanPowers.flags(player);
        if ((flags & SupermanPowers.F_HEAT) != 0) {
            boolean laser = (flags & SupermanPowers.F_LASER) != 0;
            if (SupermanPowers.spend(player, SupermanAbility.HEAT_VISION.energy() * (laser ? 1.6F : 1.0F))) {
                heatVision(player, level, session(player), laser);
            } else {
                outOfEnergy(player, SupermanPowers.F_HEAT | SupermanPowers.F_LASER);
            }
        }
        if ((flags & SupermanPowers.F_FREEZE) != 0) {
            if (SupermanPowers.spend(player, SupermanAbility.FREEZE_BREATH.energy())) {
                freezeBreath(player, level);
            } else {
                outOfEnergy(player, SupermanPowers.F_FREEZE);
            }
        }
        if ((flags & SupermanPowers.F_BREATH) != 0) {
            if (SupermanPowers.spend(player, SupermanAbility.SUPER_BREATH.energy())) {
                superBreath(player, level);
            } else {
                outOfEnergy(player, SupermanPowers.F_BREATH);
            }
        }
        if ((flags & SupermanPowers.F_SPEED) != 0 && player.isSprinting() && player.onGround()
                && player.tickCount % 2 == 0) {
            level.sendParticles(ModParticles.SM_DUST.get(), player.getX(), player.getY() + 0.1D, player.getZ(),
                    1, 0.2D, 0.0D, 0.2D, 0.0D);
        }
        if (s == null) {
            return;
        }
        if (s.launchAt >= 0L && level.getGameTime() >= s.launchAt) {
            s.launchAt = -1L;
            chargedLaunch(player, level);
        }
        if ((flags & SupermanPowers.F_SLOW) != 0) {
            if (SupermanPowers.spend(player, SupermanAbility.TIME_SLOW.energy())) {
                slowTick(player, level, s);
            } else {
                SupermanPowers.setFlag(player, SupermanPowers.F_SLOW, false);
                endSlow(player, s);
                player.displayClientMessage(Component.translatable("message.flash.superman_no_energy"), true);
            }
        }
        if (s.clapAt >= 0L && level.getGameTime() >= s.clapAt) {
            s.clapAt = -1L;
            thunderclap(player, level);
        }
        if (s.grabbed >= 0) {
            holdTick(player, level, s);
        }
        if (s.throwAt >= 0L && level.getGameTime() >= s.throwAt) {
            s.throwAt = -1L;
            if (s.grabbed >= 0) {
                throwHeld(player, s);
            }
        }
        if (s.comboTicks >= 0) {
            comboTick(player, level, s);
        }
        if (s.ultTicks >= 0) {
            ultimateTick(player, level, s);
        }
    }

    private static void outOfEnergy(ServerPlayer player, int flag) {
        SupermanPowers.setFlag(player, flag, false);
        player.displayClientMessage(Component.translatable("message.flash.superman_no_energy"), true);
    }

    /** Stops everything in progress (form change, death, logout, power removed). */
    public static void stopAll(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            endSlow(player, s);
            if (s.grabbed >= 0) {
                Entity held = player.level().getEntity(s.grabbed);
                if (held != null) {
                    held.setNoGravity(false);
                }
            }
            if (s.comboTicks >= 0 || s.ultTicks >= 0) {
                player.setNoGravity(false);
            }
        }
        if (SupermanPowers.has(player) && (SupermanPowers.flags(player)
                & (SupermanPowers.TRANSIENT | SupermanPowers.F_SLOW)) != 0) {
            SupermanPowers.clearTransient(player);
            SupermanPowers.setFlag(player, SupermanPowers.F_SLOW, false);
            SupermanPowers.sync(player);
        }
    }

    // ------------------------------------------------------------------
    // Heat vision
    // ------------------------------------------------------------------

    private static void heatVision(ServerPlayer player, ServerLevel level, Session s, boolean laser) {
        HitResult hit = trace(player, HEAT_RANGE);
        float power = SupermanPowers.power(player);
        if (hit instanceof EntityHitResult eh) {
            Entity target = eh.getEntity();
            if (player.tickCount % (laser ? 3 : 4) == 0) {
                if (target instanceof LivingEntity living) {
                    living.invulnerableTime = 0;
                }
                target.hurt(level.damageSources().playerAttack(player), 2.8F * power * (laser ? 1.8F : 1.0F));
                target.setSecondsOnFire(laser ? 8 : 4);
                SupermanPowers.addXp(player, 0.1F);
            }
            Vec3 p = eh.getLocation();
            level.sendParticles(ModParticles.SM_HEAT_GLOW.get(), p.x, p.y, p.z, laser ? 2 : 1, 0.05D, 0.05D, 0.05D, 0.0D);
            sparks(level, p, laser ? 5 : 3);
            level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.15D, 0.15D, 0.15D, 0.01D);
            s.heatPos = null;
            return;
        }
        if (!(hit instanceof BlockHitResult bh) || hit.getType() == HitResult.Type.MISS) {
            s.heatPos = null;
            return;
        }
        Vec3 p = bh.getLocation();
        level.sendParticles(ModParticles.SM_HEAT_GLOW.get(), p.x, p.y, p.z, laser ? 2 : 1, 0.05D, 0.05D, 0.05D, 0.0D);
        sparks(level, p, laser ? 4 : 2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.1D, 0.1D, 0.1D, 0.01D);
        if (player.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        BlockPos pos = bh.getBlockPos();
        BlockState state = level.getBlockState(pos);
        // ice and snow melt at once
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK)) {
            level.setBlockAndUpdate(pos, state.is(Blocks.SNOW_BLOCK) ? Blocks.AIR.defaultBlockState()
                    : Blocks.WATER.defaultBlockState());
            return;
        }
        if (!SupermanPowers.destroyBlocks(player)) {
            return;
        }
        if (!pos.equals(s.heatPos)) {
            s.heatPos = pos;
            s.heatOnBlock = 0;
        }
        s.heatOnBlock++;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness >= 0.0F && !state.isAir()) {
            int need = (int) (Math.min(80.0F, 6.0F + hardness * 10.0F) / (laser ? 2.5F : 1.0F));
            if (s.heatOnBlock >= need) {
                level.destroyBlock(pos, true, player);
                s.heatOnBlock = 0;
            }
        }
        // held about two seconds on one spot it catches fire, like flint and steel (a sweep never lights anything)
        Vec3 spot = bh.getLocation();
        if (s.dwellAt == null || s.dwellAt.distanceToSqr(spot) > 0.36D) {
            s.dwellAt = spot;
            s.dwellTicks = 0;
        } else if (++s.dwellTicks >= 40) {
            s.dwellTicks = 0;
            BlockPos fire = pos.relative(bh.getDirection());
            if (level.getBlockState(fire).isAir() && BaseFireBlock.canBePlacedAt(level, fire, bh.getDirection())) {
                level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
            }
        }

    }

    // ------------------------------------------------------------------
    // Breaths
    // ------------------------------------------------------------------

    private static Vec3 mouth(ServerPlayer player) {
        return player.getEyePosition().add(player.getLookAngle().scale(0.4D)).subtract(0.0D, 0.2D, 0.0D);
    }

    private static void spray(ServerPlayer player, ServerLevel level, ParticleOptions type, int count, double speed,
                              double spread) {
        Vec3 m = mouth(player);
        Vec3 look = player.getLookAngle();
        for (int i = 0; i < count; i++) {
            Vec3 d = look.add((level.random.nextDouble() - 0.5D) * spread, (level.random.nextDouble() - 0.5D) * spread,
                    (level.random.nextDouble() - 0.5D) * spread).normalize();
            level.sendParticles(type, m.x, m.y, m.z, 0, d.x, d.y, d.z, speed * (0.6D + 0.6D * level.random.nextDouble()));
        }
    }

    private static void freezeBreath(ServerPlayer player, ServerLevel level) {
        spray(player, level, ModParticles.SM_FROST_CLOUD.get(), 5, 0.8D, 0.4D);
        spray(player, level, ModParticles.SM_SNOW.get(), 4, 0.9D, 0.5D);
        if (player.tickCount % 3 == 0) {
            spray(player, level, ModParticles.SM_FROST.get(), 1, 0.7D, 0.3D);
        }
        float power = SupermanPowers.power(player);
        for (Entity e : cone(player, 12.0D, 0.88D)) {
            e.clearFire();
            if (e instanceof LivingEntity living) {
                living.setTicksFrozen(Math.min(living.getTicksFrozen() + 6, 400));
                living.addEffect(new MobEffectInstance(ModEffects.FROZEN.get(), 60, 2, false, true, true));
                if (player.tickCount % 6 == 0) {
                    level.sendParticles(ModParticles.SM_FROST.get(), living.getX(), living.getY() + living.getBbHeight() * 0.5D,
                            living.getZ(), 1, living.getBbWidth() * 0.4D, living.getBbHeight() * 0.3D,
                            living.getBbWidth() * 0.4D, 0.0D);
                }
                if (player.tickCount % 10 == 0) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().playerAttack(player), 2.0F * power);
                }
            }
        }
        if (player.tickCount % 2 == 0) {
            march(player, level, 12, (pos, state) -> {
                if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()) {
                    level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                    return true;
                }
                if (state.getFluidState().is(FluidTags.LAVA)) {
                    level.setBlockAndUpdate(pos, state.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState());
                    return true;
                }
                if (state.getBlock() instanceof BaseFireBlock) {
                    level.removeBlock(pos, false);
                    return false;
                }
                if (!state.isAir() && level.random.nextFloat() < 0.15F) {
                    BlockPos up = pos.above();
                    if (level.getBlockState(up).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, up)) {
                        level.setBlockAndUpdate(up, Blocks.SNOW.defaultBlockState());
                    }
                }
                return !state.isAir();
            });
        }
    }

    private static void superBreath(ServerPlayer player, ServerLevel level) {
        spray(player, level, ModParticles.SM_CLOUD.get(), 5, 1.1D, 0.4D);
        if (player.tickCount % 2 == 0) {
            spray(player, level, ModParticles.SM_VAPOR.get(), 1, 0.9D, 0.3D);
        }
        Vec3 look = player.getLookAngle();
        float power = SupermanPowers.power(player);
        for (Entity e : cone(player, 16.0D, 0.88D)) {
            e.clearFire();
            double f = 0.35D + 0.03D * SupermanPowers.level(player);
            e.push(look.x * f, look.y * f + 0.04D, look.z * f);
            e.hurtMarked = true;
            if (e instanceof LivingEntity living && player.tickCount % 10 == 0) {
                living.invulnerableTime = 0;
                living.hurt(level.damageSources().playerAttack(player), 1.0F * power);
            }
        }
        if (player.tickCount % 2 == 0) {
            boolean destroy = SupermanPowers.destroyBlocks(player);
            march(player, level, 16, (pos, state) -> {
                if (state.getBlock() instanceof BaseFireBlock) {
                    level.removeBlock(pos, false);
                    return false;
                }
                // flowers, grass and the like are blown away
                if (destroy && !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
                    level.destroyBlock(pos, true, player);
                    return false;
                }
                return !state.isAir() && state.getFluidState().isEmpty();
            });
        }
    }

    private interface BlockVisitor {
        /** @return true to stop (solid block reached). */
        boolean visit(BlockPos pos, BlockState state);
    }

    /** Walks the blocks along the look, one per block, until something solid. */
    private static void march(ServerPlayer player, ServerLevel level, int range, BlockVisitor visitor) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockPos last = null;
        for (double d = 1.0D; d <= range; d += 0.5D) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            if (pos.equals(last)) {
                continue;
            }
            last = pos;
            if (visitor.visit(pos, level.getBlockState(pos))) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Charged punch
    // ------------------------------------------------------------------

    private static void chargedPunch(ServerPlayer player, float charge) {
        ServerLevel level = player.serverLevel();
        if (!pay(player, SupermanAbility.CHARGED_PUNCH.energy() + 7.0F * charge)) {
            return;
        }
        // the wind-up was held while charging: the strike starts right at the hit
        anim(player, "super_punch@4");
        float power = SupermanPowers.power(player);
        Vec3 look = player.getLookAngle();
        Entity target = null;
        HitResult hit = trace(player, 6.0D);
        if (hit instanceof EntityHitResult eh) {
            target = eh.getEntity();
        } else {
            double best = Double.MAX_VALUE;
            for (Entity e : cone(player, 4.0D, 0.8D)) {
                double d = e.distanceToSqr(player);
                if (e instanceof LivingEntity && d < best) {
                    best = d;
                    target = e;
                }
            }
        }
        if (target != null) {
            if (target instanceof LivingEntity living) {
                living.invulnerableTime = 0;
            }
            target.hurt(level.damageSources().playerAttack(player), (12.0F + 28.0F * charge) * power);
            if (charge >= 0.95F && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(ModEffects.BLEEDING.get(), 60, 0));
            }
            double push = 1.2D + 3.0D * charge;
            target.setDeltaMovement(look.x * push, 0.3D + 0.4D * charge + look.y * push * 0.5D, look.z * push);
            target.hurtMarked = true;
            Vec3 p = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 20, 0.3D, 0.3D, 0.3D, 0.4D);
            level.sendParticles(ModParticles.SM_PUFF.get(), p.x, p.y, p.z, 3, 0.2D, 0.2D, 0.2D, 0.05D);
        }
        sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.7F);
        if (charge >= 0.95F) {
            // sonic boom: a shockwave in front of the fist
            sound(player, ModSounds.PUNCH_SONIC.get(), 2.0F, 1.0F);
            Vec3 fist = player.getEyePosition().add(look.scale(1.5D));
            level.sendParticles(ParticleTypes.SONIC_BOOM, fist.x, fist.y, fist.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            for (int i = 2; i <= 8; i += 2) {
                Vec3 p = player.getEyePosition().add(look.scale(i));
                level.sendParticles(ModParticles.SM_VAPOR.get(), p.x, p.y, p.z, 3, 0.3D * i / 4, 0.3D * i / 4,
                        0.3D * i / 4, 0.05D);
            }
            fx(player, SupermanFxPacket.RING, fist, look, 1.6F);
            fx(player, SupermanFxPacket.RING, player.getEyePosition().add(look.scale(3.5D)), look, 2.6F);
            for (Entity e : cone(player, 10.0D, 0.75D)) {
                if (e == target) {
                    continue;
                }
                if (e instanceof LivingEntity living) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().playerAttack(player), 8.0F * power);
                }
                e.setDeltaMovement(look.x * 2.0D, 0.5D, look.z * 2.0D);
                e.hurtMarked = true;
            }
            if (SupermanPowers.destroyBlocks(player)) {
                for (double d = 2.0D; d <= 6.0D; d += 1.0D) {
                    BlockPos c = BlockPos.containing(player.getEyePosition().add(look.scale(d)));
                    for (BlockPos pos : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                        BlockState st = level.getBlockState(pos);
                        float h = st.getDestroySpeed(level, pos);
                        if (!st.isAir() && h >= 0.0F && h <= 2.0F && level.random.nextFloat() < 0.6F) {
                            level.destroyBlock(pos, true, player);
                        }
                    }
                }
            }
        }
        SupermanPowers.addXp(player, 1.0F);
        SupermanPowers.setCooldown(player, SupermanAbility.CHARGED_PUNCH);
    }

    // ------------------------------------------------------------------
    // Thunderclap
    // ------------------------------------------------------------------

    private static void thunderclap(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        float power = SupermanPowers.power(player);
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.8F);
        sound(player, SoundEvents.GENERIC_EXPLODE, 2.5F, 1.4F);
        for (int d = 2; d <= 30; d += 2) {
            Vec3 p = eye.add(look.scale(d));
            if (d <= 14) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            double r = d * 0.35D;
            level.sendParticles(ModParticles.SM_VAPOR.get(), p.x, p.y, p.z, 4, r, r * 0.6D, r, 0.05D);
            if (d % 4 == 0) {
                level.sendParticles(ModParticles.SM_DUST.get(), p.x, p.y - 1.0D, p.z, 3, r, 0.3D, r, 0.03D);
            }
        }
        for (int d = 3; d <= 15; d += 6) {
            fx(player, SupermanFxPacket.RING, eye.add(look.scale(d)), look, 1.5F + d * 0.35F);
        }
        for (Entity e : cone(player, 30.0D, 0.82D)) {
            double d = e.distanceTo(player);
            double fall = Math.max(0.15D, 1.0D - d / 40.0D);
            e.clearFire();
            if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                living.hurt(level.damageSources().playerAttack(player), (float) (10.0F * power * fall) + 2.0F);
            }
            double push = 0.8D + 3.5D * fall;
            e.setDeltaMovement(look.x * push, 0.6D + look.y * push * 0.4D, look.z * push);
            e.hurtMarked = true;
        }
        if (SupermanPowers.destroyBlocks(player)) {
            int flung = 0;
            for (int i = 0; i < 260 && flung < 30; i++) {
                double d = 3.0D + level.random.nextDouble() * 14.0D;
                double r = d * 0.4D;
                Vec3 p = eye.add(look.scale(d)).add((level.random.nextDouble() - 0.5D) * 2.0D * r,
                        (level.random.nextDouble() - 0.5D) * 2.0D * r, (level.random.nextDouble() - 0.5D) * 2.0D * r);
                BlockPos pos = BlockPos.containing(p);
                BlockState st = level.getBlockState(pos);
                float h = st.getDestroySpeed(level, pos);
                if (st.isAir() || h < 0.0F || h > 0.6F || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
                    continue;
                }
                if (st.canBeReplaced()) {
                    level.destroyBlock(pos, false, player);
                    continue;
                }
                FallingBlockEntity block = FallingBlockEntity.fall(level, pos, st);
                block.setDeltaMovement(look.x * 1.6D + (level.random.nextDouble() - 0.5D) * 0.4D, 0.6D,
                        look.z * 1.6D + (level.random.nextDouble() - 0.5D) * 0.4D);
                block.hurtMarked = true;
                flung++;
            }
        }
        SupermanPowers.addXp(player, 1.5F);
    }

    // ------------------------------------------------------------------
    // Grab and throw
    // ------------------------------------------------------------------

    private static boolean grabbable(Entity e) {
        return e instanceof LivingEntity && e.isAlive() && !(e instanceof EnderDragon) && !(e instanceof WitherBoss)
                && e.getBbWidth() <= 3.5F && e.getBbHeight() <= 5.0F;
    }

    private static void grab(ServerPlayer player, Session s) {
        Entity target = null;
        HitResult hit = trace(player, 6.0D);
        if (hit instanceof EntityHitResult eh && grabbable(eh.getEntity())) {
            target = eh.getEntity();
        }
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flash.superman_grab_none"), true);
            return;
        }
        s.grabbed = target.getId();
        SupermanPowers.setFlag(player, SupermanPowers.F_HOLDING, true);
        anim(player, "grab_reach");
        sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
        if (target instanceof ServerPlayer held) {
            held.displayClientMessage(Component.translatable("message.flash.superman_grabbed", player.getDisplayName()), true);
        }
    }

    private static void holdTick(ServerPlayer player, ServerLevel level, Session s) {
        Entity e = level.getEntity(s.grabbed);
        if (e == null || !e.isAlive() || e.distanceTo(player) > 24.0D) {
            release(player, s);
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dest = player.getEyePosition().add(look.scale(1.4D + e.getBbWidth()))
                .subtract(0.0D, e.getBbHeight() * 0.5D, 0.0D);
        e.fallDistance = 0.0F;
        if (e instanceof ServerPlayer held) {
            // pulled along, the camera stays free
            Vec3 delta = dest.subtract(held.position());
            if (delta.length() > 8.0D) {
                held.teleportTo(dest.x, dest.y, dest.z);
            } else {
                held.setDeltaMovement(delta.scale(0.6D));
                held.hurtMarked = true;
            }
        } else {
            e.moveTo(dest.x, dest.y, dest.z, e.getYRot(), e.getXRot());
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
        }
    }

    private static void release(ServerPlayer player, Session s) {
        s.grabbed = -1;
        SupermanPowers.setFlag(player, SupermanPowers.F_HOLDING, false);
    }

    private static void throwHeld(ServerPlayer player, Session s) {
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(s.grabbed);
        release(player, s);
        if (e == null || !e.isAlive()) {
            return;
        }
        float power = SupermanPowers.power(player);
        Vec3 look = player.getLookAngle();
        double speed = 2.6D + 0.15D * SupermanPowers.level(player);
        e.setDeltaMovement(look.scale(speed));
        e.hurtMarked = true;
        if (e instanceof LivingEntity living) {
            living.invulnerableTime = 0;
        }
        e.hurt(level.damageSources().playerAttack(player), 8.0F * power);
        e.setDeltaMovement(look.scale(speed));
        e.hurtMarked = true;
        THROWN.put(e.getId(), new Thrown(player.getUUID(), level, 10.0F * power, new int[1]));
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
        SupermanPowers.setCooldown(player, SupermanAbility.GRAB);
        SupermanPowers.addXp(player, 0.8F);
    }

    /** Whoever is held and hits Superman breaks free. */
    public static void breakFree(ServerPlayer superman, Entity attacker) {
        Session s = SESSIONS.get(superman.getUUID());
        if (s != null && s.grabbed >= 0 && attacker.getId() == s.grabbed) {
            release(superman, s);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || THROWN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Thrown>> it = THROWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Thrown> en = it.next();
            Thrown t = en.getValue();
            Entity e = t.level().getEntity(en.getKey());
            int ticks = ++t.ticks()[0];
            if (e == null || !e.isAlive() || ticks > 100) {
                it.remove();
                continue;
            }
            e.fallDistance = 0.0F;
            if (ticks > 2 && (e.horizontalCollision || e.verticalCollision || e.onGround())) {
                it.remove();
                ServerPlayer thrower = t.level().getServer().getPlayerList().getPlayer(t.thrower());
                if (e instanceof LivingEntity living) {
                    living.invulnerableTime = 0;
                }
                e.hurt(thrower != null ? t.level().damageSources().playerAttack(thrower)
                        : t.level().damageSources().generic(), t.damage());
                BlockState ground = t.level().getBlockState(e.blockPosition().below());
                if (!ground.isAir()) {
                    t.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            e.getX(), e.getY(), e.getZ(), 40, 0.6D, 0.3D, 0.6D, 0.2D);
                }
                t.level().sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.5D, e.getZ(), 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
                groundPuff(t.level(), e.position(), 1.2D, 10);
                t.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 0.9F, 1.3F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Slow time
    // ------------------------------------------------------------------

    private static void slowTick(ServerPlayer player, ServerLevel level, Session s) {
        long now = level.getGameTime();
        Vec3 center = player.position();
        double r2 = SLOW_RADIUS * SLOW_RADIUS;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(SLOW_RADIUS + 4.0D),
                e -> e != player && e.isAlive())) {
            double distance = entity.position().distanceToSqr(center);
            Held held = s.slowed.get(entity.getUUID());
            if (held != null && distance > (SLOW_RADIUS + 3.0D) * (SLOW_RADIUS + 3.0D)) {
                restore(s, held);
                continue;
            }
            if (distance > r2 && held == null) {
                continue;
            }
            if (entity instanceof ServerPlayer other) {
                other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, true, false, false));
                other.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 10, 3, true, false, false));
            } else if (entity instanceof LivingEntity living) {
                living.getPersistentData().putLong(SLOW_TAG, now + 3L);
            } else if (entity instanceof Projectile projectile) {
                if (projectile.getOwner() != player) {
                    holdSlow(s, entity, held);
                }
            } else if (entity instanceof ItemEntity || entity instanceof FallingBlockEntity) {
                holdSlow(s, entity, held);
            }
        }
        if (now % 5L == 0L) {
            for (int i = 0; i < 40; i++) {
                double a = i * (Math.PI * 2.0D / 40.0D);
                level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * SLOW_RADIUS, center.y + 0.3D,
                        center.z + Math.sin(a) * SLOW_RADIUS, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void holdSlow(Session s, Entity entity, Held held) {
        if (held == null) {
            Vec3 v = entity.getDeltaMovement();
            if (v.lengthSqr() < 1.0E-4D) {
                return;
            }
            held = new Held(entity, v, entity.isNoGravity());
            s.slowed.put(entity.getUUID(), held);
        }
        entity.setNoGravity(true);
        entity.setDeltaMovement(held.velocity().scale(SLOW_FACTOR));
        entity.hurtMarked = true;
    }

    private static void restore(Session s, Held held) {
        s.slowed.remove(held.entity().getUUID());
        if (held.entity().isAlive()) {
            held.entity().setNoGravity(held.noGravity());
            held.entity().setDeltaMovement(held.velocity());
            held.entity().hurtMarked = true;
        }
    }

    private static void endSlow(ServerPlayer player, Session s) {
        if (!s.slowing && s.slowed.isEmpty()) {
            return;
        }
        s.slowing = false;
        for (Held held : new ArrayList<>(s.slowed.values())) {
            restore(s, held);
        }
        sound(player, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.6F);
    }

    /** Creatures in the slowed zone only move one tick in eight. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        long until = entity.getPersistentData().getLong(SLOW_TAG);
        if (until > 0L) {
            long now = entity.level().getGameTime();
            if (until > now && now % 8L != 0L) {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------
    // Super combo
    // ------------------------------------------------------------------

    private static void startCombo(ServerPlayer player, Session s) {
        if (s.comboTicks >= 0) {
            return;
        }
        HitResult hit = trace(player, 24.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("message.flash.superman_combo_none"), true);
            return;
        }
        if (!pay(player, SupermanAbility.SUPER_COMBO.energy())) {
            return;
        }
        s.comboTarget = eh.getEntity().getId();
        s.comboTicks = 0;
        player.setNoGravity(true);
        SupermanPowers.setFlag(player, SupermanPowers.F_COMBO, true);
        SupermanPowers.setCooldown(player, SupermanAbility.SUPER_COMBO);
    }

    private static void comboTick(ServerPlayer player, ServerLevel level, Session s) {
        Entity e = level.getEntity(s.comboTarget);
        int t = s.comboTicks++;
        if (!(e instanceof LivingEntity target) || !target.isAlive() || t > 60) {
            endCombo(player, s);
            return;
        }
        float power = SupermanPowers.power(player);
        Vec3 tp = target.position();
        if (t == 0) {
            Vec3 flat = new Vec3(tp.x - player.getX(), 0.0D, tp.z - player.getZ());
            flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 at = tp.subtract(flat.scale(1.4D + target.getBbWidth() * 0.5D));
            face(player, at, tp.add(0.0D, target.getBbHeight() * 0.6D, 0.0D));
            hitCombo(player, level, target, 6.0F * power);
            target.setDeltaMovement(0.0D, 1.5D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.TAKEOFF.get(), 1.0F, 1.3F);
        } else if (t == 8 || t == 12 || t == 16) {
            double a = t * 1.3D;
            Vec3 at = tp.add(Math.cos(a) * (1.6D + target.getBbWidth() * 0.5D), 0.2D,
                    Math.sin(a) * (1.6D + target.getBbWidth() * 0.5D));
            face(player, at, tp.add(0.0D, target.getBbHeight() * 0.6D, 0.0D));
            hitCombo(player, level, target, 5.0F * power);
            target.setDeltaMovement(0.0D, 0.35D, 0.0D);
            target.hurtMarked = true;
        } else if (t == 22) {
            Vec3 at = tp.add(0.4D, target.getBbHeight() + 1.6D, 0.4D);
            face(player, at, tp.add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
            hitCombo(player, level, target, 8.0F * power);
            target.setDeltaMovement(0.0D, -3.2D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.DIVE.get(), 1.0F, 1.2F);
        } else if (t > 23 && (target.onGround() || target.isInWater() || t >= 45)) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), 12.0F * power);
            target.addEffect(new MobEffectInstance(ModEffects.BLEEDING.get(), 100, 1));
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, tp.x, tp.y, tp.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            groundPuff(level, tp, 2.5D, 24);
            fxAt(level, SupermanFxPacket.SHOCKWAVE, tp.add(0.0D, 0.15D, 0.0D), new Vec3(0, 1, 0), 6.0F);
            BlockState ground = level.getBlockState(target.blockPosition().below());
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), tp.x, tp.y + 0.1D, tp.z,
                        150, 2.0D, 0.3D, 2.0D, 0.3D);
            }
            level.playSound(null, tp.x, tp.y, tp.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
            for (Entity o : level.getEntities(target, target.getBoundingBox().inflate(5.0D, 2.0D, 5.0D),
                    x -> x != player && x.isAlive())) {
                if (o instanceof LivingEntity living) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().playerAttack(player), 6.0F * power);
                }
                Vec3 away = o.position().subtract(tp);
                Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(1.4D);
                o.setDeltaMovement(push.x, 0.6D, push.z);
                o.hurtMarked = true;
            }
            if (SupermanPowers.destroyBlocks(player)) {
                level.explode(player, tp.x, tp.y, tp.z, 2.5F, Level.ExplosionInteraction.TNT);
            }
            SupermanPowers.addXp(player, 2.0F);
            endCombo(player, s);
        }
    }

    private static void hitCombo(ServerPlayer player, ServerLevel level, LivingEntity target, float damage) {
        anim(player, "super_punch@4");
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), damage);
        Vec3 p = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 16, 0.3D, 0.3D, 0.3D, 0.4D);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.8F);
    }

    /** Teleports the player to {@code at} looking at {@code look}. */
    private static void face(ServerPlayer player, Vec3 at, Vec3 look) {
        Vec3 d = look.subtract(at.add(0.0D, player.getEyeHeight(), 0.0D));
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        player.connection.teleport(at.x, at.y, at.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private static void endCombo(ServerPlayer player, Session s) {
        s.comboTicks = -1;
        s.comboTarget = -1;
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        SupermanPowers.setFlag(player, SupermanPowers.F_COMBO, false);
    }

    // ------------------------------------------------------------------
    // Solar ultimate
    // ------------------------------------------------------------------

    private static void ultimateTick(ServerPlayer player, ServerLevel level, Session s) {
        int t = s.ultTicks++;
        Vec3 c = player.position().add(0.0D, 1.0D, 0.0D);
        if (t < ULTIMATE_TICKS) {
            // the sunlight gathers into the body
            for (int i = 0; i < 8; i++) {
                Vec3 d = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian())
                        .normalize();
                double r = 7.0D;
                Vec3 p = c.add(d.scale(r));
                level.sendParticles(ModParticles.SM_SPARKLE.get(), p.x, p.y, p.z, 0, -d.x, -d.y, -d.z, 0.35D);
            }
            // sunlight pours down from the sky
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0D;
                double r = 1.5D + level.random.nextDouble() * 5.0D;
                level.sendParticles(ModParticles.SM_SOLAR_RAY.get(), c.x + Math.cos(a) * r, c.y + 12.0D,
                        c.z + Math.sin(a) * r, 0, (-Math.cos(a) * r) * 0.05D, -1.0D, (-Math.sin(a) * r) * 0.05D, 0.6D);
            }
            if (t % 3 == 0) {
                level.sendParticles(ModParticles.SM_SOLAR_GLOW.get(), c.x, c.y, c.z, 1, 0.3D, 0.4D, 0.3D, 0.0D);
            }
            if (t % 20 == 0) {
                sound(player, SoundEvents.BEACON_AMBIENT, 2.0F, 0.6F + t / 100.0F);
            }
            if (t == 10) {
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }
            return;
        }
        // release: a nuclear blast of solar energy
        s.ultTicks = -1;
        player.setNoGravity(false);
        SupermanPowers.setFlag(player, SupermanPowers.F_ULTIMATE, false);
        anim(player, "solar_release");
        sound(player, ModSounds.SOLAR_ROAR.get(), 3.0F, 1.0F);
        sound(player, ModSounds.SOLAR_BEAM.get(), 3.0F, 1.0F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0F, 0.5F);
        float power = SupermanPowers.power(player);
        double radius = 36.0D;
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(radius), x -> x.isAlive())) {
            double d = e.position().distanceTo(c);
            if (d > radius) {
                continue;
            }
            double f = Math.pow(1.0D - d / radius, 1.3D);
            if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                living.hurt(level.damageSources().playerAttack(player), (float) (120.0F * power * f) + 4.0F);
                living.setSecondsOnFire(8);
            }
            Vec3 away = e.position().subtract(c);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(0, 1, 0) : away.normalize();
            double push = 1.0D + 4.0D * f;
            e.setDeltaMovement(away.x * push, 0.8D + 1.2D * f, away.z * push);
            e.hurtMarked = true;
        }
        for (int i = 0; i < 14; i++) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x + level.random.nextGaussian() * 6.0D,
                    c.y + level.random.nextGaussian() * 3.0D, c.z + level.random.nextGaussian() * 6.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 1.0D, 1.0D, 1.0D, 0.0D);
        level.sendParticles(ModParticles.SM_SPARKLE.get(), c.x, c.y, c.z, 400, 2.0D, 2.0D, 2.0D, 1.2D);
        level.sendParticles(ModParticles.SM_SOLAR_GLOW.get(), c.x, c.y, c.z, 60, 4.0D, 3.0D, 4.0D, 0.4D);
        level.sendParticles(ModParticles.SM_EMBER.get(), c.x, c.y, c.z, 200, 3.0D, 2.0D, 3.0D, 0.8D);
        fxAt(level, SupermanFxPacket.SOLAR_BLAST, c, new Vec3(0, 1, 0), (float) radius);
        if (SupermanPowers.destroyBlocks(player)) {
            // crater: a big blast under him and a ring around it
            level.explode(player, c.x, c.y - 3.0D, c.z, 16.0F, true, Level.ExplosionInteraction.TNT);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3.0D;
                level.explode(player, c.x + Math.cos(a) * 9.0D, c.y - 4.0D, c.z + Math.sin(a) * 9.0D, 9.0F,
                        Level.ExplosionInteraction.TNT);
            }
        }
        if (!SupermanPowers.infiniteEnergy(player)) {
            SupermanPowers.setEnergy(player, 0.0F);
        }
        SupermanPowers.addXp(player, 5.0F);
        SupermanPowers.sync(player);
    }

    // ------------------------------------------------------------------
    // Hero landing
    // ------------------------------------------------------------------

    public static void heroLanding(ServerPlayer player, float distance) {
        ServerLevel level = player.serverLevel();
        double radius = Math.min(7.5D, 4.5D + distance / 20.0D);
        float power = SupermanPowers.power(player);
        anim(player, distance > 20.0F ? "hero_landing9" : "landing");
        sound(player, ModSounds.LANDING.get(), 1.4F, 1.0F);
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(radius, 2.0D, radius),
                x -> x.isAlive())) {
            Vec3 away = e.position().subtract(player.position());
            double d = Math.max(0.5D, away.length());
            if (e instanceof LivingEntity living) {
                living.hurt(level.damageSources().playerAttack(player),
                        (float) ((5.0F + Math.min(15.0F, distance * 0.3F)) * power * (1.0D - Math.min(0.8D, d / (radius + 1.0D)))));
            }
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(1.5D);
            e.setDeltaMovement(push.x, 0.6D, push.z);
            e.hurtMarked = true;
        }
        BlockState ground = level.getBlockState(player.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1D,
                    player.getZ(), 120, radius / 2.0D, 0.2D, radius / 2.0D, 0.25D);
        }
        groundPuff(level, player.position(), radius * 0.6D, 30);
        fxAt(level, SupermanFxPacket.SHOCKWAVE, player.position().add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0),
                (float) radius);
    }

    // ------------------------------------------------------------------
    // Charged takeoff
    // ------------------------------------------------------------------

    private static void chargedLaunch(ServerPlayer player, ServerLevel level) {
        if (!SupermanPowers.isSuperman(player)) {
            return;
        }
        SupermanPowers.setFlag(player, SupermanPowers.F_FLIGHT, true);
        SupermanPowers.startFlight(player);
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.2D, 2.4D + 0.1D * SupermanPowers.level(player), look.z * 1.2D);
        player.hurtMarked = true;
        anim(player, "charged_launch9");
        sound(player, ModSounds.TAKEOFF.get(), 1.4F, 0.9F);
        sound(player, ModSounds.PUNCH_SONIC.get(), 1.4F, 1.2F);
        groundPuff(level, player.position(), 2.5D, 30);
        fxAt(level, SupermanFxPacket.SHOCKWAVE, player.position().add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0), 5.0F);
        fxAt(level, SupermanFxPacket.RING, player.position().add(0.0D, 1.0D, 0.0D), new Vec3(0, 1, 0), 2.2F);
        // the ground cracks under the feet
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(3.5D, 1.0D, 3.5D), x -> x.isAlive())) {
            Vec3 away = e.position().subtract(player.position());
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(1.2D);
            e.setDeltaMovement(push.x, 0.5D, push.z);
            e.hurtMarked = true;
        }
        SupermanPowers.addXp(player, 0.5F);
    }

    // ------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------

    /** Dust and puffs blown out along the ground. */
    private static void groundPuff(ServerLevel level, Vec3 at, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2.0D / count;
            double sp = 0.15D + radius * 0.06D;
            level.sendParticles(i % 2 == 0 ? ModParticles.SM_DUST.get() : ModParticles.SM_PUFF.get(),
                    at.x + Math.cos(a) * 0.5D, at.y + 0.15D, at.z + Math.sin(a) * 0.5D, 0,
                    Math.cos(a), 0.05D, Math.sin(a), sp);
        }
    }

    private static void sparks(ServerLevel level, Vec3 p, int count) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(ModParticles.SM_EMBER.get(), p.x, p.y, p.z, 0,
                    level.random.nextGaussian() * 0.6D, 0.3D + level.random.nextDouble() * 0.6D,
                    level.random.nextGaussian() * 0.6D, 0.25D);
        }
    }

    private static void fx(ServerPlayer player, int kind, Vec3 at, Vec3 dir, float scale) {
        fxAt(player.serverLevel(), kind, at, dir, scale);
    }

    private static void fxAt(ServerLevel level, int kind, Vec3 at, Vec3 dir, float scale) {
        FlashNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        at.x, at.y, at.z, 128.0D, level.dimension())),
                new SupermanFxPacket(kind, at.x, at.y, at.z, (float) dir.x, (float) dir.y, (float) dir.z, scale));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** What the player looks at: the nearest entity or block within range. */
    public static HitResult trace(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0D);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, stop, box,
                e -> !e.isSpectator() && e.isPickable() && e.isAlive(), eye.distanceToSqr(stop));
        return entity != null ? entity : block;
    }

    /** Entities inside a cone in front of the player (cos of the half angle), in line of sight. */
    private static List<Entity> cone(ServerPlayer player, double range, double cos) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<Entity> out = new ArrayList<>();
        for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(range),
                x -> x.isAlive() && !x.isSpectator())) {
            Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double d = to.length();
            if (d > range || d < 1.0E-3D || to.normalize().dot(look) < cos) {
                continue;
            }
            if (e instanceof LivingEntity && !player.hasLineOfSight(e)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, pitch);
    }

    /** One-shot animation for everyone around. */
    public static void anim(ServerPlayer player, String name) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SupermanAnimPacket(player.getUUID(), name));
    }
}

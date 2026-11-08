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
    /** Blast radius: half of the pack's 50-block sphere. */
    public static final double ULTIMATE_RADIUS = 25.0D;

    /** Sphere carved shell by shell (about two blocks of radius per tick). */
    private record Crater(ServerLevel level, BlockPos center, int radius, int[] shell) {
    }

    private static final List<Crater> CRATERS = new ArrayList<>();

    private static void craterTick() {
        Iterator<Crater> it = CRATERS.iterator();
        while (it.hasNext()) {
            Crater c = it.next();
            int r0 = c.shell()[0];
            int r1 = Math.min(c.radius(), r0 + 2);
            c.shell()[0] = r1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            double in2 = (double) r0 * r0;
            double out2 = (double) r1 * r1 + 0.5D;
            for (int dx = -r1; dx <= r1; dx++) {
                for (int dy = -r1; dy <= r1; dy++) {
                    for (int dz = -r1; dz <= r1; dz++) {
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > out2 || (r0 > 0 && d2 <= in2)) {
                            continue;
                        }
                        pos.set(c.center().getX() + dx, c.center().getY() + dy, c.center().getZ() + dz);
                        BlockState st = c.level().getBlockState(pos);
                        if (st.isAir() || st.getDestroySpeed(c.level(), pos) < 0.0F) {
                            continue;
                        }
                        // the rim is ragged and scorched
                        double edge = Math.sqrt(d2) / c.radius();
                        if (edge > 0.85D && c.level().random.nextFloat() < 0.35F) {
                            if (c.level().random.nextFloat() < 0.3F) {
                                c.level().setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                            }
                            continue;
                        }
                        c.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            if (r1 >= c.radius()) {
                it.remove();
            }
        }
    }
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
        int comboMove;
        Vec3 comboOrigin;
        Vec3 comboStart;
        float comboYaw;
        double comboAlt;
        double comboImpactY;
        int comboMark = -1;
        int ultTicks = -1;
        long clapAt = -1L;
        long launchAt = -1L;
        long throwAt = -1L;
        long grabFrom = -1L;
        float throwCharge;
        Vec3 dwellAt;
        int dwellTicks;
        final Map<UUID, Held> slowed = new HashMap<>();
        boolean slowing;
    }

    private record Held(Entity entity, Vec3 velocity, boolean noGravity) {
    }

    private record Thrown(UUID thrower, ServerLevel level, float damage, int[] ticks, boolean explode) {
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
        if (ability == SupermanAbility.GRAB) {
            grabKey(player, s, pressed);
            return;
        }
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
                // the key flies / lands; double jump still flies whenever (flight stays available)
                boolean on = !player.getAbilities().flying;
                if (on) {
                    SupermanPowers.setFlag(player, SupermanPowers.F_FLIGHT, true);
                    SupermanPowers.startFlight(player);
                    sound(player, ModSounds.TAKEOFF.get(), 1.0F, 1.0F);
                    // the takeoff pose comes from the clients (they see the flight start)
                    if (player.level() instanceof ServerLevel level) {
                        groundPuff(level, player.position(), 1.5D, 16);
                    }
                } else {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
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
        if ((flags & SupermanPowers.F_HEAT) != 0 && (flags & SupermanPowers.F_COMBO) == 0) {
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
                target.hurt(level.damageSources().playerAttack(player), 6.0F * power * (laser ? 1.8F : 1.0F));
                target.setSecondsOnFire(laser ? 8 : 4);
                SupermanPowers.addXp(player, 0.1F);
                if (target instanceof LivingEntity living) {
                    SupermanCut.onLaserHit(player, living, eh.getLocation(), laser);
                }
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
                // cold enough: frozen solid in a block of ice (can't move)
                int amp = living.getTicksFrozen() >= 100 ? 4 : 2;
                living.addEffect(new MobEffectInstance(ModEffects.FROZEN.get(), 60, amp, false, true, true));
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
        // the Super Punch of Man of Tomorrow on the release (the client of the puncher already started it)
        anim(player, "super_punch");
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
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
                    living.hurt(level.damageSources().playerAttack(player), 12.0F * power);
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
                living.hurt(level.damageSources().playerAttack(player), (float) (28.0F * power * fall) + 2.0F);
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

    /** Time to fill the throw bar while the grab key is held. */
    public static final int THROW_CHARGE_TICKS = 50;
    /** A press shorter than this with someone in hand just lets go. */
    public static final int THROW_TAP_TICKS = 5;

    /**
     * J: a press grabs the creature / player in sight; with it in hand, a tap lets it go, while holding J fills the
     * throw bar and the release throws it with that force. Thrown at 100 % it blows up when it hits the ground.
     */
    private static void grabKey(ServerPlayer player, Session s, boolean pressed) {
        long now = player.level().getGameTime();
        if (pressed) {
            if (s.throwAt >= 0L) {
                return;
            }
            if (s.grabbed >= 0) {
                // second press: a tap lets go, holding charges the throw
                s.grabFrom = now;
                return;
            }
            if (cooling(player, SupermanAbility.GRAB)) {
                return;
            }
            // first press: grab (the release of this press does nothing)
            grab(player, s);
            s.grabFrom = -1L;
            return;
        }
        if (s.grabbed >= 0 && s.grabFrom >= 0L && s.throwAt < 0L) {
            long held = now - s.grabFrom;
            s.grabFrom = -1L;
            if (held < THROW_TAP_TICKS) {
                // just drops it
                Entity e = player.serverLevel().getEntity(s.grabbed);
                release(player, s);
                if (e != null) {
                    e.setDeltaMovement(Vec3.ZERO);
                    e.hurtMarked = true;
                }
                return;
            }
            s.throwCharge = Math.min(1.0F, (held - THROW_TAP_TICKS) / (float) THROW_CHARGE_TICKS);
            // the throw lands on the swing of the animation (tick 6)
            s.throwAt = now + 5L;
            anim(player, "grab_throw");
        }
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
        float charge = s.throwCharge;
        boolean full = charge >= 0.999F;
        Vec3 look = player.getLookAngle();
        // the bar sets the force: a soft toss at 0 %, the full throw at 100 %
        double speed = (0.8D + 1.8D * charge) * (1.0D + 0.06D * SupermanPowers.level(player));
        if (e instanceof LivingEntity living) {
            living.invulnerableTime = 0;
        }
        // no damage on the throw itself: it all comes when the target hits something
        e.setDeltaMovement(look.scale(speed).add(0.0D, 0.2D, 0.0D));
        e.hurtMarked = true;
        THROWN.put(e.getId(), new Thrown(player.getUUID(), level, (12.0F + 24.0F * charge) * power, new int[1], full));
        s.throwCharge = 0.0F;
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
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!CRATERS.isEmpty()) {
            craterTick();
        }
        if (THROWN.isEmpty()) {
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
                if (t.explode()) {
                    // thrown with the bar full: it blows up on impact (about a creeper, not more)
                    boolean destroy = thrower != null && SupermanPowers.destroyBlocks(thrower);
                    t.level().explode(thrower, e.getX(), e.getY() + 0.3D, e.getZ(), 2.5F,
                            destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                    fxAt(t.level(), SupermanFxPacket.SHOCKWAVE, e.position().add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0),
                            4.0F);
                }
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
        int move = SupermanPowers.combo(player);
        HitResult hit = trace(player, SupermanCombos.finisher(move) ? 6.0D : 24.0D);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity)) {
            player.displayClientMessage(Component.translatable("message.flash.superman_combo_none"), true);
            return;
        }
        if (!pay(player, SupermanAbility.SUPER_COMBO.energy())) {
            return;
        }
        s.comboTarget = eh.getEntity().getId();
        s.comboTicks = 0;
        s.comboMove = move;
        s.comboMark = -1;
        player.setNoGravity(true);
        SupermanPowers.setFlag(player, SupermanPowers.F_COMBO, true);
        SupermanPowers.setCooldown(player, SupermanAbility.SUPER_COMBO);
    }

    private static void comboTick(ServerPlayer player, ServerLevel level, Session s) {
        switch (s.comboMove) {
            case SupermanCombos.UPLIFT -> upliftTick(player, level, s, false);
            case SupermanCombos.UPLIFT_COMBO -> upliftTick(player, level, s, true);
            case SupermanCombos.CHOKEHOLD -> finisherTick(player, level, s, false);
            case SupermanCombos.EYE_PIERCE -> finisherTick(player, level, s, true);
            default -> superComboTick(player, level, s);
        }
    }

    // ------------------------------------------------------------------
    // Uplift / Uplift Combo
    // ------------------------------------------------------------------

    /** Dash to the target and look at it; true on the tick of arrival. */
    private static boolean dashTo(ServerPlayer player, LivingEntity target, int t, int arrive) {
        Vec3 tp = target.position();
        double reach = 1.3D + target.getBbWidth() * 0.5D;
        Vec3 flat = new Vec3(tp.x - player.getX(), 0.0D, tp.z - player.getZ());
        flat = flat.lengthSqr() < 1.0E-4D ? player.getLookAngle().multiply(1, 0, 1) : flat.normalize();
        Vec3 at = tp.subtract(flat.scale(reach));
        Vec3 from = player.position();
        face(player, from.add(at.subtract(from).scale(t >= arrive - 1 ? 1.0D : 0.4D)),
                tp.add(0.0D, target.getBbHeight() * 0.6D, 0.0D));
        return t == arrive;
    }

    private static void laserOn(ServerPlayer player, boolean on) {
        SupermanPowers.setFlag(player, SupermanPowers.F_HEAT | SupermanPowers.F_LASER, on);
    }

    /**
     * Uplift: dash, uppercut that throws the target up, then a Laser Blast on it (15 + 30 in the original).
     * Uplift Combo: uppercut high, fly up past it, smash it down with both fists, then hover over the crater and burn
     * it with the Laser Blast until it blows up (50 in the original).
     */
    private static void upliftTick(ServerPlayer player, ServerLevel level, Session s, boolean full) {
        Entity e = level.getEntity(s.comboTarget);
        int t = s.comboTicks++;
        if (!(e instanceof LivingEntity target) || t > 200 || (!target.isAlive() && s.comboMark < 0)) {
            laserOn(player, false);
            endCombo(player, s);
            return;
        }
        float power = SupermanPowers.power(player);
        if (t == 0) {
            sound(player, ModSounds.TAKEOFF.get(), 1.0F, 1.4F);
        }
        if (t <= 6) {
            if (dashTo(player, target, t, 6)) {
                anim(player, "super_punch");
            }
            return;
        }
        if (t == 8) {
            hitCombo(player, level, target, (full ? 20.0F : 18.0F) * power);
            target.setDeltaMovement(0.0D, full ? 2.1D : 1.25D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.PUNCH_SONIC.get(), 1.0F, 1.2F);
            return;
        }
        if (!full) {
            // Uplift: hover in place and blast the rising target
            if (t >= 12 && t < 42) {
                if (t == 12) {
                    laserOn(player, true);
                    sound(player, ModSounds.HEAT_START.get(), 1.0F, 0.8F);
                }
                face(player, player.position(), target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
                if (t > 20) {
                    target.setDeltaMovement(target.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
                    target.hurtMarked = true;
                    target.fallDistance = 0.0F;
                }
                if (t % 2 == 0) {
                    laserHit(player, level, target, 1.5F * power);
                }
            } else if (t >= 42) {
                laserOn(player, false);
                SupermanPowers.addXp(player, 1.5F);
                endCombo(player, s);
            }
            return;
        }
        // Uplift Combo
        if (t > 8 && t <= 30) {
            Vec3 above = target.position().add(0.6D, target.getBbHeight() + 2.0D, 0.6D);
            Vec3 from = player.position();
            face(player, from.add(above.subtract(from).scale(0.35D)),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
            if (t > 20) {
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
                target.hurtMarked = true;
                target.fallDistance = 0.0F;
            }
            return;
        }
        if (t == 31) {
            anim(player, "super_combo@112");
            return;
        }
        if (t == 36) {
            hitCombo(player, level, target, 20.0F * power);
            target.setDeltaMovement(0.0D, -3.4D, 0.0D);
            target.hurtMarked = true;
            sound(player, ModSounds.DIVE.get(), 1.0F, 1.2F);
            return;
        }
        if (t > 36 && s.comboMark < 0) {
            if (target.onGround() || target.isInWater() || t >= 70) {
                s.comboMark = t;
                crash(player, level, target, 24.0F * power);
                Vec3 hover = target.position().add(0.0D, 5.0D, 0.0D);
                face(player, hover, target.position());
                laserOn(player, true);
                sound(player, ModSounds.HEAT_START.get(), 1.0F, 0.8F);
            }
            return;
        }
        if (s.comboMark >= 0) {
            int k = t - s.comboMark;
            Vec3 tp = target.position();
            face(player, tp.add(0.0D, 5.0D, 0.0D), tp.add(0.0D, target.getBbHeight() * 0.4D, 0.0D));
            if (k % 2 == 0 && target.isAlive()) {
                laserHit(player, level, target, 1.4F * power);
            }
            if (k >= 30 || !target.isAlive()) {
                // it blows up
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, tp.x, tp.y + 0.5D, tp.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, tp.x, tp.y + 0.5D, tp.z, 60, 0.6D, 0.6D, 0.6D, 0.2D);
                level.playSound(null, tp.x, tp.y, tp.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 1.1F);
                if (target.isAlive()) {
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().playerAttack(player), 16.0F * power);
                }
                laserOn(player, false);
                SupermanPowers.addXp(player, 2.5F);
                endCombo(player, s);
            }
        }
    }

    private static void laserHit(ServerPlayer player, ServerLevel level, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), damage);
        target.setSecondsOnFire(6);
        Vec3 hit = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        level.sendParticles(ModParticles.SM_HEAT_GLOW.get(), hit.x, hit.y, hit.z, 2, 0.1D, 0.1D, 0.1D, 0.0D);
        sparks(level, hit, 4);
        SupermanCut.onLaserHit(player, target, hit, true);
    }

    /** The target slammed into the ground: crater, shockwave, cracks. */
    private static void crash(ServerPlayer player, ServerLevel level, LivingEntity target, float damage) {
        Vec3 tp = target.position();
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), damage);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, tp.x, tp.y, tp.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        groundPuff(level, tp, 2.5D, 24);
        fxAt(level, SupermanFxPacket.SHOCKWAVE, tp.add(0.0D, 0.15D, 0.0D), new Vec3(0, 1, 0), 6.0F);
        cracks(level, tp, 4.0F);
        level.playSound(null, tp.x, tp.y, tp.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
        if (SupermanPowers.destroyBlocks(player)) {
            level.explode(player, tp.x, tp.y, tp.z, 2.5F, Level.ExplosionInteraction.TNT);
        }
    }

    // ------------------------------------------------------------------
    // Finishers: Chokehold and Eye Pierce
    // ------------------------------------------------------------------

    /**
     * Superman lifts the target by the neck; Chokehold squeezes and snaps, Eye Pierce burns through the eyes from
     * point blank. A common creature dies, a boss takes 40 and a player 12 (as in the original).
     */
    private static void finisherTick(ServerPlayer player, ServerLevel level, Session s, boolean eyes) {
        Entity e = level.getEntity(s.comboTarget);
        int t = s.comboTicks++;
        if (!(e instanceof LivingEntity target) || !target.isAlive() || t > 80) {
            laserOn(player, false);
            SupermanPowers.setFlag(player, SupermanPowers.F_HOLDING, false);
            endCombo(player, s);
            return;
        }
        if (t == 0) {
            anim(player, "grab_reach");
            SupermanPowers.setFlag(player, SupermanPowers.F_HOLDING, true);
            sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
        }
        // held up in front of Superman, the neck at his hand
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        look = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 hold = player.position().add(look.scale(0.9D + target.getBbWidth() * 0.5D))
                .add(0.0D, player.getEyeHeight() + 0.35D - target.getBbHeight() * 0.85D, 0.0D);
        target.teleportTo(hold.x, hold.y, hold.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.fallDistance = 0.0F;
        Vec3 head = hold.add(0.0D, target.getEyeHeight(), 0.0D);
        face(player, player.position(), head);
        int end = eyes ? 46 : 40;
        if (!eyes) {
            if (t % 8 == 4) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, head.x, head.y, head.z, 3, 0.2D, 0.2D, 0.2D, 0.1D);
                level.playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 0.6F);
            }
        } else if (t >= 14 && t < end) {
            if (t == 14) {
                laserOn(player, true);
                sound(player, ModSounds.HEAT_START.get(), 1.0F, 1.0F);
            }
            level.sendParticles(ModParticles.SM_HEAT_GLOW.get(), head.x, head.y, head.z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
            level.sendParticles(ParticleTypes.SMOKE, head.x, head.y, head.z, 2, 0.1D, 0.1D, 0.1D, 0.01D);
            target.setSecondsOnFire(4);
        }
        if (t == end) {
            laserOn(player, false);
            boolean boss = target.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
            float damage = boss ? 40.0F : target instanceof net.minecraft.world.entity.player.Player ? 12.0F
                    : Float.MAX_VALUE;
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), damage);
            if (eyes) {
                level.sendParticles(ParticleTypes.FLAME, head.x, head.y, head.z, 30, 0.2D, 0.2D, 0.2D, 0.1D);
                level.playSound(null, head.x, head.y, head.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.4F);
            } else {
                level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 20, 0.2D, 0.2D, 0.2D, 0.3D);
                level.playSound(null, head.x, head.y, head.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.2F, 0.5F);
                level.playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.6F);
            }
            SupermanPowers.setFlag(player, SupermanPowers.F_HOLDING, false);
            anim(player, "grab_throw");
            SupermanPowers.addXp(player, 2.0F);
            endCombo(player, s);
        }
    }

    // ------------------------------------------------------------------
    // Super Combo: the choreography of Man of Tomorrow (Combo24Math)
    // ------------------------------------------------------------------

    private static double smooth(double t, int a, int b) {
        double v = Math.max(0.0D, Math.min(1.0D, (t - a) / (double) (b - a)));
        return v * v * (3.0D - 2.0D * v);
    }

    private static double throwProgress(double t) {
        return Math.pow(Math.max(0.0D, Math.min(1.0D, (t - 101.0D) / 16.0D)), 1.55D);
    }

    private static Vec3 comboForward(float yaw) {
        double a = Math.toRadians(yaw);
        return new Vec3(-Math.sin(a), 0.0D, Math.cos(a));
    }

    private static Vec3 comboLocal(Vec3 o, float yaw, double y, double travel, double side) {
        Vec3 f = comboForward(yaw);
        return new Vec3(o.x + f.x * travel + f.z * side, y, o.z + f.z * travel - f.x * side);
    }

    /** Where the target is: uppercut up to the altitude, two punches of 50 blocks each, then dragged down. */
    private static Vec3 comboTargetAt(double t, Vec3 o, float yaw, double alt, double impactY) {
        double height = o.y + alt * smooth(t, 12, 28);
        if (t >= 101.0D) {
            height = o.y + alt + (impactY - o.y - alt) * throwProgress(t);
        }
        double travel = 50.0D * (smooth(t, 40, 58) + smooth(t, 69, 87)) - 48.0D * throwProgress(t);
        return comboLocal(o, yaw, height, travel, 0.0D);
    }

    /** Where Superman is: in front of the target, chasing it after each punch, then holding its head in the dive. */
    private static Vec3 comboCasterAt(double t, Vec3 o, float yaw, double alt, double impactY, double eye,
                                      double tall, Vec3 target) {
        double scene = t >= 28.0D ? 1.0D : 0.0D;
        double lunge = 0.37D * smooth(t, 8, 12) * (1.0D - smooth(t, 14, 20))
                + 0.29D * smooth(t, 36, 40) * (1.0D - smooth(t, 42, 48))
                + 0.29D * smooth(t, 66, 69) * (1.0D - smooth(t, 71, 77));
        double first = smooth(t, 58, 66);
        double second = smooth(t, 87, 89);
        double travel = -0.9D + 0.08D * scene + 50.0D * (first + second) + 1.52D * second + lunge;
        double height = o.y + alt * scene + (eye - 1.22D) * smooth(t, 87, 95);
        double side = 0.0D;
        if (t >= 89.0D) {
            travel = 100.7D;
        }
        if (t >= 95.0D && t < 117.0D) {
            // the dive: Superman behind the target's head, pushing it down to the ground
            Vec3 f = comboForward(yaw);
            Vec3 held = target.add(f.scale(1.1D)).add(0.0D, tall * 0.35D, 0.0D);
            return new Vec3(held.x, Math.max(impactY + 0.005D, held.y), held.z);
        }
        if (t >= 117.0D) {
            double recover = smooth(t, 117, 135);
            height = impactY + 0.005D * (1.0D - recover);
            double begin = 52.0D - (eye - tall * 0.5D) - 0.39D * Math.sin(-1.1344640137963142D);
            travel = begin + (53.45D - begin) * recover;
            side = -0.6D * (1.0D - recover);
        }
        return comboLocal(o, yaw, height, travel, side);
    }

    /**
     * Super Combo of Man of Tomorrow (135 ticks, on the pack's combo animation): uppercut that throws the target high
     * up (12), Superman jumps after it (28), punch that sends it 50 blocks away (40), he flies after it (58-66),
     * second punch, 50 more (69), he catches up and turns around (87-89), grabs it by the head (89-95) and drags it
     * diving into the ground (101-117): crater. Beats 20 / 20 / 20 / 25 in the original, scaled by the level.
     */
    private static void superComboTick(ServerPlayer player, ServerLevel level, Session s) {
        Entity e = level.getEntity(s.comboTarget);
        int t = s.comboTicks++;
        if (!(e instanceof LivingEntity target) || (!target.isAlive() && t < 117) || t > 136) {
            if (e instanceof LivingEntity dead) {
                dead.setNoGravity(false);
            }
            endCombo(player, s);
            return;
        }
        float power = SupermanPowers.power(player);
        if (t == 0) {
            s.comboOrigin = target.position();
            s.comboStart = player.position();
            Vec3 d = target.position().subtract(player.position());
            s.comboYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0D);
            // as high as the sky allows (64 blocks, at least 12)
            double alt = 64.0D;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int k = 2; k <= 64; k++) {
                m.set(target.getX(), target.getY() + k + target.getBbHeight(), target.getZ());
                if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                    alt = Math.max(12.0D, k - 3.0D);
                    break;
                }
            }
            s.comboAlt = Math.min(alt, level.getMaxBuildHeight() - 4 - target.getY());
            Vec3 end = comboLocal(s.comboOrigin, s.comboYaw, 0.0D, 52.0D, 0.0D);
            BlockPos ep = BlockPos.containing(end.x, 0, end.z);
            level.getChunk(ep);
            s.comboImpactY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    ep.getX(), ep.getZ());
            target.setNoGravity(true);
            anim(player, "super_combo");
            sound(player, ModSounds.TAKEOFF.get(), 1.0F, 1.3F);
        }
        Vec3 o = s.comboOrigin;
        float yaw = s.comboYaw;
        Vec3 tp = comboTargetAt(t, o, yaw, s.comboAlt, s.comboImpactY);
        if (t >= 117) {
            tp = new Vec3(tp.x, s.comboImpactY, tp.z);
        }
        // the target follows the path
        target.teleportTo(tp.x, tp.y, tp.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.fallDistance = 0.0F;
        Vec3 cp = comboCasterAt(t, o, yaw, s.comboAlt, s.comboImpactY, target.getEyeHeight(), target.getBbHeight(), tp);
        if (t < 8) {
            cp = s.comboStart.add(cp.subtract(s.comboStart).scale(smooth(t, 0, 8)));
        }
        face(player, cp, tp.add(0.0D, target.getBbHeight() * (t >= 95 ? 0.85D : 0.6D), 0.0D));
        // trails while flying
        if ((t > 40 && t < 58) || (t > 69 && t < 87) || (t > 101 && t < 117)) {
            level.sendParticles(ParticleTypes.CLOUD, tp.x, tp.y + target.getBbHeight() * 0.5D, tp.z, 3, 0.2D, 0.2D,
                    0.2D, 0.02D);
        }
        if (t == 12 || t == 40 || t == 69) {
            hitCombo(player, level, target, 20.0F * power);
            fxAt(level, SupermanFxPacket.RING, tp.add(0.0D, target.getBbHeight() * 0.5D, 0.0D),
                    t == 12 ? new Vec3(0, 1, 0) : comboForward(yaw), 2.0F);
            sound(player, ModSounds.PUNCH_SONIC.get(), 1.2F, t == 12 ? 1.2F : 1.0F);
        } else if (t == 28 || t == 58 || t == 87) {
            sound(player, ModSounds.TAKEOFF.get(), 1.0F, 1.4F);
        } else if (t == 101) {
            sound(player, ModSounds.DIVE.get(), 1.2F, 1.1F);
        } else if (t == 117) {
            target.setNoGravity(false);
            crash(player, level, target, 25.0F * power);
            target.addEffect(new MobEffectInstance(ModEffects.BLEEDING.get(), 100, 1));
            BlockState ground = level.getBlockState(BlockPos.containing(tp).below());
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), tp.x, tp.y + 0.1D, tp.z,
                        150, 2.0D, 0.3D, 2.0D, 0.3D);
            }
            for (Entity other : level.getEntities(target, target.getBoundingBox().inflate(5.0D, 2.0D, 5.0D),
                    x -> x != player && x.isAlive())) {
                if (other instanceof LivingEntity living) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().playerAttack(player), 6.0F * power);
                }
                Vec3 away = other.position().subtract(tp);
                Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(1.4D);
                other.setDeltaMovement(push.x, 0.6D, push.z);
                other.hurtMarked = true;
            }
            SupermanPowers.addXp(player, 2.0F);
        } else if (t >= 135) {
            target.setNoGravity(false);
            endCombo(player, s);
        }
    }

    private static void hitCombo(ServerPlayer player, ServerLevel level, LivingEntity target, float damage) {
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
        if (s.comboTarget >= 0 && player.serverLevel().getEntity(s.comboTarget) instanceof LivingEntity held
                && s.comboMove == SupermanCombos.SUPER_COMBO) {
            held.setNoGravity(false);
        }
        s.comboTicks = -1;
        s.comboTarget = -1;
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        SupermanPowers.setFlag(player, SupermanPowers.F_COMBO, false);
        if (s.comboMove != SupermanCombos.SUPER_COMBO) {
            SupermanPowers.setFlag(player, SupermanPowers.F_HEAT | SupermanPowers.F_LASER, false);
            if (s.grabbed < 0) {
                SupermanPowers.setFlag(player, SupermanPowers.F_HOLDING, false);
            }
        }
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
        double radius = ULTIMATE_RADIUS;
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(radius), x -> x.isAlive())) {
            double d = e.position().distanceTo(c);
            if (d > radius) {
                continue;
            }
            double f = Math.pow(1.0D - d / radius, 1.3D);
            if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                // the pack: 100 on creatures, 25 on players (here a little more near the centre)
                float base = living instanceof net.minecraft.world.entity.player.Player ? 15.0F : 50.0F;
                living.hurt(level.damageSources().playerAttack(player), (float) (base * power * (0.6D + 0.4D * f)));
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
            // crater: a sphere of the blast radius carved shell by shell over the next ticks
            CRATERS.add(new Crater(level, BlockPos.containing(c), (int) radius, new int[] {0}));
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
        anim(player, "hero_landing9");
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
        cracks(level, player.position(), (float) radius);
        fxAt(level, SupermanFxPacket.SHOCKWAVE, player.position().add(0.0D, 0.1D, 0.0D), new Vec3(0, 1, 0),
                (float) radius);
    }

    // ------------------------------------------------------------------
    // Charged takeoff
    // ------------------------------------------------------------------

    /**
     * Charged takeoff (jump held on the ground): phase 0 = the crouch starts, 1 = launch (~10 blocks straight up,
     * the ground cracks; the client switches to flight at the top), 2 = cancelled.
     */
    public static void launch(ServerPlayer player, int phase) {
        if (!SupermanPowers.isSuperman(player) || player.isSpectator()) {
            return;
        }
        Session s = session(player);
        long now = player.level().getGameTime();
        if (phase == 0) {
            if (now - s.launchAt < 20L) {
                return;
            }
            anim(player, "charge_takeoff9");
            sound(player, ModSounds.CHARGE.get(), 1.0F, 0.8F);
        } else if (phase == 1) {
            if (now - s.launchAt < 20L) {
                return;
            }
            s.launchAt = now;
            chargedLaunch(player, player.serverLevel());
        } else {
            anim(player, "none");
        }
    }

    private static void chargedLaunch(ServerPlayer player, ServerLevel level) {
        if (!SupermanPowers.isSuperman(player)) {
            return;
        }
        // flight switched on (mayfly) but not flying yet: the jump carries him up, the client hovers at the top
        SupermanPowers.setFlag(player, SupermanPowers.F_FLIGHT, true);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(player.getDeltaMovement().x, 2.0D, player.getDeltaMovement().z);
        player.hurtMarked = true;
        cracks(level, player.position(), 3.5F);
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
    public static void groundPuff(ServerLevel level, Vec3 at, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2.0D / count;
            double sp = 0.15D + radius * 0.06D;
            level.sendParticles(i % 2 == 0 ? ModParticles.SM_DUST.get() : ModParticles.SM_PUFF.get(),
                    at.x + Math.cos(a) * 0.5D, at.y + 0.15D, at.z + Math.sin(a) * 0.5D, 0,
                    Math.cos(a), 0.05D, Math.sin(a), sp);
        }
    }

    /** Cracks spreading on the ground (drawn by the clients). */
    public static void cracks(ServerLevel level, Vec3 at, float radius) {
        fxAt(level, SupermanFxPacket.CRACKS, at, new Vec3(0, 1, 0), radius);
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

    public static void fxAt(ServerLevel level, int kind, Vec3 at, Vec3 dir, float scale) {
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

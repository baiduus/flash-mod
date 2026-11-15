package com.flash.infinity;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Time Stone (the original uses Chrono API; here the mod does it itself): the flow of time (the whole server
 * faster or slower, 2 to 40 ticks a second, its user moving at their own pace), time frozen (everything stands still
 * but those immune to time: its user walks on water), projectiles stopped in mid-air, setting the time of day.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class TimeStone {

    private static volatile long msPerTick = 50L;
    private static UUID flowOwner;
    private static UUID freezeOwner;
    private static Boolean savedDaylight;
    private static Integer savedRandomTick;

    private TimeStone() {
    }

    public static long msPerTick() {
        return msPerTick;
    }

    /** Ticks a second at a step: each step below 0 adds 4 (−5: 40), above 0 takes 4 away (5 or more: 2). */
    static int tps(int step) {
        return step >= 5 ? 2 : Math.max(2, 20 - 4 * step);
    }

    // ------------------------------------------------------------------
    // The flow of time
    // ------------------------------------------------------------------

    static void flow(ServerPlayer p, InfPowers.Ability a, boolean on, boolean was) {
        if (on && !was) {
            flowOwner = p.getUUID();
            apply(p, step(p));
        } else if (!on && was && p.getUUID().equals(flowOwner)) {
            flowOwner = null;
            msPerTick = 50L;
            InfinityServer.setScore(p, "TimeFlow", 6);
        }
        if (on && step(p) > 0 && p.tickCount % 20 == 0) {
            // keeping up with the slowed world
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, step(p) / 2, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, step(p) / 2, false, false));
        }
    }

    private static int step(ServerPlayer p) {
        return p.getPersistentData().getInt("flash_time_step");
    }

    private static void apply(ServerPlayer p, int step) {
        msPerTick = Math.max(25L, 1000L / tps(step));
        InfinityServer.setScore(p, "TimeFlow", step + 6);
        p.level().playSound(null, p.blockPosition(), InfinitySounds.get("time_stone"), SoundSource.PLAYERS, 0.6F,
                1.0F - step * 0.08F);
    }

    static void flowChange(ServerPlayer p, InfPowers.Ability a) {
        int s = Math.max(-5, Math.min(5, step(p) + a.json.get("amount").getAsInt()));
        p.getPersistentData().putInt("flash_time_step", s);
        if (p.getUUID().equals(flowOwner)) {
            apply(p, s);
        }
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flash.infinity.tps",
                tps(s)), true);
    }

    // ------------------------------------------------------------------
    // Time frozen
    // ------------------------------------------------------------------

    public static boolean frozen() {
        return freezeOwner != null;
    }

    static void freeze(ServerPlayer p, boolean on, boolean was) {
        MinecraftServer server = p.server;
        if (on && !was) {
            freezeOwner = p.getUUID();
            GameRules rules = server.getGameRules();
            if (savedDaylight == null) {
                savedDaylight = rules.getBoolean(GameRules.RULE_DAYLIGHT);
                savedRandomTick = rules.getInt(GameRules.RULE_RANDOMTICKING);
            }
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
            p.level().playSound(null, p.blockPosition(), InfinitySounds.get("time_stone"), SoundSource.PLAYERS, 1.0F,
                    0.5F);
        } else if (!on && was && p.getUUID().equals(freezeOwner)) {
            unfreeze(server);
        }
    }

    private static void unfreeze(MinecraftServer server) {
        freezeOwner = null;
        if (savedDaylight != null) {
            server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(savedDaylight, server);
            server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(savedRandomTick, server);
        }
        savedDaylight = null;
        savedRandomTick = null;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                CompoundTag d = e.getPersistentData();
                if (d.contains("flash_frozen")) {
                    CompoundTag f = d.getCompound("flash_frozen");
                    e.setDeltaMovement(f.getDouble("X"), f.getDouble("Y"), f.getDouble("Z"));
                    e.setNoGravity(f.getBoolean("NoGravity"));
                    e.hurtMarked = true;
                    d.remove("flash_frozen");
                }
            }
        }
    }

    /** Living things don't move while time stands still (but those immune to time). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (freezeOwner != null && !e.level().isClientSide && !immune(e)) {
            event.setCanceled(true);
        }
    }

    static boolean immune(Entity e) {
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
            return true;
        }
        return InfinityImmunity.immune(e, "time") || e instanceof ServerPlayer p && p.getUUID().equals(freezeOwner);
    }

    // ------------------------------------------------------------------
    // Projectiles stopped
    // ------------------------------------------------------------------

    private static final Map<UUID, Integer> STOPPERS = new HashMap<>();

    static void projectileStop(ServerPlayer p, boolean on) {
        if (on) {
            STOPPERS.put(p.getUUID(), 0);
        } else {
            STOPPERS.remove(p.getUUID());
        }
    }

    private static void hold(Entity e) {
        CompoundTag d = e.getPersistentData();
        if (!d.contains("flash_frozen")) {
            CompoundTag f = new CompoundTag();
            Vec3 v = e.getDeltaMovement();
            f.putDouble("X", v.x);
            f.putDouble("Y", v.y);
            f.putDouble("Z", v.z);
            f.putBoolean("NoGravity", e.isNoGravity());
            d.put("flash_frozen", f);
        }
        d.putInt("flash_frozen_release", 40);
        e.setNoGravity(true);
        e.setDeltaMovement(Vec3.ZERO);
        e.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (freezeOwner != null && server.getPlayerList().getPlayer(freezeOwner) == null) {
            unfreeze(server);
        }
        if (flowOwner != null && server.getPlayerList().getPlayer(flowOwner) == null) {
            flowOwner = null;
            msPerTick = 50L;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof Projectile) || e instanceof FishingHook) {
                    continue;
                }
                boolean stop = freezeOwner != null;
                if (!stop) {
                    for (UUID id : STOPPERS.keySet()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p != null && p.level() == level && p.distanceToSqr(e) < 12.0D * 12.0D) {
                            stop = true;
                            break;
                        }
                    }
                }
                CompoundTag d = e.getPersistentData();
                if (stop) {
                    hold(e);
                } else if (d.contains("flash_frozen")) {
                    int left = d.getInt("flash_frozen_release") - 1;
                    e.setDeltaMovement(Vec3.ZERO);
                    if (left <= 0) {
                        CompoundTag f = d.getCompound("flash_frozen");
                        e.setDeltaMovement(f.getDouble("X"), f.getDouble("Y"), f.getDouble("Z"));
                        e.setNoGravity(f.getBoolean("NoGravity"));
                        e.hurtMarked = true;
                        d.remove("flash_frozen");
                        d.remove("flash_frozen_release");
                    } else {
                        d.putInt("flash_frozen_release", left);
                    }
                }
            }
        }
    }

    /** Walking on water while time is frozen (palladium:water_walk). */
    static void waterWalk(ServerPlayer p, boolean on) {
        if (!on || p.isShiftKeyDown()) {
            return;
        }
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.1D, p.getZ());
        if (p.level().getFluidState(below).is(Fluids.WATER) && p.getDeltaMovement().y < 0.0D) {
            p.setDeltaMovement(p.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            p.setOnGround(true);
            p.fallDistance = 0.0F;
            p.setPos(p.getX(), Math.floor(p.getY()) + (p.getY() % 1.0D > 0.9D ? 1.0D : 0.0D), p.getZ());
        }
    }

    /** /infinity time set: always forward to that time of day. */
    public static void setTime(ServerPlayer p, long time) {
        for (ServerLevel level : p.server.getAllLevels()) {
            long day = level.getDayTime();
            long target = day - Math.floorMod(day, 24000L) + Math.floorMod(time, 24000L);
            if (target <= day) {
                target += 24000L;
            }
            level.setDayTime(target);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        msPerTick = 50L;
        if (freezeOwner != null) {
            unfreeze(event.getServer());
        }
    }
}

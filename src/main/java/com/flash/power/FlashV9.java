package com.flash.power;

import com.flash.ability.FlashAbilityManager;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Velocity-9. Injected from the syringe:
 * <ul>
 *   <li>a player without powers gets the speed powers (level 1) only for as long
 *       as the dose lasts: no progression, and they are gone again afterwards;</li>
 *   <li>then, for 5 minutes, the speed limit and the selected level rise by 5
 *       (blue lightning, blue K screen);</li>
 *   <li>when it wears off the player goes back to the level used before, loses
 *       the powers for 30 seconds and decays visibly for 10 seconds.</li>
 * </ul>
 * Everything lives in its own persistent tag ({@link #KEY}) and only stores the
 * selected level used before the injection and the end times, so the trained
 * progress is never touched. The timers use the world clock and are re-checked
 * every tick, so death, logout and rejoining cannot leave a bonus or a penalty stuck.
 */
public final class FlashV9 {

    public static final String KEY = "flash_v9";
    public static final int BONUS_LEVELS = 5;
    public static final int DURATION_TICKS = 5 * 60 * 20;
    public static final int NO_POWERS_TICKS = 30 * 20;
    public static final int DECAY_TICKS = 10 * 20;

    private static final String ACTIVE_UNTIL = "activeUntil";
    private static final String PREVIOUS_SELECTED = "previousSelected";
    private static final String FIRST_POWERS = "firstPowers";
    private static final String NO_POWERS_UNTIL = "noPowersUntil";
    private static final String DECAY_UNTIL = "decayUntil";

    private static final DustParticleOptions DECAY_DUST =
            new DustParticleOptions(new Vector3f(0.12F, 0.12F, 0.13F), 1.2F);

    private FlashV9() {
    }

    private static long now(Player player) {
        return player.level().getGameTime();
    }

    private static CompoundTag tag(Player player) {
        return player.getPersistentData().getCompound(KEY);
    }

    private static void save(Player player, CompoundTag tag) {
        if (tag.isEmpty()) {
            player.getPersistentData().remove(KEY);
        } else {
            player.getPersistentData().put(KEY, tag);
        }
    }

    /** V-9 boost running (blue lightning, +5 levels). */
    public static boolean isActive(Player player) {
        CompoundTag tag = tag(player);
        return tag.contains(ACTIVE_UNTIL) && tag.getLong(ACTIVE_UNTIL) > now(player);
    }

    /** The 30 seconds without powers after the effect. */
    public static boolean isSuppressed(Player player) {
        if (!player.getPersistentData().contains(KEY)) {
            return false;
        }
        CompoundTag tag = tag(player);
        return tag.contains(NO_POWERS_UNTIL) && tag.getLong(NO_POWERS_UNTIL) > now(player);
    }

    public static boolean isDecaying(Player player) {
        CompoundTag tag = tag(player);
        return tag.contains(DECAY_UNTIL) && tag.getLong(DECAY_UNTIL) > now(player);
    }

    /** True while the only powers the player has come from the V-9 (no progression for them). */
    public static boolean isBorrowedPowers(Player player) {
        if (!player.getPersistentData().contains(KEY)) {
            return false;
        }
        CompoundTag tag = tag(player);
        return tag.getBoolean(FIRST_POWERS) && tag.contains(ACTIVE_UNTIL);
    }

    /** The player got the real Acceleration Force meanwhile: the powers are no longer taken away. */
    public static void adoptPowers(Player player) {
        if (isBorrowedPowers(player)) {
            CompoundTag tag = tag(player).copy();
            tag.putBoolean(FIRST_POWERS, false);
            save(player, tag);
        }
    }

    /** Extra levels on top of the normal limit while the effect runs. */
    public static int bonus(Player player) {
        return player.getPersistentData().contains(KEY) && isActive(player) ? BONUS_LEVELS : 0;
    }

    /** Ticks left of the 30 seconds without powers (0 when not running). */
    public static int suppressedTicks(Player player) {
        CompoundTag tag = tag(player);
        if (!tag.contains(NO_POWERS_UNTIL)) {
            return 0;
        }
        return (int) Math.max(0L, tag.getLong(NO_POWERS_UNTIL) - now(player));
    }

    /** Ticks left of the boost (0 when not active). */
    public static int remainingTicks(Player player) {
        CompoundTag tag = tag(player);
        if (!tag.contains(ACTIVE_UNTIL)) {
            return 0;
        }
        return (int) Math.max(0L, tag.getLong(ACTIVE_UNTIL) - now(player));
    }

    /** Syringe injection. @return true when the dose was used. */
    public static boolean inject(ServerPlayer player) {
        CompoundTag tag = tag(player);
        if (tag.contains(ACTIVE_UNTIL) || isSuppressed(player)) {
            player.displayClientMessage(Component.translatable("message.flash.v9_busy"), true);
            return false;
        }
        boolean first = !FlashPowers.hasPermanentPowers(player);
        if (first) {
            // borrowed speed powers for the duration of the dose (replaces a temporary grant, if any)
            FlashPowers.grant(player);
            FlashAbilityManager.reset(player);
        }
        int previous = FlashPowers.getSelectedLevel(player);
        tag = new CompoundTag();
        tag.putLong(ACTIVE_UNTIL, now(player) + DURATION_TICKS);
        tag.putInt(PREVIOUS_SELECTED, previous);
        tag.putBoolean(FIRST_POWERS, first);
        save(player, tag);
        FlashPowers.setSelectedLevel(player, previous + BONUS_LEVELS);

        refresh(player);
        player.displayClientMessage(Component.translatable(
                first ? "message.flash.v9_first" : "message.flash.v9_injected",
                FlashPowers.getSelectedLevel(player)), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.8F);
        return true;
    }

    /** Called every server tick for every player. */
    public static void tick(ServerPlayer player) {
        if (!player.getPersistentData().contains(KEY)) {
            return;
        }
        CompoundTag tag = tag(player).copy();
        long now = now(player);
        boolean changed = false;

        if (tag.contains(ACTIVE_UNTIL) && tag.getLong(ACTIVE_UNTIL) <= now) {
            int previous = tag.getInt(PREVIOUS_SELECTED);
            boolean borrowed = tag.getBoolean(FIRST_POWERS);
            tag.remove(ACTIVE_UNTIL);
            tag.remove(PREVIOUS_SELECTED);
            tag.remove(FIRST_POWERS);
            save(player, tag);
            if (borrowed) {
                // the powers only came from the dose: they go away with it
                FlashPowers.revoke(player);
            } else {
                // back to the level used before (stored; it shows again after the 30 s)
                FlashPowers.setSelectedLevel(player, previous);
            }
            tag.putLong(NO_POWERS_UNTIL, now + NO_POWERS_TICKS);
            tag.putLong(DECAY_UNTIL, now + DECAY_TICKS);
            save(player, tag);
            FlashAbilityManager.reset(player);
            refresh(player);
            player.removeEffect(net.minecraft.world.effect.MobEffects.HUNGER);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WITHER, DECAY_TICKS, 0, false, true, true));
            player.displayClientMessage(Component.translatable(
                    borrowed ? "message.flash.v9_ended_borrowed" : "message.flash.v9_ended"), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.6F, 0.6F);
            return;
        }
        if (tag.contains(DECAY_UNTIL) && tag.getLong(DECAY_UNTIL) <= now) {
            tag.remove(DECAY_UNTIL);
            changed = true;
        }
        if (tag.contains(NO_POWERS_UNTIL) && tag.getLong(NO_POWERS_UNTIL) <= now) {
            tag.remove(NO_POWERS_UNTIL);
            save(player, tag);
            refresh(player);
            if (FlashPowers.hasPowers(player)) {
                player.displayClientMessage(Component.translatable("message.flash.v9_powers_back"), true);
            }
            return;
        }
        if (changed) {
            save(player, tag);
        }
        if (tag.contains(DECAY_UNTIL) && player.level() instanceof ServerLevel level) {
            spawnDecay(level, player);
        }
    }

    private static void spawnDecay(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.5D;
        double z = player.getZ();
        level.sendParticles(DECAY_DUST, x, y, z, 3, 0.30D, 0.55D, 0.30D, 0.0D);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 4, 0.35D, 0.6D, 0.35D, 0.0D);
        if (player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.25D, 0.5D, 0.25D, 0.01D);
        }
        if (player.tickCount % 20 == 0) {
            level.playSound(null, x, y, z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.5F, 0.6F);
        }
    }

    private static void refresh(ServerPlayer player) {
        FlashSpeed.apply(player);
        FlashStep.apply(player);
        FlashCombustion.onLevelChanged(player);
        FlashStatusEffects.onLevelChanged(player);
        FlashSyncPacket.send(player);
        FlashVisualSyncPacket.send(player);
    }

    public static void copy(Player from, Player to) {
        CompoundTag data = from.getPersistentData();
        if (data.contains(KEY)) {
            to.getPersistentData().put(KEY, data.getCompound(KEY).copy());
        }
    }
}

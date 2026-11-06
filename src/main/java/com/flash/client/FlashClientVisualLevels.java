package com.flash.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client cache of the speed level and active-ability mask of every tracked
 * player. The local player is read from the normal optimistic progression copy;
 * other players are updated by {@code FlashVisualSyncPacket}.
 */
public final class FlashClientVisualLevels {

    private static final Map<UUID, Integer> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> NEGATIVE = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<UUID> V9 = ConcurrentHashMap.newKeySet();

    private FlashClientVisualLevels() {
    }

    public static void set(UUID playerId, int level) {
        set(playerId, level, 0);
    }

    /** Local player only: client game time when the V-9 / the time without powers ends. */
    private static long v9End;
    private static long lockEnd;

    public static void set(UUID playerId, int level, int activeMask, boolean negative, boolean v9,
                           int v9Ticks, int lockTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && mc.player.getUUID().equals(playerId)) {
            long now = mc.level.getGameTime();
            v9End = v9Ticks > 0 ? now + v9Ticks : 0L;
            lockEnd = lockTicks > 0 ? now + lockTicks : 0L;
        }
        set(playerId, level, activeMask, negative, v9);
    }

    /** Ticks left of the local player's V-9 (0 when not running). */
    public static int localV9Ticks() {
        return remaining(v9End);
    }

    /** Ticks left of the local player's 30 seconds without powers. */
    public static int localLockTicks() {
        return remaining(lockEnd);
    }

    private static int remaining(long end) {
        Minecraft mc = Minecraft.getInstance();
        if (end <= 0L || mc.level == null) {
            return 0;
        }
        return (int) Math.max(0L, end - mc.level.getGameTime());
    }

    public static void set(UUID playerId, int level, int activeMask, boolean negative, boolean v9) {
        if (v9) {
            V9.add(playerId);
        } else {
            V9.remove(playerId);
        }
        set(playerId, level, activeMask, negative);
    }

    /** True while the player is under Velocity-9 (blue lightning). */
    public static boolean isV9(Player player) {
        return V9.contains(player.getUUID());
    }

    public static boolean isLocalV9() {
        Player player = Minecraft.getInstance().player;
        return player != null && V9.contains(player.getUUID());
    }

    public static void set(UUID playerId, int level, int activeMask, boolean negative) {
        if (negative) {
            NEGATIVE.add(playerId);
        } else {
            NEGATIVE.remove(playerId);
        }
        set(playerId, level, activeMask);
    }

    /** True when the player carries the (red) Negative Acceleration Force. */
    public static boolean isNegative(Player player) {
        return NEGATIVE.contains(player.getUUID());
    }

    /** Same for the local player (HUD, own effects). */
    public static boolean isLocalNegative() {
        Player player = Minecraft.getInstance().player;
        return player != null && NEGATIVE.contains(player.getUUID());
    }

    public static void set(UUID playerId, int level, int activeMask) {
        if (level < 1) {
            LEVELS.remove(playerId);
        } else {
            LEVELS.put(playerId, level);
        }
        if (activeMask == 0) {
            ACTIVE.remove(playerId);
        } else {
            ACTIVE.put(playerId, activeMask);
        }
    }

    public static int get(Player player) {
        if (player == Minecraft.getInstance().player) {
            return FlashClientData.hasPowers() ? FlashClientData.getSelectedLevel() : 0;
        }
        Integer level = LEVELS.get(player.getUUID());
        return level == null ? 0 : level;
    }

    public static int activeMask(Player player) {
        if (player == Minecraft.getInstance().player) {
            return FlashClientData.getActiveAbilityMask();
        }
        Integer mask = ACTIVE.get(player.getUUID());
        return mask == null ? 0 : mask;
    }

    public static boolean isActive(Player player, int abilityId) {
        return (activeMask(player) & (1 << abilityId)) != 0;
    }

    public static void reset() {
        v9End = 0L;
        lockEnd = 0L;
        V9.clear();
        LEVELS.clear();
        ACTIVE.clear();
        NEGATIVE.clear();
    }
}

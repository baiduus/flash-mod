package com.flash.power;

import com.flash.FlashMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Persistent Flash data of a single player.
 *
 * <p>All values live in Forge's persistent player data, so they are saved per
 * player and survive logout/login, death, respawn and dimension changes. The
 * server is always the source of truth; the client receives a read-only copy
 * through {@code FlashSyncPacket}.</p>
 *
 * <p>Permanent powers and temporary powers are stored separately. Temporary
 * grants (level 9 ability) never overwrite permanent progress, expire on the
 * server clock and are rechecked after a reconnect.</p>
 */
public final class FlashPowers {

    public static final String ROOT = FlashMod.MODID;
    private static final String PREFERENCES = "flash_prefs";

    private static final String HAS_POWERS = "hasPowers";
    private static final String MAX_LEVEL = "maxLevel";
    private static final String SELECTED_LEVEL = "selectedLevel";
    private static final String DISTANCE = "distance";
    private static final String TOTAL_DISTANCE = "totalDistance";
    private static final String LEGACY_RANK = "rank";

    private static final String NEGATIVE = "negative";

    private static final String TEMP_LEVEL = "tempLevel";
    private static final String TEMP_UNTIL = "tempUntil";
    private static final String DESTROY_BLOCKS = "destroyBlocks";

    private FlashPowers() {
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public static boolean hasPowers(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(ROOT)) {
            return false;
        }
        if (FlashV9.isSuppressed(player)) {
            return false; // the 30 seconds without powers after the V-9
        }
        CompoundTag flash = data.getCompound(ROOT);
        return flash.getBoolean(HAS_POWERS)
                || isTemporaryActive(flash, gameTime(player));
    }

    /**
     * True when the player carries the Negative Acceleration Force. It shares
     * the level system with the Positive force (same levels, same abilities);
     * only the look changes: everything is red instead of orange / yellow.
     */
    public static boolean isNegative(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(ROOT) && data.getCompound(ROOT).getBoolean(NEGATIVE)
                && hasPowers(player);
    }

    public static void setNegative(Player player, boolean negative) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag flash = persistent.contains(ROOT)
                ? persistent.getCompound(ROOT).copy()
                : new CompoundTag();
        if (negative) {
            flash.putBoolean(NEGATIVE, true);
        } else {
            flash.remove(NEGATIVE);
        }
        persistent.put(ROOT, flash);
    }

    /**
     * Gives the Negative force. A player without powers starts at level 1; a
     * player who already has the Positive force keeps the level reached.
     */
    public static void grantNegative(Player player) {
        grant(player);
        setNegative(player, true);
    }

    public static boolean hasPermanentPowers(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(ROOT) && data.getCompound(ROOT).getBoolean(HAS_POWERS);
    }

    public static boolean isTemporary(Player player) {
        if (hasPermanentPowers(player) || !player.getPersistentData().contains(ROOT)) {
            return false;
        }
        return isTemporaryActive(player.getPersistentData().getCompound(ROOT), gameTime(player));
    }

    /** Highest level reachable by this player: trained level, or 20 with the Tachyon Amplifier at 10. */
    public static int getMaxLevel(Player player) {
        if (!hasPowers(player)) {
            return 0;
        }
        CompoundTag flash = player.getPersistentData().getCompound(ROOT);
        if (flash.getBoolean(HAS_POWERS)) {
            int stored = getStoredMax(player);
            int limit = stored >= FlashSpeedLevels.BASE_MAX_LEVEL && FlashAmplifier.isEquipped(player)
                    ? FlashSpeedLevels.MAX_LEVEL
                    : stored;
            // the V-9 adds five levels for as long as it lasts
            return FlashSpeedLevels.clamp(limit + FlashV9.bonus(player));
        }
        return FlashSpeedLevels.clamp(flash.getInt(TEMP_LEVEL));
    }

    /** The stored selection, even while the powers are suppressed. */
    private static int storedSelected(Player player) {
        CompoundTag flash = player.getPersistentData().getCompound(ROOT);
        return flash.contains(SELECTED_LEVEL) ? flash.getInt(SELECTED_LEVEL) : getStoredMax(player);
    }

    /** The trained level (never above 10), without the amplifier. */
    private static int getStoredMax(Player player) {
        CompoundTag flash = player.getPersistentData().getCompound(ROOT);
        int value = flash.contains(MAX_LEVEL) ? flash.getInt(MAX_LEVEL) : flash.getInt(LEGACY_RANK);
        return Math.max(FlashSpeedLevels.MIN_LEVEL, Math.min(FlashSpeedLevels.BASE_MAX_LEVEL, value));
    }

    public static int getSelectedLevel(Player player) {
        if (!hasPowers(player)) {
            return 0;
        }
        CompoundTag flash = player.getPersistentData().getCompound(ROOT);
        if (flash.getBoolean(HAS_POWERS)) {
            int max = getMaxLevel(player);
            int selected = flash.contains(SELECTED_LEVEL)
                    ? flash.getInt(SELECTED_LEVEL)
                    : max;
            return Math.max(FlashSpeedLevels.MIN_LEVEL, Math.min(selected, max));
        }
        return FlashSpeedLevels.clamp(flash.getInt(TEMP_LEVEL));
    }

    public static double getDistance(Player player) {
        if (!hasPermanentPowers(player)) {
            return 0.0D;
        }
        return Math.max(0.0D, player.getPersistentData().getCompound(ROOT).getDouble(DISTANCE));
    }

    public static double getTotalDistance(Player player) {
        if (!hasPermanentPowers(player)) {
            return 0.0D;
        }
        return Math.max(0.0D,
                player.getPersistentData().getCompound(ROOT).getDouble(TOTAL_DISTANCE));
    }

    public static boolean isMaxLevel(Player player) {
        return hasPermanentPowers(player) && getStoredMax(player) >= FlashSpeedLevels.BASE_MAX_LEVEL;
    }

    public static int getBlocksToNextLevel(Player player) {
        if (!hasPermanentPowers(player) || isMaxLevel(player)) {
            return 0;
        }
        return Math.max(0, FlashSpeedLevels.BLOCKS_PER_LEVEL - (int) Math.floor(getDistance(player)));
    }

    public static float getProgressFraction(Player player) {
        if (!hasPermanentPowers(player) || isMaxLevel(player)) {
            return isMaxLevel(player) ? 1.0F : 0.0F;
        }
        return (float) Math.min(1.0D, getDistance(player) / (double) FlashSpeedLevels.BLOCKS_PER_LEVEL);
    }

    public static int getTemporaryRemaining(Player player) {
        if (!isTemporary(player)) {
            return 0;
        }
        CompoundTag flash = player.getPersistentData().getCompound(ROOT);
        long remaining = flash.getLong(TEMP_UNTIL) - gameTime(player);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    // ------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------

    public static boolean isDestroyBlocksEnabled(Player player) {
        CompoundTag prefs = player.getPersistentData().getCompound(PREFERENCES);
        return prefs.getBoolean(DESTROY_BLOCKS);
    }

    public static void setDestroyBlocksEnabled(Player player, boolean enabled) {
        CompoundTag prefs = player.getPersistentData().contains(PREFERENCES)
                ? player.getPersistentData().getCompound(PREFERENCES).copy()
                : new CompoundTag();
        prefs.putBoolean(DESTROY_BLOCKS, enabled);
        player.getPersistentData().put(PREFERENCES, prefs);
    }

    // ------------------------------------------------------------------
    // Permanent mutations (server side)
    // ------------------------------------------------------------------

    public static void grant(Player player) {
        if (hasPermanentPowers(player)) {
            int max = Math.max(1, getMaxLevel(player));
            int selected = Math.max(1, Math.min(getSelectedLevel(player), max));
            write(player, max, selected, getDistance(player), getTotalDistance(player));
            return;
        }
        write(player, 1, 1, 0.0D, 0.0D);
    }

    public static void revoke(Player player) {
        player.getPersistentData().remove(ROOT);
    }

    public static boolean setSelectedLevel(Player player, int level) {
        if (!hasPermanentPowers(player) || isTemporary(player)) {
            return false;
        }
        int max = getMaxLevel(player);
        int clamped = Math.max(FlashSpeedLevels.MIN_LEVEL, Math.min(level, max));
        write(player, getStoredMax(player), clamped, getDistance(player), getTotalDistance(player));
        return clamped == level;
    }

    public static void setMaxLevel(Player player, int level) {
        if (!hasPermanentPowers(player) || isTemporary(player)) {
            return;
        }
        int clamped = Math.min(FlashSpeedLevels.BASE_MAX_LEVEL, FlashSpeedLevels.clamp(level));
        int selected = Math.min(getSelectedLevel(player), clamped);
        write(player, clamped, selected, getDistance(player), getTotalDistance(player));
    }

    public static void setDistance(Player player, double distance) {
        if (!hasPermanentPowers(player) || isTemporary(player)) {
            return;
        }
        write(player, getStoredMax(player), storedSelected(player),
                Math.max(0.0D, distance), getTotalDistance(player));
    }

    public static int addDistance(Player player, double amount) {
        if (!hasPermanentPowers(player) || isTemporary(player)
                || amount <= 0.0D || isMaxLevel(player)) {
            return 0;
        }

        int max = getStoredMax(player);
        int selected = storedSelected(player);
        double distance = getDistance(player) + amount;
        double total = getTotalDistance(player) + amount;
        int unlocked = 0;

        while (distance >= FlashSpeedLevels.BLOCKS_PER_LEVEL
                && max < FlashSpeedLevels.BASE_MAX_LEVEL) {
            distance -= FlashSpeedLevels.BLOCKS_PER_LEVEL;
            max++;
            unlocked++;
        }

        write(player, max, selected, distance, total);
        return unlocked;
    }

    // ------------------------------------------------------------------
    // Temporary powers
    // ------------------------------------------------------------------

    public static boolean grantTemporary(Player player, int level, int durationTicks) {
        if (hasPermanentPowers(player) || isTemporary(player) || durationTicks <= 0) {
            return false;
        }
        CompoundTag persistent = player.getPersistentData();
        CompoundTag flash = persistent.contains(ROOT)
                ? persistent.getCompound(ROOT).copy()
                : new CompoundTag();
        flash.putInt(TEMP_LEVEL, FlashSpeedLevels.clamp(level));
        flash.putLong(TEMP_UNTIL, gameTime(player) + durationTicks);
        persistent.put(ROOT, flash);
        return true;
    }

    /**
     * Clears an expired temporary grant.
     *
     * @return true when a grant was active before this call and has just expired
     */
    public static boolean tickTemporary(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT)) {
            return false;
        }
        CompoundTag flash = persistent.getCompound(ROOT);
        if (flash.getBoolean(HAS_POWERS)) {
            flash.remove(TEMP_LEVEL);
            flash.remove(TEMP_UNTIL);
            persistent.put(ROOT, flash);
            return false;
        }
        if (flash.contains(TEMP_UNTIL) && gameTime(player) >= flash.getLong(TEMP_UNTIL)) {
            persistent.remove(ROOT);
            return true;
        }
        return false;
    }

    public static void clearTemporary(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT)) {
            return;
        }
        CompoundTag flash = persistent.getCompound(ROOT);
        if (flash.getBoolean(HAS_POWERS)) {
            flash.remove(TEMP_LEVEL);
            flash.remove(TEMP_UNTIL);
            persistent.put(ROOT, flash);
        } else {
            persistent.remove(ROOT);
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private static void write(Player player, int maxLevel, int selectedLevel,
                              double distance, double totalDistance) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag flash = persistent.contains(ROOT)
                ? persistent.getCompound(ROOT).copy()
                : new CompoundTag();
        flash.putBoolean(HAS_POWERS, true);
        flash.putInt(MAX_LEVEL,
                Math.min(FlashSpeedLevels.BASE_MAX_LEVEL, FlashSpeedLevels.clamp(maxLevel)));
        flash.putInt(SELECTED_LEVEL,
                Math.max(0, Math.min(selectedLevel, FlashSpeedLevels.MAX_LEVEL)));
        flash.putDouble(DISTANCE, Math.max(0.0D, distance));
        flash.putDouble(TOTAL_DISTANCE, Math.max(0.0D, totalDistance));
        flash.remove(TEMP_LEVEL);
        flash.remove(TEMP_UNTIL);
        persistent.put(ROOT, flash);
    }

    private static boolean isTemporaryActive(CompoundTag flash, long now) {
        return flash.contains(TEMP_UNTIL)
                && flash.getLong(TEMP_UNTIL) > now
                && flash.getInt(TEMP_LEVEL) > 0;
    }

    private static long gameTime(Player player) {
        return player.level().getGameTime();
    }

    public static void copy(Player oldPlayer, Player newPlayer) {
        CompoundTag oldData = oldPlayer.getPersistentData();
        if (oldData.contains(ROOT)) {
            newPlayer.getPersistentData().put(ROOT, oldData.getCompound(ROOT).copy());
        }
        if (oldData.contains(PREFERENCES)) {
            newPlayer.getPersistentData().put(PREFERENCES,
                    oldData.getCompound(PREFERENCES).copy());
        }
        if (oldData.contains("flash_dimension")) {
            newPlayer.getPersistentData().put("flash_dimension",
                    oldData.getCompound("flash_dimension").copy());
        }
    }
}

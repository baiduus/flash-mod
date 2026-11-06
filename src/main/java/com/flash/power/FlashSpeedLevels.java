package com.flash.power;

import java.util.Locale;

/**
 * Central, configurable table of the Flash speed levels.
 *
 * <p>The table is indexed by level. Level 0 is vanilla speed (no attribute
 * modifier); level 1 is about three times the vanilla movement speed. The
 * array can simply be extended in the future to support levels above 10:
 * every other system (persistence, commands, HUD and the K screen) reads
 * {@link #MAX_LEVEL} and the table instead of hard-coded limits.</p>
 */
public final class FlashSpeedLevels {

    public static final int MIN_LEVEL = 0;

    /**
     * Total speed multiplier relative to vanilla movement speed.
     * Index = level. Keep every entry strictly greater than the previous one.
     */
    private static final double[] TOTAL_MULTIPLIERS = {
            1.00D, // level 0 - vanilla
            3.00D, // level 1 - ~3x vanilla
            3.50D, // level 2
            4.00D, // level 3 - combustion and electric visuals start here
            4.50D, // level 4
            5.00D, // level 5
            5.50D, // level 6
            6.00D, // level 7 - unchanged from the previous version
            16.875D, // level 8 - 2.5x the previous level-8 value
            18.75D,  // level 9 - 2.5x the previous level-9 value
            20.625D, // level 10 - 2.5x the previous 8.25x value
            // levels 11..20 are only reachable with the Tachyon Amplifier (same +1.875 step)
            22.500D, 24.375D, 26.250D, 28.125D, 30.000D,
            31.875D, 33.750D, 35.625D, 37.500D, 39.375D
    };

    /** Highest level reachable by training alone (without the Tachyon Amplifier). */
    public static final int BASE_MAX_LEVEL = 10;

    /** Level at which real combustion and the electric visuals start. */
    public static final int FIRE_LEVEL = 3;

    /** Distance in blocks required for each new level. */
    public static final int BLOCKS_PER_LEVEL = 1000;

    public static final int MAX_LEVEL = TOTAL_MULTIPLIERS.length - 1;

    private FlashSpeedLevels() {
    }

    public static int clamp(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public static double totalMultiplier(int level) {
        return TOTAL_MULTIPLIERS[clamp(level)];
    }

    /**
     * Amount for {@link net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation#MULTIPLY_TOTAL}.
     * A multiplier of 3.0 becomes +200%.
     */
    public static double attributeBonus(int level) {
        return totalMultiplier(level) - 1.0D;
    }

    /**
     * Unique, human-readable amount used by the client HUD when the synced
     * data is not available yet.
     */
    public static int bonusPercent(int level) {
        return (int) Math.round(attributeBonus(level) * 100.0D);
    }

    public static String multiplierText(int level) {
        return String.format(Locale.ROOT, "%.1fx", totalMultiplier(level));
    }
}

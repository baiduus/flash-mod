package com.flash.client;

/**
 * Look of the K screen for one kind of power. The screen itself is neutral: it
 * only asks which theme applies to the local player. To add another power (the
 * Hulk, say) create one more theme here, return it from {@link #current()} and
 * feed the screen its levels and abilities.
 */
public record PowerTheme(String id, int accent, int accentDark, int accentSoft,
                         String titleKey, String subtitleKey, Icon icon, boolean hasPower) {

    public enum Icon { NONE, BOLT }

    /** No power at all: grey, nothing that names a specific power. */
    public static final PowerTheme NEUTRAL = new PowerTheme("neutral",
            0xFF8A94A3, 0xFF23272E, 0xFF15181D,
            "gui.flash.theme.neutral.title", "gui.flash.theme.neutral.subtitle", Icon.NONE, false);

    /** Positive Acceleration Force: yellow / gold. */
    public static final PowerTheme FLASH = new PowerTheme("flash",
            0xFFFFC21A, 0xFF3F3207, 0xFF1F1A08,
            "gui.flash.progress.title", "gui.flash.progress.subtitle", Icon.BOLT, true);

    /** Negative Acceleration Force: red. */
    public static final PowerTheme NEGATIVE = new PowerTheme("negative",
            0xFFFF4040, 0xFF3C0C0E, 0xFF1E0A0B,
            "gui.flash.theme.negative.title", "gui.flash.progress.subtitle", Icon.BOLT, true);

    /** Velocity-9 running: electric blue. */
    public static final PowerTheme V9 = new PowerTheme("v9",
            0xFF3FB4FF, 0xFF0B2A44, 0xFF08162A,
            "gui.flash.theme.v9.title", "gui.flash.progress.subtitle", Icon.BOLT, true);

    public static PowerTheme current() {
        if (!FlashClientData.hasPowers()) {
            return NEUTRAL;
        }
        if (FlashClientVisualLevels.isLocalV9()) {
            return V9;
        }
        return FlashClientVisualLevels.isLocalNegative() ? NEGATIVE : FLASH;
    }
}

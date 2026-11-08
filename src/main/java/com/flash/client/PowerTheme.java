package com.flash.client;

/**
 * Look of the K screen for one kind of power. The screen itself is neutral: it
 * only asks which theme applies to the local player. To add another power (the
 * Hulk, say) create one more theme here, return it from {@link #current()} and
 * feed the screen its levels and abilities.
 */
public record PowerTheme(String id, int accent, int accentDark, int accentSoft,
                         String titleKey, String subtitleKey, Icon icon, boolean hasPower) {

    public enum Icon { NONE, BOLT, FIST, SHIELD }

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

    /** The Hulk: emerald green. */
    public static final PowerTheme HULK = new PowerTheme("hulk",
            0xFF5BD94A, 0xFF143A12, 0xFF0D1F0B,
            "gui.flash.theme.hulk.title", "gui.flash.theme.hulk.subtitle", Icon.FIST, true);

    /** The Red Hulk: crimson. */
    public static final PowerTheme RED_HULK = new PowerTheme("red_hulk",
            0xFFFF4A3A, 0xFF3F0E0B, 0xFF200807,
            "gui.flash.theme.red_hulk.title", "gui.flash.theme.red_hulk.subtitle", Icon.FIST, true);

    /** Superman: Kryptonian blue (red and gold in the details). */
    public static final PowerTheme SUPERMAN = new PowerTheme("superman",
            0xFF3F8CFF, 0xFF0E2350, 0xFF0A1630,
            "gui.flash.theme.superman.title", "gui.flash.theme.superman.subtitle", Icon.SHIELD, true);

    /** Thor: storm blue with silver. */
    public static final PowerTheme THOR = new PowerTheme("thor",
            0xFF6FC3FF, 0xFF13304A, 0xFF0B1A2A,
            "gui.flash.theme.thor.title", "gui.flash.theme.thor.subtitle", Icon.BOLT, true);

    /** True when the local player carries the Thor power. */
    public static boolean localThor() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && com.flash.thor.ThorState.has(player);
    }

    /** True when the local player carries the Superman power. */
    public static boolean localSuperman() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && com.flash.superman.SupermanState.has(player);
    }

    /** True when the local player is the Red Hulk (red HUD). */
    public static boolean localRedHulk() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && com.flash.hulk.HulkState.has(player) && com.flash.hulk.HulkState.isRed(player);
    }

    /** True when the local player carries the Hulk. */
    public static boolean localHulk() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && com.flash.hulk.HulkState.has(player);
    }

    public static PowerTheme current() {
        if (localThor()) {
            return THOR;
        }
        if (localSuperman()) {
            return SUPERMAN;
        }
        if (localHulk()) {
            return localRedHulk() ? RED_HULK : HULK;
        }
        if (!FlashClientData.hasPowers()) {
            return NEUTRAL;
        }
        if (FlashClientVisualLevels.isLocalV9()) {
            return V9;
        }
        return FlashClientVisualLevels.isLocalNegative() ? NEGATIVE : FLASH;
    }
}

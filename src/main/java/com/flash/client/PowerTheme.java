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

    /** Yas, the transcendence: pale gold light on deep night. */
    public static final PowerTheme YAS = new PowerTheme("yas",
            0xFFFFE89A, 0xFF3A3010, 0xFF17130A,
            "gui.flash.theme.yas.title", "gui.flash.theme.yas.subtitle", Icon.BOLT, true);

    /** The Scarlet Witch: chaos red on deep crimson. */
    public static final PowerTheme SCARLET = new PowerTheme("scarlet",
            0xFFE0263A, 0xFF3D0A12, 0xFF1C060A,
            "gui.flash.theme.scarlet.title", "gui.flash.theme.scarlet.subtitle", Icon.BOLT, true);

    /** Storm: lightning white-blue on storm grey. */
    public static final PowerTheme STORM = new PowerTheme("storm",
            0xFFBFE6FF, 0xFF1E2A38, 0xFF0C121A,
            "gui.flash.theme.storm.title", "gui.flash.theme.storm.subtitle", Icon.BOLT, true);

    /** Spider-Man: suit red on deep blue. */
    public static final PowerTheme SPIDER = new PowerTheme("spider",
            0xFFE8333C, 0xFF14234A, 0xFF0A1022,
            "gui.flash.theme.spider.title", "gui.flash.theme.spider.subtitle", Icon.SHIELD, true);

    /** Doctor Doom: Latverian green on steel. */
    public static final PowerTheme DOOM = new PowerTheme("doom",
            0xFF4FD86A, 0xFF1C2A20, 0xFF0B120D,
            "gui.flash.theme.doom.title", "gui.flash.theme.doom.subtitle", Icon.SHIELD, true);

    /** Wonder Woman: Amazon gold on deep red. */
    public static final PowerTheme WONDER = new PowerTheme("wonder",
            0xFFFFC83C, 0xFF3A0C12, 0xFF1A0508,
            "gui.flash.theme.wonder.title", "gui.flash.theme.wonder.subtitle", Icon.SHIELD, true);

    public static boolean localWonder() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && WonderClient.isWonder(player);
    }

    /** God Emperor Doom: Beyonder gold on Latverian green. */
    public static final PowerTheme DOOM_GOD = new PowerTheme("doom_god",
            0xFFFFD86A, 0xFF1C2A14, 0xFF0B1208,
            "gui.flash.theme.doom_god.title", "gui.flash.theme.doom_god.subtitle", Icon.SHIELD, true);

    public static boolean localSpider() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && SpiderClient.isSpider(player);
    }

    public static boolean localDoom() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && DoomClient.isDoom(player);
    }

    /** One of the heroes with modes of their own on R / Z (Storm, Spider-Man, Doom: not Yas, not Wanda). */
    public static boolean localOwnModes() {
        return localStorm() || localSpider() || localDoom() || localWonder();
    }

    public static boolean localStorm() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && StormClient.isStorm(player);
    }

    /** True when the local player is the Scarlet Witch. */
    public static boolean localScarlet() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && ScarletClient.isScarlet(player);
    }

    /** True when the local player is Yas. */
    public static boolean localYas() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && YasClient.isYas(player);
    }

    /** Lex Luthor's suit: Kryptonite green with LexCorp purple. */
    public static final PowerTheme LEX = new PowerTheme("lex",
            0xFF6CFF6C, 0xFF2A1440, 0xFF140A20,
            "gui.flash.theme.lex.title", "gui.flash.theme.lex.subtitle", Icon.SHIELD, true);

    /** True when the local player flies Lex Luthor's suit (and has no powers of his own). */
    public static boolean localLex() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && LexClient.pilot(player);
    }

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
        if (localYas()) {
            return YAS;
        }
        if (localScarlet()) {
            return SCARLET;
        }
        if (localStorm()) {
            return STORM;
        }
        if (localSpider()) {
            return SPIDER;
        }
        if (localDoom()) {
            return DoomClient.god(net.minecraft.client.Minecraft.getInstance().player) ? DOOM_GOD : DOOM;
        }
        if (localWonder()) {
            return WONDER;
        }
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
            return localLex() ? LEX : NEUTRAL;
        }
        if (FlashClientVisualLevels.isLocalV9()) {
            return V9;
        }
        return FlashClientVisualLevels.isLocalNegative() ? NEGATIVE : FLASH;
    }
}

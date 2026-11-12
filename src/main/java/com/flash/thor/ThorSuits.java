package com.flash.thor;

import java.util.Set;
import net.minecraft.network.chat.Component;

/** The Thor suits of the Legends: Superheroes pack (3D model + textures), chosen in the K menu. */
public final class ThorSuits {

    public static final String[] IDS = {"thor", "thor_iconic", "thor_modern", "thor_ih", "thor_mangaverse",
            "thor_partyprince", "thor_ultimate", "thor_2011", "thor_darkworld", "thor_avengers", "thor_avengers_v2",
            "thor_aou", "thor_ragnarok_v1", "thor_ragnarok_v2", "thor_iwv1", "thor_iwv2", "thor_lat_v1", "thor_lat_v2",
            "thor_lat_v3", "thor_cosmic_king", "thor_battle_armor", "thor_godpack", "thor_heroes_reborn", "thor_emh",
            "thor_emh_v2"};

    private static final Set<String> CAPES = Set.of("thor", "thor_2011", "thor_aou", "thor_avengers",
            "thor_battle_armor", "thor_cosmic_king", "thor_darkworld", "thor_emh", "thor_emh_v2", "thor_godpack",
            "thor_iconic", "thor_iwv2", "thor_lat_v2", "thor_lat_v3", "thor_mangaverse", "thor_modern",
            "thor_partyprince", "thor_ragnarok_v1", "thor_ragnarok_v2");

    /** Suits with a texture without the helmet (the helmet can be taken off in the K menu). */
    private static final Set<String> HELMETLESS = Set.of("thor", "thor_2011", "thor_battle_armor",
            "thor_cosmic_king", "thor_emh", "thor_emh_v2", "thor_godpack", "thor_heroes_reborn", "thor_iconic",
            "thor_ih", "thor_lat_v3", "thor_modern");

    /** Weapons: the classic Mjolnir, the MCU Mjolnir, the Cosmic King Mjolnir and Stormbreaker. */
    public static final int CLASSIC = 0;
    public static final int MCU = 1;
    public static final int COSMIC = 2;
    public static final int STORMBREAKER = 3;

    private static final Set<String> MCU_SUITS = Set.of("thor_2011", "thor_darkworld", "thor_avengers",
            "thor_avengers_v2", "thor_aou", "thor_ragnarok_v1", "thor_ragnarok_v2", "thor_iwv1");
    private static final Set<String> STORMBREAKER_SUITS = Set.of("thor_iwv2", "thor_lat_v1", "thor_lat_v2",
            "thor_lat_v3");

    private ThorSuits() {
    }

    /** The weapon that goes with a suit. */
    public static int weapon(String id) {
        if (STORMBREAKER_SUITS.contains(id)) {
            return STORMBREAKER;
        }
        if ("thor_cosmic_king".equals(id)) {
            return COSMIC;
        }
        return MCU_SUITS.contains(id) ? MCU : CLASSIC;
    }

    public static int weapon(int index) {
        return weapon(id(index));
    }

    public static int clamp(int index) {
        return Math.floorMod(index, IDS.length);
    }

    public static String id(int index) {
        return IDS[clamp(index)];
    }

    public static boolean hasCape(String id) {
        return CAPES.contains(id);
    }

    public static boolean hasHelmetless(String id) {
        return HELMETLESS.contains(id);
    }

    public static Component name(int index) {
        return Component.translatable("suit.flash." + id(index));
    }
}

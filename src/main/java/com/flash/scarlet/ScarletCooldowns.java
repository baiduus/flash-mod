package com.flash.scarlet;

import java.util.Map;

/**
 * The Scarlet Witch's cooldowns (ticks), shared by the server (which holds them, ScarletAbilities.ready) and the
 * client (the HUD rows, ScarletClient). The reality on the ground has none.
 */
public final class ScarletCooldowns {

    public static final Map<String, Integer> TICKS = Map.ofEntries(
            Map.entry("reality_mob", 80),
            Map.entry("tk_grab", 40),
            Map.entry("tk_throw", 120),
            Map.entry("dismember", 200),
            Map.entry("read", 100),
            Map.entry("ally", 60),
            Map.entry("wave", 300),
            Map.entry("puppet", 400),
            Map.entry("fury", 100),
            Map.entry("hysteria", 400),
            Map.entry("bolt", 12),
            Map.entry("beam", 120),
            Map.entry("blast", 160),
            Map.entry("heal", 300),
            Map.entry("raise", 900));

    /** The rows of each mode in the HUD: cooldown id ("" none), name key suffix, key ("R", "Z", "RMB", "R+"). */
    public static String[][] rows(String mode) {
        return switch (mode) {
            case "realidade" -> new String[][] {{"", "terrain", "R+"}, {"reality_mob", "creature", "R"}};
            case "telecinese" -> new String[][] {{"tk_grab", "lift", "R"}, {"tk_throw", "throw", "R+"},
                    {"dismember", "dismember", "RMB"}};
            case "mind" -> new String[][] {{"read", "read", "R"}, {"ally", "ally", "R"}, {"wave", "wave", "R"},
                    {"puppet", "puppet", "Z"}, {"fury", "fury", "Z"}, {"hysteria", "hysteria", "Z"}};
            case "energy" -> new String[][] {{"bolt", "bolt", "R"}, {"beam", "beam", "R+"}, {"blast", "blast", "Z+"}};
            case "necro" -> new String[][] {{"heal", "heal", "R"}, {"raise", "raise", "Z"}};
            default -> new String[0][];
        };
    }

    public static int ticks(String id) {
        return TICKS.getOrDefault(id, 20);
    }

    private ScarletCooldowns() {
    }
}

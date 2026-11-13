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
            Map.entry("raise", 900),
            Map.entry("storm_strike", 30),
            Map.entry("storm_beam", 100),
            Map.entry("storm_chain", 140),
            Map.entry("gust", 40),
            Map.entry("tornado", 400),
            Map.entry("weather", 100),
            Map.entry("fury_storm", 900),
            Map.entry("hail", 200),
            Map.entry("frost", 300));

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
            case "lightning" -> new String[][] {{"storm_strike", "storm_strike", "R"},
                    {"storm_beam", "storm_beam", "R+"}, {"storm_chain", "storm_chain", "Z"}};
            case "wind" -> new String[][] {{"gust", "gust", "R"}, {"tornado", "tornado", "Z"}};
            case "weather" -> new String[][] {{"weather", "weather", "R"}, {"fury_storm", "fury_storm", "Z"}};
            case "ice" -> new String[][] {{"hail", "hail", "R"}, {"frost", "frost", "Z"}};
            // Yas' God Wand modes (cooldowns sent by the server with their own length, by charge level)
            case "singularity" -> new String[][] {{"gw_1", "gw_singularity", "R+"}, {"", "gw_collapse", "R"}};
            case "earth_drill" -> new String[][] {{"gw_2", "gw_earth_drill", "R+"}};
            case "supernova" -> new String[][] {{"gw_3", "gw_supernova", "R+"}};
            case "stone_cannon" -> new String[][] {{"gw_4", "gw_stone_cannon", "R+"}};
            case "atomic" -> new String[][] {{"gw_5", "gw_atomic", "R+"}};
            case "fuga" -> new String[][] {{"gw_6", "gw_fuga", "R+"}};
            default -> new String[0][];
        };
    }

    public static int ticks(String id) {
        return TICKS.getOrDefault(id, 20);
    }

    private ScarletCooldowns() {
    }
}

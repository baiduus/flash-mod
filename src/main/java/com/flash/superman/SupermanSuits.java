package com.flash.superman;

import java.util.Set;
import net.minecraft.network.chat.Component;

/** The Superman suits (models geo/superman/<id>.geo.json, textures textures/superman_suits/<id>/). */
public final class SupermanSuits {

    /** Selectable suits, in the order of the K menu. Index 0 is the classic one. */
    public static final String[] IDS = {
            "superman", "superman_1941", "superman_dcau", "superman_jlw", "superman_justice_lords", "superman_beyond",
            "superman_mos", "superman_bvs", "superman_zsjl", "superman_recovery", "superman_cw", "superman_maws",
            "superman_new52", "superman_rebirth", "superman_injustice", "superman_injustice2", "superman_red_son_v1",
            "superman_red_son_v2", "superman_godfall", "superman_apokolips", "superman_antikryptonite", "superman_sal",
            "superman_wonk"};

    /** Civilian clothes worn as Clark Kent. */
    public static final String CLARK = "superman_clark_kent";

    private static final Set<String> CAPES = Set.of("superman", "superman_1941", "superman_bvs", "superman_cw",
            "superman_dcau", "superman_godfall", "superman_injustice", "superman_injustice2", "superman_jlw",
            "superman_justice_lords", "superman_maws", "superman_mos", "superman_new52", "superman_rebirth",
            "superman_red_son_v1", "superman_red_son_v2", "superman_sal", "superman_zsjl");

    private static final Set<String> OVERLAY = Set.of("superman_antikryptonite", "superman_clark_kent");

    private SupermanSuits() {
    }

    public static int clamp(int index) {
        return index < 0 || index >= IDS.length ? 0 : index;
    }

    public static String id(int index) {
        return IDS[clamp(index)];
    }

    public static boolean hasCape(String id) {
        return CAPES.contains(id);
    }

    public static boolean hasOverlay(String id) {
        return OVERLAY.contains(id);
    }

    public static Component name(int index) {
        return Component.translatable("suit.flash." + id(index));
    }
}

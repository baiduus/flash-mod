package com.flash.superman;

import net.minecraft.network.chat.Component;

/** The moves of the combo key, chosen in the K menu (as the combo wheel of Man of Tomorrow). */
public final class SupermanCombos {

    public static final int SUPER_COMBO = 0;
    public static final int UPLIFT = 1;
    public static final int UPLIFT_COMBO = 2;
    public static final int CHOKEHOLD = 3;
    public static final int EYE_PIERCE = 4;
    public static final int COUNT = 5;

    private static final String[] IDS = {"super_combo", "uplift", "uplift_combo", "chokehold", "eye_pierce"};

    private SupermanCombos() {
    }

    public static String id(int combo) {
        return IDS[Math.floorMod(combo, COUNT)];
    }

    public static Component name(int combo) {
        return Component.translatable("combo.flash." + id(combo));
    }

    /** Finishers: grab range 6 blocks. */
    public static boolean finisher(int combo) {
        return combo == CHOKEHOLD || combo == EYE_PIERCE;
    }
}

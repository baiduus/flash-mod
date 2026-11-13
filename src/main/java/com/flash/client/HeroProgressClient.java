package com.flash.client;

import com.flash.power.HeroAdvancements;

/** The local player's advancements of the mod and his hostile kills (HeroProgressPacket). */
public final class HeroProgressClient {

    private static int kills;
    private static int mask;
    private static int chaosChance = 10;

    private HeroProgressClient() {
    }

    public static void set(int k, int m, int chance) {
        kills = k;
        mask = m;
        chaosChance = chance;
    }

    /** The chance (percent) of the energy of chaos on this server (/scarlet chance). */
    public static int chaosChance() {
        return chaosChance;
    }

    public static void reset() {
        kills = 0;
        mask = 0;
    }

    public static int kills() {
        return kills;
    }

    public static boolean has(String id) {
        for (int i = 0; i < HeroAdvancements.ALL.length; i++) {
            if (HeroAdvancements.ALL[i].equals(id)) {
                return (mask & (1 << i)) != 0;
            }
        }
        return false;
    }
}

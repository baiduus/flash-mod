package com.flash.yas;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The powers of Yas are those of the Hand of God mod (by @onikoox: Celestial, Temporal, Reality and Matter
 * manipulation, Telekinesis, Super speed, Heat vision, Void, Electrokinesis), installed in the same pack. It
 * normally sells its powers for XP levels to anyone; here they belong to Yas alone:
 * <ul>
 * <li>Yas gets the full access of Hand of God ({@code Desbloqueios.definirAcessoTotal});</li>
 * <li>anyone else is kept without it: what he unlocks is taken away at once and the levels he paid come back.</li>
 * </ul>
 * Called by reflection, so the mod works (without these powers) when Hand of God is not installed.
 */
public final class YasHandOfGod {

    private static final String CLASS = "handofgod.servidor.Desbloqueios";
    private static boolean looked;
    private static Method setFull;
    private static Method full;
    private static Method bought;
    private static Method clear;
    private static Method sync;
    /** XP level of each player on the last tick (to give back what Hand of God charged him). */
    private static final Map<UUID, Integer> LEVELS = new HashMap<>();

    private YasHandOfGod() {
    }

    public static boolean present() {
        lookup();
        return setFull != null;
    }

    private static void lookup() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            Class<?> c = Class.forName(CLASS);
            setFull = c.getMethod("definirAcessoTotal", ServerPlayer.class, boolean.class);
            full = c.getMethod("acessoTotal", ServerPlayer.class);
            bought = c.getMethod("comprados", ServerPlayer.class);
            clear = c.getMethod("limpar", ServerPlayer.class);
            sync = c.getMethod("sincronizar", ServerPlayer.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            setFull = null;
        }
    }

    /** Yas has every power of Hand of God, or none. */
    public static void apply(ServerPlayer p, boolean yas) {
        if (!present()) {
            if (yas) {
                p.displayClientMessage(Component.translatable("message.flash.yas_no_hand_of_god"), false);
            }
            return;
        }
        try {
            setFull.invoke(null, p, yas);
            if (!yas) {
                clear.invoke(null, p);
            }
            sync.invoke(null, p);
        } catch (ReflectiveOperationException e) {
            setFull = null;
        }
    }

    /** Every tick: nobody but Yas keeps a power of Hand of God (the levels he paid for it come back). */
    public static void guard(ServerPlayer p, boolean yas) {
        if (!present()) {
            return;
        }
        int level = p.experienceLevel;
        Integer before = LEVELS.put(p.getUUID(), level);
        if (yas || p.isCreative()) {
            return;
        }
        try {
            boolean hasFull = (Boolean) full.invoke(null, p);
            int count = (Integer) bought.invoke(null, p);
            if (hasFull || count > 0) {
                setFull.invoke(null, p, false);
                clear.invoke(null, p);
                sync.invoke(null, p);
                if (before != null && before > level) {
                    p.giveExperienceLevels(before - level);
                }
                LEVELS.put(p.getUUID(), p.experienceLevel);
                p.displayClientMessage(Component.translatable("message.flash.yas_only"), true);
            }
        } catch (ReflectiveOperationException e) {
            setFull = null;
        }
    }
}

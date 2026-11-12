package com.flash.yas;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Telekinesis of Hand of God, with the held creature brought closer or pushed away (scroll while holding it,
 * YasAbilities): changes the distance of the hold ({@code Telecinese.PEGADAS -> Pegada.distancia}) by reflection.
 */
public final class YasTelekinesis {

    public static final double MIN = 2.0D;
    public static final double MAX = 32.0D;
    private static boolean looked;
    private static Field holds;
    private static Field distance;

    private YasTelekinesis() {
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                Class<?> t = Class.forName("handofgod.telecinese.Telecinese");
                holds = t.getDeclaredField("PEGADAS");
                holds.setAccessible(true);
                distance = Class.forName("handofgod.telecinese.Telecinese$Pegada").getDeclaredField("distancia");
                distance.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError e) {
                holds = null;
            }
        }
        return holds != null;
    }

    /** One step of the scroll: closer (negative) or farther (positive). */
    public static void move(ServerPlayer p, int steps) {
        if (!YasPowers.has(p) || !ready()) {
            return;
        }
        try {
            Object hold = ((Map<?, ?>) holds.get(null)).get(p.getUUID());
            if (hold == null) {
                return;
            }
            double d = distance.getDouble(hold);
            double next = Math.max(MIN, Math.min(MAX, d + steps * Math.max(1.0D, d * 0.15D)));
            distance.setDouble(hold, next);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Hand of God changed: nothing to do
        }
    }
}

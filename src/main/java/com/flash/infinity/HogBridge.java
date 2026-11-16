package com.flash.infinity;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The Time and Reality Stones borrow two of Yas' powers from the Hand of God mod (called by reflection, as Yas
 * does): Temporal's fast-forward (held) and Reality's rewriting of the land (hold to charge, let go to cast). Off
 * the Overworld, or without Hand of God, the stones fall back on their own (time add / the mod's reality wave).
 */
public final class HogBridge {

    private static boolean looked;
    private static Method tempo;
    private static Method gesto;

    private HogBridge() {
    }

    private static void lookup() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            tempo = Class.forName("handofgod.tempo.Tempo").getMethod("tecla", ServerPlayer.class, byte.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            tempo = null;
        }
        try {
            gesto = Class.forName("handofgod.realidade.GestoRealidade").getMethod("receber", ServerPlayer.class,
                    byte.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            gesto = null;
        }
    }

    private static boolean call(Method m, ServerPlayer p, int action) {
        if (m == null) {
            return false;
        }
        try {
            m.invoke(null, p, (byte) action);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** flash:hog_time (held): Hand of God's Accelerate, the same as Yas' Z in Temporal. */
    static void time(ServerPlayer p, InfPowers.Ability a, boolean on, boolean was) {
        lookup();
        boolean hog = tempo != null && p.level().dimension() == Level.OVERWORLD;
        if (on && !was) {
            SpaceStone.sound(p.serverLevel(), p.position(), "time_stone", 1.0F, 1.0F);
            if (hog) {
                call(tempo, p, 0);
            }
        } else if (!on && was && hog) {
            call(tempo, p, 1);
        }
        if (on && !hog) {
            // no Hand of God here: the day itself runs fast
            for (var l : p.server.getAllLevels()) {
                l.setDayTime(l.getDayTime() + 100L);
            }
        }
    }

    /** flash:hog_reality (held): charge while held, rewrite the land when let go (again: undo). */
    static void reality(ServerPlayer p, InfPowers.Ability a, boolean on, boolean was) {
        lookup();
        boolean hog = gesto != null && p.level().dimension() == Level.OVERWORLD;
        if (on && !was) {
            if (hog) {
                call(gesto, p, 0);
            } else {
                com.flash.yas.YasRealityWave.startFor(p);
            }
        } else if (!on && was && hog) {
            call(gesto, p, 1);
        }
    }
}

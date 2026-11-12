package com.flash.lex;

import com.flash.network.LexSyncPacket;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client copy of the Lex suits around: what each one is doing, and the cooldowns of the local pilot. */
public final class LexState {

    private static final Map<Integer, Integer> FLAGS = new HashMap<>();
    private static final long[] COOLDOWN_UNTIL = new long[LexAbility.values().length];

    private LexState() {
    }

    public static void handle(LexSyncPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (p.flags() == 0) {
            FLAGS.remove(p.entity());
        } else {
            FLAGS.put(p.entity(), p.flags());
        }
        if (mc.player != null && p.entity() == mc.player.getId() && p.cooldowns().length == COOLDOWN_UNTIL.length) {
            long now = mc.level.getGameTime();
            for (int i = 0; i < COOLDOWN_UNTIL.length; i++) {
                COOLDOWN_UNTIL[i] = now + p.cooldowns()[i];
            }
        }
    }

    public static void reset() {
        FLAGS.clear();
        java.util.Arrays.fill(COOLDOWN_UNTIL, 0L);
    }

    public static boolean flag(Entity e, int flag) {
        return (FLAGS.getOrDefault(e.getId(), 0) & flag) != 0;
    }

    /** Ticks left of a system of the local pilot. */
    public static int cooldown(LexAbility a, long now) {
        return (int) Math.max(0L, COOLDOWN_UNTIL[a.ordinal()] - now);
    }
}

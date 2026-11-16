package com.flash.client.infinity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.entity.Entity;

/** Which creatures the Soul Stone left without a soul (drawn greyish: SoulGrayMixin). */
public final class SoulGray {

    private static final IntOpenHashSet IDS = new IntOpenHashSet();
    public static boolean current;

    private SoulGray() {
    }

    private static final IntOpenHashSet BLUE = new IntOpenHashSet();

    /** The Space Stone's telekinesis (holder and held): Hand of God's energy in light blue (BlueTkMixin). */
    public static boolean blue(Entity e) {
        return e != null && !BLUE.isEmpty() && BLUE.contains(e.getId());
    }

    public static void set(int id, boolean on) {
        if (id <= -2) {
            int real = -2 - id;
            if (on) {
                BLUE.add(real);
            } else {
                BLUE.remove(real);
            }
            return;
        }
        if (on) {
            IDS.add(id);
        } else {
            IDS.remove(id);
        }
    }

    public static boolean is(Entity e) {
        return !IDS.isEmpty() && IDS.contains(e.getId());
    }

    public static void clear() {
        IDS.clear();
    }
}

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
    /** Drawing Hand of God's energy for the Space Stone now (its skin, its pieces): light blue. */
    public static boolean hogBlue;
    private static final java.util.Map<java.util.UUID, Long> BLUE_TORN = new java.util.HashMap<>();
    private static Object blueType;
    private static Object blueGlowType;

    public static void tornBlue(java.util.UUID id) {
        BLUE_TORN.put(id, System.currentTimeMillis() + 15000L);
    }

    public static boolean isTornBlue(java.util.UUID id) {
        Long until = id == null ? null : BLUE_TORN.get(id);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            BLUE_TORN.remove(id);
            return false;
        }
        return true;
    }

    private static Object energy(String tex) {
        try {
            return Class.forName("handofgod.client.render.SolRenderTypes").getMethod("energiaTelecinese",
                    net.minecraft.resources.ResourceLocation.class).invoke(null,
                    new net.minecraft.resources.ResourceLocation("flash", tex));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Hand of God's energy type on the light-blue texture (or its own, when it cannot be made). */
    public static net.minecraft.client.renderer.RenderType blueType(net.minecraft.client.renderer.RenderType own) {
        if (blueType == null) {
            blueType = energy("textures/entity/stone_flight_energy.png");
        }
        return blueType instanceof net.minecraft.client.renderer.RenderType t ? t : own;
    }

    public static net.minecraft.client.renderer.RenderType blueGlowType(
            net.minecraft.client.renderer.RenderType own) {
        if (blueGlowType == null) {
            blueGlowType = energy("textures/entity/stone_tk_glow.png");
        }
        return blueGlowType instanceof net.minecraft.client.renderer.RenderType t ? t : own;
    }

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

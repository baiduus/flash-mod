package com.flash.godwand;

import com.mojang.math.Transformation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The block displays God Wand moves the land with, as 1.20.1 has them: their setters are private here, so their
 * synced values are set straight (found by kind and order, the names being obfuscated at run time). Setting a
 * transformation restarts its interpolation, as in later versions.
 */
@SuppressWarnings("unchecked")
public final class GodWandDisplays {

    private static boolean looked;
    private static EntityDataAccessor<Integer> start;
    private static EntityDataAccessor<Integer> duration;
    private static EntityDataAccessor<Integer> brightness;
    private static EntityDataAccessor<Integer> glow;
    private static EntityDataAccessor<Vector3f> translation;
    private static EntityDataAccessor<Vector3f> scale;
    private static EntityDataAccessor<Quaternionf> left;
    private static EntityDataAccessor<Quaternionf> right;
    private static EntityDataAccessor<BlockState> block;

    private GodWandDisplays() {
    }

    private static List<EntityDataAccessor<?>> accessors(Class<?> type) {
        List<EntityDataAccessor<?>> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == EntityDataAccessor.class) {
                try {
                    f.setAccessible(true);
                    out.add((EntityDataAccessor<?>) f.get(null));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // not this one
                }
            }
        }
        out.sort(Comparator.comparingInt(EntityDataAccessor::getId));
        return out;
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        List<EntityDataAccessor<?>> ints = new ArrayList<>();
        List<EntityDataAccessor<?>> vectors = new ArrayList<>();
        List<EntityDataAccessor<?>> quaternions = new ArrayList<>();
        for (EntityDataAccessor<?> a : accessors(Display.class)) {
            if (a.getSerializer() == EntityDataSerializers.INT) {
                ints.add(a);
            } else if (a.getSerializer() == EntityDataSerializers.VECTOR3) {
                vectors.add(a);
            } else if (a.getSerializer() == EntityDataSerializers.QUATERNION) {
                quaternions.add(a);
            }
        }
        if (ints.size() >= 4) {
            // start of the interpolation, its length, ... the brightness, the glow colour (last two)
            start = (EntityDataAccessor<Integer>) ints.get(0);
            duration = (EntityDataAccessor<Integer>) ints.get(1);
            brightness = (EntityDataAccessor<Integer>) ints.get(ints.size() - 2);
            glow = (EntityDataAccessor<Integer>) ints.get(ints.size() - 1);
        }
        if (vectors.size() >= 2) {
            translation = (EntityDataAccessor<Vector3f>) vectors.get(0);
            scale = (EntityDataAccessor<Vector3f>) vectors.get(1);
        }
        if (quaternions.size() >= 2) {
            left = (EntityDataAccessor<Quaternionf>) quaternions.get(0);
            right = (EntityDataAccessor<Quaternionf>) quaternions.get(1);
        }
        for (EntityDataAccessor<?> a : accessors(Display.BlockDisplay.class)) {
            if (a.getSerializer() == EntityDataSerializers.BLOCK_STATE) {
                block = (EntityDataAccessor<BlockState>) a;
            }
        }
    }

    public static void delay(Display d, int ticks) {
        look();
        if (d != null && start != null) {
            d.getEntityData().set(start, ticks, true);
        }
    }

    public static void duration(Display d, int ticks) {
        look();
        if (d != null && duration != null) {
            d.getEntityData().set(duration, ticks);
        }
    }

    public static void transform(Display d, Transformation t) {
        look();
        if (d == null || translation == null || left == null) {
            return;
        }
        var data = d.getEntityData();
        data.set(translation, new Vector3f(t.getTranslation()));
        data.set(left, new Quaternionf(t.getLeftRotation()));
        data.set(scale, new Vector3f(t.getScale()));
        data.set(right, new Quaternionf(t.getRightRotation()));
        if (start != null) {
            data.set(start, 0, true);
        }
    }

    public static void blockState(Display.BlockDisplay d, BlockState state) {
        look();
        if (d != null && block != null && state != null) {
            d.getEntityData().set(block, state);
        }
    }

    public static void brightness(Display d, Brightness b) {
        look();
        if (d != null && brightness != null) {
            d.getEntityData().set(brightness, b == null ? -1 : b.pack());
        }
    }

    public static void glowColor(Display d, int color) {
        look();
        if (d != null && glow != null) {
            d.getEntityData().set(glow, color);
        }
    }
}

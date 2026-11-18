package com.flash.power;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dropped items burnt away by Superman's heat vision, Thor's lightning and Storm's lightning. The one-of-a-kind ones
 * are spared: the Infinity Stones and the things that hold them, the Gauntlet, Mjolnir and Stormbreaker.
 */
public final class ItemZap {

    private ItemZap() {
    }

    private static boolean spared(ItemStack s) {
        return com.flash.infinity.GroundItems.get(s) != null
                || com.flash.infinity.InfinityStone.of(s) != null
                || s.getItem() instanceof com.flash.infinity.InfinityGauntletItem
                || s.getItem() instanceof com.flash.item.MjolnirItem;
    }

    private static void burn(ServerLevel level, ItemEntity ie) {
        level.sendParticles(ParticleTypes.LAVA, ie.getX(), ie.getY() + 0.2D, ie.getZ(), 2, 0.1D, 0.1D, 0.1D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, ie.getX(), ie.getY() + 0.2D, ie.getZ(), 3, 0.1D, 0.1D, 0.1D,
                0.02D);
        ie.discard();
    }

    /** Items within {@code radius} of a point. */
    public static void around(ServerLevel level, Vec3 at, double radius) {
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && e.position().distanceTo(at) <= radius + 0.3D && !spared(e.getItem()))) {
            burn(level, ie);
        }
    }

    /** Items within {@code radius} of the segment a-b. */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, double radius) {
        Vec3 d = b.subtract(a);
        double len2 = d.lengthSqr();
        if (len2 > 256.0D * 256.0D) {
            return;
        }
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(a, b).inflate(radius),
                e -> e.isAlive() && !spared(e.getItem()))) {
            Vec3 c = ie.position().add(0.0D, 0.15D, 0.0D);
            double t = len2 < 1.0E-6D ? 0.0D : Math.max(0.0D, Math.min(1.0D, c.subtract(a).dot(d) / len2));
            if (c.distanceTo(a.add(d.scale(t))) <= radius) {
                burn(level, ie);
            }
        }
    }
}

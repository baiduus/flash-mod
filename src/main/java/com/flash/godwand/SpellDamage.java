package com.flash.godwand;

import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;

/** How God Wand's spells hurt (the dragon through its head; the caster never). */
public final class SpellDamage {

    private SpellDamage() {
    }

    public static boolean isCaster(Entity target, Entity caster) {
        return target != null && caster != null && target.getUUID().equals(caster.getUUID());
    }

    public static boolean hurt(Entity target, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F || !(target instanceof LivingEntity living)
                || !(living.level() instanceof ServerLevel)) {
            return false;
        }
        if (living instanceof EnderDragon dragon) {
            return dragon.hurt(dragon.head, source, amount);
        }
        return living.hurt(source, amount);
    }

    public static void poolExperience(ServerLevel world) {
    }

    public static UUID identity(Entity target) {
        return target.getUUID();
    }

    /** (God Wand's extra boss parts of other mods: none here.) */
    public static List<Entity> extraTargets(ServerLevel world, AABB area) {
        return List.of();
    }

    public static void hurtExtras(ServerLevel world, AABB area, DamageSource source, ToDoubleFunction<Entity> damage) {
    }
}

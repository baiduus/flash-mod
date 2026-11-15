package com.flash.infinity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The original's immunities (its InfinityImmunity): an entity with an enabled immunity ability of a kind (the stone
 * of that kind in hand gives it: mental, reality, soul, spatial, time) is not touched by the other stones' powers of
 * that kind.
 */
public final class InfinityImmunity {

    private InfinityImmunity() {
    }

    /** kind: "mental", "reality", "soul", "spatial" (also "black_hole", "telekinesis"), "time". */
    public static boolean immune(Entity e, String kind) {
        if (!(e instanceof ServerPlayer p)) {
            return false;
        }
        String type = switch (kind) {
            case "black_hole", "telekinesis", "spatial" -> "flash:spatial_immunity";
            case "mental" -> "flash:mental_immunity";
            case "reality" -> "flash:reality_immunity";
            case "soul" -> "flash:soul_immunity";
            default -> "flash:time_immunity";
        };
        for (String key : InfinityServer.state(p).enabled) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a != null && type.equals(a.type)) {
                return true;
            }
        }
        return false;
    }
}

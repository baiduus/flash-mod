package com.flash.infinity;

import com.flash.FlashMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.common.Mod;

/** The Soul Stone. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SoulStone {

    public static final String DRAINED = "flash_soul_drained";

    private SoulStone() {
    }

    public static boolean drained(Entity e) {
        return e.getPersistentData().getBoolean(DRAINED);
    }

    /** The online player of that name, else the closest entity with that name (not soul-immune). */
    public static LivingEntity locate(ServerPlayer p, String name) {
        ServerPlayer target = p.server.getPlayerList().getPlayerByName(name);
        if (target != null && !InfinityImmunity.immune(target, "soul")) {
            return target;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ServerLevel level : p.server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof LivingEntity l && e.hasCustomName() && name.equals(e.getCustomName().getString())
                        && !InfinityImmunity.immune(e, "soul")) {
                    double d = e.level() == p.level() ? e.distanceToSqr(p) : 1.0E12D;
                    if (d < bd) {
                        bd = d;
                        best = l;
                    }
                }
            }
        }
        return best;
    }
}

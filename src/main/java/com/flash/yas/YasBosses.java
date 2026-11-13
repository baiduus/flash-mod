package com.flash.yas;

import com.flash.FlashMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Yas' Matter manipulation, Void and Telekinesis reach bosses (only hers: HandOfGodBossMixin): a living creature
 * that Hand of God would refuse for being a boss (multi-part, or on its exclusion list, the Ender Dragon and the
 * Wither by default) is a valid target when she aims at it.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasBosses {

    private YasBosses() {
    }

    /** True: Yas aims at a boss, let it through (anything else is left to Hand of God's own checks). */
    public static boolean allowed(ServerPlayer p, Entity e) {
        if (p == null || e == null || e == p || !YasPowers.has(p)) {
            return false;
        }
        // the parts of a multi-part boss are not creatures themselves: only the boss's own body is a target
        if (!(e instanceof Mob mob) || !mob.isAlive() || mob.isRemoved() || mob.isPassenger() || mob.isVehicle()) {
            return false;
        }
        return mob instanceof EnderDragon || mob.isMultipartEntity() || !mob.canChangeDimensions()
                || mob.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
    }

    /**
     * The Ender Dragon unmade by her powers is taken away (not killed): the End fight is told it died, so the exit
     * portal and the egg come as after a normal fight.
     */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon && event.getLevel() instanceof ServerLevel level
                && dragon.getRemovalReason() == Entity.RemovalReason.DISCARDED && dragon.getHealth() > 0.0F
                && level.getDragonFight() != null) {
            try {
                level.getDragonFight().setDragonKilled(dragon);
            } catch (RuntimeException ignored) {
                // the fight was already over
            }
        }
    }
}

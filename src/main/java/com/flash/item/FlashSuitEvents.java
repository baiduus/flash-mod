package com.flash.item;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Extra damage reduction of the suits. Each piece removes 22 % of the damage
 * (88 % with the full set, above a full netherite set) without raising the
 * armor-bar count, because the suit material has zero vanilla defense.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID)
public final class FlashSuitEvents {

    public static final float REDUCTION_PER_PIECE = 0.22F;

    private FlashSuitEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        int pieces = FlashSuitItem.countWorn(player);
        if (pieces <= 0) {
            return;
        }
        float factor = Math.max(0.0F, 1.0F - REDUCTION_PER_PIECE * pieces);
        event.setAmount(event.getAmount() * factor);
    }
}

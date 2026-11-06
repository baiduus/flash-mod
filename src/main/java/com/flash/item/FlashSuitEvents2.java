package com.flash.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suit pieces created by the ring only exist while worn: they vanish anywhere else. */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID)
public final class FlashSuitEvents2 {

    private FlashSuitEvents2() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || event.player.tickCount % 10 != 0) {
            return;
        }
        Player player = event.player;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (FlashSuitUtil.isRingPiece(stack)) {
                player.getInventory().items.set(i, ItemStack.EMPTY);
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (FlashSuitUtil.isRingPiece(offhand)) {
            player.getInventory().offhand.set(0, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            event.getDrops().removeIf(drop -> FlashSuitUtil.isRingPiece(drop.getItem()));
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (FlashSuitUtil.isRingPiece(event.getEntity().getItem())) {
            event.getEntity().discard();
            event.setCanceled(true);
        }
    }
}

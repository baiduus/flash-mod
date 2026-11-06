package com.flash.entity;

import com.flash.FlashMod;
import com.flash.item.FlashSuitUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Crouch + right click on the suit stand. Handled through the interaction events
 * (before any vanilla armour-stand logic, both for the "interact at" and the plain
 * "interact" packets), so nothing else can take the click first. The swap runs
 * once per click on the server; the client just reports success.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class FlashSuitStandEvents {

    private static final String LAST_SWAP = "flash_stand_swap_tick";

    private FlashSuitStandEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof FlashSuitStand stand) {
            handle(event, event.getEntity(), stand, event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof FlashSuitStand stand) {
            handle(event, event.getEntity(), stand, event.getHand());
        }
    }

    private static void handle(PlayerInteractEvent event, Player player, FlashSuitStand stand,
                               InteractionHand hand) {
        if (!player.getAbilities().instabuild
                && (stand.isExhibit() || (!player.level().isClientSide && FlashMuseumGuard.isExhibit(stand)))) {
            // museum exhibit: look, don't touch (FlashMuseumGuard shows the message)
            event.setCanceled(true);
            setResult(event, InteractionResult.FAIL);
            return;
        }
        if (!player.isShiftKeyDown() || stand.isMarker() || player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
        if (hand != InteractionHand.MAIN_HAND) {
            // the off hand repeats the same click: ignore it, but keep vanilla out of it too
            setResult(event, InteractionResult.SUCCESS);
            return;
        }
        if (player.level().isClientSide) {
            setResult(event, InteractionResult.SUCCESS);
            return;
        }
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(LAST_SWAP) == now) {
            setResult(event, InteractionResult.CONSUME); // second packet of the same click
            return;
        }
        player.getPersistentData().putLong(LAST_SWAP, now);
        boolean swapped = FlashSuitUtil.swapWithStand(player, stand);
        if (!swapped) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.flash.stand_nothing"), true);
        }
        setResult(event, InteractionResult.CONSUME);
    }

    private static void setResult(PlayerInteractEvent event, InteractionResult result) {
        if (event instanceof PlayerInteractEvent.EntityInteractSpecific specific) {
            specific.setCancellationResult(result);
        } else if (event instanceof PlayerInteractEvent.EntityInteract plain) {
            plain.setCancellationResult(result);
        }
    }
}

package com.flash.client;

import com.flash.item.FlashSuitItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hides the skin's second layer (hat, jacket, sleeves, pants) under the matching
 * suit piece, so the skin never pokes out of the suit: the suit takes the place
 * of the skin.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class FlashSuitSkinHider {

    private FlashSuitSkinHider() {
    }

    @SubscribeEvent
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Object model = event.getRenderer().getModel();
        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }
        if (worn(player, EquipmentSlot.HEAD)) {
            playerModel.hat.visible = false;
        }
        if (worn(player, EquipmentSlot.CHEST)) {
            playerModel.jacket.visible = false;
            playerModel.leftSleeve.visible = false;
            playerModel.rightSleeve.visible = false;
        }
        if (worn(player, EquipmentSlot.LEGS) || worn(player, EquipmentSlot.FEET)) {
            playerModel.leftPants.visible = false;
            playerModel.rightPants.visible = false;
        }
    }

    private static boolean worn(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getItem() instanceof FlashSuitItem;
    }
}

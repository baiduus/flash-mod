package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Flash ring: it holds the compressed suit. Using it puts the whole suit on
 * the wearer instantly (positive or negative, following the player's force);
 * using it again takes the suit off and gives the old clothes back.
 */
public class FlashRingItem extends Item {

    public FlashRingItem() {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack ring = player.getItemInHand(hand);
        if (!level.isClientSide) {
            FlashSuitUtil.toggleRing(player, ring);
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(ring, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(FlashSuitUtil.ringHasSuit(stack)
                ? "tooltip.flash.ring.full" : "tooltip.flash.ring.empty").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.ring.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.ring.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}

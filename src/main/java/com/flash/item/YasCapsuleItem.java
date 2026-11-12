package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Yas's essence, taken out by a syringe: it only answers to the player who transcended (named on it). */
public class YasCapsuleItem extends Item {

    public YasCapsuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.yas_essence_capsule").withStyle(ChatFormatting.YELLOW,
                ChatFormatting.ITALIC));
        if (stack.hasTag() && stack.getTag().contains(SyringeItem.YAS_OWNER_NAME)) {
            tooltip.add(Component.translatable("tooltip.flash.yas_owner",
                    stack.getTag().getString(SyringeItem.YAS_OWNER_NAME)).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.flash.power_capsule").withStyle(ChatFormatting.GRAY));
    }
}

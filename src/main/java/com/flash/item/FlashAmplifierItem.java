package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Tachyon Amplifier: equipped from the K screen, raises the maximum speed level to 20. */
public class FlashAmplifierItem extends Item {

    public FlashAmplifierItem() {
        super(new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.amplifier.1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.flash.amplifier.2").withStyle(ChatFormatting.GRAY));
    }
}

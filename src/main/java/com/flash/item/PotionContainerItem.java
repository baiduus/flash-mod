package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class PotionContainerItem extends BlockItem {

    public PotionContainerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.potion_container")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.potion_container.2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

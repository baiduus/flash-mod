package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Plain item with one or more grey tooltip lines (lang keys). */
public class FlashTooltipItem extends Item {

    private final String[] keys;

    public FlashTooltipItem(Properties properties, String... keys) {
        super(properties);
        this.keys = keys;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 0; i < this.keys.length; i++) {
            tooltip.add(Component.translatable(this.keys[i])
                    .withStyle(i == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}

package com.flash.infinity;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * An Infinity Stone: held in a hand it gives its power (its ability tree: InfinityPowers); it can also be worn on
 * the head (the original's Curios head slot: the stone floats at the forehead). Glows in its colour (the item colour
 * handler, InfinityClientSetup).
 */
public class InfinityStoneItem extends Item {

    public final InfinityStone stone;

    public InfinityStoneItem(InfinityStone stone, Properties props) {
        super(props);
        this.stone = stone;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(this.stone.tooltip);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.flash.infinity_stone").withStyle(this.stone.tooltip));
    }
}

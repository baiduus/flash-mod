package com.flash.item;

import com.flash.superman.SupermanPowers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;

/**
 * Kryptonian baby (a joke item, found in the crashed Kryptonian ship): eating it makes the player a temporary
 * Superman for {@link #TICKS} ticks.
 */
public class KryptonianBabyItem extends Item {

    public static final int TICKS = 12000;

    public KryptonianBabyItem() {
        super(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)
                .food(new FoodProperties.Builder().nutrition(4).saturationMod(0.6F).alwaysEat().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack out = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            SupermanPowers.grantTemporary(player, TICKS);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS, 0.8F, 1.3F);
        }
        return out;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.kryptonian_baby").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.kryptonian_baby.2").withStyle(net.minecraft.ChatFormatting.BLUE));
    }
}

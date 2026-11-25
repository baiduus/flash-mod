package com.flash.ultron;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Ultron's nanocore: used, it is taken in and the nanites cover the player in Ultron's body. Only for a player with
 * no power. The surgery table takes it out again and gives it back.
 */
public class UltronNanocoreItem extends Item {

    public UltronNanocoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer p)) {
            return InteractionResultHolder.pass(stack);
        }
        if (UltronPowers.has(p)) {
            p.displayClientMessage(Component.translatable("message.flash.ultron.already"), true);
            return InteractionResultHolder.fail(stack);
        }
        com.flash.power.HeroPowers.Hero other = com.flash.power.HeroPowers.current(p);
        if (other != null) {
            p.displayClientMessage(Component.translatable("message.flash.inject_has_power", other.displayName()),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        stack.shrink(1);
        UltronPowers.grant(p);
        p.displayClientMessage(Component.translatable("message.flash.ultron.become").withStyle(ChatFormatting.RED),
                false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.ultron_nanocore").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.ultron_nanocore.2").withStyle(ChatFormatting.DARK_RED));
    }
}

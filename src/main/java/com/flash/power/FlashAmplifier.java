package com.flash.power;

import com.flash.item.FlashAmplifierItem;
import com.flash.item.FlashSuitUtil;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Tachyon Amplifier slot (K screen, Equipment tab). While the device is in
 * the slot and the player has reached level 10, the maximum speed level rises to 20.
 */
public final class FlashAmplifier {

    public static final String KEY = "flash_amplifier_slot";

    private FlashAmplifier() {
    }

    public static ItemStack get(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(data.getCompound(KEY));
    }

    public static boolean isEquipped(Player player) {
        return player.getPersistentData().contains(KEY) && !get(player).isEmpty();
    }

    private static void set(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            player.getPersistentData().remove(KEY);
        } else {
            player.getPersistentData().put(KEY, stack.save(new CompoundTag()));
        }
    }

    public static void equip(ServerPlayer player) {
        if (isEquipped(player)) {
            return;
        }
        ItemStack found = ItemStack.EMPTY;
        if (player.getOffhandItem().getItem() instanceof FlashAmplifierItem) {
            found = player.getOffhandItem();
        }
        if (player.getMainHandItem().getItem() instanceof FlashAmplifierItem) {
            found = player.getMainHandItem();
        }
        if (found.isEmpty()) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof FlashAmplifierItem) {
                    found = stack;
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flash.amplifier_missing"), true);
            return;
        }
        ItemStack one = found.copy();
        one.setCount(1);
        set(player, one);
        found.shrink(1);
        player.displayClientMessage(Component.translatable("message.flash.amplifier_equipped"), true);
        refresh(player);
    }

    public static void unequip(ServerPlayer player) {
        ItemStack stack = get(player);
        if (stack.isEmpty()) {
            return;
        }
        set(player, ItemStack.EMPTY);
        FlashSuitUtil.giveOrDrop(player, stack);
        player.displayClientMessage(Component.translatable("message.flash.amplifier_removed"), true);
        refresh(player);
    }

    /** Re-applies everything that depends on the speed level and tells the clients. */
    private static void refresh(ServerPlayer player) {
        FlashSpeed.apply(player);
        FlashStep.apply(player);
        FlashCombustion.onLevelChanged(player);
        FlashStatusEffects.onLevelChanged(player);
        FlashVisualSyncPacket.send(player);
        FlashSyncPacket.send(player);
        FlashRing.sync(player);
    }

    public static void copy(Player from, Player to) {
        CompoundTag data = from.getPersistentData();
        if (data.contains(KEY)) {
            to.getPersistentData().put(KEY, data.getCompound(KEY).copy());
        }
    }
}

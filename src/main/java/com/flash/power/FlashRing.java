package com.flash.power;

import com.flash.item.FlashRingItem;
import com.flash.item.FlashSuitItem;
import com.flash.item.FlashSuitUtil;
import com.flash.network.FlashNetwork;
import com.flash.network.FlashRingSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/**
 * The ring slot of a player (opened from the K screen). A ring in the slot is
 * shown on the player's hand and lets a key put the suit on or take it off.
 * The ring is stored in the player's persistent data.
 */
public final class FlashRing {

    public static final String KEY = "flash_ring_slot";

    private FlashRing() {
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

    private static void set(Player player, ItemStack ring) {
        if (ring.isEmpty()) {
            player.getPersistentData().remove(KEY);
        } else {
            player.getPersistentData().put(KEY, ring.save(new CompoundTag()));
        }
    }

    /** Moves a ring from the inventory into the slot. */
    public static void equip(ServerPlayer player) {
        if (isEquipped(player)) {
            return;
        }
        ItemStack found = ItemStack.EMPTY;
        if (player.getOffhandItem().getItem() instanceof FlashRingItem) {
            found = player.getOffhandItem();
        }
        if (player.getMainHandItem().getItem() instanceof FlashRingItem) {
            found = player.getMainHandItem();
        }
        if (found.isEmpty()) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof FlashRingItem) {
                    found = stack;
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flash.ring_missing"), true);
            return;
        }
        ItemStack one = found.copy();
        one.setCount(1);
        set(player, one);
        found.shrink(1);
        player.displayClientMessage(Component.translatable("message.flash.ring_equipped"), true);
        sync(player);
    }

    /** Returns the ring to the inventory (taking the suit off first). */
    public static void unequip(ServerPlayer player) {
        ItemStack ring = get(player);
        if (ring.isEmpty()) {
            return;
        }
        if (FlashSuitItem.countWorn(player) > 0) {
            FlashSuitUtil.stowToRing(player, ring);
        }
        set(player, ItemStack.EMPTY);
        FlashSuitUtil.giveOrDrop(player, ring);
        player.displayClientMessage(Component.translatable("message.flash.ring_removed"), true);
        sync(player);
    }

    /** Key / button: suit on or off using the ring in the slot. */
    public static void toggleSuit(ServerPlayer player) {
        ItemStack ring = get(player);
        if (ring.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flash.ring_needed"), true);
            return;
        }
        if (FlashSuitItem.countWorn(player) > 0) {
            FlashSuitUtil.stowToRing(player, ring);
        } else {
            FlashSuitUtil.deployFromRing(player, ring);
        }
        set(player, ring);
    }

    public static void sync(ServerPlayer player) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new FlashRingSyncPacket(player.getUUID(), isEquipped(player)));
    }

    /** Tells one client about the ring state of another player it just started tracking. */
    public static void syncTo(ServerPlayer listener, ServerPlayer target) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener),
                new FlashRingSyncPacket(target.getUUID(), isEquipped(target)));
    }

    public static void copy(Player from, Player to) {
        CompoundTag data = from.getPersistentData();
        if (data.contains(KEY)) {
            to.getPersistentData().put(KEY, data.getCompound(KEY).copy());
        }
    }
}

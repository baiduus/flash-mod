package com.flash.network;

import com.flash.power.FlashCombustion;
import com.flash.power.FlashPowers;
import com.flash.power.FlashSpeed;
import com.flash.power.FlashStatusEffects;
import com.flash.power.FlashStep;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Turns the speed on or off while keeping the level: pressing it at level 5
 * drops to level 0 and remembers 5; pressing it again goes straight back to 5.
 */
public record FlashToggleSpeedPacket() {

    private static final String PREVIOUS = "toggle_previous_level";

    public void encode(FriendlyByteBuf buffer) {
    }

    public FlashToggleSpeedPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player == null) {
            return;
        }
        if (!FlashPowers.hasPowers(player)) {
            player.displayClientMessage(Component.translatable("message.flash.no_powers"), true);
            FlashSyncPacket.send(player);
            return;
        }
        if (FlashPowers.isTemporary(player)) {
            player.displayClientMessage(
                    Component.translatable("message.flash.temporary_level_fixed"), true);
            FlashSyncPacket.send(player);
            return;
        }

        CompoundTag flash = player.getPersistentData().getCompound(FlashPowers.ROOT);
        int current = FlashPowers.getSelectedLevel(player);
        int target;
        if (current > 0) {
            flash.putInt(PREVIOUS, current);
            target = 0;
        } else {
            int max = FlashPowers.getMaxLevel(player);
            int previous = flash.contains(PREVIOUS) ? flash.getInt(PREVIOUS) : max;
            target = Math.max(1, Math.min(previous, max));
        }
        player.getPersistentData().put(FlashPowers.ROOT, flash);

        FlashPowers.setSelectedLevel(player, target);
        FlashSpeed.apply(player);
        FlashStep.apply(player);
        FlashCombustion.onLevelChanged(player);
        FlashStatusEffects.onLevelChanged(player);
        FlashVisualSyncPacket.send(player);
        player.displayClientMessage(Component.translatable(
                target == 0 ? "message.flash.speed_off" : "message.flash.speed_on", target), true);
        FlashSyncPacket.send(player);
    }
}

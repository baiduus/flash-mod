package com.flash.network;

import com.flash.power.FlashCombustion;
import com.flash.power.FlashPowers;
import com.flash.power.FlashStatusEffects;
import com.flash.power.FlashSpeed;
import com.flash.power.FlashSpeedLevels;
import com.flash.power.FlashStep;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client request to switch the selected speed level. The server validates
 * ownership and the unlocked cap before changing anything, so a modified
 * client cannot select locked levels.
 */
public record FlashSelectLevelPacket(int level) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.level);
    }

    public FlashSelectLevelPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }

        if (!FlashPowers.hasPowers(player)) {
            player.displayClientMessage(Component.translatable("message.flash.no_powers"), true);
            FlashSyncPacket.send(player);
            context.setPacketHandled(true);
            return;
        }

        if (FlashPowers.isTemporary(player)) {
            player.displayClientMessage(
                    Component.translatable("message.flash.temporary_level_fixed"), true);
            FlashSyncPacket.send(player);
            context.setPacketHandled(true);
            return;
        }

        int max = FlashPowers.getMaxLevel(player);
        int requested = Math.max(FlashSpeedLevels.MIN_LEVEL,
                Math.min(this.level, FlashSpeedLevels.MAX_LEVEL));
        if (requested > max) {
            player.displayClientMessage(Component.translatable(
                    "message.flash.level_locked",
                    Component.translatable("level.flash." + requested)
            ), true);
            FlashSyncPacket.send(player);
            context.setPacketHandled(true);
            return;
        }

        FlashPowers.setSelectedLevel(player, requested);
        FlashSpeed.apply(player);
        FlashStep.apply(player);
        FlashCombustion.onLevelChanged(player);
        FlashStatusEffects.onLevelChanged(player);
        FlashVisualSyncPacket.send(player);
        player.displayClientMessage(Component.translatable(
                "message.flash.level_selected",
                requested
        ), true);
        FlashSyncPacket.send(player);
        context.setPacketHandled(true);
    }
}

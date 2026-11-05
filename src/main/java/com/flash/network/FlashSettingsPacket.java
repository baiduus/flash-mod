package com.flash.network;

import com.flash.power.FlashPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server update for the global block-destruction preference. */
public record FlashSettingsPacket(boolean destroyBlocks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.destroyBlocks);
    }

    public FlashSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            FlashPowers.setDestroyBlocksEnabled(player, this.destroyBlocks);
            player.displayClientMessage(Component.translatable(
                    this.destroyBlocks
                            ? "message.flash.destroy_blocks_enabled"
                            : "message.flash.destroy_blocks_disabled"), true);
            FlashSyncPacket.send(player);
        }
        context.setPacketHandled(true);
    }
}

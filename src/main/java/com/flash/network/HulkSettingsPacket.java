package com.flash.network;

import com.flash.hulk.HulkPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Hulk options of the K screen (block destruction, growing with the level). */
public record HulkSettingsPacket(boolean destroy, boolean grow) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.destroy);
        buffer.writeBoolean(this.grow);
    }

    public HulkSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            HulkPowers.setSettings(player, this.destroy, this.grow);
        }
    }
}

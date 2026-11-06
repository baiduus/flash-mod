package com.flash.network;

import com.flash.hulk.HulkPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Hulk options of the K screen (block destruction, growing with the level, chosen height). */
public record HulkSettingsPacket(boolean destroy, boolean grow, float height) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.destroy);
        buffer.writeBoolean(this.grow);
        buffer.writeFloat(this.height);
    }

    public HulkSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null && Float.isFinite(this.height)) {
            HulkPowers.setSettings(player, this.destroy, this.grow, this.height);
        }
    }
}

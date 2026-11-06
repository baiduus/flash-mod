package com.flash.network;

import com.flash.hulk.HulkPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Hulk level selector (+1 / -1). */
public record HulkLevelPacket(int direction) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.direction > 0 ? 1 : -1);
    }

    public HulkLevelPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            HulkPowers.selectLevel(player, this.direction);
        }
    }
}

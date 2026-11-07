package com.flash.network;

import com.flash.superman.SupermanPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: the Superman suit chosen in the K menu. */
public record SupermanSuitPacket(int suit) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.suit);
    }

    public SupermanSuitPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null && SupermanPowers.has(player)) {
            SupermanPowers.setSuit(player, this.suit);
        }
    }
}

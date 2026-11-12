package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: a key of Lex Luthor's suit (missiles / beam) went down or up. */
public record LexKeyPacket(int key, boolean pressed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.key);
        buffer.writeBoolean(this.pressed);
    }

    public LexKeyPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            com.flash.lex.LexSuit.key(player, this.key, this.pressed);
        }
    }
}

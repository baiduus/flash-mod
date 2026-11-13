package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: his answer to the energy of chaos (ScarletChaos). */
public record ScarletAnswerPacket(boolean accept) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.accept);
    }

    public ScarletAnswerPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.scarlet.ScarletChaos.answer(p, this.accept);
        }
    }
}

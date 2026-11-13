package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: Yas opens (or collapses) her black hole (YasBlackHoles). */
public record YasBlackHolePacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public YasBlackHolePacket(FriendlyByteBuf buffer) {
        this();
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.yas.YasBlackHoles.cast(p);
        }
    }
}

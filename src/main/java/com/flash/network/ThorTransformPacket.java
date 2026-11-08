package com.flash.network;

import com.flash.thor.ThorPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: civil clothes <-> Thor. */
public record ThorTransformPacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public ThorTransformPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            ThorPowers.toggle(player);
        }
    }
}

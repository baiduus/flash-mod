package com.flash.network;

import com.flash.hulk.HulkPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: the transform key (Bruce Banner <-> Hulk). */
public record HulkTransformPacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public HulkTransformPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            HulkPowers.toggle(player);
        }
    }
}

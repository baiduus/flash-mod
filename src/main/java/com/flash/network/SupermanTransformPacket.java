package com.flash.network;

import com.flash.superman.SupermanPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Clark Kent <-> Superman. */
public record SupermanTransformPacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public SupermanTransformPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            SupermanPowers.toggle(player);
        }
    }
}

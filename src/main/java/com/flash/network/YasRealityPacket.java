package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: the Manipulation of Reality on a creature (YasReality). */
public record YasRealityPacket(int entity) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
    }

    public YasRealityPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null && this.entity == -2) {
            com.flash.yas.YasRealityWave.start(p);
        } else if (p != null) {
            com.flash.yas.YasReality.start(p, this.entity);
        }
    }
}

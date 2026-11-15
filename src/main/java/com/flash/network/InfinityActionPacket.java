package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: buy an ability, the ability key pressed / let go, a switch clicked (InfinityServer.handle). */
public record InfinityActionPacket(int op, String key) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.op);
        buffer.writeUtf(this.key, 128);
    }

    public InfinityActionPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readUtf(128));
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.infinity.InfinityServer.handle(p, this.op, this.key);
        }
    }
}

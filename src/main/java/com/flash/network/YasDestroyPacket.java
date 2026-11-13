package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Yas' block destruction switch: player -> server when she flips it, server -> her with its value. */
public record YasDestroyPacket(boolean destroy) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.destroy);
    }

    public YasDestroyPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.yas.YasDestruction.set(p, this.destroy);
        } else {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.flash.client.YasClient.localDestroy = this.destroy);
        }
    }
}

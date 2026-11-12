package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> player: the Guardian asks whether he wishes to transcend (opens YasDialogScreen). */
public record YasDialogPacket(int guardian) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.guardian);
    }

    public YasDialogPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.YasClient.openDialog(this.guardian));
    }
}

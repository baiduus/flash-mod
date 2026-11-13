package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> player: the lightning that struck him asks whether he wants the power of the storms (StormOfferScreen). */
public record StormOfferPacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public StormOfferPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> com.flash.client.StormOfferScreen::open);
    }
}

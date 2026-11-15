package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> player: the bite (Spider-Man) or the grimoire (Doctor Doom) asks whether he wants the power. */
public record HeroOfferPacket(int hero) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.hero);
    }

    public HeroOfferPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.HeroOfferScreen.open(this.hero));
    }
}

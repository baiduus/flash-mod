package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> player: the energy of chaos is before him, asking (ChaosOfferScreen). */
public record ScarletOfferPacket(int entity) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
    }

    public ScarletOfferPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> net.minecraft.client.Minecraft.getInstance()
                        .setScreen(new com.flash.client.ChaosOfferScreen()));
    }
}

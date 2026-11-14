package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> a player the Scarlet Witch raised from the dead: the death screen closes (he is alive again). */
public record ScarletRaisedPacket(int unused) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.unused);
    }

    public ScarletRaisedPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.ScarletTornClient.raised());
    }
}

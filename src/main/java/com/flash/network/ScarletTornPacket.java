package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> players around: a player torn apart by the Scarlet Witch's telekinesis (ticks left, 0 = whole again),
 * so his body is not drawn and Hand of God's pieces stay meanwhile (ScarletTornClient).
 */
public record ScarletTornPacket(int entity, int ticks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeVarInt(this.ticks);
    }

    public ScarletTornPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.ScarletTornClient.receive(this.entity, this.ticks));
    }
}

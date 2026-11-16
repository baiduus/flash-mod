package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: a creature lost (or got back) its soul: drawn greyish. */
public record SoulGrayPacket(int entity, boolean on) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeBoolean(this.on);
    }

    public SoulGrayPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.infinity.SoulGray.set(this.entity, this.on)));
    }
}

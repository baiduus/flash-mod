package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: a player is drawn as this creature ("" : as himself again). */
public record PlayerMorphPacket(int entity, String type) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeUtf(this.type);
    }

    public PlayerMorphPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.MorphClient.set(this.entity, this.type)));
    }
}

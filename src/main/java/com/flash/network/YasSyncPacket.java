package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a player is (or is no longer) Yas, for her look (lightning on the body, white eyes). */
public record YasSyncPacket(int entity, boolean yas) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeBoolean(this.yas);
    }

    public YasSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.YasClient.setYas(this.entity, this.yas));
    }
}

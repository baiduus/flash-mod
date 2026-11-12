package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> clients: a player is (or is no longer) Yas, for her look (lightning on the body, white eyes), and
 * whether she flies (her flight poses).
 */
public record YasSyncPacket(int entity, boolean yas, boolean flying, boolean lightning, boolean eyes) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeBoolean(this.yas);
        buffer.writeBoolean(this.flying);
        buffer.writeBoolean(this.lightning);
        buffer.writeBoolean(this.eyes);
    }

    public YasSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.YasClient.setYas(this.entity, this.yas, this.flying, this.lightning, this.eyes));
    }
}

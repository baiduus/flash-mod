package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a player is (or is no longer) Storm, and whether she flies. */
public record StormSyncPacket(int entity, boolean storm, boolean flying) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeBoolean(this.storm);
        buffer.writeBoolean(this.flying);
    }

    public StormSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.StormClient.set(this.entity, this.storm, this.flying));
    }
}

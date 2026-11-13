package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a player is (or is no longer) the Scarlet Witch, and whether she flies (her red aura). */
public record ScarletSyncPacket(int entity, boolean scarlet, boolean flying) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeBoolean(this.scarlet);
        buffer.writeBoolean(this.flying);
    }

    public ScarletSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.ScarletClient.set(this.entity, this.scarlet, this.flying));
    }
}

package com.flash.network;

import com.flash.client.FlashClientRing;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: whether a player has the ring in the slot (drives the hand model and the K tab). */
public record FlashRingSyncPacket(UUID playerId, boolean equipped) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.equipped);
    }

    public FlashRingSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashClientRing.set(this.playerId, this.equipped));
        context.setPacketHandled(true);
    }
}

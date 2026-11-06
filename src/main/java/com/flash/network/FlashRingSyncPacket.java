package com.flash.network;

import com.flash.client.FlashClientRing;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: which slot items (ring, tachyon amplifier) a player has equipped. */
public record FlashRingSyncPacket(UUID playerId, boolean equipped, boolean amplifier) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.equipped);
        buffer.writeBoolean(this.amplifier);
    }

    public FlashRingSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashClientRing.set(this.playerId, this.equipped, this.amplifier));
        context.setPacketHandled(true);
    }
}

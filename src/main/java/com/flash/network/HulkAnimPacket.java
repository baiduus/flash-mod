package com.flash.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a Hulk used an ability, so its animation plays on his model. */
public record HulkAnimPacket(UUID playerId, int abilityId) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeVarInt(this.abilityId);
    }

    public HulkAnimPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.flash.client.HulkAnimator.playAbility(this.playerId, this.abilityId)));
        context.setPacketHandled(true);
    }
}

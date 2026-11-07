package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: a limb cut off by the laser (or just blood when {@code limb} is 0). */
public record SupermanCutPacket(int entityId, int mask, int limb, float x, float y, float z) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeVarInt(this.mask);
        buffer.writeVarInt(this.limb);
        buffer.writeFloat(this.x);
        buffer.writeFloat(this.y);
        buffer.writeFloat(this.z);
    }

    public SupermanCutPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.flash.client.SupermanLimbs.onCut(this.entityId, this.mask, this.limb, this.x, this.y, this.z)));
        context.setPacketHandled(true);
    }
}

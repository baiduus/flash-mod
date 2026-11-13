package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> the Scarlet Witch: one of her abilities went on cooldown ({@code ticks}; 0 clears it). */
public record ScarletCooldownPacket(String id, int ticks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.id, 32);
        buffer.writeVarInt(this.ticks);
    }

    public ScarletCooldownPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.ScarletClient.startLocal(this.id, this.ticks));
    }
}

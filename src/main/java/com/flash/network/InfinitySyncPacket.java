package com.flash.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> player: its Infinity Stones state (InfinityServer.sync: powers, bought, settings, on, cooldowns). */
public record InfinitySyncPacket(CompoundTag data) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeNbt(this.data);
    }

    public InfinitySyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readNbt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.infinity.InfinityClient.receive(this.data));
    }
}

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> player: the hostile creatures he killed and which of the mod's advancements he has (bits in the order
 * of HeroAdvancements.ALL), for the hero guide of the K menu.
 */
public record HeroProgressPacket(int kills, int mask) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.kills);
        buffer.writeVarInt(this.mask);
    }

    public HeroProgressPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.HeroProgressClient.set(this.kills, this.mask));
    }
}

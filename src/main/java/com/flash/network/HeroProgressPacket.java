package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> player: the hostile creatures he killed and which of the mod's advancements he has (bits in the order
 * of HeroAdvancements.ALL), and the chance of the energy of chaos now, for the hero guide of the K menu.
 */
public record HeroProgressPacket(int kills, int mask, int chaosChance) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.kills);
        buffer.writeVarInt(this.mask);
        buffer.writeVarInt(this.chaosChance);
    }

    public HeroProgressPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.HeroProgressClient.set(this.kills, this.mask, this.chaosChance));
    }
}

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: this Yas began charging a God Wand skill ({@code skill}; -1: she stopped). */
public record YasWandChargePacket(int entity, int skill) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeVarInt(this.skill);
    }

    public YasWandChargePacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.GodWandClient.charge(this.entity, this.skill));
    }
}

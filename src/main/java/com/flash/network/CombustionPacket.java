package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> players: a hero's Combustion (HeroCombustion): state 1 he burns as a meteor, 0 the flames are out,
 * 2 the impact at (x, y, z) with that radius.
 */
public record CombustionPacket(int entity, int state, float x, float y, float z, float radius) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeByte(this.state);
        buffer.writeFloat(this.x);
        buffer.writeFloat(this.y);
        buffer.writeFloat(this.z);
        buffer.writeFloat(this.radius);
    }

    public CombustionPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readByte(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.CombustionClient.receive(this));
    }
}

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: an oriented Superman effect (sonic ring, ground shockwave, solar blast). */
public record SupermanFxPacket(int kind, double x, double y, double z, float dx, float dy, float dz, float scale) {

    public static final int RING = 0;
    public static final int SHOCKWAVE = 1;
    public static final int SOLAR_BLAST = 2;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.kind);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.dx);
        buffer.writeFloat(this.dy);
        buffer.writeFloat(this.dz);
        buffer.writeFloat(this.scale);
    }

    public SupermanFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.flash.client.SupermanFx.spawn(this.kind, this.x, this.y, this.z, this.dx, this.dy, this.dz,
                        this.scale)));
        context.setPacketHandled(true);
    }
}

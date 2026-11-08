package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: a Thor effect (a lightning bolt between two points, an arc burst...). */
public record ThorFxPacket(int kind, double ax, double ay, double az, double bx, double by, double bz, float scale) {

    public static final int BOLT = 0;
    public static final int BURST = 1;
    public static final int TORNADO = 2;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.kind);
        buffer.writeDouble(this.ax);
        buffer.writeDouble(this.ay);
        buffer.writeDouble(this.az);
        buffer.writeDouble(this.bx);
        buffer.writeDouble(this.by);
        buffer.writeDouble(this.bz);
        buffer.writeFloat(this.scale);
    }

    public ThorFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.flash.client.ThorFx.handle(this)));
        context.setPacketHandled(true);
    }
}

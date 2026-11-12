package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a shot of Lex's suit to draw (repulsor blast, laser sweep, dive impact). */
public record LexFxPacket(int kind, int entity, double ax, double ay, double az, double bx, double by, double bz) {

    public static final int BLAST = 0;
    public static final int LASER = 1;
    public static final int DIVE = 2;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.kind);
        buffer.writeVarInt(this.entity);
        buffer.writeDouble(this.ax);
        buffer.writeDouble(this.ay);
        buffer.writeDouble(this.az);
        buffer.writeDouble(this.bx);
        buffer.writeDouble(this.by);
        buffer.writeDouble(this.bz);
    }

    public LexFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.LexFx.handle(this));
    }
}

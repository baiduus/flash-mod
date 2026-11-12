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
    /** Storm Striker shockwave: a ring of lightning running out on the ground (a = center, scale = radius). */
    public static final int RING = 3;
    /** God of Thunder impact: bolts along the ground, glowing cracks, energy smoke (a = center, scale = radius). */
    public static final int IMPACT = 4;
    /** Bifrost: the rainbow bridge coming down on a point and the scorch mark it leaves (b.x = ticks). */
    public static final int BIFROST = 5;
    /** Stormbreaker wave: an arc of lightning in a cone (a = center, b = direction, scale = reach). */
    public static final int WAVE = 6;
    /** Devourer King: the dome of cosmic lightning (a = center, scale = radius). */
    public static final int DOME = 7;
    /** God Blast: a giant beam of lightning from a to b. */
    public static final int BEAM = 8;
    /** Added to the kind: purple, cosmic lightning (Cosmic King). */
    public static final int COSMIC = 0x100;

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

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a bolt of Storm's (StormClient draws it with the Shazam lightning). */
public record StormFxPacket(int caster, int type, Vec3 from, Vec3 to, float power, long seed) {

    /** A bolt from the sky onto the ground (a burn mark where it lands). */
    public static final int SKY = 0;
    /** A short arc (chains, the tornado's lightning, the takeoff). */
    public static final int ARC = 1;
    /** The stream from her hands (power 1: it hits the ground, a burn trail). */
    public static final int BEAM = 2;
    /** Nothing drawn: her eyes and body light up. */
    public static final int CAST = 3;
    /** A gust (from the eyes along "to" as a direction). */
    public static final int GUST = 4;
    /** Her tornado standing at "from" (renewed while it lasts). */
    public static final int TORNADO = 5;
    /** The hail falling on "from" (power: ticks). */
    public static final int HAIL = 6;
    /** The frost running out of her (power: radius). */
    public static final int FROST = 7;
    /** Her taking off. */
    public static final int TAKEOFF = 8;
    /** The power of the storms coming into her (accepted). */
    public static final int CALL = 9;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.caster);
        buffer.writeByte(this.type);
        buffer.writeDouble(this.from.x);
        buffer.writeDouble(this.from.y);
        buffer.writeDouble(this.from.z);
        buffer.writeDouble(this.to.x);
        buffer.writeDouble(this.to.y);
        buffer.writeDouble(this.to.z);
        buffer.writeFloat(this.power);
        buffer.writeLong(this.seed);
    }

    public StormFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readByte(), new Vec3(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble()), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat(), buffer.readLong());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.StormClient.fx(this));
    }
}

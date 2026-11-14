package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> players around the Flash's transformation machine: the transformation's light (MachineVfx). Stage
 * RUNNING (with the progress, also sent now and then so late comers catch up), STRIKE (the bolt from the rod),
 * DONE (he is the Flash), STOP (aborted). (x, y, z) is the occupant's chest, top the rod's tip height.
 */
public record MachineFxPacket(double x, double y, double z, double top, int stage, int progress) {

    public static final int RUNNING = 0;
    public static final int STRIKE = 1;
    public static final int DONE = 2;
    public static final int STOP = 3;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeDouble(this.top);
        buffer.writeByte(this.stage);
        buffer.writeVarInt(this.progress);
    }

    public MachineFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readByte(),
                buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.MachineVfx.receive(this));
    }
}

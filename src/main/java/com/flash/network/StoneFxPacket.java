package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: one of the stones' effects (StoneVfx), between two points or on a creature. */
public record StoneFxPacket(byte kind, int entity, Vec3 a, Vec3 b) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(this.kind);
        buf.writeVarInt(this.entity);
        buf.writeDouble(this.a.x);
        buf.writeDouble(this.a.y);
        buf.writeDouble(this.a.z);
        buf.writeDouble(this.b.x);
        buf.writeDouble(this.b.y);
        buf.writeDouble(this.b.z);
    }

    public StoneFxPacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.infinity.StoneVfx.add(this.kind, this.entity, this.a, this.b)));
    }
}

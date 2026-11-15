package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: another Spider-Man's locomotion changed (SpiderMovePacket kinds), for his poses and his web. */
public record SpiderMoveSyncPacket(int entity, int kind, int variant, Vec3 at, float nx, float nz) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeByte(this.kind);
        buffer.writeVarInt(this.variant);
        buffer.writeDouble(this.at.x);
        buffer.writeDouble(this.at.y);
        buffer.writeDouble(this.at.z);
        buffer.writeFloat(this.nx);
        buffer.writeFloat(this.nz);
    }

    public SpiderMoveSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readByte(), buffer.readVarInt(), new Vec3(buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble()), buffer.readFloat(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.SpiderClient.remoteMove(this));
    }
}

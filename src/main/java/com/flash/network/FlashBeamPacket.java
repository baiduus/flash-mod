package com.flash.network;

import com.flash.client.FlashBeamRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Short-lived bright electric beam drawn by the client. */
public record FlashBeamPacket(Vec3 start, Vec3 end, int color, int durationTicks,
                              float width) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(this.start.x);
        buffer.writeDouble(this.start.y);
        buffer.writeDouble(this.start.z);
        buffer.writeDouble(this.end.x);
        buffer.writeDouble(this.end.y);
        buffer.writeDouble(this.end.z);
        buffer.writeInt(this.color);
        buffer.writeVarInt(this.durationTicks);
        buffer.writeFloat(this.width);
    }

    public FlashBeamPacket(FriendlyByteBuf buffer) {
        this(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readInt(), buffer.readVarInt(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashBeamRenderer.add(
                this.start, this.end, this.color, this.durationTicks, this.width));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer source, Vec3 start, Vec3 end,
                            int color, int durationTicks, float width) {
        FlashNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> source),
                new FlashBeamPacket(start, end, color, durationTicks, width));
    }
}

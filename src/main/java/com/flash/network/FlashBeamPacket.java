package com.flash.network;

import com.flash.client.FlashBeamRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Short-lived bright electric beam drawn by the client. */
public record FlashBeamPacket(Vec3 start, Vec3 end, int color, int durationTicks,
                              float width, boolean negative, boolean v9) {

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
        buffer.writeBoolean(this.negative);
        buffer.writeBoolean(this.v9);
    }

    public FlashBeamPacket(FriendlyByteBuf buffer) {
        this(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readInt(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readBoolean(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashBeamRenderer.add(
                this.start, this.end, this.color, this.durationTicks, this.width, this.negative, this.v9));
        context.setPacketHandled(true);
    }

    /** Beam fired by any entity (seen by everyone tracking it). */
    public static void sendFrom(net.minecraft.world.entity.Entity source, Vec3 start, Vec3 end,
                                int color, int durationTicks, float width, boolean negative) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> source),
                new FlashBeamPacket(start, end, color, durationTicks, width, negative, false));
    }

    public static void send(ServerPlayer source, Vec3 start, Vec3 end,
                            int color, int durationTicks, float width) {
        FlashNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> source),
                new FlashBeamPacket(start, end, color, durationTicks, width,
                        com.flash.power.FlashPowers.isNegative(source),
                        com.flash.power.FlashV9.isActive(source)));
    }
}

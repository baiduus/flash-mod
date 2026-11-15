package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Player -> server: a change in Spider-Man's locomotion, moved on his own client (the swing line fastened or let
 * go, a flip, the wall or the ceiling he crawls on, a wall jump, a double jump, hanging): the server shows it to
 * the others (SpiderMoveSyncPacket) and plays its sound.
 */
public record SpiderMovePacket(int kind, int variant, Vec3 at, float nx, float nz) {

    public static final int LINE_ON = 0;
    public static final int LINE_OFF = 1;
    public static final int RELEASE = 2;
    public static final int WALL = 3;
    public static final int CEILING = 4;
    public static final int CRAWL_OFF = 5;
    public static final int WALL_JUMP = 6;
    public static final int DOUBLE_JUMP = 7;
    public static final int HANG_ON = 8;
    public static final int HANG_OFF = 9;
    public static final int LINE_BREAK = 10;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.kind);
        buffer.writeVarInt(this.variant);
        buffer.writeDouble(this.at.x);
        buffer.writeDouble(this.at.y);
        buffer.writeDouble(this.at.z);
        buffer.writeFloat(this.nx);
        buffer.writeFloat(this.nz);
    }

    public SpiderMovePacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readVarInt(), new Vec3(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble()), buffer.readFloat(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.spider.SpiderAbilities.move(p, this);
        }
    }
}

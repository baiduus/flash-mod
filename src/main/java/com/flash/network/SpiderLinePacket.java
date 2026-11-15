package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Player -> server: Spider-Man's swing line is fastened at {@code anchor} (or let go): the server shows it to the
 * others (the swing itself is moved on his own client, as every player's motion).
 */
public record SpiderLinePacket(boolean on, Vec3 anchor) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.on);
        buffer.writeDouble(this.anchor.x);
        buffer.writeDouble(this.anchor.y);
        buffer.writeDouble(this.anchor.z);
    }

    public SpiderLinePacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.spider.SpiderAbilities.line(p, this.on, this.anchor);
        }
    }
}

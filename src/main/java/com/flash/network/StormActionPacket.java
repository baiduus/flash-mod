package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: one of Storm's powers (StormAbilities). */
public record StormActionPacket(int action, int value) {

    public static final int STRIKE = 0;
    public static final int BEAM = 1;
    public static final int CHAIN = 2;
    public static final int GUST = 3;
    public static final int TORNADO = 4;
    public static final int WEATHER = 5;
    public static final int FURY = 6;
    public static final int HAIL = 7;
    public static final int FROST = 8;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.action);
        buffer.writeVarInt(this.value);
    }

    public StormActionPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.storm.StormAbilities.act(p, this.action, this.value);
        }
    }
}

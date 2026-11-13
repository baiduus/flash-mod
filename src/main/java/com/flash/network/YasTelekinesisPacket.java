package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Player -> server: Yas' telekinesis. {@code kind} 0: a scroll while holding a creature (closer / farther,
 * {@code value} steps); 1: the charged throw ({@code value} the charge, 0..100).
 */
public record YasTelekinesisPacket(int kind, int value) {

    public YasTelekinesisPacket(int steps) {
        this(0, steps);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.kind);
        buffer.writeVarInt(this.value);
    }

    public YasTelekinesisPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p == null) {
            return;
        }
        if (this.kind == 1) {
            com.flash.yas.YasTelekinesis.charge(p, Math.max(0, Math.min(100, this.value)));
        } else {
            com.flash.yas.YasTelekinesis.move(p, Math.max(-3, Math.min(3, this.value)));
        }
    }
}

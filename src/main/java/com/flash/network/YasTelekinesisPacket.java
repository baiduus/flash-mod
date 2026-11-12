package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: Yas scrolls while holding a creature by telekinesis (closer / farther). */
public record YasTelekinesisPacket(int steps) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.steps);
    }

    public YasTelekinesisPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.yas.YasTelekinesis.move(p, Math.max(-3, Math.min(3, this.steps)));
        }
    }
}

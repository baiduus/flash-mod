package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: Yas's look, chosen in her K menu (lightning on the body, glowing eyes). */
public record YasSettingsPacket(boolean lightning, boolean eyes) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.lightning);
        buffer.writeBoolean(this.eyes);
    }

    public YasSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.yas.YasPowers.setLook(p, this.lightning, this.eyes);
        }
    }
}

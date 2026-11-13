package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Yas -> server: her key for a God Wand skill went down / came up / the charge is dropped (GodWandSkills). */
public record YasWandPacket(int skill, int action, int value) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.skill);
        buffer.writeVarInt(this.action);
        buffer.writeVarInt(this.value);
    }

    public YasWandPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.godwand.GodWandSkills.input(p, this.skill, this.action, this.value);
        }
    }
}

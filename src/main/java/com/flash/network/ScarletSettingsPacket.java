package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: the Scarlet Witch's settings (the flight aura shown or not, for everyone). */
public record ScarletSettingsPacket(boolean aura) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.aura);
    }

    public ScarletSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p != null) {
            com.flash.scarlet.ScarletPowers.setAura(p, this.aura);
        }
    }
}

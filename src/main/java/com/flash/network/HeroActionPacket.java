package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: one of Spider-Man's (SpiderAbilities) or Doctor Doom's (DoomAbilities) powers. */
public record HeroActionPacket(int hero, int action, int value) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.hero);
        buffer.writeByte(this.action);
        buffer.writeVarInt(this.value);
    }

    public HeroActionPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readByte(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p == null) {
            return;
        }
        if (this.hero == HeroSyncPacket.SPIDER) {
            com.flash.spider.SpiderAbilities.act(p, this.action, this.value);
        } else {
            com.flash.doom.DoomAbilities.act(p, this.action, this.value);
        }
    }
}

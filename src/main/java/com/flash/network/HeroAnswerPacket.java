package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: his answer to the bite (SpiderCalling) or to the grimoire (DoomCalling). */
public record HeroAnswerPacket(int hero, boolean accept) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.hero);
        buffer.writeBoolean(this.accept);
    }

    public HeroAnswerPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p == null) {
            return;
        }
        if (this.hero == HeroSyncPacket.SPIDER) {
            com.flash.spider.SpiderCalling.answer(p, this.accept);
        } else {
            com.flash.doom.DoomCalling.answer(p, this.accept);
        }
    }
}

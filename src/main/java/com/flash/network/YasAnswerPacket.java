package com.flash.network;

import com.flash.yas.YasGuardian;
import com.flash.yas.YasTranscendence;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/** Player -> server: his answer to the Guardian (yes: transcend). */
public record YasAnswerPacket(int guardian, boolean yes) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.guardian);
        buffer.writeBoolean(this.yes);
    }

    public YasAnswerPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p == null) {
            return;
        }
        Entity e = p.level().getEntity(this.guardian);
        if (!(e instanceof YasGuardian) || e.distanceToSqr(p) > 12.0D * 12.0D) {
            return;
        }
        YasTranscendence.answer(p, e, this.yes);
    }
}

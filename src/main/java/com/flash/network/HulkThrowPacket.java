package com.flash.network;

import com.flash.hulk.HulkAbilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: the rock / grab key was released after charging (0..1 of the force bar, super throw). */
public record HulkThrowPacket(float charge, boolean superThrow) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.charge);
        buffer.writeBoolean(this.superThrow);
    }

    public HulkThrowPacket(FriendlyByteBuf buffer) {
        this(buffer.readFloat(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null && Float.isFinite(this.charge)) {
            HulkAbilities.throwHeld(player, Math.max(0.0F, Math.min(1.0F, this.charge)), this.superThrow);
        }
    }
}

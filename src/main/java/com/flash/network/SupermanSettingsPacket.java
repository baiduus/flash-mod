package com.flash.network;

import com.flash.superman.SupermanPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Superman options of the K screen (abilities break blocks). */
public record SupermanSettingsPacket(boolean destroy) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.destroy);
    }

    public SupermanSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null) {
            SupermanPowers.setDestroy(player, this.destroy);
        }
    }
}

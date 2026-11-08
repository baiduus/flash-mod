package com.flash.network;

import com.flash.thor.ThorPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Thor options of the K screen (0 break blocks, 1 suit, 2 helmet off). */
public record ThorSettingsPacket(int setting, int value) {

    public static final int DESTROY = 0;
    public static final int SUIT = 1;
    public static final int HELMET = 2;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.setting);
        buffer.writeVarInt(this.value);
    }

    public ThorSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player == null || !ThorPowers.has(player)) {
            return;
        }
        switch (this.setting) {
            case DESTROY -> ThorPowers.setDestroy(player, this.value != 0);
            case SUIT -> ThorPowers.setSuit(player, this.value);
            case HELMET -> ThorPowers.setHelmetOff(player, this.value != 0);
            default -> {
            }
        }
    }
}

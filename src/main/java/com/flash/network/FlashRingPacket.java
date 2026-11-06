package com.flash.network;

import com.flash.power.FlashRing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: equip / remove the ring slot, or toggle the suit with it. */
public record FlashRingPacket(int action) {

    public static final int EQUIP = 0;
    public static final int UNEQUIP = 1;
    public static final int TOGGLE_SUIT = 2;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.action);
    }

    public FlashRingPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player == null) {
            return;
        }
        switch (this.action) {
            case EQUIP -> FlashRing.equip(player);
            case UNEQUIP -> FlashRing.unequip(player);
            case TOGGLE_SUIT -> FlashRing.toggleSuit(player);
            default -> {
            }
        }
    }
}

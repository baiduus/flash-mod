package com.flash.network;

import com.flash.superman.SupermanPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: Superman options of the K screen (0 break blocks, 1 combo move, 2 laser cuts limbs). */
public record SupermanSettingsPacket(int setting, int value) {

    public static final int DESTROY = 0;
    public static final int COMBO = 1;
    public static final int LASER_CUT = 2;

    public SupermanSettingsPacket(boolean destroy) {
        this(DESTROY, destroy ? 1 : 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.setting);
        buffer.writeVarInt(this.value);
    }

    public SupermanSettingsPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player == null) {
            return;
        }
        switch (this.setting) {
            case DESTROY -> SupermanPowers.setDestroy(player, this.value != 0);
            case COMBO -> SupermanPowers.setCombo(player, this.value);
            case LASER_CUT -> SupermanPowers.setLaserCut(player, this.value != 0);
            default -> {
            }
        }
    }
}

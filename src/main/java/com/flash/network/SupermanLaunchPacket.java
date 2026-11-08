package com.flash.network;

import com.flash.superman.SupermanAbilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: charged takeoff (jump held on the ground): 0 crouch, 1 launch, 2 cancelled. */
public record SupermanLaunchPacket(int phase) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.phase);
    }

    public SupermanLaunchPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        if (player != null && com.flash.thor.ThorPowers.isThor(player)) {
            com.flash.thor.ThorAbilities.launch(player, this.phase);
        } else if (player != null) {
            SupermanAbilities.launch(player, this.phase);
        }
    }
}

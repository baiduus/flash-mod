package com.flash.network;

import com.flash.superman.SupermanAbilities;
import com.flash.superman.SupermanAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: a Superman ability key went down (pressed) or up. */
public record SupermanAbilityPacket(int abilityId, boolean pressed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.abilityId);
        buffer.writeBoolean(this.pressed);
    }

    public SupermanAbilityPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        SupermanAbility ability = SupermanAbility.byId(this.abilityId);
        if (player != null && ability != null) {
            SupermanAbilities.key(player, ability, this.pressed);
        }
    }
}

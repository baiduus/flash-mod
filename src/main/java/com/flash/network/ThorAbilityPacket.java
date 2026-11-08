package com.flash.network;

import com.flash.thor.ThorAbilities;
import com.flash.thor.ThorAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: a Thor ability key went down (pressed) or up. */
public record ThorAbilityPacket(int abilityId, boolean pressed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.abilityId);
        buffer.writeBoolean(this.pressed);
    }

    public ThorAbilityPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        ThorAbility ability = ThorAbility.byId(this.abilityId);
        if (player != null && ability != null) {
            ThorAbilities.key(player, ability, this.pressed);
        }
    }
}

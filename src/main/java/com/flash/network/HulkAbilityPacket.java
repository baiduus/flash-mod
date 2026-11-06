package com.flash.network;

import com.flash.hulk.HulkAbilities;
import com.flash.hulk.HulkAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: use a Hulk ability. */
public record HulkAbilityPacket(int abilityId) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.abilityId);
    }

    public HulkAbilityPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.setPacketHandled(true);
        HulkAbility ability = HulkAbility.byId(this.abilityId);
        if (player != null && ability != null) {
            HulkAbilities.use(player, ability);
        }
    }
}

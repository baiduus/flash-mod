package com.flash.network;

import com.flash.ability.FlashAbility;
import com.flash.ability.FlashAbilityManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server request to press or release an ability. The server validates
 * ownership, unlock level and cooldowns before executing anything.
 */
public record FlashAbilityPacket(int abilityId, boolean pressed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.abilityId);
        buffer.writeBoolean(this.pressed);
    }

    public FlashAbilityPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            FlashAbility ability = FlashAbility.byId(this.abilityId);
            if (ability != null) {
                if (this.pressed) {
                    FlashAbilityManager.onPress(player, ability);
                } else {
                    FlashAbilityManager.onRelease(player, ability);
                }
            }
        }
        context.setPacketHandled(true);
    }
}

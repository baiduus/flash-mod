package com.flash.network;

import com.flash.ability.FlashAbility;
import com.flash.client.FlashClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Server -> owner state used by the ability HUD and the phasing movement. */
public record FlashAbilitySyncPacket(int abilityId, boolean active, int cooldownTicks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.abilityId);
        buffer.writeBoolean(this.active);
        buffer.writeVarInt(this.cooldownTicks);
    }

    public FlashAbilitySyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashClientData.setAbilityState(
                this.abilityId, this.active, this.cooldownTicks));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer player, FlashAbility ability,
                            boolean active, int cooldownTicks) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FlashAbilitySyncPacket(ability.ordinal(), active, cooldownTicks));
    }
}

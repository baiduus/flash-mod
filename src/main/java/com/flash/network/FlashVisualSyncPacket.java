package com.flash.network;

import com.flash.client.FlashClientVisualLevels;
import com.flash.power.FlashPowers;
import com.flash.power.FlashSpeedLevels;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-authoritative speed level copy used by the client-side body-lightning
 * renderer. It is broadcast to the player and to every client tracking them,
 * so other players see the aura at the correct intensity.
 */
public record FlashVisualSyncPacket(UUID playerId, int level, int activeMask) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeVarInt(this.level);
        buffer.writeVarInt(this.activeMask);
    }

    public FlashVisualSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashClientVisualLevels.set(
                this.playerId, this.level, this.activeMask));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer player) {
        int level = FlashPowers.hasPowers(player)
                ? FlashPowers.getSelectedLevel(player)
                : FlashSpeedLevels.MIN_LEVEL;
        send(player, level);
    }

    public static void send(ServerPlayer player, int level) {
        int clamped = FlashSpeedLevels.clamp(level);
        int mask = com.flash.ability.FlashAbilityManager.activeMask(player);
        FlashNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new FlashVisualSyncPacket(player.getUUID(), clamped, mask)
        );
    }
}

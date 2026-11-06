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
public record FlashVisualSyncPacket(UUID playerId, int level, int activeMask, boolean negative,
                                    boolean v9, int v9Ticks, int lockTicks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeVarInt(this.level);
        buffer.writeVarInt(this.activeMask);
        buffer.writeBoolean(this.negative);
        buffer.writeBoolean(this.v9);
        buffer.writeVarInt(this.v9Ticks);
        buffer.writeVarInt(this.lockTicks);
    }

    public FlashVisualSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashClientVisualLevels.set(
                this.playerId, this.level, this.activeMask, this.negative, this.v9,
                this.v9Ticks, this.lockTicks));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer player) {
        int level = FlashPowers.hasPowers(player)
                ? FlashPowers.getSelectedLevel(player)
                : 0;
        send(player, level);
    }

    public static void send(ServerPlayer player, int level) {
        int clamped = level <= 0 ? 0 : FlashSpeedLevels.clamp(level);
        int mask = com.flash.ability.FlashAbilityManager.activeMask(player);
        FlashNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new FlashVisualSyncPacket(player.getUUID(), clamped, mask,
                        clamped > 0 && FlashPowers.isNegative(player),
                        clamped > 0 && com.flash.power.FlashV9.isActive(player),
                        com.flash.power.FlashV9.remainingTicks(player),
                        com.flash.power.FlashV9.suppressedTicks(player))
        );
    }
}

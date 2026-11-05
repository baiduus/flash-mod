package com.flash.network;

import com.flash.ability.FlashAbility;
import com.flash.ability.FlashAbilityManager;
import com.flash.client.FlashClientData;
import com.flash.power.FlashPowers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Read-only snapshot of the server-side progression, preferences, temporary
 * power timer and real ability cooldowns. The cooldown array is what keeps the
 * bottom-right ability panel in sync with the server instead of relying on a
 * local guess.
 */
public record FlashSyncPacket(boolean hasPowers, int maxLevel, int selectedLevel,
                              double distance, double totalDistance,
                              boolean destroyBlocks, boolean temporary,
                              int temporaryRemaining, int[] abilityCooldowns) {

    public FlashSyncPacket(ServerPlayer player) {
        this(
                FlashPowers.hasPowers(player),
                FlashPowers.getMaxLevel(player),
                FlashPowers.getSelectedLevel(player),
                FlashPowers.getDistance(player),
                FlashPowers.getTotalDistance(player),
                FlashPowers.isDestroyBlocksEnabled(player),
                FlashPowers.isTemporary(player),
                FlashPowers.getTemporaryRemaining(player),
                collectCooldowns(player)
        );
    }

    private static int[] collectCooldowns(ServerPlayer player) {
        FlashAbility[] abilities = FlashAbility.values();
        int[] cooldowns = new int[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            cooldowns[i] = FlashAbilityManager.getRemainingCooldown(player, abilities[i]);
        }
        return cooldowns;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.hasPowers);
        buffer.writeVarInt(this.maxLevel);
        buffer.writeVarInt(this.selectedLevel);
        buffer.writeDouble(this.distance);
        buffer.writeDouble(this.totalDistance);
        buffer.writeBoolean(this.destroyBlocks);
        buffer.writeBoolean(this.temporary);
        buffer.writeVarInt(this.temporaryRemaining);
        int[] cooldowns = this.abilityCooldowns == null ? new int[0] : this.abilityCooldowns;
        buffer.writeVarInt(cooldowns.length);
        for (int cooldown : cooldowns) {
            buffer.writeVarInt(Math.max(0, cooldown));
        }
    }

    public FlashSyncPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                readCooldowns(buffer)
        );
    }

    private static int[] readCooldowns(FriendlyByteBuf buffer) {
        int count = Math.min(64, Math.max(0, buffer.readVarInt()));
        int[] cooldowns = new int[count];
        for (int i = 0; i < count; i++) {
            cooldowns[i] = buffer.readVarInt();
        }
        return cooldowns;
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> FlashClientData.set(
                this.hasPowers,
                this.maxLevel,
                this.selectedLevel,
                this.distance,
                this.totalDistance,
                this.destroyBlocks,
                this.temporary,
                this.temporaryRemaining,
                this.abilityCooldowns
        ));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer player) {
        FlashNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FlashSyncPacket(player)
        );
    }
}

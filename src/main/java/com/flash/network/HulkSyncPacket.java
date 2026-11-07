package com.flash.network;

import com.flash.hulk.HulkState;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: Hulk state of a player (scale for everyone, the rest for the player himself). */
public record HulkSyncPacket(UUID playerId, boolean has, boolean transformed, float scale, int level,
                             float rage, int tempTicks, int furyTicks, boolean destroy, boolean grow,
                             int[] cooldowns, int activeLevel, float height, boolean red, int extraTicks, boolean noCooldowns) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.has);
        buffer.writeBoolean(this.transformed);
        buffer.writeFloat(this.scale);
        buffer.writeVarInt(this.level);
        buffer.writeFloat(this.rage);
        buffer.writeVarInt(this.tempTicks);
        buffer.writeVarInt(this.furyTicks);
        buffer.writeBoolean(this.destroy);
        buffer.writeBoolean(this.grow);
        buffer.writeVarInt(this.cooldowns.length);
        for (int c : this.cooldowns) {
            buffer.writeVarInt(Math.max(0, c));
        }
        buffer.writeVarInt(this.activeLevel);
        buffer.writeFloat(this.height);
        buffer.writeBoolean(this.red);
        buffer.writeVarInt(this.extraTicks);
        buffer.writeBoolean(this.noCooldowns);
    }

    public HulkSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), readInts(buffer), buffer.readVarInt(),
                buffer.readFloat(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean());
    }

    private static int[] readInts(FriendlyByteBuf buffer) {
        int n = Math.min(32, Math.max(0, buffer.readVarInt()));
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = buffer.readVarInt();
        }
        return out;
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(this)));
        context.setPacketHandled(true);
    }

    private static final class Client {
        static void apply(HulkSyncPacket p) {
            Minecraft mc = Minecraft.getInstance();
            boolean local = mc.player != null && mc.player.getUUID().equals(p.playerId);
            long now = mc.level != null ? mc.level.getGameTime() : 0L;
            Float before = HulkState.clientEntry(p.playerId);
            if (before != null && p.has && (before > 0.0F) != p.transformed) {
                // the form changed while we were watching: play the transformation
                com.flash.client.HulkAnimator.onFormChange(p.playerId, p.transformed,
                        p.transformed ? p.scale : before);
            }
            HulkState.setClient(p.playerId, p.has, p.transformed, p.scale, local, p.level, p.rage,
                    p.tempTicks, p.furyTicks, p.destroy, p.grow, p.cooldowns, p.activeLevel, p.height, now);
            HulkState.setRed(p.playerId, p.has && p.red);
            if (mc.player != null && mc.player.getUUID().equals(p.playerId)) {
                HulkState.setExtra(p.extraTicks, now);
                HulkState.setNoCooldowns(p.noCooldowns);
            }
            if (mc.level != null) {
                Player player = mc.level.getPlayerByUUID(p.playerId);
                if (player != null) {
                    player.refreshDimensions();
                }
            }
        }
    }
}

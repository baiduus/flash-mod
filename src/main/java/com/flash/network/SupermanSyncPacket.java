package com.flash.network;

import com.flash.superman.SupermanState;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: Superman state of a player (form and flags for everyone, the rest for himself). */
public record SupermanSyncPacket(UUID playerId, boolean has, boolean superman, int level, float xp, float energy,
                                 int flags, boolean destroy, int[] cooldowns, boolean noCooldowns,
                                 boolean infinite, boolean sun) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.has);
        buffer.writeBoolean(this.superman);
        buffer.writeVarInt(this.level);
        buffer.writeFloat(this.xp);
        buffer.writeFloat(this.energy);
        buffer.writeVarInt(this.flags);
        buffer.writeBoolean(this.destroy);
        buffer.writeVarInt(this.cooldowns.length);
        for (int c : this.cooldowns) {
            buffer.writeVarInt(Math.max(0, c));
        }
        buffer.writeBoolean(this.noCooldowns);
        buffer.writeBoolean(this.infinite);
        buffer.writeBoolean(this.sun);
    }

    public SupermanSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readFloat(), buffer.readVarInt(), buffer.readBoolean(), readInts(buffer), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean());
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
        static void apply(SupermanSyncPacket p) {
            Minecraft mc = Minecraft.getInstance();
            boolean local = mc.player != null && mc.player.getUUID().equals(p.playerId);
            long now = mc.level != null ? mc.level.getGameTime() : 0L;
            SupermanState.setClient(p.playerId, p.has, p.superman, p.flags, local, p.level, p.xp, p.energy,
                    p.destroy, p.cooldowns, p.noCooldowns, p.infinite, p.sun, now);
        }
    }
}

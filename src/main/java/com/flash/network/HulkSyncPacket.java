package com.flash.network;

import com.flash.hulk.HulkState;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: whether a player has the Hulk, is transformed, and (own player) the rage. */
public record HulkSyncPacket(UUID playerId, boolean has, boolean transformed, float rage) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.has);
        buffer.writeBoolean(this.transformed);
        buffer.writeFloat(this.rage);
    }

    public HulkSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(this)));
        context.setPacketHandled(true);
    }

    private static final class Client {
        static void apply(HulkSyncPacket packet) {
            Minecraft mc = Minecraft.getInstance();
            boolean local = mc.player != null && mc.player.getUUID().equals(packet.playerId);
            HulkState.setClient(packet.playerId, packet.has, packet.transformed, packet.rage, local);
            if (mc.level != null) {
                Player player = mc.level.getPlayerByUUID(packet.playerId);
                if (player != null) {
                    player.refreshDimensions();
                }
            }
        }
    }
}

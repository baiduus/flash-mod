package com.flash.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: a one-shot Superman animation (punch, clap, grab, throw, solar release...). */
public record SupermanAnimPacket(UUID playerId, String animation) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.playerId);
        buffer.writeUtf(this.animation, 64);
    }

    public SupermanAnimPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(64));
    }

    public void handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.flash.client.SupermanPose.play(this.playerId, this.animation)));
        context.setPacketHandled(true);
    }
}

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: the state of a Lex suit (systems running, faceplate) and, for its pilot, the cooldowns. */
public record LexSyncPacket(int entity, int flags, int[] cooldowns) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeVarInt(this.flags);
        buffer.writeVarIntArray(this.cooldowns);
    }

    public LexSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarIntArray());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.lex.LexState.handle(this));
    }
}

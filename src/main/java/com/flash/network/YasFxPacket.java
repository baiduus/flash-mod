package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: the effects of the transcendence on a player (from the Guardian {@code source}). */
public record YasFxPacket(int kind, int entity, int source, int ticks) {

    /** Rising in pale yellow lightning, the white beam through the body (10 s). */
    public static final int ASCEND = 1;
    public static final int ASCEND_END = 2;
    /** The black beam and black lightning of the Guardian (3 s). */
    public static final int DOOM = 3;
    public static final int DOOM_END = 4;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.kind);
        buffer.writeVarInt(this.entity);
        buffer.writeVarInt(this.source);
        buffer.writeVarInt(this.ticks);
    }

    public YasFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.YasClient.fx(this));
    }
}

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Player -> server, every tick of Yas' fast flight, just before her move: her speed now. The server takes it as
 * hers, so its "moved too quickly" check (about 10 blocks a tick) lets her seven-times flight through on a server.
 */
public record YasSpeedPacket(float x, float y, float z) {

    /** Her top speed (87.5 blocks a tick) and a margin. */
    private static final double MAX = 95.0D;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.x);
        buffer.writeFloat(this.y);
        buffer.writeFloat(this.z);
    }

    public YasSpeedPacket(FriendlyByteBuf buffer) {
        this(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        ServerPlayer p = context.getSender();
        if (p == null || !com.flash.yas.YasPowers.has(p) || !p.getAbilities().flying || p.isPassenger()) {
            return;
        }
        Vec3 v = new Vec3(this.x, this.y, this.z);
        if (!Double.isFinite(v.lengthSqr())) {
            return;
        }
        if (v.length() > MAX) {
            v = v.normalize().scale(MAX);
        }
        // already on the server thread (consumerMainThread), handled just before her move packet
        p.setDeltaMovement(v);
        p.fallDistance = 0.0F;
    }
}

package com.flash.godwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: one of God Wand's effects (ported from God Wand by HyIsNoob). */
public record SpatialAuraPayload(int playerId, boolean active, Vec3 hitNormal) {

    public SpatialAuraPayload(int playerId, boolean active) {
        this(playerId, active, Vec3.ZERO);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.playerId);
        buffer.writeBoolean(this.active);
        buffer.writeDouble(this.hitNormal.x);
        buffer.writeDouble(this.hitNormal.y);
        buffer.writeDouble(this.hitNormal.z);
    }

    public static SpatialAuraPayload decode(FriendlyByteBuf buffer) {
        return new SpatialAuraPayload(buffer.readVarInt(), buffer.readBoolean(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.SpatialAuraRenderer.receive(this));
    }
}

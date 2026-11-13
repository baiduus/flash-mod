package com.flash.godwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: one of God Wand's effects (ported from God Wand by HyIsNoob). */
public record CannonPressurePayload(Vec3 center, Vec3 direction, int level, Vec3 surfaceNormal) {


    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(this.center.x);
        buffer.writeDouble(this.center.y);
        buffer.writeDouble(this.center.z);
        buffer.writeDouble(this.direction.x);
        buffer.writeDouble(this.direction.y);
        buffer.writeDouble(this.direction.z);
        buffer.writeVarInt(this.level);
        buffer.writeDouble(this.surfaceNormal.x);
        buffer.writeDouble(this.surfaceNormal.y);
        buffer.writeDouble(this.surfaceNormal.z);
    }

    public static CannonPressurePayload decode(FriendlyByteBuf buffer) {
        return new CannonPressurePayload(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readVarInt(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.GodWandClient.cannon(this));
    }
}

package com.flash.godwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: one of God Wand's effects (ported from God Wand by HyIsNoob). */
public record DrillWindPayload(long id, double x, double y, double z, float radius, float angle, float speed) {


    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(this.id);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.radius);
        buffer.writeFloat(this.angle);
        buffer.writeFloat(this.speed);
    }

    public static DrillWindPayload decode(FriendlyByteBuf buffer) {
        return new DrillWindPayload(buffer.readLong(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.DrillWindRenderer.receive(this));
    }
}

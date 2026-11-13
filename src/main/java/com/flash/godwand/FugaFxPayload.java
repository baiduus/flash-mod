package com.flash.godwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: one of God Wand's effects (ported from God Wand by HyIsNoob). */
public record FugaFxPayload(int casterId, long startedAt, double x, double y, double z, double tx, double ty, double tz, int phase, long phaseAt) {

    public static final int CHARGE = 0;
    public static final int FIRE = 1;
    public static final int IMPACT = 2;
    public static final int CANCEL = 3;
    public static final int END = 4;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.casterId);
        buffer.writeLong(this.startedAt);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeDouble(this.tx);
        buffer.writeDouble(this.ty);
        buffer.writeDouble(this.tz);
        buffer.writeVarInt(this.phase);
        buffer.writeLong(this.phaseAt);
    }

    public static FugaFxPayload decode(FriendlyByteBuf buffer) {
        return new FugaFxPayload(buffer.readVarInt(), buffer.readLong(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(), buffer.readLong());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.FugaRenderer.receive(this));
    }
}

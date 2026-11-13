package com.flash.godwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: one of God Wand's effects (ported from God Wand by HyIsNoob). */
public record ImpactFxPayload(double x, double y, double z, int fxType, int durationTicks, float intensity) {

    public static final int TYPE_DETONATION = 0;
    public static final int TYPE_IMPLOSION = 1;
    public static final int TYPE_COLLAPSE = 2;
    public static final int TYPE_SUPERNOVA = 3;
    public static final int TYPE_BLACK_HOLE = 4;
    public static final int TYPE_SEISMIC_SLAM = 5;
    public static final int TYPE_GRAVITY_CORE = 6;
    public static final int TYPE_REASSEMBLY = 7;
    public static final int TYPE_SUPERNOVA_IMPACT = 8;
    public static final int SUPERNOVA_IMPACT_TICKS = 4;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeVarInt(this.fxType);
        buffer.writeVarInt(this.durationTicks);
        buffer.writeFloat(this.intensity);
    }

    public static ImpactFxPayload decode(FriendlyByteBuf buffer) {
        return new ImpactFxPayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.GodWandClient.impact(this));
    }
}

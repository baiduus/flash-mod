package com.flash.godwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> players around: one of God Wand's effects (ported from God Wand by HyIsNoob). */
public record CinematicFxPayload(int eventType, double x, double y, double z, float intensity, int durationTicks) {

    public static final int EVENT_TRAUMA_SHAKE = 0;
    public static final int EVENT_FOV_PUNCH = 1;
    public static final int EVENT_VACUUM_SILENCE = 2;
    public static final int EVENT_METEOR_FOCUS = 3;
    public static final int EVENT_METEOR_SLAM = 4;
    public static final int EVENT_SUPERNOVA_COMPRESS = 5;
    public static final int EVENT_SUPERNOVA_BURST = 6;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.eventType);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.intensity);
        buffer.writeVarInt(this.durationTicks);
    }

    public static CinematicFxPayload decode(FriendlyByteBuf buffer) {
        return new CinematicFxPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readVarInt());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.godwand.GodWandCinematicEngine.onCinematicEvent(this));
    }
}

package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Server -> clients: an effect of the Scarlet Witch's magic (ScarletClient.fx). */
public record ScarletFxPacket(int caster, int type, Vec3 from, Vec3 to, float power) {

    public static final int BOLT = 0;
    public static final int BEAM = 1;
    public static final int BLAST = 2;
    public static final int WAVE = 3;
    public static final int HEAL = 4;
    public static final int RAISE = 5;
    public static final int LINK = 6;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.caster);
        buffer.writeByte(this.type);
        buffer.writeDouble(this.from.x);
        buffer.writeDouble(this.from.y);
        buffer.writeDouble(this.from.z);
        buffer.writeDouble(this.to.x);
        buffer.writeDouble(this.to.y);
        buffer.writeDouble(this.to.z);
        buffer.writeFloat(this.power);
    }

    public ScarletFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readByte(), new Vec3(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble()), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat());
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.flash.client.ScarletClient.fx(this));
    }
}

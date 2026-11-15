package com.flash.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> clients: something of Spider-Man's (SpiderClient) or Doctor Doom's (DoomVfx) to draw: from / to, a
 * power and an entity it is about (-1 none).
 */
public record HeroFxPacket(int hero, int caster, int type, Vec3 from, Vec3 to, float power, int target) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.hero);
        buffer.writeVarInt(this.caster);
        buffer.writeByte(this.type);
        buffer.writeDouble(this.from.x);
        buffer.writeDouble(this.from.y);
        buffer.writeDouble(this.from.z);
        buffer.writeDouble(this.to.x);
        buffer.writeDouble(this.to.y);
        buffer.writeDouble(this.to.z);
        buffer.writeFloat(this.power);
        buffer.writeVarInt(this.target + 1);
    }

    public HeroFxPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer.readVarInt(), buffer.readByte(), new Vec3(buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble()), new Vec3(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble()), buffer.readFloat(), buffer.readVarInt() - 1);
    }

    public void handle(NetworkEvent.Context context) {
        context.setPacketHandled(true);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> {
                    if (this.hero == HeroSyncPacket.SPIDER) {
                        com.flash.client.SpiderClient.fx(this);
                    } else {
                        com.flash.client.DoomVfx.fx(this);
                    }
                });
    }
}

package com.flash.infinity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** The stones' one-shot effects, sent to whoever is around (drawn by StoneVfx, client). */
public final class StoneFx {

    public static final byte JUMP = 0;
    public static final byte PUNCH = 1;
    public static final byte HEAL = 2;
    public static final byte DRAIN = 3;
    public static final byte REALITY = 4;
    public static final byte CLONES = 5;
    public static final byte MIND = 6;
    public static final byte TIME = 7;
    public static final byte ROCKETS = 8;

    private StoneFx() {
    }

    public static void send(ServerLevel level, byte kind, Entity e, Vec3 a, Vec3 b) {
        com.flash.network.FlashNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.NEAR.with(
                () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(a.x, a.y, a.z, 128.0D,
                        level.dimension())), new com.flash.network.StoneFxPacket(kind, e == null ? -1 : e.getId(), a, b));
    }
}

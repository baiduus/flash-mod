package com.flash.godwand;

import com.flash.network.FlashNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** God Wand's effects to the players around (its Services.NETWORK). */
public final class GodWandNet {

    private GodWandNet() {
    }

    public static void near(Level level, Vec3 at, double radius, Object packet) {
        if (!(level instanceof ServerLevel world)) {
            return;
        }
        double r2 = radius * radius;
        for (ServerPlayer p : world.players()) {
            if (p.position().distanceToSqr(at) <= r2) {
                FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    public static void to(ServerPlayer p, Object packet) {
        if (p != null && !p.hasDisconnected()) {
            FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
        }
    }
}

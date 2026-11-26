package com.flash.hulk;

import com.flash.network.FlashNetwork;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** The Hulk's effects sent to whoever sees them (drawn in light by HulkVfx: gamma green, the Red Hulk's fire). */
public final class HulkFx {

    /** The fists hit the ground: a = the ground, power = the level. */
    public static final int SMASH = 0;
    /** A step of the charge: a = the feet, b = the way. */
    public static final int CHARGE = 1;
    /** The roar: a = the mouth, b = the look. */
    public static final int ROAR = 2;
    /** The hands meet: a = the hands, b = the look, power = the reach. */
    public static final int CLAP = 3;
    /** A rock or a creature thrown: a = the hands, b = the way, power = the charge. */
    public static final int THROW = 4;
    /** Gamma fury: the aura for its 20 s. */
    public static final int FURY = 5;
    /** The gamma wave: a = the centre, power = the radius. */
    public static final int GAMMA_WAVE = 6;
    /** A stomp of the World Breaker: a = the centre, power = the ring's radius, target = the stomp. */
    public static final int STOMP = 7;
    /** Landing from a jump: a = the feet, power = the radius. */
    public static final int LAND = 8;
    /** Banner becomes the Hulk (power 1) or back (0). */
    public static final int TRANSFORM = 9;
    /** The extra life. */
    public static final int EXTRA_LIFE = 10;
    /** A great impact (the super throw's, a gamma reactor's burst): a = the place, power = the radius. */
    public static final int IMPACT = 11;
    /** A punch landing: a = where, b = the way. */
    public static final int PUNCH = 12;
    /** A rock ripped from the ground: a = the ground. */
    public static final int RIP = 13;

    private HulkFx() {
    }

    /** From the Hulk to everyone tracking him (target: bit 0 the Red Hulk, the rest a number of the effect's). */
    public static void send(ServerPlayer hulk, int type, Vec3 a, Vec3 b, float power) {
        send(hulk, type, a, b, power, HulkPowers.isRed(hulk) ? 1 : 0);
    }

    public static void send(ServerPlayer hulk, int type, Vec3 a, Vec3 b, float power, int extra) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> hulk),
                new HeroFxPacket(HeroSyncPacket.HULK, hulk.getId(), type, a, b, power, extra));
    }

    /** With no Hulk behind it (a reactor): to everyone near the place. */
    public static void at(ServerLevel level, int type, Vec3 a, float power) {
        FlashNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(a.x, a.y, a.z,
                        160.0D, level.dimension())),
                new HeroFxPacket(HeroSyncPacket.HULK, -1, type, a, a, power, 0));
    }
}

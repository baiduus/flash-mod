package com.flash.ability;

import com.flash.network.FlashNetwork;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Flash's effects sent to whoever sees them (drawn in light by FlashCineVfx, in his colours: gold, the Negative
 * Force's red, the V-9's blue). The lasting ones (tornado, charge, phasing, slowed time) are sent again every few
 * ticks while they last and fade out when they stop coming.
 */
public final class FlashFx {

    /** A great impact (the lightning's, the mass punch's): a = where, b = the way it came, power = its size. */
    public static final int IMPACT = 0;
    /** The speed tornado round its centre: a = the centre. */
    public static final int TORNADO = 1;
    /** The run round the circle before the mass punch: a = the centre, power = the radius. */
    public static final int MASS_CHARGE = 2;
    /** A dash of the dash punch: a -> b. */
    public static final int DASH = 3;
    /** A punch landing (dash punch): a = where, b = the way. */
    public static final int HIT = 4;
    /** Vibrating through matter. */
    public static final int PHASE = 5;
    /** Time slowed round him: power = the radius. */
    public static final int TIME_SLOW = 6;
    /** The lightning thrown or the mass punch let go: a = the hand, b = the way. */
    public static final int RELEASE = 7;

    private FlashFx() {
    }

    public static void send(ServerPlayer flash, int type, Vec3 a, Vec3 b, float power) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> flash),
                new HeroFxPacket(HeroSyncPacket.FLASH, flash.getId(), type, a, b, power, -1));
    }
}

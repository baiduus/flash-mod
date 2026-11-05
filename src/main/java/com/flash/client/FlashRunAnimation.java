package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Decides when the Flash run animation plays and how fast. It plays while a
 * Flash player (speed levels 1-10) is moving on the ground and, for every
 * level, during the abilities that make the player run: the speed tornado, the
 * lightning charge and the mass punch charge. Speeds are measured from the
 * position change of every player, so it also works for other players.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashRunAnimation {

    /** Horizontal blocks per tick above which a Flash player counts as running. */
    private static final double RUN_THRESHOLD = 0.12D;
    private static final int FORCED_MASK = (1 << FlashAbility.SPEED_TORNADO.ordinal())
            | (1 << FlashAbility.LIGHTNING_STRIKE.ordinal())
            | (1 << FlashAbility.MASS_PUNCH.ordinal());

    private static final Map<UUID, double[]> TRACK = new HashMap<>();

    private FlashRunAnimation() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TRACK.clear();
            return;
        }
        for (Player player : level.players()) {
            double[] data = TRACK.computeIfAbsent(player.getUUID(), id -> new double[] {
                    player.getX(), player.getZ(), 0.0D});
            double dx = player.getX() - data[0];
            double dz = player.getZ() - data[1];
            double speed = Math.sqrt(dx * dx + dz * dz);
            // Light smoothing so one-tick jumps do not flicker the animation.
            data[2] = speed > 4.0D ? 0.0D : data[2] * 0.4D + speed * 0.6D;
            data[0] = player.getX();
            data[1] = player.getZ();
        }
        Iterator<UUID> it = TRACK.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (level.getPlayerByUUID(id) == null) {
                it.remove();
            }
        }
    }

    public static void reset() {
        TRACK.clear();
    }

    private static double speed(Player player) {
        double[] data = TRACK.get(player.getUUID());
        return data == null ? 0.0D : data[2];
    }

    /** True when the run pose should be applied to this entity. */
    public static boolean isActive(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isSpectator() || !player.isAlive()
                || player.isPassenger() || player.isSleeping() || player.isFallFlying()
                || player.isSwimming() || player.isUsingItem()
                || FlashClientVisualLevels.isActive(player, FlashAbility.PHASING.ordinal())
                || FlashClientVisualLevels.isActive(player, FlashAbility.WIND_HANDS.ordinal())) {
            return false;
        }
        if (FlashClientVisualLevels.get(player) < 1) {
            return false;
        }
        if ((FlashClientVisualLevels.activeMask(player) & FORCED_MASK) != 0) {
            return true;
        }
        return player.onGround() && speed(player) > RUN_THRESHOLD;
    }

    /** Stride frequency in cycles per second: faster players move their limbs faster. */
    public static double frequency(Player player) {
        int level = Math.max(1, FlashClientVisualLevels.get(player));
        if ((FlashClientVisualLevels.activeMask(player) & FORCED_MASK) != 0) {
            return 4.6D;
        }
        double speedBoost = Math.min(1.0D, speed(player) / 1.2D);
        return 2.6D + level * 0.22D + speedBoost * 1.2D;
    }

    /** Forward lean of the whole body in degrees. */
    public static float lean(Player player) {
        int level = Math.max(1, FlashClientVisualLevels.get(player));
        return 14.0F + Math.min(10, level) * 1.6F;
    }

}

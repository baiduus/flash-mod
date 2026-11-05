package com.flash.ability;

import com.flash.power.FlashPowers;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Passive level 3+ water running, rebuilt.
 *
 * <p>The old version teleported the player from the server every tick, which
 * fought the client's own swimming physics and felt slow and jittery. The new
 * method treats the water surface as solid ground, and does it on <b>both</b>
 * sides with the same deterministic rule, once at the end of every player tick:
 * if the feet are at (or just below) the real surface height of the water
 * column, the player is placed exactly on it and flagged as standing on the
 * ground. Because the feet never enter the water, vanilla walking physics apply
 * (full speed, normal jumps, no drag) and client and server never disagree, so
 * there is no rubber banding. Sneaking lets the player sink and dive.</p>
 */
public final class FlashWaterWalk {

    /** Players for whom the server temporarily allowed "flying" so the anti-fly kick ignores water walking. */
    private static final Set<UUID> FLY_GRANTED = ConcurrentHashMap.newKeySet();

    private FlashWaterWalk() {
    }

    /**
     * Shared rule, called at the end of the player tick on the client (local
     * player) and on the server.
     *
     * @return true when the player was placed on the water surface
     */
    public static boolean walk(Player player) {
        if (player.isSpectator() || player.isPassenger() || player.isShiftKeyDown()
                || player.getAbilities().flying || player.noPhysics || player.isFallFlying()
                || player.isEyeInFluid(FluidTags.WATER)) {
            return false;
        }
        double top = surface(player);
        if (Double.isNaN(top)) {
            return false;
        }
        double feet = player.getY();
        if (feet > top + 0.05D) {
            return false;
        }
        // Deep entry (e.g. a fall): rise quickly instead of popping up at once.
        double target = feet < top - 0.3D ? Math.min(top, feet + 0.3D) : top;
        player.setPos(player.getX(), target, player.getZ());
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.0D, motion.z);
        if (target >= top) {
            player.setOnGround(true);
        }
        player.fallDistance = 0.0F;
        return true;
    }

    /** Server side: rule, anti-fly allowance, particles and sound. */
    public static void tick(ServerPlayer player) {
        boolean allowed = FlashPowers.hasPowers(player)
                && FlashPowers.getSelectedLevel(player) >= 3
                && !FlashAbilityManager.isPhasing(player);
        boolean walked = allowed && walk(player);
        if (!walked) {
            release(player);
            return;
        }

        // The server's "flying is not enabled" check would kick a player who
        // stands on water for four seconds; allow it only while walking.
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            FLY_GRANTED.add(player.getUUID());
        }

        Vec3 motion = player.getDeltaMovement();
        boolean moving = motion.x * motion.x + motion.z * motion.z > 0.0036D;
        if (moving && player.tickCount % 3 == 0) {
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.SPLASH,
                    player.getX(), player.getY() + 0.05D, player.getZ(),
                    3, 0.25D, 0.03D, 0.25D, 0.06D);
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.03D, player.getZ(),
                    1, 0.2D, 0.01D, 0.2D, 0.01D);
        }
        if (moving && player.tickCount % 10 == 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.25F, 1.6F);
        }
    }

    /** Takes back the temporary flight allowance. Safe to call at any time. */
    public static void release(ServerPlayer player) {
        if (FLY_GRANTED.remove(player.getUUID()) && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
        }
    }

    /**
     * Y of the real water surface under the player's feet (block Y + fluid
     * height), or NaN when the feet are not at/in a water column.
     */
    private static double surface(Player player) {
        Level level = player.level();
        int x = (int) Math.floor(player.getX());
        int z = (int) Math.floor(player.getZ());
        double feet = player.getY();
        BlockPos cell = new BlockPos(x, (int) Math.floor(feet + 0.15D), z);

        if (!level.getFluidState(cell).is(FluidTags.WATER)) {
            // Feet slightly above the surface: look at the cell right below.
            cell = cell.below();
            if (!level.getFluidState(cell).is(FluidTags.WATER)) {
                return Double.NaN;
            }
        }
        for (int i = 0; i < 4 && level.getFluidState(cell.above()).is(FluidTags.WATER); i++) {
            cell = cell.above();
        }
        FluidState fluid = level.getFluidState(cell);
        return cell.getY() + fluid.getHeight(level, cell);
    }
}

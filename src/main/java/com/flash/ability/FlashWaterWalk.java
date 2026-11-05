package com.flash.ability;

import com.flash.power.FlashPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Passive level 3+ water running.
 *
 * <p>Instead of only giving a tiny upward push when the player is already
 * submerged, the code now finds the top of the water column and keeps the
 * player's feet at that surface. The player can stand still on the water,
 * run across it and jump normally. Sneaking deliberately lets the player sink
 * so this does not remove the ability to dive.</p>
 */
public final class FlashWaterWalk {

    private FlashWaterWalk() {
    }

    public static void tick(ServerPlayer player) {
        if (!FlashPowers.hasPowers(player)
                || FlashPowers.getSelectedLevel(player) < 3
                || player.isSpectator()
                || player.isPassenger()
                || player.isShiftKeyDown()
                || FlashAbilityManager.isPhasing(player)) {
            return;
        }

        double surface = waterSurface(player);
        if (Double.isNaN(surface)) {
            return;
        }

        double feet = player.getY();
        if (feet < surface - 0.05D) {
            // Correct the vertical position on the server instead of sending a
            // velocity packet. A velocity packet would carry the server's
            // stale horizontal motion and make running on water feel slow.
            player.connection.teleport(player.getX(), surface, player.getZ(),
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.hasImpulse = true;
            player.fallDistance = 0.0F;
        } else if (feet <= surface + 0.18D) {
            player.fallDistance = 0.0F;
        }

        if (player.tickCount % 3 == 0) {
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.SPLASH,
                    player.getX(), surface + 0.04D, player.getZ(),
                    4, 0.28D, 0.05D, 0.28D, 0.08D);
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), surface + 0.02D, player.getZ(),
                    2, 0.22D, 0.02D, 0.22D, 0.01D);
        }
        if (player.tickCount % 12 == 0) {
            ServerLevel level = (ServerLevel) player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.35F, 1.5F);
        }
    }

    /**
     * @return the Y coordinate of the top face of the highest water block at
     *         the player's column, or NaN when the player is not in/on water.
     */
    private static double waterSurface(ServerPlayer player) {
        BlockPos feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        FluidState feetFluid = player.level().getFluidState(feet);
        FluidState belowFluid = player.level().getFluidState(feet.below());
        boolean inWater = feetFluid.is(Fluids.WATER) || belowFluid.is(Fluids.WATER);
        if (!inWater) {
            // A player standing exactly on the surface is not "in water", but
            // the block below is; accept that case as well.
            if (!belowFluid.is(Fluids.WATER)) {
                return Double.NaN;
            }
        }

        int top = feet.getY();
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos pos = feet.above(dy);
            if (player.level().getFluidState(pos).is(Fluids.WATER)) {
                top = pos.getY();
            } else if (dy > 0) {
                break;
            }
        }

        double surface = top + 1.0D;
        // Only treat it as walkable water when the player is close to the
        // surface; otherwise the player is diving and normal swimming applies.
        if (player.getY() > surface + 0.6D || player.getY() < surface - 3.2D) {
            return Double.NaN;
        }
        return surface;
    }
}

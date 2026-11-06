package com.flash.power;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side distance tracking and level unlocking.
 *
 * <p>The travelled distance is the sum of the real horizontal displacement of
 * every server tick. This naturally handles direction changes and only counts
 * movement while the player is actually running with a Flash level selected.
 * Flying, falling, diving, riding and teleports are ignored; running on water counts.</p>
 */
public final class FlashProgression {

    private static final double MIN_STEP = 0.002D;
    private static final double MAX_STEP = 8.0D;

    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();

    private FlashProgression() {
    }

    public static void tick(ServerPlayer player) {
        Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track());
        double x = player.getX();
        double z = player.getZ();

        if (!track.initialized) {
            track.initialized = true;
            track.lastX = x;
            track.lastZ = z;
            return;
        }

        double dx = x - track.lastX;
        double dz = z - track.lastZ;
        double step = Math.sqrt(dx * dx + dz * dz);

        if (isEligible(player)
                && step >= MIN_STEP
                && step <= MAX_STEP
                && FlashPowers.hasPowers(player)
                && !FlashPowers.isTemporary(player)
                // only the real Acceleration Force (positive or negative) grows; V-9-only powers do not
                && !FlashV9.isBorrowedPowers(player)
                && FlashPowers.getSelectedLevel(player) >= 1
                && !FlashPowers.isMaxLevel(player)) {
            int unlocked = FlashPowers.addDistance(player, step);
            if (unlocked > 0) {
                onLevelUnlocked(player);
            }
        }

        track.lastX = x;
        track.lastZ = z;
    }

    private static boolean isEligible(ServerPlayer player) {
        if (player.isSpectator() || player.isDeadOrDying() || player.isPassenger()) {
            return false;
        }
        if (player.getAbilities().flying || player.isFallFlying()) {
            return false;
        }
        if (player.isInLava() || player.isUnderWater()) {
            return false;
        }
        if (player.isInWater()) {
            return true; // running over (or through) water counts
        }
        // Walking, sprinting and jumping count. Sustained falling does not.
        if (!player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            return motion.y > -0.2D && motion.y < 0.6D;
        }
        return true;
    }

    private static void onLevelUnlocked(ServerPlayer player) {
        int level = FlashPowers.getMaxLevel(player);
        if (FlashPowers.isMaxLevel(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.flash.level_unlocked_max",
                    level
            ), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.flash.level_unlocked",
                    level,
                    FlashPowers.getBlocksToNextLevel(player)
            ), true);
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    public static void reset(ServerPlayer player) {
        TRACKS.remove(player.getUUID());
    }

    public static void clear() {
        TRACKS.clear();
    }

    private static final class Track {
        private boolean initialized;
        private double lastX;
        private double lastZ;
    }
}

package com.flash.transformation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * Visual and weather part of the transformation. The machine block entity owns
 * the state machine; this class only performs effects, which keeps the sequence
 * reusable if more machines are added later.
 */
public final class TransformationSequence {

    public static final int TOTAL_TICKS = 120;
    public static final int LIGHTNING_TICK = 50;
    private static final int WEATHER_TICKS = 6000;

    private TransformationSequence() {
    }

    public static void startStorm(ServerLevel level) {
        // Rain without vanilla thunder: random thunder strikes could damage the
        // player or nearby blocks. The scripted lightning below is visual-only.
        level.setWeatherParameters(0, WEATHER_TICKS, true, false);
    }

    public static void restoreWeather(ServerLevel level, boolean wasRaining, boolean wasThundering) {
        if (wasRaining || wasThundering) {
            level.setWeatherParameters(0, WEATHER_TICKS, wasRaining, wasThundering);
        } else {
            level.setWeatherParameters(WEATHER_TICKS, 0, false, false);
        }
    }

    public static void playStartSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    public static void playChargeSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.7F);
    }

    public static void playFinishSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    public static void spawnAmbientParticles(ServerLevel level, BlockPos pos, int progress) {
        spawnAmbientParticles(level, new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D), progress);
    }

    public static void spawnAmbientParticles(ServerLevel level, Vec3 center, int progress) {
        double x = center.x;
        double y = center.y;
        double z = center.z;

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.45D, 0.55D, 0.45D, 0.02D);
        if (progress % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.4D, z, 3, 0.35D, 0.45D, 0.35D, 0.01D);
        }
        if (progress % 20 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 5, 0.6D, 0.2D, 0.6D, 0.01D);
        }
    }

    /**
     * Strikes the chamber occupant directly.
     *
     * <p>The lightning entity itself is visual-only, so it cannot destroy
     * blocks or hurt nearby entities. The occupant receives one controlled
     * vanilla damage event and a short real fire state. The state machine
     * calls this at most once per transformation.</p>
     */
    public static void strikeLightning(ServerLevel level, ServerPlayer player) {
        Vec3 target = player.position();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.x, target.y, target.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        // Keep the hit non-lethal even if the player entered with low health;
        // the transformation must be allowed to finish.
        float damage = Math.min(2.0F, Math.max(0.0F, player.getHealth() - 1.0F));
        if (damage > 0.0F) {
            player.hurt(level.damageSources().lightningBolt(), damage);
        }
        player.setSecondsOnFire(2);

        level.sendParticles(ParticleTypes.FLASH,
                target.x, target.y + 0.9D, target.z,
                2, 0.1D, 0.1D, 0.1D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.x, target.y + 0.9D, target.z,
                10, 0.35D, 0.55D, 0.35D, 0.02D);
        level.playSound(null, target.x, target.y, target.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0F, 0.7F);
        level.playSound(null, target.x, target.y, target.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 0.9F);
    }

    public static void spawnFinishParticles(ServerLevel level, BlockPos pos) {
        spawnFinishParticles(level, new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D));
    }

    public static void spawnFinishParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y, center.z,
                40, 0.6D, 0.8D, 0.6D, 0.08D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
}

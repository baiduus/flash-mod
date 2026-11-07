package com.flash.superman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor dive: Superman up at the sun-bathing altitude who dives straight down catches fire on the way, and when he
 * hits the ground blows a crater the size of three TNTs and sets everything around on fire.
 */
public final class SupermanMeteor {

    /** Explosion power (TNT is 4; 6 is about three TNTs of crater). */
    public static final float POWER = 6.0F;
    private static final float START_PITCH = 62.0F;
    private static final double START_SPEED = 0.7D;

    private static final class Dive {
        double lastY;
        boolean active;
        int slow;
        int ticks;
    }

    private static final Map<UUID, Dive> DIVES = new HashMap<>();

    private SupermanMeteor() {
    }

    public static boolean active(ServerPlayer player) {
        Dive d = DIVES.get(player.getUUID());
        return d != null && d.active;
    }

    public static void clear(ServerPlayer player) {
        DIVES.remove(player.getUUID());
    }

    public static void tick(ServerPlayer player) {
        Dive d = DIVES.computeIfAbsent(player.getUUID(), k -> new Dive());
        double dy = player.getY() - d.lastY;
        d.lastY = player.getY();
        if (Math.abs(dy) > 64.0D) {
            return; // teleport
        }
        ServerLevel level = player.serverLevel();
        if (!d.active) {
            boolean up = player.getY() >= SupermanPowers.HIGH_ALTITUDE - 8;
            boolean down = player.getXRot() >= START_PITCH && dy < -START_SPEED;
            if (up && down && !player.onGround()) {
                d.active = true;
                d.slow = 0;
                d.ticks = 0;
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE,
                        SoundSource.PLAYERS, 2.0F, 0.6F);
            }
            return;
        }
        d.ticks++;
        // still a meteor: on fire, fire resistant, flames and smoke behind
        player.setRemainingFireTicks(40);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
        Vec3 at = player.position().add(0.0D, 0.9D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 26, 0.45D, 0.7D, 0.45D, 0.06D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.4D, 0.5D, 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0D, at.z, 8, 0.4D, 0.6D, 0.4D, 0.02D);
        for (int i = 1; i <= 4; i++) {
            double ty = at.y - dy * i * 0.3D;
            level.sendParticles(ParticleTypes.FLAME, at.x, ty, at.z, 6, 0.3D, 0.3D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, ty, at.z, 1, 0.2D, 0.2D, 0.2D, 0.01D);
        }
        if (d.ticks % 8 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.PLAYERS, 1.2F, 0.5F);
        }
        // the ground right ahead (fast dives cover many blocks per tick)
        double reach = Math.max(2.5D, -dy * 1.5D + 1.5D);
        BlockHitResult hit = level.clip(new ClipContext(player.position().add(0.0D, 0.2D, 0.0D),
                player.position().add(0.0D, -reach, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (player.onGround() || player.isInWater() || hit.getType() == HitResult.Type.BLOCK && dy < -0.4D) {
            Vec3 impact = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : player.position();
            impact(player, level, impact);
            d.active = false;
            return;
        }
        // pulled up or stopped: the fire goes out
        if (dy > -0.25D) {
            if (++d.slow > 10) {
                d.active = false;
                player.clearFire();
            }
        } else {
            d.slow = 0;
        }
    }

    private static void impact(ServerPlayer player, ServerLevel level, Vec3 at) {
        boolean destroy = SupermanPowers.destroyBlocks(player);
        level.explode(player, at.x, at.y + 0.5D, at.z, POWER, destroy,
                destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        if (destroy) {
            spreadFire(level, BlockPos.containing(at), player.getRandom());
        }
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5D, at.z, 160, 3.0D, 0.6D, 3.0D, 0.25D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5D, at.z, 40, 2.5D, 0.5D, 2.5D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 0.5D, at.z, 30, 3.0D, 0.5D, 3.0D, 0.03D);
        SupermanAbilities.cracks(level, at, 9.0F);
        SupermanAbilities.fxAt(level, com.flash.network.SupermanFxPacket.SHOCKWAVE, at.add(0.0D, 0.1D, 0.0D),
                new Vec3(0, 1, 0), 9.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        player.clearFire();
    }

    /** Fire on the ground all around the crater. */
    private static void spreadFire(ServerLevel level, BlockPos center, RandomSource random) {
        int radius = 9;
        for (int i = 0; i < 90; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0D;
            double dist = 2.0D + Math.sqrt(random.nextDouble()) * (radius - 2);
            int x = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, center.getY() + 4, z);
            for (int k = 0; k < 12; k++, pos.move(0, -1, 0)) {
                if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                    if (BaseFireBlock.canBePlacedAt(level, pos, net.minecraft.core.Direction.UP)) {
                        level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                    }
                    break;
                }
            }
        }
    }
}

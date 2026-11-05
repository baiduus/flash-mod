package com.flash.ability;

import com.flash.power.FlashPowers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Level 10 ability: for 15 seconds everything within 15 blocks of the player
 * (except the player) is in slow motion. Mobs run one tick in four, other
 * players get heavy slowness, and projectiles / items that enter the zone are
 * slowed down and restored when the ability ends.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID)
public final class FlashTimeSlow {

    static final int DURATION_TICKS = 300;
    static final double RADIUS = 15.0D;
    private static final String TAG = "flash_slow_until";
    private static final double PROJECTILE_FACTOR = 0.08D;

    private FlashTimeSlow() {
    }

    static final class Zone {
        int ticks;
        final Map<UUID, Held> held = new HashMap<>();
    }

    private static final class Held {
        final Entity entity;
        final Vec3 velocity;
        final boolean noGravity;

        Held(Entity entity, Vec3 velocity, boolean noGravity) {
            this.entity = entity;
            this.velocity = velocity;
            this.noGravity = noGravity;
        }
    }

    static Zone start(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(), SoundSource.PLAYERS, 1.6F, 0.6F);
        return new Zone();
    }

    /** @return {@code false} when the zone has expired. */
    static boolean tick(ServerPlayer player, Zone zone) {
        if (!player.isAlive() || zone.ticks >= DURATION_TICKS) {
            end(player, zone);
            return false;
        }
        zone.ticks++;
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Vec3 center = player.position();
        double radiusSqr = RADIUS * RADIUS;

        for (Entity entity : level.getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(RADIUS + 4.0D),
                e -> e != player && e.isAlive())) {
            double distance = entity.position().distanceToSqr(center);
            Held held = zone.held.get(entity.getUUID());
            if (held != null && distance > (RADIUS + 3.0D) * (RADIUS + 3.0D)) {
                release(zone, held);
                continue;
            }
            if (distance > radiusSqr && held == null) {
                continue;
            }
            if (entity instanceof ServerPlayer other) {
                other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 5,
                        true, false, false));
                other.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 10, 2,
                        true, false, false));
            } else if (entity instanceof LivingEntity living) {
                living.getPersistentData().putLong(TAG, now + 3L);
            } else if (entity instanceof Projectile projectile) {
                if (projectile.getOwner() == player) {
                    continue;
                }
                hold(zone, entity, held);
            } else if (entity instanceof ItemEntity) {
                hold(zone, entity, held);
            }
        }

        if (now % 4L == 0L) {
            ring(level, center, FlashPowers.isNegative(player));
        }
        return true;
    }

    private static void hold(Zone zone, Entity entity, Held held) {
        if (held == null) {
            Vec3 velocity = entity.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-4D) {
                return; // already stuck in the ground or resting
            }
            held = new Held(entity, velocity, entity.isNoGravity());
            zone.held.put(entity.getUUID(), held);
        }
        entity.setNoGravity(true);
        entity.setDeltaMovement(held.velocity.scale(PROJECTILE_FACTOR));
        entity.hurtMarked = true;
    }

    private static void release(Zone zone, Held held) {
        zone.held.remove(held.entity.getUUID());
        if (held.entity.isAlive()) {
            held.entity.setNoGravity(held.noGravity);
            held.entity.setDeltaMovement(held.velocity);
            held.entity.hurtMarked = true;
        }
    }

    static void end(ServerPlayer player, Zone zone) {
        Iterator<Held> iterator = zone.held.values().iterator();
        while (iterator.hasNext()) {
            Held held = iterator.next();
            iterator.remove();
            if (held.entity.isAlive()) {
                held.entity.setNoGravity(held.noGravity);
                held.entity.setDeltaMovement(held.velocity);
                held.entity.hurtMarked = true;
            }
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 1.6F);
    }

    private static void ring(ServerLevel level, Vec3 center, boolean negative) {
        ParticleOptions options = negative
                ? new DustParticleOptions(new Vector3f(1.0F, 0.08F, 0.08F), 1.4F)
                : ParticleTypes.ELECTRIC_SPARK;
        for (int i = 0; i < 48; i++) {
            double angle = i * (Math.PI * 2.0D / 48.0D);
            double x = center.x + Math.cos(angle) * RADIUS;
            double z = center.z + Math.sin(angle) * RADIUS;
            level.sendParticles(options, x, center.y + 0.3D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (i % 3 == 0) {
                level.sendParticles(options, x, center.y + 2.2D, z, 1, 0.0D, 0.3D, 0.0D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        long until = entity.getPersistentData().getLong(TAG);
        if (until > 0L) {
            long now = entity.level().getGameTime();
            if (until > now && now % 4L != 0L) {
                event.setCanceled(true);
            }
        }
    }
}

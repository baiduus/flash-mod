package com.flash.ability;

import com.flash.power.FlashPowers;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

/**
 * Level 6 ability: run to the nearest entity, punch it four times, then run to
 * the next nearest one (at most five entities in total).
 */
final class FlashDashPunch {

    static final int MAX_TARGETS = 5;
    static final int PUNCHES = 4;
    static final double SEARCH_RANGE = 24.0D;
    private static final int TARGET_TIMEOUT = 60;

    private FlashDashPunch() {
    }

    static final class Run {
        final Set<UUID> done = new HashSet<>();
        LivingEntity current;
        int total;
        int punches;
        int timeout;
        int stall;
        int cooldownTick;
    }

    /** Returns a new run, or {@code null} when nobody is close enough. */
    static Run start(ServerPlayer player) {
        Run run = new Run();
        LivingEntity first = findNext(player, run);
        if (first == null) {
            return null;
        }
        run.current = first;
        run.total = 1;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.9F, 1.5F);
        return run;
    }

    /** @return {@code false} once the ability is over. */
    static boolean tick(ServerPlayer player, Run run) {
        if (!player.isAlive()) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);

        if (run.current == null || !run.current.isAlive() || run.timeout > TARGET_TIMEOUT
                || run.stall > 8) {
            if (run.current != null) {
                run.done.add(run.current.getUUID());
            }
            if (run.total >= MAX_TARGETS) {
                return false;
            }
            LivingEntity next = findNext(player, run);
            if (next == null) {
                return false;
            }
            run.current = next;
            run.total++;
            run.punches = 0;
            run.timeout = 0;
            run.stall = 0;
        }
        run.timeout++;

        LivingEntity target = run.current;
        Vec3 from = player.position();
        Vec3 to = target.position();
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        float yaw = (float) (Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y,
                Math.max(0.001D, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));

        if (distance > 2.2D) {
            Vec3 direction = delta.normalize();
            double step = Math.min(2.6D, distance - 1.7D);
            Vec3 move = direction.scale(Math.max(0.4D, step));
            if (level.noCollision(player, player.getBoundingBox().move(move))) {
                trail(level, from, from.add(move));
                player.connection.teleport(from.x + move.x, from.y + move.y, from.z + move.z,
                        yaw, 0.0F);
            } else if (level.noCollision(player, player.getBoundingBox().move(move.add(0, 1.0D, 0)))) {
                player.connection.teleport(from.x + move.x, from.y + move.y + 1.0D,
                        from.z + move.z, yaw, 0.0F);
            } else {
                run.stall++;
            }
            return true;
        }

        // In reach: one punch every second tick.
        if (run.cooldownTick-- > 0) {
            return true;
        }
        run.cooldownTick = 1;
        player.connection.teleport(from.x, from.y, from.z, yaw, pitch);
        player.swing(InteractionHand.MAIN_HAND, true);
        float damage = (float) FlashAbilityCombat.meleeDamage(player) * 0.6F;
        FlashAbilityCombat.hit(player, target, damage);
        Vec3 push = delta.normalize().scale(0.12D);
        target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.03D, push.z));
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                target.getZ(), 6, 0.25D, 0.25D, 0.25D, 0.15D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.1F + run.punches * 0.1F);
        run.punches++;
        if (run.punches >= PUNCHES) {
            run.done.add(target.getUUID());
            run.current = null;
            run.timeout = 0;
            run.stall = 0;
        }
        return true;
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        for (int i = 0; i < 6; i++) {
            Vec3 p = from.lerp(to, i / 5.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.0D, p.z,
                    2, 0.12D, 0.35D, 0.12D, 0.02D);
        }
    }

    private static LivingEntity findNext(ServerPlayer player, Run run) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SEARCH_RANGE),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand)
                        && !e.isSpectator() && !run.done.contains(e.getUUID()))) {
            if (entity instanceof Player other && (other.isCreative() || other.isSpectator())) {
                continue;
            }
            if (entity instanceof TamableAnimal pet && pet.isOwnedBy(player)) {
                continue;
            }
            double distance = entity.distanceToSqr(player);
            if (distance < bestDistance && distance <= SEARCH_RANGE * SEARCH_RANGE) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    static boolean negative(ServerPlayer player) {
        return FlashPowers.isNegative(player);
    }
}

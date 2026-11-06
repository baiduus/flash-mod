package com.flash.hulk;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A gamma explosion (reactor accident or bomb): everyone close enough becomes the Hulk on the spot. */
public final class GammaBurst {

    private GammaBurst() {
    }

    public static void explode(ServerLevel level, Vec3 at, double radius) {
        List<ServerPlayer> exposed = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(radius), p -> p.isAlive() && !p.isSpectator()
                        && p.distanceToSqr(at) <= radius * radius);
        // the radiation rewrites the body first, so the blast does not kill them
        for (ServerPlayer player : exposed) {
            HulkPowers.grant(player, true);
            player.displayClientMessage(Component.translatable("message.flash.gamma_exposed"), false);
        }
        level.explode(null, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.NONE);
        level.sendParticles(HulkPowers.GAMMA_DUST, at.x, at.y + 0.5D, at.z, 300, radius / 2.0D, 1.5D, radius / 2.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5D, at.z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 1.0D, at.z, 80, radius / 2.0D, 1.5D, radius / 2.0D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 2.0F, 0.6F);
    }
}

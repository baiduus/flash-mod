package com.flash.storm;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.StormFxPacket;
import com.flash.network.StormOfferPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The way to Storm: a player with no power struck by a real lightning bolt (a thunderstorm, a lightning rod, a
 * Channeling trident, {@code /summon lightning_bolt}) is not hurt: the lightning chose him, and asks whether he wants
 * the power of the storms (StormOfferScreen). Yes: he becomes Storm; no: nothing, the next bolt asks again.
 * <p>
 * The mod's own bolts (the Flash chamber, Mjolnir, Thor, Storm) only show, they never strike anyone, so they never
 * ask. {@code /storm strike [player]} drops a real bolt on him; {@code /storm offer [player]} asks at once.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class StormCalling {

    /** Players being asked (the window open): player -> server tick it was opened. */
    private static final Map<UUID, Integer> ASKED = new HashMap<>();
    /** Struck a moment ago: the question comes once the flash is over (player -> server tick to ask). */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private StormCalling() {
    }

    private static boolean eligible(ServerPlayer p) {
        return com.flash.scarlet.ScarletChaos.eligible(p) && !p.isSpectator();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStruck(EntityStruckByLightningEvent event) {
        // (only real bolts strike: the visual-only ones never reach this event)
        if (!(event.getEntity() instanceof ServerPlayer p) || !eligible(p)) {
            return;
        }
        // the lightning does not burn the one it chose
        event.setCanceled(true);
        p.clearFire();
        UUID id = p.getUUID();
        if (ASKED.containsKey(id) || PENDING.containsKey(id)) {
            return;
        }
        PENDING.put(id, p.server.getTickCount() + 12);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0D, p.getZ(), 80, 0.4D, 1.0D, 0.4D,
                0.5D);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0D, p.getZ(), 25, 0.3D, 0.8D, 0.3D, 0.08D);
        p.displayClientMessage(Component.translatable("message.flash.storm.struck"), true);
    }

    /** The question (the window on his screen). */
    public static void offer(ServerPlayer p) {
        ASKED.put(p.getUUID(), p.server.getTickCount());
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0D, p.getZ(), 40, 0.5D, 1.0D, 0.5D,
                0.3D);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.6F,
                1.4F);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new StormOfferPacket());
    }

    public static void answer(ServerPlayer p, boolean accept) {
        if (ASKED.remove(p.getUUID()) == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (accept && eligible(p)) {
            StormPowers.grant(p);
            // the sky answers her: a bolt of her own onto her, the eyes lit
            Vec3 at = p.position();
            StormAbilities.fx(p, StormFxPacket.SKY, at.add(0.0D, 45.0D, 0.0D), at, 1.2F);
            StormAbilities.casting(p);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F,
                    0.8F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F,
                    1.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0D, at.z, 40, 0.7D, 1.2D, 0.7D, 0.6D);
            StormAbilities.fx(p, StormFxPacket.CALL, at, at, 1.0F);
            p.sendSystemMessage(Component.translatable("message.flash.storm.accepted"));
        } else {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0D, p.getZ(), 20, 0.3D, 0.6D,
                    0.3D, 0.05D);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                    0.6F, 1.6F);
            p.displayClientMessage(Component.translatable("message.flash.storm.refused"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int now = event.getServer().getTickCount();
        for (Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            p.clearFire();
            if (now < en.getValue()) {
                continue;
            }
            it.remove();
            if (p.isAlive() && eligible(p)) {
                offer(p);
            }
        }
        // a window left open too long (or lost) stops counting after 5 min: the next bolt asks again
        ASKED.values().removeIf(t -> now - t > 6000);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ASKED.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------
    // Test commands (added to /storm)
    // ------------------------------------------------------------------

    /** A real lightning bolt on him, as a thunderstorm's (the real way). */
    public static void strike(ServerPlayer p) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(p.serverLevel());
        if (bolt != null) {
            bolt.moveTo(p.getX(), p.getY(), p.getZ());
            p.serverLevel().addFreshEntity(bolt);
        }
    }

    public static boolean canBe(ServerPlayer p) {
        return eligible(p);
    }
}

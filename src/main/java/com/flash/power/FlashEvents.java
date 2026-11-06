package com.flash.power;

import com.flash.FlashMod;
import com.flash.ability.FlashAbilityManager;
import com.flash.ability.FlashDimensionTravel;
import com.flash.block.FlashAccelerationPortal;
import com.flash.network.FlashSyncPacket;
import com.flash.network.FlashVisualSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the speed modifier, the progression tracker, combustion,
 * regeneration and the client snapshot alive across login, death, respawn,
 * dimension changes and logout.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class FlashEvents {

    private FlashEvents() {
    }

    /** With the speed active (level 1 or higher) the player takes no fall damage. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && FlashPowers.hasPowers(player) && FlashPowers.getSelectedLevel(player) >= 1) {
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        FlashPowers.copy(event.getOriginal(), event.getEntity());
        FlashRing.copy(event.getOriginal(), event.getEntity());
        FlashAmplifier.copy(event.getOriginal(), event.getEntity());
        FlashV9.copy(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            resetTransient(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
            FlashRing.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resetTransient(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashDimensionTravel.onLogin(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
            FlashRing.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resetTransient(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
            FlashRing.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer leaving && leaving.getServer() != null) {
            net.minecraft.server.level.ServerLevel from = leaving.getServer().getLevel(event.getFrom());
            if (from != null) {
                com.flash.entity.AccelerationPhantom.dismiss(leaving, from);
            }
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            resetTransient(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
            FlashRing.sync(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer listener
                && event.getTarget() instanceof ServerPlayer target) {
            FlashRing.syncTo(listener, target);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.flash.ability.FlashWaterWalk.release(player);
            FlashVisualSyncPacket.send(player, 0);
            resetTransient(player);
        }
    }

    /**
     * Teleports (ender pearl, chorus fruit, /tp, portals) must never count as
     * travelled distance or as a trail segment. The next tick re-initialises
     * the trackers silently.
     */
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlashProgression.reset(player);
            FlashCombustion.reset(player, true);
        }
    }

    /**
     * Caps the healing from very high Regeneration tiers so level 10 remains
     * strong but is not permanent invulnerability.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().is(DamageTypes.IN_WALL)
                && FlashAbilityManager.isPhasing(player)) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float allowed = FlashStatusEffects.allowHeal(player, event.getAmount());
        if (allowed <= 0.0F) {
            event.setCanceled(true);
        } else if (allowed < event.getAmount()) {
            event.setAmount(allowed);
        }
    }
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (FlashPowers.tickTemporary(player)) {
            FlashSpeed.remove(player);
            FlashStep.remove(player);
            FlashStatusEffects.reset(player);
            FlashAbilityManager.reset(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player, 0);
            player.displayClientMessage(
                    Component.translatable("message.flash.temporary_expired"), true);
        }

        FlashV9.tick(player);
        com.flash.entity.AccelerationPhantom.tickHunt(player);
        FlashProgression.tick(player);
        FlashCombustion.tick(player);
        FlashStatusEffects.tick(player);
        FlashAbilityManager.tick(player);

        // every tick: the speed follows sprinting (walk = normal speed, sprint = selected level)
        FlashSpeed.apply(player);
        if (player.tickCount % 20 == 0) {
            FlashStep.apply(player);
        }
        if (player.tickCount % 10 == 0 && FlashPowers.hasPowers(player)) {
            FlashSyncPacket.send(player);
        }
        if (player.tickCount % 20 == 0 && FlashPowers.hasPowers(player)) {
            FlashVisualSyncPacket.send(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FlashProgression.clear();
        FlashStatusEffects.clear();
        FlashAbilityManager.clear();
        FlashAccelerationPortal.clear();
        FlashCombustion.clear();
    }

    private static void resetTransient(ServerPlayer player) {
        FlashProgression.reset(player);
        FlashStatusEffects.reset(player);
        FlashAbilityManager.reset(player);
        FlashCombustion.reset(player, true);
        FlashStep.remove(player);
    }

    @SubscribeEvent
    public static void onLoggedOutGhost(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.flash.entity.AccelerationPhantom.dismiss(player, level);
        }
    }

    @SubscribeEvent
    public static void onDeathGhost(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.flash.entity.AccelerationPhantom.dismiss(player, level);
        }
    }
}

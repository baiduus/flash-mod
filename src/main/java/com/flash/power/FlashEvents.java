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

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        FlashPowers.copy(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            resetTransient(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
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
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resetTransient(player);
            FlashSpeed.apply(player);
            FlashStep.apply(player);
            FlashStatusEffects.onLevelChanged(player);
            FlashSyncPacket.send(player);
            FlashVisualSyncPacket.send(player);
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

        FlashProgression.tick(player);
        FlashCombustion.tick(player);
        FlashStatusEffects.tick(player);
        FlashAbilityManager.tick(player);

        if (player.tickCount % 20 == 0) {
            FlashSpeed.apply(player);
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
}

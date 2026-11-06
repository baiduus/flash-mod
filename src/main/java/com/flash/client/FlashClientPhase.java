package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import com.flash.ability.FlashWaterWalk;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the molecular vibration.
 *
 * <p>Vanilla resets {@code noPhysics} at the very start of every player tick
 * ({@code Player.tick}), so setting it once per tick from outside never let the
 * local player walk through walls. {@link LivingEvent.LivingTickEvent} fires
 * after that reset and before the movement is computed, which is exactly where
 * collisions must be switched off. The skill is also applied the moment the key
 * is pressed (without waiting for the server), and the "inside a block" screen
 * overlay is hidden.</p>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashClientPhase {

    private static boolean wasPhasing;
    /** Ground height where the current vibration started. */
    private static double startFloor;

    private FlashClientPhase() {
    }

    public static boolean isPhasing() {
        if (!FlashClientData.hasPowers() || FlashClientData.getSelectedLevel() < 1
                || FlashClientData.getMaxLevel() < FlashAbility.PHASING.requiredLevel()) {
            return false;
        }
        return FlashClientData.isAbilityActive(FlashAbility.PHASING.ordinal())
                || FlashAbilityKeys.isHeld(FlashAbility.PHASING);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        boolean phasing = isPhasing() && !player.isSpectator();
        if (phasing) {
            if (!wasPhasing) {
                startFloor = com.flash.ability.FlashPhase.startFloor(player);
            }
            // walls are crossed, but gravity and jumping stay normal (the floor holds the feet)
            player.noPhysics = true;
            player.setNoGravity(false);
            player.fallDistance = 0.0F;
        } else if (wasPhasing) {
            player.noPhysics = false;
            player.setNoGravity(false);
        }
        wasPhasing = phasing;
    }

    /** Water walking, client half: same rule, end of the local player's tick. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof LocalPlayer player)) {
            return;
        }
        if (wasPhasing) {
            com.flash.ability.FlashPhase.holdFloor(player,
                    com.flash.ability.FlashPhase.floor(player, startFloor));
            return;
        }
        if (FlashClientData.hasPowers() && FlashClientData.getSelectedLevel() >= 3) {
            FlashWaterWalk.walk(player);
        }
    }

    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (wasPhasing && event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK) {
            event.setCanceled(true);
        }
    }
}

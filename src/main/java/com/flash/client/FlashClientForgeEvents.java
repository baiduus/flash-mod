package com.flash.client;

import com.flash.FlashMod;
import com.flash.config.FlashClientConfig;
import com.flash.network.FlashNetwork;
import com.flash.network.FlashSelectLevelPacket;
import com.flash.power.FlashSpeedLevels;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus client events: configurable level controls, mouse-wheel handling
 * and cache cleanup. Other screens, text fields, inventories and scrollable
 * lists keep their vanilla behaviour; only the mod's own read-only K panel
 * accepts the speed keys while it is open.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashClientForgeEvents {

    private FlashClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        FlashClientData.tick();
        FlashAbilityKeys.tick();


        while (FlashKeys.TOGGLE_SPEED.consumeClick()) {
            if (minecraft.screen == null && FlashClientData.hasPowers()) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashToggleSpeedPacket());
            }
        }
        while (FlashKeys.TOGGLE_SUIT.consumeClick()) {
            if (minecraft.screen == null && PowerTheme.localSuperman()) {
                // Superman: the suit key turns Clark Kent into Superman and back
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanTransformPacket());
            } else if (minecraft.screen == null) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                        com.flash.network.FlashRingPacket.TOGGLE_SUIT));
            }
        }
        FlashWallRun.tick(minecraft);

        boolean increase = drain(FlashKeys.INCREASE_SPEED);
        boolean decrease = drain(FlashKeys.DECREASE_SPEED);
        if (FlashClientConfig.SCROLL_MODE.get() || PowerTheme.localHulk() || PowerTheme.localSuperman()) {
            // Alt + scroll mode is active (always for the Hulk, whose V key grabs): the V keys are disabled.
            increase = false;
            decrease = false;
        }

        while (FlashKeys.OPEN_PROGRESS.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new FlashProgressScreen());
            }
        }

        if (increase || decrease) {
            // Screens own their input. The K panel implements the same keys in
            // keyPressed so a key press cannot be handled twice.
            if (minecraft.screen != null) {
                return;
            }
            int direction;
            if (increase && decrease) {
                direction = Screen.hasControlDown() ? -1 : 1;
            } else if (increase) {
                direction = Screen.hasControlDown() ? -1 : 1;
            } else {
                direction = -1;
            }
            changeLevel(minecraft, direction, true);
        }
    }

    private static boolean drain(KeyMapping mapping) {
        boolean clicked = false;
        while (mapping.consumeClick()) {
            clicked = true;
        }
        return clicked;
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(FlashClientConfig.SCROLL_MODE.get() || PowerTheme.localHulk())
                || minecraft.player == null
                || minecraft.screen != null
                || !Screen.hasAltDown()
                || minecraft.player.isSpectator()
                || !selectorAvailable()) {
            return;
        }

        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }

        // Consume the notch so the hotbar does not scroll too.
        event.setCanceled(true);
        changeLevel(minecraft, delta > 0.0D ? 1 : -1, false);
    }

    /** The level selector works for the Flash (speed) and for the Hulk (active level). */
    static boolean selectorAvailable() {
        return FlashClientData.hasPowers() || PowerTheme.localHulk();
    }

    static void changeLevel(Minecraft minecraft, int direction, boolean showMessages) {
        if (PowerTheme.localHulk()) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkLevelPacket(direction));
            return;
        }
        if (!FlashClientData.hasPowers()) {
            if (showMessages) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.flash.no_powers"), true);
            }
            return;
        }

        int current = FlashClientData.getSelectedLevel();
        int max = Math.min(FlashClientData.getMaxLevel(), FlashSpeedLevels.MAX_LEVEL);
        int next = current + direction;
        if (next < 0 || next > max || next == current) {
            return;
        }
        FlashClientData.setSelectedLevelOptimistic(next);
        FlashNetwork.CHANNEL.sendToServer(new FlashSelectLevelPacket(next));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FlashAbilityKeys.releaseAll();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.noPhysics = false;
            Minecraft.getInstance().player.setNoGravity(false);
        }
        FlashClientData.reset();
        FlashClientVisualLevels.reset();
        FlashClientRing.reset();
    }

    /** True while this handler draws the hand itself (the event fires again from inside renderRightHand). */
    private static boolean drawingRingHand;

    /** First person: the empty hand is drawn here so the ring / the Hulk's arm can be added on top of it. */
    @SubscribeEvent
    public static void onRenderArm(net.minecraftforge.client.event.RenderArmEvent event) {
        if (drawingRingHand) {
            return;
        }
        boolean right = event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        boolean ring = right && FlashClientRing.has(event.getPlayer());
        boolean hulk = com.flash.hulk.HulkState.isTransformed(event.getPlayer());
        if (!ring && !hulk) {
            return;
        }
        net.minecraft.client.renderer.entity.EntityRenderer<?> renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(event.getPlayer());
        if (!(renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player)) {
            return;
        }
        event.setCanceled(true);
        drawingRingHand = true;
        try {
            // the nested event passes through untouched, so the vanilla hand is drawn
            if (hulk) {
                // the Hulk's arm replaces the skin arm (drawn below)
            } else if (right) {
                player.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(),
                        event.getPackedLight(), event.getPlayer());
            } else {
                player.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(),
                        event.getPackedLight(), event.getPlayer());
            }
        } finally {
            drawingRingHand = false;
        }
        if (hulk) {
            HulkClient.drawArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    event.getPlayer(), player.getModel(), event.getArm());
        }
        if (ring) {
            event.getPoseStack().pushPose();
            player.getModel().rightArm.translateAndRotate(event.getPoseStack());
            FlashRingLayer.drawRing(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), "slim".equals(event.getPlayer().getModelName()));
            event.getPoseStack().popPose();
        }
    }
}

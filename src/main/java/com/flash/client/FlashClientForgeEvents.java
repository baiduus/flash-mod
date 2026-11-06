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
        FlashWallRun.tick(minecraft);

        boolean increase = drain(FlashKeys.INCREASE_SPEED);
        boolean decrease = drain(FlashKeys.DECREASE_SPEED);

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
        if (!FlashClientConfig.MOUSE_WHEEL_CONTROLS.get()
                || minecraft.player == null
                || minecraft.screen != null
                || !minecraft.player.isShiftKeyDown()
                || minecraft.player.isSpectator()
                || !FlashClientData.hasPowers()) {
            return;
        }

        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }

        // Consume the notch so the camera does not also zoom while sneaking.
        event.setCanceled(true);
        changeLevel(minecraft, delta > 0.0D ? 1 : -1, false);
    }

    static void changeLevel(Minecraft minecraft, int direction, boolean showMessages) {
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
    }
}

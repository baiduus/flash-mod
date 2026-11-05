package com.flash.client;

import com.flash.power.FlashSpeedLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Compact HUD: selected level, progress to the next unlock and the combustion
 * warning. It reads the server-synced snapshot, never the local guess.
 */
public class FlashHud implements IGuiOverlay {

    private static final int COLOR_GREEN = 0xFF35D06A;
    private static final int COLOR_ORANGE = 0xFFFF7A3D;
    private static final int COLOR_RED = 0xFFFF5A3C;
    private static final int COLOR_MUTED = 0xFFB9C0C8;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
                || minecraft.player.isSpectator() || minecraft.screen != null
                || minecraft.options.renderDebug) {
            return;
        }
        if (!FlashClientData.hasPowers()) {
            return;
        }

        int level = FlashClientData.getSelectedLevel();
        boolean combustion = level >= FlashSpeedLevels.FIRE_LEVEL;
        int accent = combustion ? COLOR_ORANGE : COLOR_GREEN;

        String line1 = Component.translatable("hud.flash.level", level).getString();
        String line2;
        if (FlashClientData.getMaxLevel() >= FlashSpeedLevels.MAX_LEVEL) {
            line2 = Component.translatable("hud.flash.max_level").getString();
        } else {
            int travelled = Math.min(FlashSpeedLevels.BLOCKS_PER_LEVEL,
                    (int) Math.floor(FlashClientData.getDistance()));
            line2 = Component.translatable(
                    "hud.flash.next",
                    FlashClientData.getMaxLevel() + 1,
                    travelled,
                    FlashSpeedLevels.BLOCKS_PER_LEVEL
            ).getString();
        }
        String line3 = combustion ? Component.translatable("hud.flash.combustion").getString() : null;

        int textWidth = minecraft.font.width(line1);
        textWidth = Math.max(textWidth, minecraft.font.width(line2));
        if (line3 != null) {
            textWidth = Math.max(textWidth, minecraft.font.width(line3));
        }

        int x = 6;
        int y = 6;
        int boxWidth = textWidth + 14;
        int boxHeight = 12 + (line3 != null ? 22 : 12);

        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0000000);
        graphics.fill(x, y, x + 2, y + boxHeight, accent);
        graphics.drawString(minecraft.font, line1, x + 7, y + 4, accent, true);
        graphics.drawString(minecraft.font, line2, x + 7, y + 15, COLOR_MUTED, true);
        if (line3 != null) {
            graphics.drawString(minecraft.font, line3, x + 7, y + 26, COLOR_RED, true);
        }
    }
}

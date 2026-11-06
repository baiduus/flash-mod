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
        // any suit protects from the combustion, so the warning only shows without one
        boolean combustion = level >= FlashSpeedLevels.FIRE_LEVEL
                && com.flash.item.FlashSuitItem.countWorn(minecraft.player) == 0;

        String line1 = Component.translatable(
                FlashClientVisualLevels.isLocalNegative() ? "hud.flash.level_negative" : "hud.flash.level", level).getString();
        String line2;
        if (FlashClientData.getMaxLevel() >= FlashSpeedLevels.BASE_MAX_LEVEL) {
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

        var font = minecraft.font;
        int textWidth = Math.max(font.width(line1), font.width(line2));
        if (line3 != null) {
            textWidth = Math.max(textWidth, font.width(line3));
        }

        int x = 6;
        int y = 6;
        int boxWidth = textWidth + 14;
        int boxHeight = 12 + (line3 != null ? 22 : 12);

        graphics.fill(x, y, x + boxWidth, y + boxHeight, FlashHudStyle.PANEL);
        FlashHudStyle.barVertical(graphics, x, y, 3, boxHeight);
        FlashHudStyle.gradientText(graphics, font, line1, x + 8, y + 4);
        graphics.drawString(font, line2, x + 8, y + 15, FlashHudStyle.TEXT_MUTED, true);
        if (line3 != null) {
            FlashHudStyle.gradientText(graphics, font, line3, x + 8, y + 26);
        }
        // Progress strip along the bottom edge (orange to yellow).
        if (FlashClientData.getMaxLevel() < FlashSpeedLevels.BASE_MAX_LEVEL) {
            float progress = Math.min(1.0F, (float) (FlashClientData.getDistance()
                    / FlashSpeedLevels.BLOCKS_PER_LEVEL));
            int filled = Math.round((boxWidth - 3) * progress);
            if (filled > 0) {
                FlashHudStyle.barHorizontal(graphics, x + 3, y + boxHeight - 2, filled, 2);
            }
        }
    }
}

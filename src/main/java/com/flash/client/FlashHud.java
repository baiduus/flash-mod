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
        if (PowerTheme.localHulk()) {
            drawHulk(graphics, minecraft);
            return;
        }
        if (!FlashClientData.hasPowers()) {
            // the 30 seconds without powers after the V-9
            int lock = FlashClientVisualLevels.localLockTicks();
            if (lock > 0) {
                drawTimer(graphics, minecraft.font, 6, 6, "hud.flash.v9_lock", lock,
                        com.flash.power.FlashV9.NO_POWERS_TICKS, 0xFF6A6F78, 0xFFB9C0C8);
            }
            return;
        }

        int level = FlashClientData.getSelectedLevel();
        // any suit protects from the combustion, so the warning only shows without one
        boolean combustion = level >= FlashSpeedLevels.FIRE_LEVEL
                && com.flash.item.FlashSuitItem.countWorn(minecraft.player) == 0;

        String line1 = Component.translatable(
                FlashClientVisualLevels.isLocalV9() ? "hud.flash.level_v9"
                        : FlashClientVisualLevels.isLocalNegative() ? "hud.flash.level_negative" : "hud.flash.level",
                level).getString();
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
        // V-9 countdown right under the panel
        int v9 = FlashClientVisualLevels.localV9Ticks();
        if (v9 > 0) {
            drawTimer(graphics, font, x, y + boxHeight + 3, "hud.flash.v9_timer", v9,
                    com.flash.power.FlashV9.DURATION_TICKS, 0xFF1A5CFF, 0xFF7FE0FF);
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

    /** Small panel "label m:ss" with a bar that empties as the time runs out (blinks in the last 10 s). */
    private static void drawTimer(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y,
                                  String key, int ticks, int total, int from, int to) {
        int seconds = (ticks + 19) / 20;
        String text = Component.translatable(key,
                String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)).getString();
        int w = Math.max(120, font.width(text) + 14);
        int h = 18;
        g.fill(x, y, x + w, y + h, FlashHudStyle.PANEL);
        g.fill(x, y, x + 3, y + h, to);
        boolean blink = seconds <= 10 && (ticks / 5) % 2 == 0;
        g.drawString(font, text, x + 8, y + 3, blink ? 0xFFFFFFFF : to, true);
        float left = Math.min(1.0F, ticks / (float) total);
        int bar = Math.round((w - 3) * left);
        for (int i = 0; i < bar; i++) {
            float t = i / (float) Math.max(1, w - 3);
            int r = (int) (((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
            int gg = (int) (((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
            int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
            g.fill(x + 3 + i, y + h - 3, x + 4 + i, y + h - 1, 0xFF000000 | (r << 16) | (gg << 8) | b);
        }
    }

    /** Hulk HUD: form and the rage meter. */
    private static void drawHulk(GuiGraphics g, Minecraft mc) {
        boolean form = com.flash.hulk.HulkState.isTransformed(mc.player);
        float rage = Math.max(0.0F, Math.min(1.0F,
                com.flash.hulk.HulkState.clientRage() / com.flash.hulk.HulkPowers.MAX_RAGE));
        var font = mc.font;
        String line1 = Component.translatable(form ? "hud.flash.hulk" : "hud.flash.banner").getString();
        String line2 = form
                ? Component.translatable("hud.flash.hulk_rage", Math.round(rage * 100.0F)).getString()
                : Component.translatable("hud.flash.banner_hint", FlashKeys.HULK_TRANSFORM.getTranslatedKeyMessage()).getString();
        int w = Math.max(font.width(line1), font.width(line2)) + 14;
        int h = 26;
        int x = 6;
        int y = 6;
        g.fill(x, y, x + w, y + h, FlashHudStyle.PANEL);
        g.fill(x, y, x + 3, y + h, 0xFF5BD94A);
        g.drawString(font, line1, x + 8, y + 4, 0xFF7CF06A, true);
        g.drawString(font, line2, x + 8, y + 15, FlashHudStyle.TEXT_MUTED, true);
        if (form) {
            int bar = Math.round((w - 3) * rage);
            int color = rage > 0.75F ? 0xFFFF5A3C : 0xFF5BD94A;
            g.fill(x + 3, y + h - 2, x + 3 + bar, y + h, color);
        }
    }
}

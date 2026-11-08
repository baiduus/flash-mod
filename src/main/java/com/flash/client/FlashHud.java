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
        if (PowerTheme.localThor()) {
            drawThor(graphics, minecraft);
            return;
        }
        if (PowerTheme.localSuperman()) {
            drawSuperman(graphics, minecraft);
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
        // borrowed powers (Speed Manipulation): 5-minute countdown under the panel
        if (FlashClientData.isTemporary()) {
            int left = FlashClientData.getTemporaryRemainingNow();
            if (left > 0) {
                drawTimer(graphics, font, x, y + boxHeight + 3, "hud.flash.temp_timer", left, 6000,
                        FlashHudStyle.from(), FlashHudStyle.to());
                boxHeight += 17;
            }
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

    /** Hulk HUD (top left), styled like the Flash one with the green-to-purple gradient. */
    private static void drawHulk(GuiGraphics g, Minecraft mc) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean form = com.flash.hulk.HulkState.isTransformed(mc.player);
        int level = Math.max(1, com.flash.hulk.HulkState.level());
        float rage = Math.max(0.0F, Math.min(1.0F,
                com.flash.hulk.HulkState.rage() / com.flash.hulk.HulkPowers.MAX_RAGE));
        int temp = com.flash.hulk.HulkState.tempTicks(now);
        int fury = com.flash.hulk.HulkState.furyTicks(now);
        var font = mc.font;
        int active = Math.max(1, Math.min(level, com.flash.hulk.HulkState.activeLevel()));
        String line1 = form ? Component.translatable("hud.flash.hulk_level",
                        active < level ? active + "/" + level : String.valueOf(level)).getString()
                : Component.translatable("hud.flash.banner").getString();
        String line2 = form
                ? (level >= com.flash.hulk.HulkPowers.MAX_LEVEL
                    ? Component.translatable("hud.flash.hulk_rage_max").getString()
                    : Component.translatable("hud.flash.hulk_rage", Math.round(rage * 100.0F)).getString())
                : Component.translatable("hud.flash.banner_hint", FlashKeys.HULK_TRANSFORM.getTranslatedKeyMessage()).getString();
        int extra = com.flash.hulk.HulkState.extraTicks(now);
        String line3 = temp > 0 ? Component.translatable("hud.flash.hulk_temp", time(temp)).getString()
                : extra > 0 ? Component.translatable("hud.flash.hulk_extra", time(extra)).getString()
                : fury > 0 ? Component.translatable("hud.flash.hulk_fury", time(fury)).getString() : null;
        int textWidth = Math.max(font.width(line1), font.width(line2));
        if (line3 != null) {
            textWidth = Math.max(textWidth, font.width(line3));
        }
        int x = 6;
        int y = 6;
        int boxWidth = textWidth + 14;
        int boxHeight = line3 != null ? 37 : 26;
        g.fill(x, y, x + boxWidth, y + boxHeight, FlashHudStyle.PANEL);
        FlashHudStyle.barVertical(g, x, y, 3, boxHeight);
        FlashHudStyle.gradientText(g, font, line1, x + 8, y + 4);
        g.drawString(font, line2, x + 8, y + 15, FlashHudStyle.TEXT_MUTED, true);
        if (line3 != null) {
            FlashHudStyle.gradientText(g, font, line3, x + 8, y + 26);
        }
        if (form) {
            int filled = Math.round((boxWidth - 3) * rage);
            if (filled > 0) {
                FlashHudStyle.barHorizontal(g, x + 3, y + boxHeight - 2, filled, 2);
            }
        }
        // what the Hulk is holding
        boolean rockHeld = HulkAnimator.holdingRock(mc.player.getUUID());
        if (rockHeld && !HulkClient.charging()) {
            String t = Component.translatable("hud.flash.hulk_rock_held",
                    HulkClient.key(com.flash.hulk.HulkAbility.BOULDER).getTranslatedKeyMessage()).getString();
            int ty = y + boxHeight + 4;
            int tw = font.width(t) + 14;
            g.fill(x, ty, x + tw, ty + 14, FlashHudStyle.PANEL);
            FlashHudStyle.barVertical(g, x, ty, 3, 14);
            FlashHudStyle.gradientText(g, font, t, x + 8, ty + 3);
        }
        // super throw countdown (charging the grab), under the panel
        if (HulkClient.chargingGrab()) {
            int left = HulkClient.superTicksLeft();
            String t = left > 0
                    ? Component.translatable("hud.flash.hulk_super", time(left)).getString()
                    : Component.translatable("hud.flash.hulk_super_ready").getString();
            int ty = y + boxHeight + 4;
            int tw = font.width(t) + 14;
            g.fill(x, ty, x + tw, ty + 14, FlashHudStyle.PANEL);
            FlashHudStyle.barVertical(g, x, ty, 3, 14);
            FlashHudStyle.gradientText(g, font, t, x + 8, ty + 3);
            float f = 1.0F - left / (float) HulkClient.superTicks();
            int filled = Math.round((tw - 3) * f);
            if (filled > 0) {
                FlashHudStyle.barHorizontal(g, x + 3, ty + 12, filled, 2);
            }
        }
    }

    /** Superman HUD (top left): form, level, solar energy bar and progress to the next level. */
    /** Thor: level, storm energy and the Mjolnir charge ("Mjolnir · X %"). */
    private static void drawThor(GuiGraphics g, Minecraft mc) {
        var font = mc.font;
        boolean form = com.flash.thor.ThorState.isThor(mc.player);
        int level = Math.max(1, com.flash.thor.ThorState.level());
        float energy = com.flash.thor.ThorState.energy();
        float charge = com.flash.thor.ThorState.charge();
        boolean infinite = com.flash.thor.ThorState.infiniteEnergy();
        int storm = com.flash.thor.ThorState.storm();
        String line1 = form ? Component.translatable("hud.flash.thor_level", level).getString()
                : Component.translatable("hud.flash.thor_civil").getString();
        String line2 = form
                ? Component.translatable(infinite ? "hud.flash.thor_energy_infinite"
                        : storm > 0 ? "hud.flash.thor_energy_storm" : "hud.flash.thor_energy", Math.round(energy)).getString()
                : Component.translatable("hud.flash.thor_hint", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage()).getString();
        String line3 = form ? Component.translatable("hud.flash.thor_charge", Math.round(charge)).getString()
                : level >= com.flash.thor.ThorPowers.MAX_LEVEL ? Component.translatable("hud.flash.superman_max").getString()
                : Component.translatable("hud.flash.superman_next", level + 1,
                        Math.round(com.flash.thor.ThorState.xp())).getString();
        int textWidth = Math.max(font.width(line1), Math.max(font.width(line2), font.width(line3)));
        int x = 6;
        int y = 6;
        int boxWidth = Math.max(120, textWidth + 14);
        int boxHeight = 40;
        g.fill(x, y, x + boxWidth, y + boxHeight, FlashHudStyle.PANEL);
        FlashHudStyle.barVertical(g, x, y, 3, boxHeight);
        FlashHudStyle.gradientText(g, font, line1, x + 8, y + 4);
        g.drawString(font, line2, x + 8, y + 15, storm > 0 && form ? 0xFF8FD8FF : FlashHudStyle.TEXT_MUTED, true);
        g.drawString(font, line3, x + 8, y + 26, charge > 100.0F ? 0xFFB98BFF : 0xFF6FC3FF, true);
        float f = infinite ? 1.0F : Math.max(0.0F, Math.min(1.0F, energy / com.flash.thor.ThorPowers.MAX_ENERGY));
        int filled = Math.round((boxWidth - 3) * f);
        g.fill(x + 3, y + boxHeight - 3, x + boxWidth, y + boxHeight, 0xFF101010);
        if (filled > 0) {
            g.fill(x + 3, y + boxHeight - 3, x + 3 + filled, y + boxHeight, 0xFF6FC3FF);
        }
    }

    private static void drawSuperman(GuiGraphics g, Minecraft mc) {
        var font = mc.font;
        boolean form = com.flash.superman.SupermanState.isSuperman(mc.player);
        int level = Math.max(1, com.flash.superman.SupermanState.level());
        float energy = com.flash.superman.SupermanState.energy();
        boolean infinite = com.flash.superman.SupermanState.infiniteEnergy();
        boolean sun = com.flash.superman.SupermanState.inSun();
        String line1 = form ? Component.translatable("hud.flash.superman_level", level).getString()
                : Component.translatable("hud.flash.clark").getString();
        String line2 = form
                ? Component.translatable(infinite ? "hud.flash.superman_energy_infinite"
                        : sun ? "hud.flash.superman_energy_sun" : "hud.flash.superman_energy",
                        Math.round(energy)).getString()
                : Component.translatable("hud.flash.clark_hint", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage()).getString();
        String line3 = level >= com.flash.superman.SupermanPowers.MAX_LEVEL
                ? Component.translatable("hud.flash.superman_max").getString()
                : Component.translatable("hud.flash.superman_next", level + 1,
                        Math.round(com.flash.superman.SupermanState.xp())).getString();
        int textWidth = Math.max(font.width(line1), Math.max(font.width(line2), font.width(line3)));
        int x = 6;
        int y = 6;
        int boxWidth = Math.max(120, textWidth + 14);
        int boxHeight = 40;
        g.fill(x, y, x + boxWidth, y + boxHeight, FlashHudStyle.PANEL);
        FlashHudStyle.barVertical(g, x, y, 3, boxHeight);
        FlashHudStyle.gradientText(g, font, line1, x + 8, y + 4);
        g.drawString(font, line2, x + 8, y + 15, sun && form ? 0xFFFFD84A : FlashHudStyle.TEXT_MUTED, true);
        g.drawString(font, line3, x + 8, y + 26, FlashHudStyle.TEXT_MUTED, true);
        // solar energy along the bottom edge (blinks red when almost empty)
        float f = infinite ? 1.0F : Math.max(0.0F, Math.min(1.0F, energy / com.flash.superman.SupermanPowers.MAX_ENERGY));
        int filled = Math.round((boxWidth - 3) * f);
        g.fill(x + 3, y + boxHeight - 3, x + boxWidth, y + boxHeight, 0xFF101010);
        if (filled > 0) {
            boolean low = f < 0.15F && (mc.player.tickCount / 5) % 2 == 0;
            if (low) {
                g.fill(x + 3, y + boxHeight - 3, x + 3 + filled, y + boxHeight, 0xFFFF3030);
            } else {
                for (int i = 0; i < filled; i++) {
                    float t = i / (float) Math.max(1, boxWidth - 4);
                    int r = (int) (0xFF * (0.85F + 0.15F * t));
                    int gg = (int) (0xB0 + 0x30 * t);
                    g.fill(x + 3 + i, y + boxHeight - 3, x + 4 + i, y + boxHeight, 0xFF000000 | (r << 16) | (gg << 8) | 0x20);
                }
            }
        }
        int extraY = y + boxHeight + 4;
        int temp = com.flash.superman.SupermanState.temporaryTicks(mc.level.getGameTime());
        if (temp > 0) {
            String t = Component.translatable("hud.flash.superman_temporary", time(temp)).getString();
            int tw = font.width(t) + 14;
            g.fill(x, extraY, x + tw, extraY + 14, FlashHudStyle.PANEL);
            FlashHudStyle.barVertical(g, x, extraY, 3, 14);
            g.drawString(font, t, x + 8, extraY + 3, temp < 1200 && (mc.player.tickCount / 10) % 2 == 0
                    ? 0xFFFF5050 : 0xFF7FD6FF, true);
            extraY += 18;
        }
        // ultimate charging: the solar countdown
        if (com.flash.superman.SupermanState.flag(mc.player, com.flash.superman.SupermanPowers.F_ULTIMATE)) {
            String t = Component.translatable("hud.flash.superman_ultimate").getString();
            int ty = extraY;
            int tw = font.width(t) + 14;
            g.fill(x, ty, x + tw, ty + 14, FlashHudStyle.PANEL);
            FlashHudStyle.barVertical(g, x, ty, 3, 14);
            FlashHudStyle.gradientText(g, font, t, x + 8, ty + 3);
        }
    }

    private static String time(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}

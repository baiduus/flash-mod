package com.flash.client;

import com.flash.ability.FlashAbility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Bottom-right ability list: one row per unlocked ability with a gradient
 * (orange to yellow) bar on the left, the name and the key in a small box on
 * the right. A row darkens and shows the remaining seconds while cooling down
 * and lights up while the ability is active. The first row is the speed
 * control. Cooldowns come from the server snapshot.
 */
public final class FlashAbilityHud implements IGuiOverlay {

    private static final int ROW_HEIGHT = 14;
    private static final int ROW_GAP = 2;
    private static final int MIN_WIDTH = 118;
    private static final int MARGIN = 6;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && PowerTheme.localHulk()) {
            if (!minecraft.options.hideGui && !minecraft.player.isSpectator() && minecraft.screen == null
                    && !minecraft.options.renderDebug
                    && com.flash.config.FlashClientConfig.SHOW_ABILITY_HUD.get()) {
                renderHulk(graphics, minecraft, width, height);
            }
            return;
        }
        if (minecraft.player == null || minecraft.options.hideGui
                || minecraft.player.isSpectator() || minecraft.screen != null
                || minecraft.options.renderDebug
                || !FlashClientData.hasPowers()
                || !com.flash.config.FlashClientConfig.SHOW_ABILITY_HUD.get()) {
            return;
        }
        int maxLevel = FlashClientData.getMaxLevel();
        if (maxLevel < 1) {
            return;
        }

        Font font = minecraft.font;
        int rows = 1;
        int panelWidth = MIN_WIDTH;
        panelWidth = Math.max(panelWidth, rowWidth(font,
                Component.translatable("hud.flash.speed").getString(), keyLabel(FlashKeys.INCREASE_SPEED)));
        for (FlashAbility ability : FlashAbility.values()) {
            if (ability.requiredLevel() <= maxLevel) {
                rows++;
                panelWidth = Math.max(panelWidth, rowWidth(font,
                        ability.displayName().getString(), keyLabel(FlashAbilityKeys.mapping(ability))));
            }
        }

        int x = width - panelWidth - MARGIN;
        int y = height - 26 - rows * (ROW_HEIGHT + ROW_GAP) + ROW_GAP;
        if (x < 4 || y < 4) {
            return;
        }

        drawRow(graphics, font, x, y, panelWidth,
                Component.translatable("hud.flash.speed").getString(),
                keyLabel(FlashKeys.INCREASE_SPEED), 0, 1, false);
        y += ROW_HEIGHT + ROW_GAP;

        for (FlashAbility ability : FlashAbility.values()) {
            if (ability.requiredLevel() > maxLevel) {
                continue;
            }
            int cooldown = FlashClientData.getAbilityCooldown(ability.ordinal());
            boolean active = FlashClientData.isAbilityActive(ability.ordinal());
            drawRow(graphics, font, x, y, panelWidth, ability.displayName().getString(),
                    keyLabel(FlashAbilityKeys.mapping(ability)),
                    cooldown, Math.max(1, ability.cooldownTicks()), active);
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    /** Same panel for the Hulk: the transform key and the unlocked Hulk abilities. */
    private static void renderHulk(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        Font font = minecraft.font;
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        int level = Math.max(1, com.flash.hulk.HulkState.level());
        boolean form = com.flash.hulk.HulkState.isTransformed(minecraft.player);
        String transform = Component.translatable("hud.flash.hulk_transform").getString();
        int rows = 1;
        int panelWidth = Math.max(MIN_WIDTH, rowWidth(font, transform, keyLabel(FlashKeys.HULK_TRANSFORM)));
        if (form) {
            for (com.flash.hulk.HulkAbility a : com.flash.hulk.HulkAbility.values()) {
                if (a.level() <= level) {
                    rows++;
                    panelWidth = Math.max(panelWidth, rowWidth(font, a.displayName().getString(),
                            keyLabel(FlashAbilityKeys.mapping(a.keySlot()))));
                }
            }
        }
        int x = width - panelWidth - MARGIN;
        int y = height - 26 - rows * (ROW_HEIGHT + ROW_GAP) + ROW_GAP;
        if (x < 4 || y < 4) {
            return;
        }
        drawRow(graphics, font, x, y, panelWidth, transform, keyLabel(FlashKeys.HULK_TRANSFORM), 0, 1, form);
        y += ROW_HEIGHT + ROW_GAP;
        if (!form) {
            return;
        }
        boolean furious = com.flash.hulk.HulkState.furyTicks(now) > 0;
        for (com.flash.hulk.HulkAbility a : com.flash.hulk.HulkAbility.values()) {
            if (a.level() > level) {
                continue;
            }
            int cd = com.flash.hulk.HulkState.cooldown(a, now);
            boolean active = a == com.flash.hulk.HulkAbility.FURY && furious;
            drawRow(graphics, font, x, y, panelWidth, a.displayName().getString(),
                    keyLabel(FlashAbilityKeys.mapping(a.keySlot())), cd, Math.max(1, a.cooldown()), active);
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static int rowWidth(Font font, String name, String key) {
        return 8 + font.width(name) + 10 + keyBoxWidth(font, key) + 4;
    }

    private static int keyBoxWidth(Font font, String key) {
        return Math.max(14, font.width(key) + 8);
    }

    private static String keyLabel(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) {
            return "-";
        }
        String label = mapping.getTranslatedKeyMessage().getString();
        return label.length() > 6 ? label.substring(0, 6) : label.toUpperCase();
    }

    private static void drawRow(GuiGraphics g, Font font, int x, int y, int w, String name,
                                String key, int cooldown, int cooldownTotal, boolean active) {
        boolean cooling = cooldown > 0;
        g.fill(x, y, x + w, y + ROW_HEIGHT, FlashHudStyle.PANEL);

        // Gradient bar on the left edge (dimmed while cooling down).
        FlashHudStyle.barVertical(g, x, y, 3, ROW_HEIGHT);
        if (cooling) {
            g.fill(x, y, x + 3, y + ROW_HEIGHT, 0xA0000000);
        }

        // Cooldown fill: dark block that shrinks as time passes.
        if (cooling) {
            float remaining = Math.min(1.0F, cooldown / (float) cooldownTotal);
            int fill = Math.max(1, Math.round((w - 3) * remaining));
            g.fill(x + 3, y, x + 3 + fill, y + ROW_HEIGHT, PowerTheme.localHulk() ? 0x90201230
                    : FlashClientVisualLevels.isLocalNegative() ? 0x90300808 : 0x90301000);
        }
        if (active) {
            FlashHudStyle.barHorizontal(g, x + 3, y + ROW_HEIGHT - 2, w - 3, 2);
        }

        int textY = y + (ROW_HEIGHT - 8) / 2;
        if (active) {
            FlashHudStyle.gradientText(g, font, name, x + 8, textY);
        } else {
            g.drawString(font, name, x + 8, textY,
                    cooling ? 0xFF8C8279 : FlashHudStyle.WHITE, true);
        }

        // Key box.
        String label = cooling ? ((cooldown + 19) / 20) + "s" : key;
        int boxW = keyBoxWidth(font, key);
        int boxX = x + w - boxW - 3;
        int boxY = y + 2;
        int boxH = ROW_HEIGHT - 4;
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF15110D);
        FlashHudStyle.barHorizontal(g, boxX, boxY, boxW, 1);
        FlashHudStyle.barHorizontal(g, boxX, boxY + boxH - 1, boxW, 1);
        g.fill(boxX, boxY, boxX + 1, boxY + boxH, FlashHudStyle.from());
        g.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, FlashHudStyle.to());
        int labelW = font.width(label);
        g.drawString(font, label, boxX + (boxW - labelW) / 2, boxY + (boxH - 8) / 2 + 1,
                cooling ? FlashHudStyle.to() : FlashHudStyle.WHITE, false);
    }
}

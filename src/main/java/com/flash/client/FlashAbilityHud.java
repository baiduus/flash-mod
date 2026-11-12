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
        if (minecraft.player != null && PowerTheme.localLex()) {
            if (!minecraft.options.hideGui && !minecraft.player.isSpectator() && minecraft.screen == null
                    && !minecraft.options.renderDebug && com.flash.config.FlashClientConfig.SHOW_ABILITY_HUD.get()) {
                renderLex(graphics, minecraft, width, height);
            }
            return;
        }
        if (minecraft.player != null && PowerTheme.localThor()) {
            if (!minecraft.options.hideGui && !minecraft.player.isSpectator() && minecraft.screen == null
                    && !minecraft.options.renderDebug && com.flash.config.FlashClientConfig.SHOW_ABILITY_HUD.get()) {
                renderThor(graphics, minecraft, width, height);
            }
            return;
        }
        if (minecraft.player != null && PowerTheme.localSuperman()) {
            if (!minecraft.options.hideGui && !minecraft.player.isSpectator() && minecraft.screen == null
                    && !minecraft.options.renderDebug) {
                if (com.flash.config.FlashClientConfig.SHOW_ABILITY_HUD.get()) {
                    renderSuperman(graphics, minecraft, width, height);
                }
                float charge = SupermanClient.punchCharge();
                float toss = SupermanClient.throwCharge();
                if (charge >= 0.0F) {
                    drawChargeBar(graphics, minecraft.font, width, height, charge);
                } else if (toss >= 0.0F) {
                    drawChargeBar(graphics, minecraft.font, width, height, toss, "hud.flash.superman_throw",
                            "hud.flash.superman_throw_full");
                }
            }
            return;
        }
        if (minecraft.player != null && PowerTheme.localHulk()) {
            if (!minecraft.options.hideGui && !minecraft.player.isSpectator() && minecraft.screen == null
                    && !minecraft.options.renderDebug
                    && com.flash.config.FlashClientConfig.SHOW_ABILITY_HUD.get()) {
                renderHulk(graphics, minecraft, width, height);
                if (HulkClient.charging()) {
                    drawThrowBar(graphics, minecraft.font, width, height);
                }
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

    /** Force bar of the rock throw, centred above the hotbar. */
    private static void drawThrowBar(GuiGraphics g, Font font, int width, int height) {
        int w = 120;
        int x = (width - w) / 2;
        int y = height - 62;
        float f = HulkClient.chargeFraction();
        g.fill(x - 2, y - 2, x + w + 2, y + 7, FlashHudStyle.PANEL);
        int filled = Math.round(w * f);
        if (filled > 0) {
            FlashHudStyle.barHorizontal(g, x, y, filled, 5);
        }
        String label = Component.translatable("hud.flash.hulk_throw", Math.round(f * 100.0F)).getString();
        FlashHudStyle.gradientText(g, font, label, (width - font.width(label)) / 2, y - 12);
    }

    /** Charged punch: green, then yellow, then red when full. */
    private static void drawChargeBar(GuiGraphics g, Font font, int width, int height, float f) {
        drawChargeBar(g, font, width, height, f, "hud.flash.superman_punch", "hud.flash.superman_punch_full");
    }

    private static void drawChargeBar(GuiGraphics g, Font font, int width, int height, float f, String key,
                                      String fullKey) {
        int w = 120;
        int x = (width - w) / 2;
        int y = height - 62;
        g.fill(x - 2, y - 2, x + w + 2, y + 7, FlashHudStyle.PANEL);
        int color = f < 0.5F ? 0xFF3DDC84 : f < 0.95F ? 0xFFFFD21F : 0xFFFF3A3A;
        int filled = Math.round(w * f);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + 5, color);
        }
        String label = Component.translatable(f >= 0.999F ? fullKey : key, Math.round(f * 100.0F)).getString();
        g.drawString(font, label, (width - font.width(label)) / 2, y - 12, color, true);
    }

    /** Superman panel: the suit key and the unlocked abilities (lit while running). */
    private static void renderSuperman(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        Font font = minecraft.font;
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        int level = Math.max(1, com.flash.superman.SupermanState.level());
        boolean form = com.flash.superman.SupermanState.isSuperman(minecraft.player);
        String transform = Component.translatable("hud.flash.superman_transform").getString();
        int rows = 1;
        int panelWidth = Math.max(MIN_WIDTH, rowWidth(font, transform, keyLabel(FlashKeys.TOGGLE_SUIT)));
        if (form) {
            for (com.flash.superman.SupermanAbility a : com.flash.superman.SupermanAbility.values()) {
                if (a.level() <= level) {
                    rows++;
                    panelWidth = Math.max(panelWidth, rowWidth(font, a.displayName().getString(),
                            keyLabel(SupermanClient.key(a))));
                }
            }
        }
        int x = width - panelWidth - MARGIN;
        int y = height - 26 - rows * (ROW_HEIGHT + ROW_GAP) + ROW_GAP;
        if (x < 4 || y < 4) {
            return;
        }
        drawRow(graphics, font, x, y, panelWidth, transform, keyLabel(FlashKeys.TOGGLE_SUIT), 0, 1, form);
        y += ROW_HEIGHT + ROW_GAP;
        if (!form) {
            return;
        }
        for (com.flash.superman.SupermanAbility a : com.flash.superman.SupermanAbility.values()) {
            if (a.level() > level) {
                continue;
            }
            int cd = com.flash.superman.SupermanState.cooldown(a, now);
            drawRow(graphics, font, x, y, panelWidth, a.displayName().getString(),
                    keyLabel(SupermanClient.key(a)), cd, Math.max(1, a.cooldown()), SupermanClient.active(a));
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    /** Thor panel: the transform key and the unlocked abilities (lit while running). */
    /** Lex Luthor's suit: its systems, keys and cooldowns. */
    private static void renderLex(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        Font font = minecraft.font;
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        com.flash.lex.LexAbility[] all = com.flash.lex.LexAbility.values();
        int panelWidth = MIN_WIDTH;
        for (com.flash.lex.LexAbility a : all) {
            panelWidth = Math.max(panelWidth, rowWidth(font, a.displayName().getString(), keyLabel(LexClient.key(a))));
        }
        int x = width - panelWidth - MARGIN;
        int y = height - 26 - all.length * (ROW_HEIGHT + ROW_GAP) + ROW_GAP;
        if (x < 4 || y < 4) {
            return;
        }
        for (com.flash.lex.LexAbility a : all) {
            int cd = com.flash.lex.LexState.cooldown(a, now);
            boolean active = switch (a) {
                case REPULSOR_BEAM -> com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_BEAM);
                case UNIBEAM -> com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_UNIBEAM)
                        || com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_UNI_CHARGING);
                case OVERDRIVE -> com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_OVERDRIVE);
                case FACEPLATE -> com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_OPEN);
                case POWER_DIVE -> com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_DIVE);
                case LASERS -> com.flash.lex.LexState.flag(minecraft.player, com.flash.lex.LexSuit.F_LASER);
                default -> false;
            };
            drawRow(graphics, font, x, y, panelWidth, a.displayName().getString(), keyLabel(LexClient.key(a)), cd,
                    Math.max(1, a.cooldown()), active);
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static void renderThor(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        Font font = minecraft.font;
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        int level = Math.max(1, com.flash.thor.ThorState.level());
        boolean form = com.flash.thor.ThorState.isThor(minecraft.player);
        String transform = Component.translatable("hud.flash.thor_transform").getString();
        int rows = 1;
        int panelWidth = Math.max(MIN_WIDTH, rowWidth(font, transform, keyLabel(FlashKeys.TOGGLE_SUIT)));
        if (form) {
            for (com.flash.thor.ThorAbility a : com.flash.thor.ThorAbility.values()) {
                if (a.level() <= level) {
                    rows++;
                    panelWidth = Math.max(panelWidth, rowWidth(font, a.displayName().getString(),
                            keyLabel(ThorClient.key(a))));
                }
            }
        }
        int x = width - panelWidth - MARGIN;
        int y = height - 26 - rows * (ROW_HEIGHT + ROW_GAP) + ROW_GAP;
        if (x < 4 || y < 4) {
            return;
        }
        drawRow(graphics, font, x, y, panelWidth, transform, keyLabel(FlashKeys.TOGGLE_SUIT), 0, 1, form);
        y += ROW_HEIGHT + ROW_GAP;
        if (!form) {
            return;
        }
        for (com.flash.thor.ThorAbility a : com.flash.thor.ThorAbility.values()) {
            if (a.level() > level) {
                continue;
            }
            int cd = com.flash.thor.ThorState.cooldown(a, now);
            drawRow(graphics, font, x, y, panelWidth, a.displayName(com.flash.thor.ThorState.weapon(minecraft.player)).getString(), keyLabel(ThorClient.key(a)), cd,
                    Math.max(1, a.cooldown()), ThorClient.active(a));
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
        int active = Math.max(1, Math.min(level, com.flash.hulk.HulkState.activeLevel()));
        String levelName = Component.translatable("hud.flash.hulk_active", active, level).getString();
        String levelKey = Component.translatable("hud.flash.hulk_scroll").getString();
        if (form) {
            rows++;
            panelWidth = Math.max(panelWidth, rowWidth(font, levelName, levelKey));
            for (com.flash.hulk.HulkAbility a : com.flash.hulk.HulkAbility.values()) {
                if (a.level() <= level) {
                    rows++;
                    panelWidth = Math.max(panelWidth, rowWidth(font, a.displayName().getString(),
                            keyLabel(HulkClient.key(a))));
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
        drawRow(graphics, font, x, y, panelWidth, levelName, levelKey, 0, 1, false);
        y += ROW_HEIGHT + ROW_GAP;
        boolean furious = com.flash.hulk.HulkState.furyTicks(now) > 0;
        for (com.flash.hulk.HulkAbility a : com.flash.hulk.HulkAbility.values()) {
            if (a.level() > level) {
                continue;
            }
            int cd = com.flash.hulk.HulkState.cooldown(a, now);
            boolean lit = a == com.flash.hulk.HulkAbility.FURY && furious;
            drawRow(graphics, font, x, y, panelWidth, a.displayName().getString(),
                    keyLabel(HulkClient.key(a)), cd, Math.max(1, a.cooldown()), lit);
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
            g.fill(x + 3, y, x + 3 + fill, y + ROW_HEIGHT, PowerTheme.localSuperman() ? 0x90081A40 : PowerTheme.localHulk() ? (PowerTheme.localRedHulk() ? 0x90300808 : 0x90201230)
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

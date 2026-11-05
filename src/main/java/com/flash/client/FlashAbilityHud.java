package com.flash.client;

import com.flash.ability.FlashAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Bottom-right ability panel.
 *
 * <p>Only permanently unlocked abilities are shown. Each slot has a small
 * pixel-art icon, a green indicator when the ability is ready, a gold border
 * while it is active and a dark fill plus the remaining seconds while it is on
 * cooldown. Cooldowns come from the server snapshot, so the panel matches the
 * real cooldown used by {@code FlashAbilityManager}.</p>
 */
public final class FlashAbilityHud implements IGuiOverlay {

    private static final int CELL = 24;
    private static final int GAP = 3;
    private static final int COLUMNS = 2;

    private static final int COLOR_BORDER = 0xFF2B3038;
    private static final int COLOR_BG = 0xC00A0C10;
    private static final int COLOR_SLOT = 0xFF101418;
    private static final int COLOR_READY = 0xFF35D06A;
    private static final int COLOR_ACTIVE = 0xFFFFD24A;
    private static final int COLOR_COOLING = 0xFF4A525D;
    private static final int COLOR_TEXT = 0xFFF2F5F7;

    /**
     * 9x9 pixel-art icons. Index matches {@link FlashAbility#ordinal()}.
     */
    private static final String[][] ICONS = {
            {   // BARRAGE - fist
                    "..#####..",
                    ".#######.",
                    ".#.#.#.#.",
                    ".#######.",
                    ".#######.",
                    ".#######.",
                    "..#####..",
                    "...###...",
                    "........."
            },
            {   // WIND_HANDS - spiral
                    "..#####..",
                    ".##...##.",
                    "##..#..##",
                    "#..###..#",
                    "#.##.#..#",
                    "#..###..#",
                    "##..#..##",
                    ".##...##.",
                    "..#####.."
            },
            {   // PHASING - ghost / vibrating body
                    "...###...",
                    "..#####..",
                    "..#####..",
                    "...###...",
                    "..#.#.#..",
                    ".#######.",
                    "..#.#.#..",
                    "..#...#..",
                    "..#...#.."
            },
            {   // SPEED_TORNADO - funnel
                    "#########",
                    ".#######.",
                    "..#####..",
                    "...###...",
                    "..#####..",
                    "...###...",
                    "....#....",
                    "...###...",
                    "....#...."
            },
            {   // LIGHTNING_STRIKE - bolt
                    "....##...",
                    "...##....",
                    "..##.....",
                    ".######..",
                    "...##....",
                    "..##.....",
                    ".##......",
                    ".#.......",
                    "........."
            },
            {   // MASS_PUNCH - impact star
                    "....#....",
                    ".#..#..#.",
                    "..#.#.#..",
                    "...###...",
                    "#########",
                    "...###...",
                    "..#.#.#..",
                    ".#..#..#.",
                    "....#...."
            },
            {   // SPEED_MANIPULATION - two linked figures
                    ".##...##.",
                    "#..#.#..#",
                    "#...#...#",
                    "#..#.#..#",
                    ".##...##.",
                    ".........",
                    "..#...#..",
                    "...#.#...",
                    "....#...."
            },
            {   // ACCELERATION_FORCE - portal ring
                    "...###...",
                    ".##...##.",
                    "#...#...#",
                    "#..###..#",
                    "#..#.#..#",
                    "#..###..#",
                    "#...#...#",
                    ".##...##.",
                    "...###..."
            }
    };

    private static final int[] ICON_COLORS = {
            0xFFFFC83A, // barrage
            0xFFFFE58A, // wind
            0xFFB388FF, // phasing
            0xFFFFB03A, // tornado
            0xFFFFF07A, // lightning
            0xFFFF7A3D, // mass punch
            0xFF5BD6FF, // speed manipulation
            0xFFFFD24A  // acceleration force
    };

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
                || minecraft.player.isSpectator() || minecraft.screen != null
                || minecraft.options.renderDebug
                || !FlashClientData.hasPowers()) {
            return;
        }

        int maxLevel = FlashClientData.getMaxLevel();
        if (maxLevel < 1) {
            return;
        }

        int unlocked = 0;
        for (FlashAbility ability : FlashAbility.values()) {
            if (ability.requiredLevel() <= maxLevel) {
                unlocked++;
            }
        }
        if (unlocked == 0) {
            return;
        }

        int rows = (unlocked + COLUMNS - 1) / COLUMNS;
        int panelWidth = COLUMNS * CELL + (COLUMNS - 1) * GAP + 10;
        int panelHeight = rows * CELL + (rows - 1) * GAP + 22;
        int x = width - panelWidth - 6;
        int y = height - 22 - panelHeight - 4;
        if (x < 4) {
            return;
        }
        if (y < 4) {
            y = 4;
        }

        graphics.fill(x, y, x + panelWidth, y + panelHeight, COLOR_BG);
        graphics.fill(x, y, x + panelWidth, y + 1, COLOR_BORDER);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + panelHeight, COLOR_BORDER);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, COLOR_BORDER);
        graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + 2, 0x66FFD24A);

        String header = Component.translatable("hud.flash.abilities").getString();
        graphics.drawString(minecraft.font, header, x + 5, y + 5, COLOR_TEXT, false);

        int index = 0;
        for (FlashAbility ability : FlashAbility.values()) {
            if (ability.requiredLevel() > maxLevel) {
                continue;
            }
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            index++;

            int cellX = x + 5 + column * (CELL + GAP);
            int cellY = y + 15 + row * (CELL + GAP);
            int cooldown = FlashClientData.getAbilityCooldown(ability.ordinal());
            boolean active = FlashClientData.isAbilityActive(ability.ordinal());
            boolean ready = cooldown <= 0 && !active;

            int border = active ? COLOR_ACTIVE : ready ? COLOR_READY : COLOR_COOLING;
            graphics.fill(cellX, cellY, cellX + CELL, cellY + CELL, border);
            graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1,
                    cellY + CELL - 1, COLOR_SLOT);

            int iconColor = ICON_COLORS[ability.ordinal() % ICON_COLORS.length];
            drawIcon(graphics, ability, cellX + 3, cellY + 3, 2, iconColor);

            if (cooldown > 0) {
                int total = Math.max(1, ability.cooldownTicks());
                float remaining = Math.min(1.0F, cooldown / (float) total);
                int overlay = Math.max(1, Math.round((CELL - 2) * remaining));
                graphics.fill(cellX + 1, cellY + CELL - 1 - overlay,
                        cellX + CELL - 1, cellY + CELL - 1, 0xB4000000);

                int seconds = (cooldown + 19) / 20;
                String text = seconds + "s";
                int textWidth = minecraft.font.width(text);
                if (textWidth > CELL - 2) {
                    text = Integer.toString(seconds);
                    textWidth = minecraft.font.width(text);
                }
                graphics.drawString(minecraft.font, text,
                        cellX + (CELL - textWidth) / 2,
                        cellY + (CELL - 8) / 2,
                        COLOR_ACTIVE, true);
            }

            if (ready) {
                graphics.fill(cellX + CELL - 5, cellY + 3,
                        cellX + CELL - 3, cellY + 5, COLOR_READY);
            } else if (active) {
                graphics.fill(cellX + 3, cellY + CELL - 3,
                        cellX + CELL - 3, cellY + CELL - 2, COLOR_ACTIVE);
            }

            // Undimmed icon on the active slot.
            if (active) {
                drawIcon(graphics, ability, cellX + 3, cellY + 3, 2, 0xFFFFFFFF);
            }
        }
    }

    private static void drawIcon(GuiGraphics graphics, FlashAbility ability,
                                 int x, int y, int scale, int color) {
        String[] pattern = ICONS[ability.ordinal() % ICONS.length];
        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '.') {
                    continue;
                }
                graphics.fill(x + column * scale, y + row * scale,
                        x + (column + 1) * scale, y + (row + 1) * scale, color);
            }
        }
    }
}

package com.flash.client;

import com.flash.ability.FlashAbility;
import com.flash.config.FlashClientConfig;
import com.flash.network.FlashNetwork;
import com.flash.network.FlashSelectLevelPacket;
import com.flash.network.FlashSettingsPacket;
import com.flash.power.FlashSpeedLevels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * K screen: a compact, server-authoritative progression panel.
 *
 * <p>All values come from {@link FlashClientData}, which is updated by
 * {@code FlashSyncPacket}. Clicking an unlocked card asks the server to select
 * that level; locked cards are never sent.</p>
 */
public class FlashProgressScreen extends Screen {

    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 360;

    private static final int CARD_COLUMNS = 4;
    private static final int CARD_WIDTH = 77;
    private static final int CARD_HEIGHT = 38;
    private static final int CARD_GAP = 6;
    private static final int GRID_X = 17;
    private static final int GRID_Y = 96;
    private static final int SKILL_X = 350;
    private static final int SKILL_Y = 96;
    private static final int SKILL_ROW = 22;
    private static final int TOGGLE_Y = 300;

    private static final int COLOR_BORDER = 0xFF262B33;
    private static final int COLOR_PANEL = 0xFF0B0D10;
    private static final int COLOR_PANEL_INNER = 0xFF111418;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF8B939E;
    private static final int COLOR_DIM = 0xFF5B626C;
    private static final int COLOR_GREEN = 0xFF35D06A;
    private static final int COLOR_GREEN_DARK = 0xFF1B6B3A;
    private static final int COLOR_ORANGE = 0xFFFF7A3D;
    private static final int COLOR_RED = 0xFFFF5A3C;
    private static final int COLOR_CARD_LOCKED = 0xFF101216;

    private int leftPos;
    private int topPos;
    private float uiScale = 1.0F;

    public FlashProgressScreen() {
        super(Component.translatable("gui.flash.progress.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.uiScale = Math.min(1.0F, Math.min(
                this.width / (float) (PANEL_WIDTH + 10),
                this.height / (float) (PANEL_HEIGHT + 10)));
        if (this.uiScale <= 0.01F) {
            this.uiScale = 1.0F;
        }
        this.leftPos = Math.round((this.width / this.uiScale - PANEL_WIDTH) / 2.0F);
        this.topPos = Math.round((this.height / this.uiScale - PANEL_HEIGHT) / 2.0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.scale(this.uiScale, this.uiScale, 1.0F);

        int x = this.leftPos;
        int y = this.topPos;
        int scaledMouseX = (int) (mouseX / this.uiScale);
        int scaledMouseY = (int) (mouseY / this.uiScale);

        graphics.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + PANEL_HEIGHT + 1, COLOR_BORDER);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, COLOR_PANEL);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + 2, COLOR_GREEN_DARK);

        graphics.drawString(this.font, this.title, x + 16, y + 14, COLOR_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.subtitle"),
                x + 16, y + 30, COLOR_MUTED, false);

        drawBadge(graphics, x, y);
        drawSummaryRows(graphics, x, y);

        graphics.fill(x + 16, y + 86, x + PANEL_WIDTH - 16, y + 87, 0xFF1D222A);
        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.levels"),
                x + 16, y + 88, COLOR_TEXT, false);

        drawLevelGrid(graphics, x, y, scaledMouseX, scaledMouseY);
        drawSkills(graphics, x, y, scaledMouseX, scaledMouseY);
        drawProgress(graphics, x, y);
        drawDestroyToggle(graphics, x, y, scaledMouseX, scaledMouseY);

        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.footer"),
                x + 16, y + PANEL_HEIGHT - 15, COLOR_DIM, false);

        poseStack.popPose();
    }

    private void drawBadge(GuiGraphics graphics, int x, int y) {
        Component badge = FlashClientData.hasPowers()
                ? (FlashClientData.isTemporary()
                    ? Component.translatable("gui.flash.progress.temporary",
                        formatTime(FlashClientData.getTemporaryRemaining()))
                    : Component.translatable("gui.flash.progress.active_badge",
                        FlashClientData.getSelectedLevel()))
                : Component.translatable("gui.flash.progress.no_powers_badge");
        int textWidth = this.font.width(badge);
        int badgeX = x + PANEL_WIDTH - 16 - textWidth - 10;
        int badgeY = y + 11;
        int color = FlashClientData.hasPowers() ? COLOR_GREEN : COLOR_MUTED;

        graphics.fill(badgeX, badgeY, badgeX + textWidth + 10, badgeY + 16, 0xFF15351F);
        graphics.fill(badgeX, badgeY, badgeX + textWidth + 10, badgeY + 1, color);
        graphics.drawString(this.font, badge, badgeX + 5, badgeY + 4, color, false);
    }

    private void drawSummaryRows(GuiGraphics graphics, int x, int y) {
        Component selected = FlashClientData.hasPowers()
                ? Component.translatable("level.flash." + FlashClientData.getSelectedLevel())
                : Component.translatable("gui.flash.progress.none");
        Component max = FlashClientData.hasPowers()
                ? Component.translatable("level.flash." + FlashClientData.getMaxLevel())
                : Component.translatable("gui.flash.progress.none");

        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.selected"),
                x + 16, y + 52, COLOR_MUTED, false);
        graphics.drawString(this.font, selected,
                x + PANEL_WIDTH - 16 - this.font.width(selected), y + 52, COLOR_TEXT, false);

        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.max"),
                x + 16, y + 68, COLOR_MUTED, false);
        graphics.drawString(this.font, max,
                x + PANEL_WIDTH - 16 - this.font.width(max), y + 68, COLOR_TEXT, false);
    }

    private void drawLevelGrid(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int max = FlashClientData.getMaxLevel();
        int selected = FlashClientData.getSelectedLevel();
        boolean hasPowers = FlashClientData.hasPowers();

        for (int level = FlashSpeedLevels.MIN_LEVEL; level <= FlashSpeedLevels.MAX_LEVEL; level++) {
            int column = level % CARD_COLUMNS;
            int row = level / CARD_COLUMNS;
            int cardX = x + GRID_X + column * (CARD_WIDTH + CARD_GAP);
            int cardY = y + GRID_Y + row * (CARD_HEIGHT + CARD_GAP);

            boolean unlocked = hasPowers && level <= max;
            boolean inUse = hasPowers && level == selected;
            boolean hovered = mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;

            int border = inUse ? COLOR_ORANGE : unlocked ? COLOR_GREEN : 0xFF2B3038;
            if (hovered && unlocked && !inUse) {
                border = 0xFF9BE8B4;
            }
            int fill = inUse ? 0xFF1C150C : unlocked ? 0xFF101A13 : COLOR_CARD_LOCKED;
            if (hovered && unlocked && !inUse) {
                fill = 0xFF17231B;
            }

            graphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, border);
            graphics.fill(cardX + 1, cardY + 1, cardX + CARD_WIDTH - 1, cardY + CARD_HEIGHT - 1, fill);

            if (unlocked) {
                drawCheck(graphics, cardX + 9, cardY + 9, inUse ? COLOR_ORANGE : COLOR_GREEN);
            } else {
                drawLock(graphics, cardX + 9, cardY + 9, COLOR_DIM);
            }

            graphics.drawString(this.font,
                    Component.translatable("level.flash." + level),
                    cardX + 23, cardY + 8, unlocked ? COLOR_TEXT : COLOR_DIM, false);

            Component status = inUse
                    ? Component.translatable("gui.flash.progress.in_use")
                    : unlocked
                    ? Component.translatable("gui.flash.progress.unlocked")
                    : Component.translatable("gui.flash.progress.locked");
            int statusColor = inUse ? COLOR_ORANGE : unlocked ? COLOR_GREEN : COLOR_DIM;
            graphics.drawString(this.font, status, cardX + 23, cardY + 22, statusColor, false);
        }
    }

    private void drawSkills(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int sx = x + SKILL_X;
        int sy = y + SKILL_Y;
        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.skills"),
                sx, sy - 8, COLOR_TEXT, false);

        FlashAbility[] abilities = FlashAbility.values();
        for (int i = 0; i < abilities.length; i++) {
            FlashAbility ability = abilities[i];
            int rowY = sy + 10 + i * SKILL_ROW;
            boolean unlocked = FlashClientData.hasPowers()
                    && FlashClientData.getMaxLevel() >= ability.requiredLevel();
            int color = unlocked ? COLOR_GREEN : COLOR_DIM;
            if (unlocked) {
                drawCheck(graphics, sx, rowY, color);
            } else {
                drawLock(graphics, sx, rowY, color);
            }

            String name = ability.displayName().getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(name, 132),
                    sx + 14, rowY, unlocked ? COLOR_TEXT : COLOR_DIM, false);

            Component requirement = unlocked
                    ? Component.translatable("gui.flash.progress.unlocked")
                    : Component.translatable("gui.flash.progress.skills_locked",
                        ability.requiredLevel());
            graphics.drawString(this.font, requirement,
                    sx + 14, rowY + 10, color, false);

            int cooldown = FlashClientData.getAbilityCooldown(ability.ordinal());
            if (cooldown > 0) {
                Component cd = Component.literal(formatTime(cooldown));
                graphics.drawString(this.font, cd,
                        sx + 186 - this.font.width(cd), rowY, COLOR_ORANGE, false);
            }

            if (mouseX >= sx && mouseX < x + PANEL_WIDTH - 16
                    && mouseY >= rowY - 1 && mouseY < rowY + 18) {
                graphics.renderTooltip(this.font, ability.description(), mouseX, mouseY);
            }
        }

        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.no_new_skills"),
                sx, y + 286, COLOR_DIM, false);
    }

    private void drawDestroyToggle(GuiGraphics graphics, int x, int y,
                                   int mouseX, int mouseY) {
        int tx = x + 16;
        int ty = y + TOGGLE_Y;
        int tw = 300;
        int th = 20;
        boolean hovered = mouseX >= tx && mouseX < tx + tw
                && mouseY >= ty && mouseY < ty + th;
        boolean enabled = FlashClientData.isDestroyBlocks();
        int border = hovered ? COLOR_ORANGE : 0xFF2B3038;
        graphics.fill(tx, ty, tx + tw, ty + th, border);
        graphics.fill(tx + 1, ty + 1, tx + tw - 1, ty + th - 1,
                enabled ? 0xFF1C150C : 0xFF101216);
        graphics.drawString(this.font,
                Component.translatable("gui.flash.progress.destroy_blocks"),
                tx + 7, ty + 6, COLOR_TEXT, false);
        Component value = Component.translatable(enabled
                ? "gui.flash.progress.destroy_on"
                : "gui.flash.progress.destroy_off");
        graphics.drawString(this.font, value,
                tx + tw - 7 - this.font.width(value), ty + 6,
                enabled ? COLOR_ORANGE : COLOR_MUTED, false);
    }

    private boolean isToggleAt(double mouseX, double mouseY) {
        int tx = this.leftPos + 16;
        int ty = this.topPos + TOGGLE_Y;
        return mouseX >= tx && mouseX < tx + 300
                && mouseY >= ty && mouseY < ty + 20;
    }

    private void drawProgress(GuiGraphics graphics, int x, int y) {
        boolean hasPowers = FlashClientData.hasPowers();
        boolean maxed = hasPowers && FlashClientData.getMaxLevel() >= FlashSpeedLevels.MAX_LEVEL;

        if (!hasPowers) {
            graphics.drawString(this.font,
                    Component.translatable("gui.flash.progress.no_powers"),
                    x + 16, y + 242, COLOR_MUTED, false);
            drawBar(graphics, x, y, 0.0F, COLOR_DIM);
            graphics.drawString(this.font,
                    Component.translatable("gui.flash.progress.no_powers_hint"),
                    x + 16, y + 266, COLOR_DIM, false);
            return;
        }

        if (maxed) {
            graphics.drawString(this.font,
                    Component.translatable("gui.flash.progress.maxed"),
                    x + 16, y + 242, COLOR_GREEN, false);
            drawBar(graphics, x, y, 1.0F, COLOR_GREEN);
            graphics.drawString(this.font,
                    Component.translatable("gui.flash.progress.all_unlocked"),
                    x + 16, y + 266, COLOR_MUTED, false);
            return;
        }

        int next = FlashClientData.getMaxLevel() + 1;
        Component title = Component.translatable("gui.flash.progress.next", next);
        graphics.drawString(this.font, title, x + 16, y + 242, COLOR_TEXT, false);

        float fraction = (float) Math.min(1.0D,
                FlashClientData.getDistance() / (double) FlashSpeedLevels.BLOCKS_PER_LEVEL);
        int percent = Math.round(fraction * 100.0F);
        Component percentText = Component.literal(percent + "%");
        graphics.drawString(this.font, percentText,
                x + PANEL_WIDTH - 16 - this.font.width(percentText), y + 242, COLOR_GREEN, false);

        drawBar(graphics, x, y, fraction, COLOR_RED);

        int travelled = Math.min(FlashSpeedLevels.BLOCKS_PER_LEVEL,
                (int) Math.floor(FlashClientData.getDistance()));
        int remaining = Math.max(0, FlashSpeedLevels.BLOCKS_PER_LEVEL - travelled);
        Component counter = Component.translatable("gui.flash.progress.blocks",
                number(travelled), number(FlashSpeedLevels.BLOCKS_PER_LEVEL));
        Component remainingText = Component.translatable("gui.flash.progress.remaining", number(remaining));
        graphics.drawString(this.font, counter, x + 16, y + 266, COLOR_MUTED, false);
        graphics.drawString(this.font, remainingText,
                x + PANEL_WIDTH - 16 - this.font.width(remainingText), y + 266, COLOR_TEXT, false);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, float fraction, int color) {
        int barX = x + 16;
        int barY = y + 254;
        int barWidth = PANEL_WIDTH - 32;
        int barHeight = 7;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF232831);
        int filled = (int) ((barWidth - 2) * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 0) {
            graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + barHeight - 1, color);
        }
    }

    private static void drawCheck(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 1, y + 4, x + 3, y + 6, color);
        graphics.fill(x + 2, y + 5, x + 4, y + 7, color);
        graphics.fill(x + 3, y + 4, x + 5, y + 6, color);
        graphics.fill(x + 4, y + 2, x + 6, y + 5, color);
        graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
    }

    private static void drawLock(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 2, y + 4, x + 7, y + 8, color);
        graphics.fill(x + 2, y + 1, x + 3, y + 4, color);
        graphics.fill(x + 6, y + 1, x + 7, y + 4, color);
        graphics.fill(x + 2, y + 1, x + 7, y + 2, color);
        graphics.fill(x + 4, y + 5, x + 5, y + 7, 0xFF101216);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double sx = mouseX / this.uiScale;
            double sy = mouseY / this.uiScale;
            if (isToggleAt(sx, sy)) {
                boolean enabled = !FlashClientData.isDestroyBlocks();
                FlashClientData.setDestroyBlocksOptimistic(enabled);
                FlashNetwork.CHANNEL.sendToServer(new FlashSettingsPacket(enabled));
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int level = levelAt(sx, sy);
            if (level >= 0
                    && FlashClientData.hasPowers()
                    && !FlashClientData.isTemporary()
                    && level <= FlashClientData.getMaxLevel()) {
                FlashClientData.setSelectedLevelOptimistic(level);
                FlashNetwork.CHANNEL.sendToServer(new FlashSelectLevelPacket(level));
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int levelAt(double mouseX, double mouseY) {
        int gridX = (int) mouseX - (this.leftPos + GRID_X);
        int gridY = (int) mouseY - (this.topPos + GRID_Y);
        if (gridX < 0 || gridY < 0) {
            return -1;
        }
        int column = gridX / (CARD_WIDTH + CARD_GAP);
        int row = gridY / (CARD_HEIGHT + CARD_GAP);
        if (column >= CARD_COLUMNS || row > 2) {
            return -1;
        }
        if (gridX % (CARD_WIDTH + CARD_GAP) >= CARD_WIDTH
                || gridY % (CARD_HEIGHT + CARD_GAP) >= CARD_HEIGHT) {
            return -1;
        }
        int level = row * CARD_COLUMNS + column;
        return level <= FlashSpeedLevels.MAX_LEVEL ? level : -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (FlashClientData.hasPowers()) {
            if (FlashKeys.INCREASE_SPEED.matches(keyCode, scanCode)) {
                int direction = Screen.hasControlDown() ? -1 : 1;
                FlashClientForgeEvents.changeLevel(Minecraft.getInstance(), direction, true);
                return true;
            }
            if (FlashKeys.DECREASE_SPEED.matches(keyCode, scanCode)) {
                FlashClientForgeEvents.changeLevel(Minecraft.getInstance(), -1, true);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (FlashClientConfig.MOUSE_WHEEL_CONTROLS.get()
                && minecraft.player != null
                && minecraft.player.isShiftKeyDown()
                && FlashClientData.hasPowers()
                && delta != 0.0D) {
            FlashClientForgeEvents.changeLevel(minecraft, delta > 0.0D ? 1 : -1, false);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d",
                seconds / 60, seconds % 60);
    }

    private static String number(int value) {
        String text = Integer.toString(Math.max(0, value));
        StringBuilder result = new StringBuilder();
        int digits = 0;
        for (int i = text.length() - 1; i >= 0; i--) {
            result.append(text.charAt(i));
            digits++;
            if (digits % 3 == 0 && i > 0) {
                result.append('.');
            }
        }
        return result.reverse().toString();
    }
}

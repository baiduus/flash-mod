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
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

/**
 * K screen with two tabs: "Progresso" (levels, abilities, progress to the next
 * level) and "Config" (block destruction, speed control mode and visual
 * options). The accent colour is gold for the positive force and red for the
 * negative one. Server-authoritative values come from {@link FlashClientData}.
 */
public class FlashProgressScreen extends Screen {

    private static final int PW = 640;
    private static final int PH = 400;

    private static final int PAD = 20;
    private static final int TAB_Y = 66;
    private static final int TAB_W = 124;
    private static final int TAB_H = 24;
    private static final int CONTENT_Y = 104;

    private static final int CARD_COLUMNS = 4;
    private static final int CARD_W = 90;
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 6;
    private static final int GRID_X = PAD;
    private static final int GRID_Y = 176;

    private static final int SKILL_X = 420;
    private static final int SKILL_ROW = 25;
    private static final int SKILL_W = 200;

    private static final int CFG_ROW_H = 44;
    private static final int CFG_ROW_GAP = 6;

    private static final int COLOR_BG_TOP = 0xFF15181E;
    private static final int COLOR_BG_BOTTOM = 0xFF0A0B0E;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A1AD;
    private static final int COLOR_DIM = 0xFF5B626C;
    private static final int COLOR_GREEN = 0xFF35D06A;
    private static final int COLOR_ORANGE = 0xFFFF7A3D;
    private static final int COLOR_BOX = 0xFF12151A;
    private static final int COLOR_BOX_BORDER = 0xFF2A2F38;

    private static int tab = 0;

    private int leftPos;
    private int topPos;
    private float uiScale = 1.0F;

    public FlashProgressScreen() {
        super(Component.translatable("gui.flash.progress.title"));
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        this.uiScale = Math.min(1.0F, Math.min(
                this.width / (float) (PW + 10),
                this.height / (float) (PH + 10)));
        if (this.uiScale <= 0.01F) {
            this.uiScale = 1.0F;
        }
        this.leftPos = Math.round((this.width / this.uiScale - PW) / 2.0F);
        this.topPos = Math.round((this.height / this.uiScale - PH) / 2.0F);
    }

    private static boolean negative() {
        return FlashClientVisualLevels.isLocalNegative();
    }

    private static int accent() {
        return negative() ? 0xFFFF4040 : 0xFFFFC21A;
    }

    private static int accentDark() {
        return negative() ? 0xFF3C0C0E : 0xFF3F3207;
    }

    private static int accentSoft() {
        return negative() ? 0xFF1E0A0B : 0xFF1F1A08;
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.scale(this.uiScale, this.uiScale, 1.0F);

        int x = this.leftPos;
        int y = this.topPos;
        int mx = (int) (mouseX / this.uiScale) - x;
        int my = (int) (mouseY / this.uiScale) - y;

        poseStack.translate(x, y, 0.0F);

        // frame, glow and body
        graphics.fill(-3, -3, PW + 3, PH + 3, accentDark());
        graphics.fill(-1, -1, PW + 1, PH + 1, accent());
        graphics.fillGradient(0, 0, PW, PH, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        drawHeader(graphics);
        drawTabs(graphics, mx, my);
        graphics.fill(PAD, TAB_Y + TAB_H + 4, PW - PAD, TAB_Y + TAB_H + 5, COLOR_BOX_BORDER);

        if (tab == 0) {
            drawProgressTab(graphics, mx, my);
        } else {
            drawConfigTab(graphics, mx, my);
        }

        graphics.drawString(this.font, footerText(), PAD, PH - 16, COLOR_DIM, false);

        poseStack.popPose();
    }

    private Component footerText() {
        return Component.translatable(FlashClientConfig.SCROLL_MODE.get()
                ? "gui.flash.footer.scroll" : "gui.flash.footer.keys");
    }

    private void drawHeader(GuiGraphics g) {
        g.fillGradient(0, 0, PW, 58, accentDark(), 0x00000000);
        g.fill(0, 0, PW, 3, accent());
        drawBolt(g, 24, 12, accent());

        scaledText(g, this.title, 64, 12, 1.5F, COLOR_TEXT);
        g.drawString(this.font, Component.translatable("gui.flash.progress.subtitle"),
                64, 36, COLOR_MUTED, false);

        Component badge = FlashClientData.hasPowers()
                ? (FlashClientData.isTemporary()
                    ? Component.translatable("gui.flash.progress.temporary",
                        formatTime(FlashClientData.getTemporaryRemaining()))
                    : Component.translatable("gui.flash.progress.active_badge",
                        FlashClientData.getSelectedLevel()))
                : Component.translatable("gui.flash.progress.no_powers_badge");
        int tw = this.font.width(badge);
        int bx = PW - PAD - tw - 18;
        int by = 18;
        int color = FlashClientData.hasPowers() ? accent() : COLOR_MUTED;
        g.fill(bx, by, bx + tw + 18, by + 22, accentSoft());
        g.fill(bx, by, bx + tw + 18, by + 2, color);
        g.drawString(this.font, badge, bx + 9, by + 8, color, false);
    }

    /** Chunky pixel lightning bolt. */
    private static void drawBolt(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 12, y, x + 22, y + 6, c);
        g.fill(x + 9, y + 6, x + 19, y + 12, c);
        g.fill(x + 3, y + 12, x + 19, y + 17, c);
        g.fill(x + 9, y + 17, x + 19, y + 22, c);
        g.fill(x + 6, y + 22, x + 16, y + 27, c);
        g.fill(x + 3, y + 27, x + 11, y + 32, c);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        String[] keys = {"gui.flash.tab.progress", "gui.flash.tab.config"};
        for (int i = 0; i < keys.length; i++) {
            int tx = PAD + i * (TAB_W + 6);
            boolean active = tab == i;
            boolean hovered = mx >= tx && mx < tx + TAB_W && my >= TAB_Y && my < TAB_Y + TAB_H;
            g.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + TAB_H,
                    active ? accentDark() : (hovered ? 0xFF1B1F26 : 0xFF111419));
            if (active) {
                g.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + 2, accent());
            }
            Component label = Component.translatable(keys[i]);
            g.drawString(this.font, label, tx + (TAB_W - this.font.width(label)) / 2,
                    TAB_Y + 8, active ? accent() : COLOR_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // Progress tab
    // ------------------------------------------------------------------

    private void drawProgressTab(GuiGraphics g, int mx, int my) {
        boolean has = FlashClientData.hasPowers();
        Component selected = has
                ? Component.translatable("level.flash." + FlashClientData.getSelectedLevel())
                : Component.translatable("gui.flash.progress.none");
        Component max = has
                ? Component.translatable("level.flash." + FlashClientData.getMaxLevel())
                : Component.translatable("gui.flash.progress.none");
        statBox(g, PAD, CONTENT_Y, 185, 48, Component.translatable("gui.flash.progress.selected"), selected);
        statBox(g, PAD + 193, CONTENT_Y, 185, 48, Component.translatable("gui.flash.progress.max"), max);

        g.drawString(this.font, Component.translatable("gui.flash.progress.levels"),
                PAD, GRID_Y - 14, COLOR_TEXT, false);
        drawLevelGrid(g, mx, my);
        drawProgress(g);
        drawSkills(g, mx, my);
    }

    private void statBox(GuiGraphics g, int x, int y, int w, int h, Component label, Component value) {
        g.fill(x, y, x + w, y + h, COLOR_BOX_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_BOX);
        g.fill(x + 1, y + 1, x + 4, y + h - 1, accent());
        g.drawString(this.font, label, x + 14, y + 8, COLOR_MUTED, false);
        scaledText(g, value, x + 14, y + 22, 1.5F, COLOR_TEXT);
    }

    private void drawLevelGrid(GuiGraphics g, int mx, int my) {
        int max = FlashClientData.getMaxLevel();
        int selected = FlashClientData.getSelectedLevel();
        boolean has = FlashClientData.hasPowers();

        for (int level = FlashSpeedLevels.MIN_LEVEL; level <= FlashSpeedLevels.MAX_LEVEL; level++) {
            int cx = GRID_X + (level % CARD_COLUMNS) * (CARD_W + CARD_GAP);
            int cy = GRID_Y + (level / CARD_COLUMNS) * (CARD_H + CARD_GAP);
            boolean unlocked = has && level <= max;
            boolean inUse = has && level == selected;
            boolean hovered = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;

            int border = inUse ? accent() : unlocked ? 0xFF2F8F55 : COLOR_BOX_BORDER;
            int fill = inUse ? accentSoft() : unlocked ? 0xFF101A13 : 0xFF0E1013;
            if (hovered && unlocked && !inUse) {
                border = 0xFF9BE8B4;
                fill = 0xFF17231B;
            }
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, border);
            g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, fill);
            if (inUse) {
                g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + 3, accent());
            }

            scaledText(g, Component.literal(Integer.toString(level)), cx + 10, cy + 11, 2.0F,
                    inUse ? accent() : unlocked ? COLOR_TEXT : COLOR_DIM);

            Component status = inUse
                    ? Component.translatable("gui.flash.progress.in_use")
                    : unlocked
                    ? Component.translatable("gui.flash.progress.unlocked")
                    : Component.translatable("gui.flash.progress.locked");
            int statusColor = inUse ? accent() : unlocked ? COLOR_GREEN : COLOR_DIM;
            g.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), 44),
                    cx + 40, cy + 16, statusColor, false);
            if (unlocked) {
                drawCheck(g, cx + CARD_W - 16, cy + 6, inUse ? accent() : COLOR_GREEN);
            } else {
                drawLock(g, cx + CARD_W - 16, cy + 6, COLOR_DIM);
            }
        }
    }

    private void drawProgress(GuiGraphics g) {
        boolean has = FlashClientData.hasPowers();
        boolean maxed = has && FlashClientData.getMaxLevel() >= FlashSpeedLevels.MAX_LEVEL;
        int y = 322;

        if (!has) {
            g.drawString(this.font, Component.translatable("gui.flash.progress.no_powers"),
                    PAD, y, COLOR_MUTED, false);
            drawBar(g, y + 14, 0.0F, COLOR_DIM);
            g.drawString(this.font, Component.translatable("gui.flash.progress.no_powers_hint"),
                    PAD, y + 34, COLOR_DIM, false);
            return;
        }
        if (maxed) {
            g.drawString(this.font, Component.translatable("gui.flash.progress.maxed"),
                    PAD, y, COLOR_GREEN, false);
            drawBar(g, y + 14, 1.0F, COLOR_GREEN);
            g.drawString(this.font, Component.translatable("gui.flash.progress.all_unlocked"),
                    PAD, y + 34, COLOR_MUTED, false);
            return;
        }

        int next = FlashClientData.getMaxLevel() + 1;
        g.drawString(this.font, Component.translatable("gui.flash.progress.next", next),
                PAD, y, COLOR_TEXT, false);
        float fraction = (float) Math.min(1.0D,
                FlashClientData.getDistance() / (double) FlashSpeedLevels.BLOCKS_PER_LEVEL);
        Component percent = Component.literal(Math.round(fraction * 100.0F) + "%");
        g.drawString(this.font, percent, PAD + 378 - this.font.width(percent), y, accent(), false);
        drawBar(g, y + 14, fraction, accent());

        int travelled = Math.min(FlashSpeedLevels.BLOCKS_PER_LEVEL,
                (int) Math.floor(FlashClientData.getDistance()));
        int remaining = Math.max(0, FlashSpeedLevels.BLOCKS_PER_LEVEL - travelled);
        g.drawString(this.font, Component.translatable("gui.flash.progress.blocks",
                number(travelled), number(FlashSpeedLevels.BLOCKS_PER_LEVEL)),
                PAD, y + 34, COLOR_MUTED, false);
        Component rest = Component.translatable("gui.flash.progress.remaining", number(remaining));
        g.drawString(this.font, rest, PAD + 378 - this.font.width(rest), y + 34, COLOR_TEXT, false);
    }

    private void drawBar(GuiGraphics g, int y, float fraction, int color) {
        int w = 378;
        int h = 12;
        g.fill(PAD, y, PAD + w, y + h, COLOR_BOX_BORDER);
        g.fill(PAD + 1, y + 1, PAD + w - 1, y + h - 1, 0xFF0C0E11);
        int filled = (int) ((w - 2) * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 0) {
            g.fillGradient(PAD + 1, y + 1, PAD + 1 + filled, y + h - 1, color, darker(color));
            // segment ticks every 10 %
            for (int i = 1; i < 10; i++) {
                int tx = PAD + 1 + (w - 2) * i / 10;
                if (tx < PAD + 1 + filled) {
                    g.fill(tx, y + 1, tx + 1, y + h - 1, 0x55000000);
                }
            }
        }
    }

    private void drawSkills(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, Component.translatable("gui.flash.progress.skills"),
                SKILL_X, CONTENT_Y, COLOR_TEXT, false);
        FlashAbility[] abilities = FlashAbility.values();
        int top = CONTENT_Y + 14;
        for (int i = 0; i < abilities.length; i++) {
            FlashAbility ability = abilities[i];
            int ry = top + i * SKILL_ROW;
            boolean unlocked = FlashClientData.hasPowers()
                    && FlashClientData.getMaxLevel() >= ability.requiredLevel();
            boolean hovered = mx >= SKILL_X && mx < SKILL_X + SKILL_W && my >= ry && my < ry + SKILL_ROW - 2;

            g.fill(SKILL_X, ry, SKILL_X + SKILL_W, ry + SKILL_ROW - 2,
                    hovered ? accent() : COLOR_BOX_BORDER);
            g.fill(SKILL_X + 1, ry + 1, SKILL_X + SKILL_W - 1, ry + SKILL_ROW - 3,
                    unlocked ? 0xFF111A14 : 0xFF0E1013);
            if (unlocked) {
                drawCheck(g, SKILL_X + 6, ry + 7, COLOR_GREEN);
            } else {
                drawLock(g, SKILL_X + 6, ry + 7, COLOR_DIM);
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(ability.displayName().getString(), 120),
                    SKILL_X + 22, ry + 3, unlocked ? COLOR_TEXT : COLOR_DIM, false);
            Component requirement = unlocked
                    ? Component.translatable("gui.flash.progress.unlocked")
                    : Component.translatable("gui.flash.progress.skills_locked", ability.requiredLevel());
            g.drawString(this.font, requirement, SKILL_X + 22, ry + 12,
                    unlocked ? COLOR_GREEN : COLOR_DIM, false);

            int cooldown = FlashClientData.getAbilityCooldown(ability.ordinal());
            if (cooldown > 0) {
                Component cd = Component.literal(formatTime(cooldown));
                g.drawString(this.font, cd, SKILL_X + SKILL_W - 6 - this.font.width(cd),
                        ry + 8, COLOR_ORANGE, false);
            }
            if (hovered) {
                g.renderTooltip(this.font, ability.description(), mx, my);
            }
        }
    }

    // ------------------------------------------------------------------
    // Config tab
    // ------------------------------------------------------------------

    private static int cfgY(int row) {
        return CONTENT_Y + row * (CFG_ROW_H + CFG_ROW_GAP);
    }

    private void drawConfigTab(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.config.destroy", "gui.flash.config.destroy.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(0) + 11, FlashClientData.isDestroyBlocks());

        configRow(g, 1, "gui.flash.config.speed_mode", "gui.flash.config.speed_mode.desc", mx, my);
        boolean scroll = FlashClientConfig.SCROLL_MODE.get();
        drawSegment(g, segX(0), cfgY(1) + 10, "gui.flash.config.mode_keys", !scroll, mx, my);
        drawSegment(g, segX(1), cfgY(1) + 10, "gui.flash.config.mode_scroll", scroll, mx, my);

        configRow(g, 2, "gui.flash.config.wall_run", "gui.flash.config.wall_run.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(2) + 11, FlashClientConfig.WALL_RUN.get());

        configRow(g, 3, "gui.flash.config.hud", "gui.flash.config.hud.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(3) + 11, FlashClientConfig.SHOW_ABILITY_HUD.get());

        configRow(g, 4, "gui.flash.config.body_lightning", "gui.flash.config.body_lightning.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(4) + 11, FlashClientConfig.BODY_LIGHTNING.get());
    }

    private static int switchX() {
        return PW - PAD - 14 - 56;
    }

    private static int segW() {
        return 128;
    }

    private static int segX(int index) {
        return PW - PAD - 10 - 2 * segW() - 6 + index * (segW() + 6);
    }

    private void configRow(GuiGraphics g, int row, String titleKey, String descKey, int mx, int my) {
        int y = cfgY(row);
        boolean hovered = mx >= PAD && mx < PW - PAD && my >= y && my < y + CFG_ROW_H;
        g.fill(PAD, y, PW - PAD, y + CFG_ROW_H, hovered ? accentDark() : COLOR_BOX_BORDER);
        g.fill(PAD + 1, y + 1, PW - PAD - 1, y + CFG_ROW_H - 1, COLOR_BOX);
        g.fill(PAD + 1, y + 1, PAD + 4, y + CFG_ROW_H - 1, accent());
        g.drawString(this.font, Component.translatable(titleKey), PAD + 16, y + 10, COLOR_TEXT, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(descKey).getString(), 330),
                PAD + 16, y + 25, COLOR_MUTED, false);
    }

    private void drawSwitch(GuiGraphics g, int x, int y, boolean on) {
        int w = 56;
        int h = 22;
        g.fill(x, y, x + w, y + h, on ? accent() : COLOR_BOX_BORDER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, on ? accentDark() : 0xFF0C0E11);
        int knob = on ? x + w - 2 - 18 : x + 2;
        g.fill(knob, y + 2, knob + 18, y + h - 2, on ? accent() : COLOR_DIM);
        Component text = Component.translatable(on ? "gui.flash.config.on" : "gui.flash.config.off");
        g.drawString(this.font, text, on ? x + 7 : x + w - 7 - this.font.width(text), y + 7,
                on ? COLOR_TEXT : COLOR_MUTED, false);
    }

    private void drawSegment(GuiGraphics g, int x, int y, String key, boolean active, int mx, int my) {
        int w = segW();
        int h = 24;
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, active ? accent() : (hovered ? COLOR_MUTED : COLOR_BOX_BORDER));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, active ? accentDark() : 0xFF0C0E11);
        Component label = Component.translatable(key);
        g.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), w - 8),
                x + (w - Math.min(w - 8, this.font.width(label))) / 2, y + 8,
                active ? accent() : COLOR_MUTED, false);
    }

    // ------------------------------------------------------------------
    // Small drawing helpers
    // ------------------------------------------------------------------

    private void scaledText(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(this.font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static int darker(int argb) {
        int r = ((argb >> 16) & 0xFF) * 6 / 10;
        int gr = ((argb >> 8) & 0xFF) * 6 / 10;
        int b = (argb & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static void drawCheck(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 1, y + 4, x + 3, y + 6, c);
        g.fill(x + 2, y + 5, x + 4, y + 7, c);
        g.fill(x + 3, y + 4, x + 5, y + 6, c);
        g.fill(x + 4, y + 2, x + 6, y + 5, c);
        g.fill(x + 5, y + 1, x + 7, y + 3, c);
    }

    private static void drawLock(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 2, y + 4, x + 7, y + 8, c);
        g.fill(x + 2, y + 1, x + 3, y + 4, c);
        g.fill(x + 6, y + 1, x + 7, y + 4, c);
        g.fill(x + 2, y + 1, x + 7, y + 2, c);
        g.fill(x + 4, y + 5, x + 5, y + 7, 0xFF101216);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double mx = mouseX / this.uiScale - this.leftPos;
        double my = mouseY / this.uiScale - this.topPos;

        // tabs
        for (int i = 0; i < 2; i++) {
            int tx = PAD + i * (TAB_W + 6);
            if (mx >= tx && mx < tx + TAB_W && my >= TAB_Y && my < TAB_Y + TAB_H) {
                tab = i;
                click();
                return true;
            }
        }

        if (tab == 1) {
            if (inRow(mx, my, 0)) {
                boolean enabled = !FlashClientData.isDestroyBlocks();
                FlashClientData.setDestroyBlocksOptimistic(enabled);
                FlashNetwork.CHANNEL.sendToServer(new FlashSettingsPacket(enabled));
                click();
                return true;
            }
            if (inRow(mx, my, 1)) {
                int y = cfgY(1) + 10;
                if (my >= y && my < y + 24) {
                    for (int i = 0; i < 2; i++) {
                        if (mx >= segX(i) && mx < segX(i) + segW()) {
                            boolean wantScroll = i == 1;
                            if (FlashClientConfig.SCROLL_MODE.get() != wantScroll) {
                                FlashClientConfig.SCROLL_MODE.set(wantScroll);
                                FlashClientConfig.SCROLL_MODE.save();
                            }
                            click();
                            return true;
                        }
                    }
                }
                return true;
            }
            ForgeConfigSpec.BooleanValue[] toggles = {
                    null, null, FlashClientConfig.WALL_RUN,
                    FlashClientConfig.SHOW_ABILITY_HUD, FlashClientConfig.BODY_LIGHTNING};
            for (int row = 2; row <= 4; row++) {
                if (inRow(mx, my, row)) {
                    FlashClientConfig.toggle(toggles[row]);
                    click();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int level = levelAt(mx, my);
        if (level >= 0
                && FlashClientData.hasPowers()
                && !FlashClientData.isTemporary()
                && level <= FlashClientData.getMaxLevel()) {
            FlashClientData.setSelectedLevelOptimistic(level);
            FlashNetwork.CHANNEL.sendToServer(new FlashSelectLevelPacket(level));
            click();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inRow(double mx, double my, int row) {
        int y = cfgY(row);
        return mx >= PAD && mx < PW - PAD && my >= y && my < y + CFG_ROW_H;
    }

    private int levelAt(double mx, double my) {
        int gx = (int) mx - GRID_X;
        int gy = (int) my - GRID_Y;
        if (gx < 0 || gy < 0) {
            return -1;
        }
        int column = gx / (CARD_W + CARD_GAP);
        int row = gy / (CARD_H + CARD_GAP);
        if (column >= CARD_COLUMNS || row > 2) {
            return -1;
        }
        if (gx % (CARD_W + CARD_GAP) >= CARD_W || gy % (CARD_H + CARD_GAP) >= CARD_H) {
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
        // Only the active control mode works: keys mode here, Ctrl+scroll otherwise.
        if (FlashClientData.hasPowers() && !FlashClientConfig.SCROLL_MODE.get()) {
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
        if (FlashClientConfig.SCROLL_MODE.get()
                && minecraft.player != null
                && Screen.hasControlDown()
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
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
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

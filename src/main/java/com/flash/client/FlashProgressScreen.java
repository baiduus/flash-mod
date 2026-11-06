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

    /** Levels on the grid: 0..10, up to 20 with the amplifier, and whatever the V-9 adds on top. */
    private static int maxShownLevel() {
        int base = FlashClientRing.isLocalAmplifier() ? FlashSpeedLevels.AMP_MAX_LEVEL : FlashSpeedLevels.BASE_MAX_LEVEL;
        return Math.min(FlashSpeedLevels.MAX_LEVEL, Math.max(base, FlashClientData.getMaxLevel()));
    }

    private static boolean compactGrid() {
        return maxShownLevel() > FlashSpeedLevels.BASE_MAX_LEVEL + 1;
    }

    private static int cardColumns() {
        return compactGrid() ? 7 : 4;
    }

    private static int cardW() {
        return compactGrid() ? 80 : 90;
    }

    private static int gridRows() {
        return (maxShownLevel() + cardColumns()) / cardColumns();
    }

    /** 3 rows fit the normal height; a 4th row (levels 21..25) makes the cards shorter. */
    private static int cardH() {
        return gridRows() > 3 ? 30 : CARD_H_DEFAULT;
    }

    private static final int CARD_H_DEFAULT = 40;
    private static final int CARD_GAP = 6;
    private static final int GRID_X = PAD;
    private static final int GRID_Y = 176;

    private static final int SKILL_X = 420;
    private static final int SKILL_ROW = 25;
    private static final int SKILL_W = 200;

    private static final int CFG_ROW_H = 36;
    private static final int CFG_ROW_GAP = 5;

    private static final int COLOR_BG_TOP = 0xFF15181E;
    private static final int COLOR_BG_BOTTOM = 0xFF0A0B0E;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A1AD;
    private static final int COLOR_DIM = 0xFF5B626C;
    private static final int COLOR_GREEN = 0xFF35D06A;
    private static final int COLOR_ORANGE = 0xFFFF7A3D;
    private static final int COLOR_BOX = 0xFF12151A;
    private static final int COLOR_BOX_BORDER = 0xFF2A2F38;

    private static final int TAB_PROGRESS = 0;
    private static final int TAB_CONFIG = 1;
    private static final int TAB_EQUIP = 2;

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

    private static PowerTheme theme() {
        return PowerTheme.current();
    }

    private static int accent() {
        return theme().accent();
    }

    private static int accentDark() {
        return theme().accentDark();
    }

    private static int accentSoft() {
        return theme().accentSoft();
    }

    /** Tabs shown for the current state: without a power only the neutral progress tab exists. */
    private static int[] visibleTabs() {
        return FlashClientData.hasPowers() ? new int[] {TAB_PROGRESS, TAB_EQUIP, TAB_CONFIG}
                : new int[] {TAB_PROGRESS};
    }

    private static int activeTab() {
        for (int id : visibleTabs()) {
            if (id == tab) {
                return id;
            }
        }
        return TAB_PROGRESS;
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

        int current = activeTab();
        if (current == TAB_PROGRESS) {
            drawProgressTab(graphics, mx, my);
        } else if (current == TAB_EQUIP) {
            drawEquipTab(graphics, mx, my);
        } else {
            drawConfigTab(graphics, mx, my);
        }

        graphics.drawString(this.font, footerText(), PAD, PH - 16, COLOR_DIM, false);

        poseStack.popPose();
    }

    private Component footerText() {
        if (!FlashClientData.hasPowers()) {
            return Component.translatable("gui.flash.footer.neutral");
        }
        return Component.translatable(FlashClientConfig.SCROLL_MODE.get()
                ? "gui.flash.footer.scroll" : "gui.flash.footer.keys");
    }

    private void drawHeader(GuiGraphics g) {
        g.fillGradient(0, 0, PW, 58, accentDark(), 0x00000000);
        g.fill(0, 0, PW, 3, accent());
        if (theme().icon() == PowerTheme.Icon.BOLT) {
            drawBolt(g, 24, 12, accent());
        } else {
            drawNeutralIcon(g, 22, 12, accent());
        }

        scaledText(g, Component.translatable(theme().titleKey()), 64, 12, 1.5F, COLOR_TEXT);
        g.drawString(this.font, Component.translatable(theme().subtitleKey()),
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

    /** Neutral emblem: an empty ring. */
    private static void drawNeutralIcon(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 8, y, x + 24, y + 4, c);
        g.fill(x + 4, y + 4, x + 8, y + 8, c);
        g.fill(x + 24, y + 4, x + 28, y + 8, c);
        g.fill(x, y + 8, x + 4, y + 24, c);
        g.fill(x + 28, y + 8, x + 32, y + 24, c);
        g.fill(x + 4, y + 24, x + 8, y + 28, c);
        g.fill(x + 24, y + 24, x + 28, y + 28, c);
        g.fill(x + 8, y + 28, x + 24, y + 32, c);
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
        String[] keys = {"gui.flash.tab.progress", "gui.flash.tab.config", "gui.flash.tab.equipment"};
        int[] visible = visibleTabs();
        for (int i = 0; i < visible.length; i++) {
            int id = visible[i];
            int tx = PAD + i * (TAB_W + 6);
            boolean active = activeTab() == id;
            boolean hovered = mx >= tx && mx < tx + TAB_W && my >= TAB_Y && my < TAB_Y + TAB_H;
            g.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + TAB_H,
                    active ? accentDark() : (hovered ? 0xFF1B1F26 : 0xFF111419));
            if (active) {
                g.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + 2, accent());
            }
            Component label = Component.translatable(keys[id]);
            g.drawString(this.font, label, tx + (TAB_W - this.font.width(label)) / 2,
                    TAB_Y + 8, active ? accent() : COLOR_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // Progress tab
    // ------------------------------------------------------------------

    private void drawProgressTab(GuiGraphics g, int mx, int my) {
        boolean has = FlashClientData.hasPowers();
        if (!has) {
            drawEmptyState(g);
            return;
        }
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

    /** Neutral screen shown while the player has no power. */
    private void drawEmptyState(GuiGraphics g) {
        int cx = PW / 2;
        int cy = CONTENT_Y + 90;
        drawNeutralIcon(g, cx - 16 - 16, cy - 70, COLOR_DIM);
        Component title = Component.translatable("gui.flash.empty.title");
        Component hint = Component.translatable("gui.flash.empty.hint");
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx - this.font.width(title) * 1.5F / 2.0F, cy - 10, 0.0F);
        pose.scale(1.5F, 1.5F, 1.0F);
        g.drawString(this.font, title, 0, 0, COLOR_MUTED, false);
        pose.popPose();
        g.drawString(this.font, hint, cx - this.font.width(hint) / 2, cy + 20, COLOR_DIM, false);
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

        for (int level = FlashSpeedLevels.MIN_LEVEL; level <= maxShownLevel(); level++) {
            int cx = GRID_X + (level % cardColumns()) * (cardW() + CARD_GAP);
            int cy = GRID_Y + (level / cardColumns()) * (cardH() + CARD_GAP);
            boolean unlocked = has && level <= max;
            boolean inUse = has && level == selected;
            boolean hovered = mx >= cx && mx < cx + cardW() && my >= cy && my < cy + cardH();

            int border = inUse ? accent() : unlocked ? 0xFF2F8F55 : COLOR_BOX_BORDER;
            int fill = inUse ? accentSoft() : unlocked ? 0xFF101A13 : 0xFF0E1013;
            if (hovered && unlocked && !inUse) {
                border = 0xFF9BE8B4;
                fill = 0xFF17231B;
            }
            g.fill(cx, cy, cx + cardW(), cy + cardH(), border);
            g.fill(cx + 1, cy + 1, cx + cardW() - 1, cy + cardH() - 1, fill);
            if (inUse) {
                g.fill(cx + 1, cy + 1, cx + cardW() - 1, cy + 3, accent());
            }

            scaledText(g, Component.literal(Integer.toString(level)), cx + 10, cy + 11, 2.0F,
                    inUse ? accent() : unlocked ? COLOR_TEXT : COLOR_DIM);

            Component status = inUse
                    ? Component.translatable("gui.flash.progress.in_use")
                    : unlocked
                    ? Component.translatable("gui.flash.progress.unlocked")
                    : Component.translatable("gui.flash.progress.locked");
            int statusColor = inUse ? accent() : unlocked ? COLOR_GREEN : COLOR_DIM;
            if (!compactGrid()) {
                g.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), 44),
                        cx + 40, cy + 16, statusColor, false);
            } else if (level >= 10) {
                scaledText(g, Component.literal(level == 10 ? "" : "+"), cx + 52, cy + 11, 1.0F, statusColor);
            }
            if (unlocked) {
                drawCheck(g, cx + cardW() - 16, cy + 6, inUse ? accent() : COLOR_GREEN);
            } else {
                drawLock(g, cx + cardW() - 16, cy + 6, COLOR_DIM);
            }
        }
    }

    private void drawProgress(GuiGraphics g) {
        boolean has = FlashClientData.hasPowers();
        boolean maxed = has && FlashClientData.getMaxLevel() >= FlashSpeedLevels.BASE_MAX_LEVEL;
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
        drawSwitch(g, switchX(), cfgY(0) + 7, FlashClientData.isDestroyBlocks());

        configRow(g, 1, "gui.flash.config.speed_mode", "gui.flash.config.speed_mode.desc", mx, my);
        boolean scroll = FlashClientConfig.SCROLL_MODE.get();
        drawSegment(g, segX(0), cfgY(1) + 6, "gui.flash.config.mode_keys", !scroll, mx, my);
        drawSegment(g, segX(1), cfgY(1) + 6, "gui.flash.config.mode_scroll", scroll, mx, my);

        configRow(g, 2, "gui.flash.config.wall_run", "gui.flash.config.wall_run.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(2) + 7, FlashClientConfig.WALL_RUN.get());

        configRow(g, 3, "gui.flash.config.hud", "gui.flash.config.hud.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(3) + 7, FlashClientConfig.SHOW_ABILITY_HUD.get());

        configRow(g, 4, "gui.flash.config.body_lightning", "gui.flash.config.body_lightning.desc", mx, my);
        drawSwitch(g, switchX(), cfgY(4) + 7, FlashClientConfig.BODY_LIGHTNING.get());

        configRow(g, 5, "gui.flash.config.mouth", "gui.flash.config.mouth.desc", mx, my);
        boolean open = FlashClientConfig.MASK_OPEN_MOUTH.get();
        drawSegment(g, segX(0), cfgY(5) + 6, "gui.flash.config.mouth_closed", !open, mx, my);
        drawSegment(g, segX(1), cfgY(5) + 6, "gui.flash.config.mouth_open", open, mx, my);
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
        g.drawString(this.font, Component.translatable(titleKey), PAD + 16, y + 6, COLOR_TEXT, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(descKey).getString(), 330),
                PAD + 16, y + 20, COLOR_MUTED, false);
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
    // Equipment tab (ring + suit)
    // ------------------------------------------------------------------

    private static final int SLOT_X = PAD + 10;
    private static final int SLOT_Y = CONTENT_Y + 28;
    private static final int SLOT_SIZE = 76;
    private static final int BTN_X = PAD + 110;
    private static final int BTN_W = 220;
    private static final int BTN_H = 30;

    private static int btnRingY() {
        return CONTENT_Y + 30;
    }

    private static int btnSuitY() {
        return CONTENT_Y + 74;
    }

    // second, dedicated slot: Tachyon Amplifier
    private static final int AMP_SLOT_Y = CONTENT_Y + 150;
    private static final int AMP_SLOT_SIZE = 56;
    private static final int AMP_BTN_Y = AMP_SLOT_Y + 13;

    private static boolean suitWorn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && com.flash.item.FlashSuitItem.countWorn(mc.player) > 0;
    }

    private void drawEquipTab(GuiGraphics g, int mx, int my) {
        boolean ring = FlashClientRing.isLocalEquipped();

        g.drawString(this.font, Component.translatable("gui.flash.equip.ring_slot"),
                PAD, CONTENT_Y, COLOR_TEXT, false);
        // ring slot
        g.fill(SLOT_X, SLOT_Y, SLOT_X + SLOT_SIZE, SLOT_Y + SLOT_SIZE, ring ? accent() : COLOR_BOX_BORDER);
        g.fill(SLOT_X + 2, SLOT_Y + 2, SLOT_X + SLOT_SIZE - 2, SLOT_Y + SLOT_SIZE - 2, COLOR_BOX);
        if (ring) {
            g.fill(SLOT_X + 2, SLOT_Y + 2, SLOT_X + SLOT_SIZE - 2, SLOT_Y + 5, accent());
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(SLOT_X + 12, SLOT_Y + 12, 0.0F);
            pose.scale(3.0F, 3.0F, 1.0F);
            g.renderItem(new net.minecraft.world.item.ItemStack(
                    com.flash.registry.ModItems.FLASH_RING.get()), 0, 0);
            pose.popPose();
        } else {
            Component empty = Component.translatable("gui.flash.equip.empty");
            g.drawString(this.font, empty, SLOT_X + (SLOT_SIZE - this.font.width(empty)) / 2,
                    SLOT_Y + SLOT_SIZE / 2 - 4, COLOR_DIM, false);
        }

        drawButton(g, BTN_X, btnRingY(), BTN_W, BTN_H,
                Component.translatable(ring ? "gui.flash.equip.remove_ring" : "gui.flash.equip.put_ring"),
                true, mx, my);
        drawButton(g, BTN_X, btnSuitY(), BTN_W, BTN_H,
                Component.translatable(suitWorn() ? "gui.flash.equip.take_off_suit" : "gui.flash.equip.put_on_suit"),
                ring, mx, my);

        Component key = Component.translatable("gui.flash.equip.key",
                FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage());
        g.drawString(this.font, key, BTN_X, btnSuitY() + BTN_H + 8, accent(), false);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.flash.equip.help").getString(), 330),
                BTN_X, btnSuitY() + BTN_H + 22, COLOR_MUTED, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.flash.equip.help2").getString(), 330),
                BTN_X, btnSuitY() + BTN_H + 34, COLOR_DIM, false);

        // tachyon amplifier slot
        boolean amp = FlashClientRing.isLocalAmplifier();
        g.drawString(this.font, Component.translatable("gui.flash.equip.amplifier_slot"),
                PAD, AMP_SLOT_Y - 12, COLOR_TEXT, false);
        int ax = SLOT_X + 10;
        g.fill(ax, AMP_SLOT_Y, ax + AMP_SLOT_SIZE, AMP_SLOT_Y + AMP_SLOT_SIZE, amp ? accent() : COLOR_BOX_BORDER);
        g.fill(ax + 2, AMP_SLOT_Y + 2, ax + AMP_SLOT_SIZE - 2, AMP_SLOT_Y + AMP_SLOT_SIZE - 2, COLOR_BOX);
        if (amp) {
            g.fill(ax + 2, AMP_SLOT_Y + 2, ax + AMP_SLOT_SIZE - 2, AMP_SLOT_Y + 5, accent());
            PoseStack apose = g.pose();
            apose.pushPose();
            apose.translate(ax + 8, AMP_SLOT_Y + 8, 0.0F);
            apose.scale(2.5F, 2.5F, 1.0F);
            g.renderItem(new net.minecraft.world.item.ItemStack(
                    com.flash.registry.ModItems.TACHYON_AMPLIFIER.get()), 0, 0);
            apose.popPose();
        } else {
            Component empty = Component.translatable("gui.flash.equip.empty");
            g.drawString(this.font, empty, ax + (AMP_SLOT_SIZE - this.font.width(empty)) / 2,
                    AMP_SLOT_Y + AMP_SLOT_SIZE / 2 - 4, COLOR_DIM, false);
        }
        drawButton(g, BTN_X, AMP_BTN_Y, BTN_W, BTN_H,
                Component.translatable(amp ? "gui.flash.equip.remove_amplifier" : "gui.flash.equip.put_amplifier"),
                true, mx, my);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.flash.equip.amplifier_help").getString(), 330),
                BTN_X, AMP_BTN_Y + BTN_H + 8, COLOR_MUTED, false);

        // preview of the player with the ring and the suit
        int px = PW - 120;
        int py = CONTENT_Y + 230;
        g.fill(PW - 220, CONTENT_Y - 2, PW - PAD, CONTENT_Y + 250, COLOR_BOX_BORDER);
        g.fill(PW - 219, CONTENT_Y - 1, PW - PAD - 1, CONTENT_Y + 249, COLOR_BOX);
        g.fill(PW - 219, CONTENT_Y - 1, PW - PAD - 1, CONTENT_Y + 2, accent());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, px, py, 95, (float) (px - mx), (float) (py - 100 - my), mc.player);
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label,
                            boolean enabled, int mx, int my) {
        boolean hovered = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, enabled ? (hovered ? accent() : accentDark()) : COLOR_BOX_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1,
                enabled ? (hovered ? accentDark() : accentSoft()) : 0xFF0C0E11);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2,
                enabled ? (hovered ? COLOR_TEXT : accent()) : COLOR_DIM, false);
    }

    private boolean clickEquip(double mx, double my) {
        if (mx >= BTN_X && mx < BTN_X + BTN_W && my >= btnRingY() && my < btnRingY() + BTN_H) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                    FlashClientRing.isLocalEquipped()
                            ? com.flash.network.FlashRingPacket.UNEQUIP
                            : com.flash.network.FlashRingPacket.EQUIP));
            click();
            return true;
        }
        if (mx >= BTN_X && mx < BTN_X + BTN_W && my >= AMP_BTN_Y && my < AMP_BTN_Y + BTN_H) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                    FlashClientRing.isLocalAmplifier()
                            ? com.flash.network.FlashRingPacket.AMP_UNEQUIP
                            : com.flash.network.FlashRingPacket.AMP_EQUIP));
            click();
            return true;
        }
        if (FlashClientRing.isLocalEquipped()
                && mx >= BTN_X && mx < BTN_X + BTN_W && my >= btnSuitY() && my < btnSuitY() + BTN_H) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                    com.flash.network.FlashRingPacket.TOGGLE_SUIT));
            click();
            return true;
        }
        return false;
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
        int[] visible = visibleTabs();
        for (int i = 0; i < visible.length; i++) {
            int tx = PAD + i * (TAB_W + 6);
            if (mx >= tx && mx < tx + TAB_W && my >= TAB_Y && my < TAB_Y + TAB_H) {
                tab = visible[i];
                click();
                return true;
            }
        }

        if (activeTab() == TAB_EQUIP) {
            return clickEquip(mx, my) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG) {
            if (inRow(mx, my, 0)) {
                boolean enabled = !FlashClientData.isDestroyBlocks();
                FlashClientData.setDestroyBlocksOptimistic(enabled);
                FlashNetwork.CHANNEL.sendToServer(new FlashSettingsPacket(enabled));
                click();
                return true;
            }
            if (inRow(mx, my, 1)) {
                int y = cfgY(1) + 6;
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
            if (inRow(mx, my, 5)) {
                int y = cfgY(5) + 6;
                if (my >= y && my < y + 24) {
                    for (int i = 0; i < 2; i++) {
                        if (mx >= segX(i) && mx < segX(i) + segW()) {
                            boolean wantOpen = i == 1;
                            if (FlashClientConfig.MASK_OPEN_MOUTH.get() != wantOpen) {
                                FlashClientConfig.MASK_OPEN_MOUTH.set(wantOpen);
                                FlashClientConfig.MASK_OPEN_MOUTH.save();
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
        int column = gx / (cardW() + CARD_GAP);
        int row = gy / (cardH() + CARD_GAP);
        if (column >= cardColumns() || row >= gridRows()) {
            return -1;
        }
        if (gx % (cardW() + CARD_GAP) >= cardW() || gy % (cardH() + CARD_GAP) >= cardH()) {
            return -1;
        }
        int level = row * cardColumns() + column;
        return level <= maxShownLevel() ? level : -1;
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

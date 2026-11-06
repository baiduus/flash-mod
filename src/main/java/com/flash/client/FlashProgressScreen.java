package com.flash.client;

import com.flash.ability.FlashAbility;
import com.flash.config.FlashClientConfig;
import com.flash.network.FlashNetwork;
import com.flash.network.FlashSelectLevelPacket;
import com.flash.network.FlashSettingsPacket;
import com.flash.power.FlashSpeedLevels;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

/**
 * K screen. Three tabs (Progress, Equipment, Config) on one balanced panel:
 * a compact header, pill tabs with a sliding indicator, and content laid out on
 * two columns. Everything animates softly: the panel opens with a short ease,
 * hover highlights fade in and out, the progress bar fills smoothly, switches
 * slide and the level in use pulses. The accent colour comes from
 * {@link PowerTheme} (neutral, positive, negative, V-9). Server-authoritative
 * values come from {@link FlashClientData}.
 */
public class FlashProgressScreen extends Screen {

    // ------------------------------------------------------------------
    // Geometry (virtual pixels; the whole panel is scaled down on small windows)
    // ------------------------------------------------------------------

    private static final int PW = 560;
    private static final int PH = 340;
    private static final int PAD = 16;

    private static final int HEADER_H = 46;
    private static final int TAB_Y = 54;
    private static final int TAB_H = 20;
    private static final int DIVIDER_Y = 80;
    private static final int CONTENT_Y = 88;
    private static final int CONTENT_BOTTOM = PH - 22;

    /** Progress tab: left column (stats, levels, progress) and right column (abilities). */
    private static final int LEFT_W = 340;
    private static final int RIGHT_X = PAD + LEFT_W + 12;
    private static final int RIGHT_W = PW - PAD - RIGHT_X;

    private static final int STAT_H = 40;
    private static final int GRID_Y = CONTENT_Y + STAT_H + 20;
    private static final int GRID_GAP = 4;
    private static final int PROGRESS_Y = CONTENT_BOTTOM - 52;

    private static final int SKILL_ROW = 21;

    private static final int CFG_ROW_H = 34;
    private static final int CFG_ROW_GAP = 4;

    // ------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------

    private static final int COLOR_BG_TOP = 0xFF14171D;
    private static final int COLOR_BG_BOTTOM = 0xFF0B0C10;
    private static final int COLOR_CARD = 0xFF161A21;
    private static final int COLOR_CARD_HI = 0xFF1D222B;
    private static final int COLOR_BORDER = 0xFF262C36;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A1AD;
    private static final int COLOR_DIM = 0xFF5B626C;
    private static final int COLOR_GREEN = 0xFF3DDC84;
    private static final int COLOR_ORANGE = 0xFFFF8A3D;
    private static final int COLOR_TRACK = 0xFF0C0E12;

    private static final int TAB_PROGRESS = 0;
    private static final int TAB_CONFIG = 1;
    private static final int TAB_EQUIP = 2;
    private static final String[] TAB_KEYS = {
            "gui.flash.tab.progress", "gui.flash.tab.config", "gui.flash.tab.equipment"};

    private static int tab = 0;

    private int leftPos;
    private int topPos;
    private float uiScale = 1.0F;

    // animation state
    private long openedAt;
    private long lastFrame;
    private float dt;
    private float tabX = -1.0F;
    private float tabW;
    private float shownProgress = -1.0F;
    private final Map<String, Float> anim = new HashMap<>();

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
                this.width / (float) (PW + 16),
                this.height / (float) (PH + 16)));
        if (this.uiScale <= 0.01F) {
            this.uiScale = 1.0F;
        }
        this.leftPos = Math.round((this.width / this.uiScale - PW) / 2.0F);
        this.topPos = Math.round((this.height / this.uiScale - PH) / 2.0F);
        if (this.openedAt == 0L) {
            this.openedAt = Util.getMillis();
        }
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
        if (PowerTheme.localHulk()) {
            return new int[] {TAB_PROGRESS, TAB_CONFIG};
        }
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

    private int tabWidth(int id) {
        return Math.max(92, this.font.width(Component.translatable(TAB_KEYS[id])) + 30);
    }

    private int tabLeft(int index) {
        int[] visible = visibleTabs();
        int x = PAD;
        for (int i = 0; i < index; i++) {
            x += tabWidth(visible[i]) + 4;
        }
        return x;
    }

    // ------------------------------------------------------------------
    // Animation helpers
    // ------------------------------------------------------------------

    /** Eases the value stored under {@code key} towards 1 (on) or 0 (off). */
    private float ease(String key, boolean on, float speed) {
        float value = this.anim.getOrDefault(key, on ? 1.0F : 0.0F);
        float target = on ? 1.0F : 0.0F;
        value += (target - value) * Math.min(1.0F, this.dt * speed);
        if (Math.abs(target - value) < 0.002F) {
            value = target;
        }
        this.anim.put(key, value);
        return value;
    }

    private float hover(String key, boolean hovered) {
        return ease(key, hovered, 14.0F);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static int withAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float seconds() {
        return (Util.getMillis() % 1_000_000L) / 1000.0F;
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        this.dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        float open = Math.min(1.0F, (now - this.openedAt) / 220.0F);
        float eased = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);

        graphics.fill(0, 0, this.width, this.height, withAlpha(0xB8000000, eased));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(this.uiScale, this.uiScale, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;
        int mx = (int) (mouseX / this.uiScale) - x;
        int my = (int) (mouseY / this.uiScale) - y;

        // opening: the panel rises a few pixels and grows to its size
        float grow = 0.96F + 0.04F * eased;
        pose.translate(x + PW / 2.0F, y + PH / 2.0F + (1.0F - eased) * 10.0F, 0.0F);
        pose.scale(grow, grow, 1.0F);
        pose.translate(-PW / 2.0F, -PH / 2.0F, 0.0F);

        drawFrame(graphics);
        drawHeader(graphics);
        drawTabs(graphics, mx, my);
        graphics.fill(PAD, DIVIDER_Y, PW - PAD, DIVIDER_Y + 1, COLOR_BORDER);

        int current = activeTab();
        if (current == TAB_PROGRESS) {
            drawProgressTab(graphics, mx, my);
        } else if (current == TAB_EQUIP) {
            drawEquipTab(graphics, mx, my);
        } else if (PowerTheme.localHulk()) {
            drawHulkConfig(graphics, mx, my);
        } else {
            drawConfigTab(graphics, mx, my);
        }
        drawFooter(graphics);
        pose.popPose();
    }

    private void drawFrame(GuiGraphics g) {
        // soft outer glow in the accent colour, a thin border and the body
        for (int i = 4; i >= 1; i--) {
            int a = 0x18 * (5 - i);
            g.fill(-i, -i, PW + i, PH + i, (a << 24) | (accent() & 0x00FFFFFF));
        }
        g.fill(-1, -1, PW + 1, PH + 1, withAlpha(accent(), 0.65F));
        g.fillGradient(0, 0, PW, PH, COLOR_BG_TOP, COLOR_BG_BOTTOM);
    }

    private void drawHeader(GuiGraphics g) {
        g.fillGradient(0, 0, PW, HEADER_H + 8, withAlpha(accentDark(), 0.9F), 0x00000000);
        g.fill(0, 0, PW, 2, accent());
        if (theme().icon() == PowerTheme.Icon.BOLT) {
            drawBolt(g, 14, 8, accent());
        } else if (theme().icon() == PowerTheme.Icon.FIST) {
            drawFist(g, 12, 9, accent());
        } else {
            drawNeutralIcon(g, 12, 9, accent());
        }
        scaledText(g, Component.translatable(theme().titleKey()), 46, 10, 1.35F, COLOR_TEXT);
        g.drawString(this.font, Component.translatable(theme().subtitleKey()), 46, 28, COLOR_MUTED, false);

        boolean hulkPower = PowerTheme.localHulk();
        boolean hulkForm = hulkPower && com.flash.hulk.HulkState.isTransformed(Minecraft.getInstance().player);
        Component badge = hulkPower
                ? Component.translatable(hulkForm ? "gui.flash.hulk.badge_hulk" : "gui.flash.hulk.badge_banner")
                : FlashClientData.hasPowers()
                ? (FlashClientData.isTemporary()
                    ? Component.translatable("gui.flash.progress.temporary",
                        formatTime(FlashClientData.getTemporaryRemaining()))
                    : Component.translatable("gui.flash.progress.active_badge",
                        FlashClientData.getSelectedLevel()))
                : Component.translatable("gui.flash.progress.no_powers_badge");
        int tw = this.font.width(badge);
        int bw = tw + 22;
        int bx = PW - PAD - bw;
        int by = 13;
        int color = FlashClientData.hasPowers() || hulkPower ? accent() : COLOR_MUTED;
        g.fill(bx, by, bx + bw, by + 20, withAlpha(color, 0.55F));
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 19, accentSoft());
        // little live dot
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds() * 4.0F);
        g.fill(bx + 7, by + 8, bx + 11, by + 12, lerpColor(withAlpha(color, 0.4F), color, pulse));
        g.drawString(this.font, badge, bx + 15, by + 6, color, false);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        int[] visible = visibleTabs();
        int activeIndex = 0;
        for (int i = 0; i < visible.length; i++) {
            if (visible[i] == activeTab()) {
                activeIndex = i;
            }
        }
        // sliding indicator behind the active tab
        float targetX = tabLeft(activeIndex);
        float targetW = tabWidth(visible[activeIndex]);
        if (this.tabX < 0.0F) {
            this.tabX = targetX;
            this.tabW = targetW;
        }
        float k = Math.min(1.0F, this.dt * 16.0F);
        this.tabX += (targetX - this.tabX) * k;
        this.tabW += (targetW - this.tabW) * k;

        int total = tabLeft(visible.length - 1) + tabWidth(visible[visible.length - 1]) - PAD;
        g.fill(PAD - 2, TAB_Y - 2, PAD + total + 2, TAB_Y + TAB_H + 2, COLOR_TRACK);
        int ix = Math.round(this.tabX);
        int iw = Math.round(this.tabW);
        g.fill(ix, TAB_Y, ix + iw, TAB_Y + TAB_H, accentSoft());
        g.fill(ix, TAB_Y + TAB_H - 2, ix + iw, TAB_Y + TAB_H, accent());

        for (int i = 0; i < visible.length; i++) {
            int id = visible[i];
            int tx = tabLeft(i);
            int w = tabWidth(id);
            boolean active = id == activeTab();
            float h = hover("tab" + id, !active && inside(mx, my, tx, TAB_Y, w, TAB_H));
            if (h > 0.0F) {
                g.fill(tx, TAB_Y, tx + w, TAB_Y + TAB_H, withAlpha(0xFF1E232C, h));
            }
            Component label = Component.translatable(TAB_KEYS[id]);
            int color = active ? accent() : lerpColor(COLOR_MUTED, COLOR_TEXT, h);
            g.drawString(this.font, label, tx + (w - this.font.width(label)) / 2, TAB_Y + 6, color, false);
        }
    }

    private void drawFooter(GuiGraphics g) {
        Component text = PowerTheme.localHulk()
                ? Component.translatable("gui.flash.footer.hulk", FlashKeys.HULK_TRANSFORM.getTranslatedKeyMessage())
                : !FlashClientData.hasPowers()
                ? Component.translatable("gui.flash.footer.neutral")
                : Component.translatable(FlashClientConfig.SCROLL_MODE.get()
                    ? "gui.flash.footer.scroll" : "gui.flash.footer.keys");
        g.fill(PAD, PH - 20, PW - PAD, PH - 19, COLOR_BORDER);
        g.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), PW - 2 * PAD - 60),
                PAD, PH - 13, COLOR_DIM, false);
        Component esc = Component.literal("ESC");
        g.drawString(this.font, esc, PW - PAD - this.font.width(esc), PH - 13, COLOR_DIM, false);
    }

    // ------------------------------------------------------------------
    // Progress tab
    // ------------------------------------------------------------------

    private void drawProgressTab(GuiGraphics g, int mx, int my) {
        if (PowerTheme.localHulk()) {
            drawHulkTab(g, mx, my);
            return;
        }
        if (!FlashClientData.hasPowers()) {
            drawEmptyState(g);
            return;
        }
        int half = (LEFT_W - 8) / 2;
        statCard(g, PAD, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.progress.selected"),
                Component.translatable("level.flash." + FlashClientData.getSelectedLevel()),
                multiplier(FlashClientData.getSelectedLevel()));
        statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.progress.max"),
                Component.translatable("level.flash." + FlashClientData.getMaxLevel()),
                multiplier(FlashClientData.getMaxLevel()));

        sectionTitle(g, Component.translatable("gui.flash.progress.levels"), PAD, GRID_Y - 13);
        drawLevelGrid(g, mx, my);
        drawProgress(g);
        drawSkills(g, mx, my);
    }

    // ------------------------------------------------------------------
    // Hulk page
    // ------------------------------------------------------------------

    private void drawHulkTab(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean form = mc.player != null && com.flash.hulk.HulkState.isTransformed(mc.player);
        int level = Math.max(1, com.flash.hulk.HulkState.level());
        int active = Math.max(1, Math.min(level, com.flash.hulk.HulkState.activeLevel()));
        float rage = Math.max(0.0F, Math.min(1.0F, com.flash.hulk.HulkState.rage() / com.flash.hulk.HulkPowers.MAX_RAGE));
        int temp = com.flash.hulk.HulkState.tempTicks(now);
        int half = (LEFT_W - 8) / 2;
        float blocks = com.flash.hulk.HulkPowers.scaleOf(active, level, com.flash.hulk.HulkState.grow(),
                com.flash.hulk.HulkState.height()) * 1.8F;
        statCard(g, PAD, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.hulk.level"),
                active < level ? Component.translatable("gui.flash.hulk.level_value", active + "/" + level)
                        : Component.translatable("gui.flash.hulk.level_value", level),
                temp > 0 ? Component.literal(formatTime(temp)) : Component.translatable("gui.flash.hulk.height_value",
                        String.format(java.util.Locale.ROOT, "%.1f", blocks)));
        statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.hulk.form"),
                Component.translatable(form ? "gui.flash.hulk.form_hulk" : "gui.flash.hulk.form_banner"),
                Component.translatable("gui.flash.hulk.key_short", FlashKeys.HULK_TRANSFORM.getTranslatedKeyMessage()));

        // levels 1..10
        sectionTitle(g, Component.translatable("gui.flash.hulk.levels"), PAD, GRID_Y - 13);
        int cols = 5;
        int cw = (LEFT_W - GRID_GAP * (cols - 1)) / cols;
        int ch = 30;
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds() * 3.2F);
        for (int l = 1; l <= com.flash.hulk.HulkPowers.MAX_LEVEL; l++) {
            int cx = PAD + ((l - 1) % cols) * (cw + GRID_GAP);
            int cy = GRID_Y + ((l - 1) / cols) * (ch + GRID_GAP);
            boolean reached = l <= level;
            if (l == level) {
                g.fill(cx - 2, cy - 2, cx + cw + 2, cy + ch + 2, withAlpha(accent(), 0.25F + 0.35F * pulse));
                g.fill(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, accent());
                g.fill(cx, cy, cx + cw, cy + ch, accentSoft());
            } else {
                card(g, cx, cy, cw, ch, 0.0F);
                if (reached) {
                    g.fill(cx + 1, cy + ch - 2, cx + cw - 1, cy + ch - 1, withAlpha(accent(), 0.6F));
                }
            }
            Component n = Component.literal(Integer.toString(l));
            int nw = Math.round(this.font.width(n) * 1.5F);
            scaledText(g, n, cx + (cw - nw) / 2, cy + 9, 1.5F,
                    l == level ? accent() : reached ? COLOR_TEXT : COLOR_DIM);
            if (!reached) {
                drawLock(g, cx + cw - 10, cy + 3, COLOR_DIM);
            }
        }

        // strength: multiplier over a normal punch and the current punch damage
        boolean furious = com.flash.hulk.HulkState.furyTicks(now) > 0;
        double punch = com.flash.hulk.HulkPowers.punchDamage(active, furious);
        int sy = GRID_Y + 2 * (ch + GRID_GAP) + 6;
        int sh = PROGRESS_Y - 6 - sy;
        card(g, PAD, sy, LEFT_W, sh, 0.0F);
        g.fill(PAD, sy + 6, PAD + 2, sy + sh - 6, accent());
        g.drawString(this.font, Component.translatable("gui.flash.hulk.strength"), PAD + 10, sy + 7, COLOR_MUTED, false);
        scaledText(g, Component.literal(String.format(java.util.Locale.ROOT, "x%.1f", punch)), PAD + 10, sy + 19,
                1.5F, furious ? 0xFFFF5A3C : accent());
        Component dmg = Component.translatable("gui.flash.hulk.punch_damage",
                String.format(java.util.Locale.ROOT, "%.1f", punch),
                String.format(java.util.Locale.ROOT, "%.1f", punch / 2.0D));
        g.drawString(this.font, dmg, PAD + LEFT_W - 10 - this.font.width(dmg), sy + 9, COLOR_TEXT, false);
        Component note = Component.translatable(furious ? "gui.flash.hulk.strength_fury"
                : form ? "gui.flash.hulk.strength_level" : "gui.flash.hulk.strength_banner", active);
        g.drawString(this.font, this.font.plainSubstrByWidth(note.getString(), LEFT_W - 90),
                PAD + LEFT_W - 10 - Math.min(LEFT_W - 90, this.font.width(note)), sy + 22, COLOR_MUTED, false);

        // rage = progress to the next level
        int y = PROGRESS_Y;
        card(g, PAD, y, LEFT_W, CONTENT_BOTTOM - y, 0.0F);
        if (this.shownProgress < 0.0F) {
            this.shownProgress = 0.0F;
        }
        this.shownProgress += (rage - this.shownProgress) * Math.min(1.0F, this.dt * 5.0F);
        boolean maxed = level >= com.flash.hulk.HulkPowers.MAX_LEVEL;
        g.drawString(this.font, Component.translatable(temp > 0 ? "gui.flash.hulk.temporary"
                        : maxed ? "gui.flash.hulk.maxed" : "gui.flash.hulk.next", level + 1),
                PAD + 10, y + 8, COLOR_TEXT, false);
        Component pct = Component.literal(Math.round(rage * 100.0F) + "%");
        g.drawString(this.font, pct, PAD + LEFT_W - 10 - this.font.width(pct), y + 8, accent(), false);
        drawBar(g, PAD + 10, y + 22, LEFT_W - 20, this.shownProgress, rage > 0.85F ? 0xFFFF5A3C : accent());
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.flash.hulk.rage_hint").getString(), LEFT_W - 20),
                PAD + 10, y + 36, COLOR_MUTED, false);

        // abilities (passives included), with level, key and cooldown
        sectionTitle(g, Component.translatable("gui.flash.progress.skills"), RIGHT_X, CONTENT_Y);
        int top = CONTENT_Y + 14;
        String[] passives = {"jump", "skin"};
        int[] passiveLevels = {1, 9};
        Component tooltip = null;
        int row = 0;
        int total = com.flash.hulk.HulkAbility.values().length + passives.length;
        for (int k = 0; k < total; k++) {
            int ry = top + row * SKILL_ROW;
            int rh = SKILL_ROW - 2;
            Component name;
            Component desc;
            int req;
            Component right;
            int cooldown = 0;
            if (k < 2) {
                name = Component.translatable("ability.flash.hulk." + passives[k]);
                desc = Component.translatable("ability.flash.hulk." + passives[k] + ".description");
                req = passiveLevels[k];
                right = Component.translatable("gui.flash.hulk.passive");
            } else {
                com.flash.hulk.HulkAbility a = com.flash.hulk.HulkAbility.values()[k - 2];
                name = a.displayName();
                desc = a.description();
                req = a.level();
                cooldown = com.flash.hulk.HulkState.cooldown(a, now);
                net.minecraft.client.KeyMapping key = FlashAbilityKeys.mapping(a.keySlot());
                right = cooldown > 0 ? Component.literal(formatTime(cooldown))
                        : key != null ? key.getTranslatedKeyMessage() : Component.empty();
            }
            boolean unlocked = level >= req;
            if (!unlocked) {
                right = Component.translatable("gui.flash.progress.level_short", req);
            }
            boolean hovered = inside(mx, my, RIGHT_X, ry, RIGHT_W, rh);
            float hv = hover("hsk" + k, hovered);
            card(g, RIGHT_X, ry, RIGHT_W, rh, hv);
            if (unlocked) {
                drawCheck(g, RIGHT_X + 6, ry + 6, accent());
            } else {
                drawLock(g, RIGHT_X + 6, ry + 5, COLOR_DIM);
            }
            int rw = this.font.width(right);
            g.drawString(this.font, this.font.plainSubstrByWidth(name.getString(), RIGHT_W - 30 - rw),
                    RIGHT_X + 18, ry + 6, unlocked ? COLOR_TEXT : COLOR_DIM, false);
            g.drawString(this.font, right, RIGHT_X + RIGHT_W - 6 - rw, ry + 6,
                    cooldown > 0 ? COLOR_ORANGE : unlocked ? accent() : COLOR_DIM, false);
            if (hovered) {
                tooltip = desc;
            }
            row++;
        }
        if (tooltip != null) {
            g.renderTooltip(this.font, tooltip, mx, my);
        }
    }

    // ------------------------------------------------------------------
    // Hulk options
    // ------------------------------------------------------------------

    private static final int HEIGHT_ROW = 2;
    /** True while the height bar knob is being dragged. */
    private boolean draggingHeight;

    private void drawHulkConfig(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.hulk.cfg.destroy", "gui.flash.hulk.cfg.destroy.desc", mx, my);
        drawSwitch(g, "hsw0", switchX(), cfgY(0) + 8, com.flash.hulk.HulkState.destroyBlocks());
        configRow(g, 1, "gui.flash.hulk.cfg.grow", "gui.flash.hulk.cfg.grow.desc", mx, my);
        drawSwitch(g, "hsw1", switchX(), cfgY(1) + 8, com.flash.hulk.HulkState.grow());
        configRow(g, HEIGHT_ROW, "gui.flash.hulk.cfg.height", "gui.flash.hulk.cfg.height.desc", mx, my);
        drawHeightBar(g);
        configRow(g, 3, "gui.flash.hulk.cfg.level_mode", "gui.flash.hulk.cfg.level_mode.desc", mx, my);
        drawSegments(g, "hseg3", cfgY(3) + 7, "gui.flash.config.mode_keys", "gui.flash.config.mode_scroll",
                FlashClientConfig.SCROLL_MODE.get(), mx, my);
        configRow(g, 4, "gui.flash.config.hud", "gui.flash.hulk.cfg.hud.desc", mx, my);
        drawSwitch(g, "hsw4", switchX(), cfgY(4) + 8, FlashClientConfig.SHOW_ABILITY_HUD.get());
    }

    private static int heightBarX() {
        return segX(0);
    }

    private static int heightBarW() {
        return 2 * SEG_W;
    }

    private static int heightBarY() {
        return cfgY(HEIGHT_ROW) + 22;
    }

    private static float heightMax() {
        return com.flash.hulk.HulkPowers.maxHeight(Math.max(1, com.flash.hulk.HulkState.level()));
    }

    private static float shownHeight() {
        float h = com.flash.hulk.HulkState.height();
        if (h <= 0.0F) {
            h = com.flash.hulk.HulkPowers.MIN_SCALE * 1.8F;
        }
        return Math.max(com.flash.hulk.HulkPowers.MIN_HEIGHT, Math.min(heightMax(), h));
    }

    /** Height bar: from 2 blocks up to the tallest the level allows; only used with "grow" off. */
    private void drawHeightBar(GuiGraphics g) {
        boolean enabled = !com.flash.hulk.HulkState.grow();
        int x = heightBarX();
        int w = heightBarW();
        int y = heightBarY();
        float min = com.flash.hulk.HulkPowers.MIN_HEIGHT;
        float max = heightMax();
        float value = shownHeight();
        float t = max - min < 1.0E-3F ? 1.0F : (value - min) / (max - min);
        int knob = x + Math.round((w - 6) * t);
        g.fill(x, y, x + w, y + 4, COLOR_TRACK);
        if (enabled) {
            for (int i = 0; i < knob - x + 3; i++) {
                float f = i / (float) Math.max(1, w);
                g.fill(x + i, y, x + i + 1, y + 4, lerpColor(FlashHudStyle.HULK_GREEN, FlashHudStyle.HULK_PURPLE, f));
            }
        }
        g.fill(knob, y - 4, knob + 6, y + 8, enabled ? 0xFFFFFFFF : COLOR_DIM);
        String label = enabled
                ? Component.translatable("gui.flash.hulk.height_value", String.format(java.util.Locale.ROOT, "%.1f", value)).getString()
                : Component.translatable("gui.flash.hulk.cfg.height_auto").getString();
        g.drawString(this.font, label, x + w - this.font.width(label), cfgY(HEIGHT_ROW) + 7,
                enabled ? accent() : COLOR_DIM, false);
    }

    private boolean onHeightBar(int mx, int my) {
        return mx >= heightBarX() - 4 && mx <= heightBarX() + heightBarW() + 4
                && my >= heightBarY() - 7 && my <= heightBarY() + 11;
    }

    private static void setHeightFromMouse(double mx) {
        float t = (float) ((mx - heightBarX() - 3) / (heightBarW() - 6));
        t = Math.max(0.0F, Math.min(1.0F, t));
        float min = com.flash.hulk.HulkPowers.MIN_HEIGHT;
        float value = min + (heightMax() - min) * t;
        value = Math.round(value * 10.0F) / 10.0F;
        com.flash.hulk.HulkState.setSettingsOptimistic(com.flash.hulk.HulkState.destroyBlocks(),
                com.flash.hulk.HulkState.grow(), value);
    }

    private static void sendHulkSettings() {
        FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkSettingsPacket(
                com.flash.hulk.HulkState.destroyBlocks(), com.flash.hulk.HulkState.grow(), shownHeight()));
    }

    private boolean clickHulkConfig(int mx, int my) {
        boolean destroy = com.flash.hulk.HulkState.destroyBlocks();
        boolean grow = com.flash.hulk.HulkState.grow();
        if (inRow(mx, my, 0)) {
            destroy = !destroy;
        } else if (inRow(mx, my, 1)) {
            grow = !grow;
        } else if (inRow(mx, my, HEIGHT_ROW)) {
            if (!grow && onHeightBar(mx, my)) {
                draggingHeight = true;
                setHeightFromMouse(mx);
            }
            return true;
        } else if (inRow(mx, my, 3)) {
            Boolean pick = segmentAt(mx, my, cfgY(3) + 7);
            boolean want = pick != null ? pick : !FlashClientConfig.SCROLL_MODE.get();
            if (FlashClientConfig.SCROLL_MODE.get() != want) {
                FlashClientConfig.SCROLL_MODE.set(want);
                FlashClientConfig.SCROLL_MODE.save();
                click();
            }
            return true;
        } else if (inRow(mx, my, 4)) {
            FlashClientConfig.toggle(FlashClientConfig.SHOW_ABILITY_HUD);
            click();
            return true;
        } else {
            return false;
        }
        com.flash.hulk.HulkState.setSettingsOptimistic(destroy, grow, 0.0F);
        sendHulkSettings();
        click();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingHeight && button == 0) {
            setHeightFromMouse(mouseX / this.uiScale - this.leftPos);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingHeight && button == 0) {
            draggingHeight = false;
            sendHulkSettings();
            click();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Pixel fist (about 26 x 26). */
    private static void drawFist(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 4, y + 2, x + 24, y + 8, c);
        g.fill(x + 2, y + 8, x + 26, y + 20, c);
        g.fill(x + 6, y + 20, x + 22, y + 28, c);
        int gap = 0xFF0D1F0B;
        g.fill(x + 9, y + 2, x + 10, y + 9, gap);
        g.fill(x + 14, y + 2, x + 15, y + 9, gap);
        g.fill(x + 19, y + 2, x + 20, y + 9, gap);
        g.fill(x + 2, y + 12, x + 9, y + 13, gap);
    }

    private static Component multiplier(int level) {
        return Component.literal(FlashSpeedLevels.multiplierText(level));
    }

    private void sectionTitle(GuiGraphics g, Component text, int x, int y) {
        g.fill(x, y + 2, x + 2, y + 7, accent());
        g.drawString(this.font, text, x + 6, y, COLOR_TEXT, false);
    }

    /** Neutral screen shown while the player has no power. */
    private void drawEmptyState(GuiGraphics g) {
        int cx = PW / 2;
        int cy = (CONTENT_Y + CONTENT_BOTTOM) / 2;
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds() * 2.0F);
        drawNeutralIcon(g, cx - 16, cy - 62, lerpColor(COLOR_DIM, COLOR_MUTED, pulse * 0.6F));
        Component title = Component.translatable("gui.flash.empty.title");
        Component hint = Component.translatable("gui.flash.empty.hint");
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx - this.font.width(title) * 1.4F / 2.0F, cy - 14, 0.0F);
        pose.scale(1.4F, 1.4F, 1.0F);
        g.drawString(this.font, title, 0, 0, COLOR_MUTED, false);
        pose.popPose();
        g.drawString(this.font, hint, cx - this.font.width(hint) / 2, cy + 8, COLOR_DIM, false);
    }

    private void statCard(GuiGraphics g, int x, int y, int w, int h, Component label, Component value,
                          Component extra) {
        card(g, x, y, w, h, 0.0F);
        g.fill(x, y + 6, x + 2, y + h - 6, accent());
        g.drawString(this.font, label, x + 10, y + 7, COLOR_MUTED, false);
        scaledText(g, value, x + 10, y + 20, 1.3F, COLOR_TEXT);
        g.drawString(this.font, extra, x + w - 8 - this.font.width(extra), y + 24, accent(), false);
    }

    /** Rounded-looking card (corners cut by one pixel), brighter when {@code highlight} > 0. */
    private static void card(GuiGraphics g, int x, int y, int w, int h, float highlight) {
        int border = lerpColor(COLOR_BORDER, 0xFF3A4250, highlight);
        g.fill(x + 1, y, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, lerpColor(COLOR_CARD, COLOR_CARD_HI, highlight));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x0FFFFFFF);
    }

    /** Levels on the grid: 0..10, up to 20 with the amplifier, and whatever the V-9 adds on top. */
    private static int maxShownLevel() {
        int base = FlashClientRing.isLocalAmplifier() ? FlashSpeedLevels.AMP_MAX_LEVEL : FlashSpeedLevels.BASE_MAX_LEVEL;
        return Math.min(FlashSpeedLevels.MAX_LEVEL, Math.max(base, FlashClientData.getMaxLevel()));
    }

    private static int gridColumns() {
        int count = maxShownLevel() + 1;
        return count <= 12 ? 6 : count <= 21 ? 7 : 9;
    }

    private static int gridRows() {
        int count = maxShownLevel() + 1;
        return (count + gridColumns() - 1) / gridColumns();
    }

    private static int cardW() {
        int cols = gridColumns();
        return (LEFT_W - GRID_GAP * (cols - 1)) / cols;
    }

    private static int cardH() {
        int rows = gridRows();
        int available = PROGRESS_Y - 10 - GRID_Y;
        return Math.min(42, (available - GRID_GAP * (rows - 1)) / rows);
    }

    /** Few rows: the grid is centred in the space above the progress card. */
    private static int gridTop() {
        int rows = gridRows();
        int used = rows * cardH() + (rows - 1) * GRID_GAP;
        return GRID_Y + Math.max(0, (PROGRESS_Y - 10 - GRID_Y - used) / 2);
    }

    private static int cardX(int level) {
        return PAD + (level % gridColumns()) * (cardW() + GRID_GAP);
    }

    private static int cardY(int level) {
        return gridTop() + (level / gridColumns()) * (cardH() + GRID_GAP);
    }

    private void drawLevelGrid(GuiGraphics g, int mx, int my) {
        int max = FlashClientData.getMaxLevel();
        int selected = FlashClientData.getSelectedLevel();
        int w = cardW();
        int h = cardH();
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds() * 3.2F);

        for (int level = FlashSpeedLevels.MIN_LEVEL; level <= maxShownLevel(); level++) {
            int cx = cardX(level);
            int cy = cardY(level);
            boolean unlocked = level <= max;
            boolean inUse = level == selected;
            float hv = hover("lvl" + level, unlocked && !inUse && inside(mx, my, cx, cy, w, h));
            int lift = Math.round(hv);
            cy -= lift;

            if (inUse) {
                // pulsing halo around the level in use
                int halo = withAlpha(accent(), 0.25F + 0.35F * pulse);
                g.fill(cx - 2, cy - 2, cx + w + 2, cy + h + 2, halo);
                g.fill(cx - 1, cy - 1, cx + w + 1, cy + h + 1, accent());
                g.fill(cx, cy, cx + w, cy + h, accentSoft());
            } else {
                card(g, cx, cy, w, h, hv);
                if (unlocked) {
                    g.fill(cx + 1, cy + h - 2, cx + w - 1, cy + h - 1, withAlpha(COLOR_GREEN, 0.35F + 0.4F * hv));
                }
            }

            Component number = Component.literal(Integer.toString(level));
            float scale = w >= 44 ? 1.5F : 1.25F;
            int nw = Math.round(this.font.width(number) * scale);
            int color = inUse ? accent() : unlocked ? lerpColor(COLOR_TEXT, 0xFFFFFFFF, hv) : COLOR_DIM;
            scaledText(g, number, cx + (w - nw) / 2, cy + (h - Math.round(8 * scale)) / 2, scale, color);
            if (!unlocked) {
                drawLock(g, cx + w - 10, cy + 3, COLOR_DIM);
            } else if (!inUse) {
                g.fill(cx + w - 7, cy + 4, cx + w - 4, cy + 7, COLOR_GREEN);
            }
        }
    }

    private void drawProgress(GuiGraphics g) {
        boolean maxed = FlashClientData.getMaxLevel() >= FlashSpeedLevels.BASE_MAX_LEVEL;
        int y = PROGRESS_Y;
        card(g, PAD, y, LEFT_W, CONTENT_BOTTOM - y, 0.0F);
        int ix = PAD + 10;
        int iw = LEFT_W - 20;

        float fraction = maxed ? 1.0F : (float) Math.min(1.0D,
                FlashClientData.getDistance() / (double) FlashSpeedLevels.BLOCKS_PER_LEVEL);
        if (this.shownProgress < 0.0F) {
            this.shownProgress = 0.0F;
        }
        this.shownProgress += (fraction - this.shownProgress) * Math.min(1.0F, this.dt * 5.0F);

        if (maxed) {
            g.drawString(this.font, Component.translatable("gui.flash.progress.maxed"), ix, y + 8, COLOR_GREEN, false);
            drawBar(g, ix, y + 22, iw, this.shownProgress, COLOR_GREEN);
            g.drawString(this.font, Component.translatable("gui.flash.progress.all_unlocked"),
                    ix, y + 36, COLOR_MUTED, false);
            return;
        }
        int next = FlashClientData.getMaxLevel() + 1;
        g.drawString(this.font, Component.translatable("gui.flash.progress.next", next), ix, y + 8, COLOR_TEXT, false);
        Component percent = Component.literal(Math.round(fraction * 100.0F) + "%");
        g.drawString(this.font, percent, ix + iw - this.font.width(percent), y + 8, accent(), false);
        drawBar(g, ix, y + 22, iw, this.shownProgress, accent());

        int travelled = Math.min(FlashSpeedLevels.BLOCKS_PER_LEVEL, (int) Math.floor(FlashClientData.getDistance()));
        int remaining = Math.max(0, FlashSpeedLevels.BLOCKS_PER_LEVEL - travelled);
        g.drawString(this.font, Component.translatable("gui.flash.progress.blocks",
                number(travelled), number(FlashSpeedLevels.BLOCKS_PER_LEVEL)), ix, y + 36, COLOR_MUTED, false);
        Component rest = Component.translatable("gui.flash.progress.remaining", number(remaining));
        g.drawString(this.font, rest, ix + iw - this.font.width(rest), y + 36, COLOR_MUTED, false);
    }

    private void drawBar(GuiGraphics g, int x, int y, int w, float fraction, int color) {
        int h = 8;
        g.fill(x, y, x + w, y + h, COLOR_TRACK);
        int filled = Math.round(w * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + h, color, darker(color));
            g.fill(x, y, x + filled, y + 1, 0x40FFFFFF);
            // a light sweeping along the filled part
            float sweep = (seconds() * 0.6F) % 1.0F;
            int sx = x + Math.round(filled * sweep);
            g.fill(Math.max(x, sx - 6), y, Math.min(x + filled, sx + 6), y + h, 0x30FFFFFF);
        }
    }

    private void drawSkills(GuiGraphics g, int mx, int my) {
        sectionTitle(g, Component.translatable("gui.flash.progress.skills"), RIGHT_X, CONTENT_Y);
        FlashAbility[] abilities = FlashAbility.values();
        int top = CONTENT_Y + 14;
        FlashAbility tooltip = null;
        for (int i = 0; i < abilities.length; i++) {
            FlashAbility ability = abilities[i];
            int ry = top + i * SKILL_ROW;
            int rh = SKILL_ROW - 2;
            boolean unlocked = FlashClientData.getMaxLevel() >= ability.requiredLevel();
            boolean hovered = inside(mx, my, RIGHT_X, ry, RIGHT_W, rh);
            float hv = hover("skill" + i, hovered);
            card(g, RIGHT_X, ry, RIGHT_W, rh, hv);
            if (hv > 0.0F) {
                g.fill(RIGHT_X, ry + 3, RIGHT_X + 2, ry + rh - 3, withAlpha(accent(), hv));
            }
            if (unlocked) {
                drawCheck(g, RIGHT_X + 6, ry + 6, COLOR_GREEN);
            } else {
                drawLock(g, RIGHT_X + 6, ry + 5, COLOR_DIM);
            }
            int cooldown = FlashClientData.getAbilityCooldown(ability.ordinal());
            Component right = cooldown > 0 ? Component.literal(formatTime(cooldown))
                    : unlocked ? Component.empty()
                    : Component.translatable("gui.flash.progress.level_short", ability.requiredLevel());
            int rightW = this.font.width(right);
            g.drawString(this.font, this.font.plainSubstrByWidth(ability.displayName().getString(),
                            RIGHT_W - 26 - rightW - 4),
                    RIGHT_X + 18, ry + 6, unlocked ? COLOR_TEXT : COLOR_DIM, false);
            if (rightW > 0) {
                g.drawString(this.font, right, RIGHT_X + RIGHT_W - 6 - rightW, ry + 6,
                        cooldown > 0 ? COLOR_ORANGE : COLOR_DIM, false);
            }
            if (hovered) {
                tooltip = ability;
            }
        }
        if (tooltip != null) {
            g.renderTooltip(this.font, tooltip.description(), mx, my);
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
        drawSwitch(g, "sw0", switchX(), cfgY(0) + 8, FlashClientData.isDestroyBlocks());

        configRow(g, 1, "gui.flash.config.speed_mode", "gui.flash.config.speed_mode.desc", mx, my);
        boolean scroll = FlashClientConfig.SCROLL_MODE.get();
        drawSegments(g, "seg1", cfgY(1) + 7, "gui.flash.config.mode_keys", "gui.flash.config.mode_scroll",
                scroll, mx, my);

        configRow(g, 2, "gui.flash.config.wall_run", "gui.flash.config.wall_run.desc", mx, my);
        drawSwitch(g, "sw2", switchX(), cfgY(2) + 8, FlashClientConfig.WALL_RUN.get());

        configRow(g, 3, "gui.flash.config.hud", "gui.flash.config.hud.desc", mx, my);
        drawSwitch(g, "sw3", switchX(), cfgY(3) + 8, FlashClientConfig.SHOW_ABILITY_HUD.get());

        configRow(g, 4, "gui.flash.config.body_lightning", "gui.flash.config.body_lightning.desc", mx, my);
        drawSwitch(g, "sw4", switchX(), cfgY(4) + 8, FlashClientConfig.BODY_LIGHTNING.get());

        configRow(g, 5, "gui.flash.config.mouth", "gui.flash.config.mouth.desc", mx, my);
        boolean open = FlashClientConfig.MASK_OPEN_MOUTH.get();
        drawSegments(g, "seg5", cfgY(5) + 7, "gui.flash.config.mouth_closed", "gui.flash.config.mouth_open",
                open, mx, my);
    }

    private static final int SWITCH_W = 40;
    private static final int SWITCH_H = 18;
    private static final int SEG_W = 104;
    private static final int SEG_H = 20;

    private static int switchX() {
        return PW - PAD - 12 - SWITCH_W;
    }

    private static int segX(int index) {
        return PW - PAD - 12 - 2 * SEG_W + index * SEG_W;
    }

    private void configRow(GuiGraphics g, int row, String titleKey, String descKey, int mx, int my) {
        int y = cfgY(row);
        float hv = hover("cfg" + row, inside(mx, my, PAD, y, PW - 2 * PAD, CFG_ROW_H));
        card(g, PAD, y, PW - 2 * PAD, CFG_ROW_H, hv);
        g.fill(PAD, y + 6, PAD + 2, y + CFG_ROW_H - 6, lerpColor(withAlpha(accent(), 0.5F), accent(), hv));
        g.drawString(this.font, Component.translatable(titleKey), PAD + 12, y + 7, COLOR_TEXT, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(descKey).getString(),
                        PW - 2 * PAD - 2 * SEG_W - 40), PAD + 12, y + 19, COLOR_MUTED, false);
    }

    /** Toggle switch whose knob slides to the new side. */
    private void drawSwitch(GuiGraphics g, String key, int x, int y, boolean on) {
        float t = ease(key, on, 12.0F);
        int track = lerpColor(COLOR_TRACK, accentDark(), t);
        g.fill(x + 1, y, x + SWITCH_W - 1, y + SWITCH_H, lerpColor(COLOR_BORDER, accent(), t));
        g.fill(x, y + 1, x + SWITCH_W, y + SWITCH_H - 1, lerpColor(COLOR_BORDER, accent(), t));
        g.fill(x + 1, y + 1, x + SWITCH_W - 1, y + SWITCH_H - 1, track);
        int knobSize = SWITCH_H - 6;
        int kx = x + 3 + Math.round((SWITCH_W - 6 - knobSize) * t);
        g.fill(kx, y + 3, kx + knobSize, y + 3 + knobSize, lerpColor(COLOR_DIM, 0xFFFFFFFF, t));
    }

    /** Two-option selector with a sliding highlight. */
    private void drawSegments(GuiGraphics g, String key, int y, String leftKey, String rightKey,
                              boolean right, int mx, int my) {
        float t = ease(key, right, 14.0F);
        int x0 = segX(0);
        g.fill(x0, y, x0 + 2 * SEG_W, y + SEG_H, COLOR_TRACK);
        int hx = x0 + Math.round(SEG_W * t);
        g.fill(hx + 1, y + 1, hx + SEG_W - 1, y + SEG_H - 1, accentDark());
        g.fill(hx + 1, y + SEG_H - 2, hx + SEG_W - 1, y + SEG_H - 1, accent());
        String[] keys = {leftKey, rightKey};
        for (int i = 0; i < 2; i++) {
            int sx = segX(i);
            boolean active = (i == 1) == right;
            float hv = hover(key + i, !active && inside(mx, my, sx, y, SEG_W, SEG_H));
            String label = this.font.plainSubstrByWidth(Component.translatable(keys[i]).getString(), SEG_W - 8);
            int color = active ? accent() : lerpColor(COLOR_MUTED, COLOR_TEXT, hv);
            g.drawString(this.font, label, sx + (SEG_W - this.font.width(label)) / 2, y + 6, color, false);
        }
    }

    // ------------------------------------------------------------------
    // Equipment tab (ring + amplifier)
    // ------------------------------------------------------------------

    private static final int EQ_W = 344;
    private static final int RING_Y = CONTENT_Y;
    private static final int RING_H = 112;
    private static final int AMP_Y = RING_Y + RING_H + 8;
    private static final int AMP_H = CONTENT_BOTTOM - AMP_Y;
    private static final int SLOT = 52;
    private static final int EQ_TEXT_X = PAD + 12 + SLOT + 12;
    private static final int BTN_H = 20;
    private static final int BTN_W = (EQ_W - (EQ_TEXT_X - PAD) - 12 - 6) / 2;

    private static int ringBtnX() {
        return EQ_TEXT_X;
    }

    private static int suitBtnX() {
        return EQ_TEXT_X + BTN_W + 6;
    }

    private static int ringBtnY() {
        return RING_Y + 46;
    }

    private static int ampBtnY() {
        return AMP_Y + 46;
    }

    private static boolean wearsArmor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        for (net.minecraft.world.item.ItemStack stack : mc.player.getArmorSlots()) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void drawEquipTab(GuiGraphics g, int mx, int my) {
        boolean ring = FlashClientRing.isLocalEquipped();
        boolean amp = FlashClientRing.isLocalAmplifier();

        // ring card
        card(g, PAD, RING_Y, EQ_W, RING_H, 0.0F);
        slot(g, PAD + 12, RING_Y + 12, ring, com.flash.registry.ModItems.FLASH_RING.get());
        g.drawString(this.font, Component.translatable("gui.flash.equip.ring_slot"), EQ_TEXT_X, RING_Y + 14,
                COLOR_TEXT, false);
        statusText(g, EQ_TEXT_X, RING_Y + 27, ring);
        button(g, "ringBtn", ringBtnX(), ringBtnY(), BTN_W, BTN_H,
                Component.translatable(ring ? "gui.flash.equip.remove_ring" : "gui.flash.equip.put_ring"),
                true, mx, my);
        button(g, "suitBtn", suitBtnX(), ringBtnY(), BTN_W, BTN_H,
                Component.translatable(wearsArmor() ? "gui.flash.equip.take_off_suit" : "gui.flash.equip.put_on_suit"),
                ring, mx, my);
        Component key = Component.translatable("gui.flash.equip.key", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage());
        g.drawString(this.font, key, PAD + 12, RING_Y + 74, accent(), false);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.flash.equip.help2").getString(), EQ_W - 24),
                PAD + 12, RING_Y + 88, COLOR_DIM, false);

        // amplifier card
        card(g, PAD, AMP_Y, EQ_W, AMP_H, 0.0F);
        slot(g, PAD + 12, AMP_Y + 12, amp, com.flash.registry.ModItems.TACHYON_AMPLIFIER.get());
        g.drawString(this.font, Component.translatable("gui.flash.equip.amplifier_slot"), EQ_TEXT_X, AMP_Y + 14,
                COLOR_TEXT, false);
        statusText(g, EQ_TEXT_X, AMP_Y + 27, amp);
        button(g, "ampBtn", EQ_TEXT_X, ampBtnY(), 2 * BTN_W + 6, BTN_H,
                Component.translatable(amp ? "gui.flash.equip.remove_amplifier" : "gui.flash.equip.put_amplifier"),
                true, mx, my);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.flash.equip.amplifier_help").getString(), EQ_W - 24),
                PAD + 12, AMP_Y + 74, COLOR_DIM, false);

        // live preview of the player (ring on the hand, amplifier on the chest)
        int vx = PAD + EQ_W + 10;
        int vw = PW - PAD - vx;
        card(g, vx, CONTENT_Y, vw, CONTENT_BOTTOM - CONTENT_Y, 0.0F);
        g.fillGradient(vx + 1, CONTENT_Y + 1, vx + vw - 1, CONTENT_BOTTOM - 1,
                withAlpha(accentDark(), 0.8F), 0x00000000);
        g.fill(vx + 10, CONTENT_BOTTOM - 26, vx + vw - 10, CONTENT_BOTTOM - 25, withAlpha(accent(), 0.4F));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int px = vx + vw / 2;
            int py = CONTENT_BOTTOM - 26;
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, px, py, 80, (float) (px - mx), (float) (py - 120 - my), mc.player);
        }
    }

    private void slot(GuiGraphics g, int x, int y, boolean filled, net.minecraft.world.item.Item item) {
        float t = ease("slot" + item.hashCode(), filled, 10.0F);
        g.fill(x, y, x + SLOT, y + SLOT, lerpColor(COLOR_BORDER, accent(), t));
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, COLOR_TRACK);
        g.fillGradient(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, withAlpha(accentSoft(), t), 0x00000000);
        if (filled) {
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(x + SLOT / 2.0F - 20.0F, y + SLOT / 2.0F - 20.0F, 0.0F);
            pose.scale(2.5F, 2.5F, 1.0F);
            g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
            pose.popPose();
        } else {
            Component empty = Component.translatable("gui.flash.equip.empty");
            g.drawString(this.font, empty, x + (SLOT - this.font.width(empty)) / 2, y + SLOT / 2 - 4, COLOR_DIM, false);
        }
    }

    private void statusText(GuiGraphics g, int x, int y, boolean on) {
        g.fill(x, y + 2, x + 4, y + 6, on ? COLOR_GREEN : COLOR_DIM);
        g.drawString(this.font, Component.translatable(on ? "gui.flash.equip.status_on" : "gui.flash.equip.empty"),
                x + 8, y, on ? COLOR_GREEN : COLOR_MUTED, false);
    }

    private void button(GuiGraphics g, String key, int x, int y, int w, int h, Component label,
                        boolean enabled, int mx, int my) {
        float hv = hover(key, enabled && inside(mx, my, x, y, w, h));
        if (!enabled) {
            g.fill(x, y, x + w, y + h, COLOR_BORDER);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_TRACK);
        } else {
            g.fill(x, y, x + w, y + h, lerpColor(accentDark(), accent(), hv));
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, lerpColor(accentSoft(), accentDark(), hv));
        }
        String text = this.font.plainSubstrByWidth(label.getString(), w - 8);
        g.drawString(this.font, text, x + (w - this.font.width(text)) / 2, y + (h - 8) / 2,
                enabled ? lerpColor(accent(), COLOR_TEXT, hv) : COLOR_DIM, false);
    }

    private boolean clickEquip(double mx, double my) {
        int ix = (int) mx;
        int iy = (int) my;
        if (inside(ix, iy, ringBtnX(), ringBtnY(), BTN_W, BTN_H)) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                    FlashClientRing.isLocalEquipped()
                            ? com.flash.network.FlashRingPacket.UNEQUIP
                            : com.flash.network.FlashRingPacket.EQUIP));
            click();
            return true;
        }
        if (FlashClientRing.isLocalEquipped() && inside(ix, iy, suitBtnX(), ringBtnY(), BTN_W, BTN_H)) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                    com.flash.network.FlashRingPacket.TOGGLE_SUIT));
            click();
            return true;
        }
        if (inside(ix, iy, EQ_TEXT_X, ampBtnY(), 2 * BTN_W + 6, BTN_H)) {
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.FlashRingPacket(
                    FlashClientRing.isLocalAmplifier()
                            ? com.flash.network.FlashRingPacket.AMP_UNEQUIP
                            : com.flash.network.FlashRingPacket.AMP_EQUIP));
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

    /** Neutral emblem: an empty ring (32 px). */
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

    /** Pixel lightning bolt (about 22 x 30). */
    private static void drawBolt(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 12, y, x + 22, y + 6, c);
        g.fill(x + 9, y + 6, x + 19, y + 11, c);
        g.fill(x + 3, y + 11, x + 19, y + 16, c);
        g.fill(x + 9, y + 16, x + 19, y + 21, c);
        g.fill(x + 6, y + 21, x + 16, y + 26, c);
        g.fill(x + 3, y + 26, x + 11, y + 30, c);
    }

    private static void drawCheck(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 1, y + 4, x + 3, y + 6, c);
        g.fill(x + 2, y + 5, x + 4, y + 7, c);
        g.fill(x + 3, y + 4, x + 5, y + 6, c);
        g.fill(x + 4, y + 2, x + 6, y + 5, c);
        g.fill(x + 5, y + 1, x + 7, y + 3, c);
    }

    private static void drawLock(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 1, y + 4, x + 7, y + 9, c);
        g.fill(x + 2, y + 1, x + 3, y + 4, c);
        g.fill(x + 5, y + 1, x + 6, y + 4, c);
        g.fill(x + 2, y, x + 6, y + 1, c);
        g.fill(x + 3, y + 6, x + 5, y + 8, 0xFF101216);
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
        int ix = (int) mx;
        int iy = (int) my;

        int[] visible = visibleTabs();
        for (int i = 0; i < visible.length; i++) {
            if (inside(ix, iy, tabLeft(i), TAB_Y, tabWidth(visible[i]), TAB_H)) {
                if (tab != visible[i]) {
                    tab = visible[i];
                    click();
                }
                return true;
            }
        }

        if (activeTab() == TAB_EQUIP) {
            return clickEquip(mx, my) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localHulk()) {
            return clickHulkConfig(ix, iy) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG) {
            if (inRow(ix, iy, 0)) {
                boolean enabled = !FlashClientData.isDestroyBlocks();
                FlashClientData.setDestroyBlocksOptimistic(enabled);
                FlashNetwork.CHANNEL.sendToServer(new FlashSettingsPacket(enabled));
                click();
                return true;
            }
            if (inRow(ix, iy, 1)) {
                Boolean pick = segmentAt(ix, iy, cfgY(1) + 7);
                if (pick != null && FlashClientConfig.SCROLL_MODE.get() != pick) {
                    FlashClientConfig.SCROLL_MODE.set(pick);
                    FlashClientConfig.SCROLL_MODE.save();
                    click();
                } else if (pick == null) {
                    FlashClientConfig.SCROLL_MODE.set(!FlashClientConfig.SCROLL_MODE.get());
                    FlashClientConfig.SCROLL_MODE.save();
                    click();
                }
                return true;
            }
            if (inRow(ix, iy, 5)) {
                Boolean pick = segmentAt(ix, iy, cfgY(5) + 7);
                boolean want = pick != null ? pick : !FlashClientConfig.MASK_OPEN_MOUTH.get();
                if (FlashClientConfig.MASK_OPEN_MOUTH.get() != want) {
                    FlashClientConfig.MASK_OPEN_MOUTH.set(want);
                    FlashClientConfig.MASK_OPEN_MOUTH.save();
                    click();
                }
                return true;
            }
            ForgeConfigSpec.BooleanValue[] toggles = {
                    null, null, FlashClientConfig.WALL_RUN,
                    FlashClientConfig.SHOW_ABILITY_HUD, FlashClientConfig.BODY_LIGHTNING};
            for (int row = 2; row <= 4; row++) {
                if (inRow(ix, iy, row)) {
                    FlashClientConfig.toggle(toggles[row]);
                    click();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int level = levelAt(ix, iy);
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

    /** Which half of a two-option selector was clicked: false = left, true = right, null = neither. */
    private static Boolean segmentAt(int mx, int my, int y) {
        if (my < y || my >= y + SEG_H) {
            return null;
        }
        if (inside(mx, my, segX(0), y, SEG_W, SEG_H)) {
            return Boolean.FALSE;
        }
        if (inside(mx, my, segX(1), y, SEG_W, SEG_H)) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static boolean inRow(int mx, int my, int row) {
        return inside(mx, my, PAD, cfgY(row), PW - 2 * PAD, CFG_ROW_H);
    }

    private static int levelAt(int mx, int my) {
        if (!FlashClientData.hasPowers()) {
            return -1;
        }
        for (int level = FlashSpeedLevels.MIN_LEVEL; level <= maxShownLevel(); level++) {
            if (inside(mx, my, cardX(level), cardY(level), cardW(), cardH())) {
                return level;
            }
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        // Only the active control mode works: keys mode here, Ctrl+scroll otherwise.
        if (FlashClientForgeEvents.selectorAvailable() && !FlashClientConfig.SCROLL_MODE.get()) {
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
                && FlashClientForgeEvents.selectorAvailable()
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

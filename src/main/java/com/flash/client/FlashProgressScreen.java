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
 * K screen, the hero console: a sidebar on the left (the hero's emblem turning in its rings, his name and state, the
 * pages to pick from, the hints and credits) beside the page, all over a living backdrop (drifting light, a grid, a
 * sweep of light, motes rising). Cards of dark glass with lit corners, switches and selectors as glowing pills.
 *
 * <p>(Older notes:) Three tabs (Progress, Equipment, Config) on one balanced panel:
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
    /** The sidebar, left of the page (drawn at negative x: the page keeps its own coordinates). */
    private static final int SB = 140;
    private static final int NAV_Y = 150;
    private static final int NAV_H = 24;
    private static final int NAV_GAP = 5;
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
                this.width / (float) (PW + SB + 16),
                this.height / (float) (PH + 16)));
        if (this.uiScale <= 0.01F) {
            this.uiScale = 1.0F;
        }
        // leftPos is the page's corner: the sidebar lies left of it
        this.leftPos = Math.round((this.width / this.uiScale - PW - SB) / 2.0F) + SB;
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
        if (PowerTheme.localYas()) {
            return new int[] {TAB_PROGRESS, TAB_CONFIG};
        }
        if (PowerTheme.localScarlet()) {
            return new int[] {TAB_PROGRESS, TAB_CONFIG};
        }
        if (PowerTheme.localStorm() || PowerTheme.localDoom() || PowerTheme.localUltron()) {
            return new int[] {TAB_PROGRESS};
        }
        if (PowerTheme.localSpider() || PowerTheme.localWonder()) {
            // the suit is picked in the settings
            return new int[] {TAB_PROGRESS, TAB_CONFIG};
        }
        if (PowerTheme.localLex() && !FlashClientData.hasPowers()) {
            return new int[] {TAB_PROGRESS, TAB_EQUIP};
        }
        if (PowerTheme.localHulk() || PowerTheme.localSuperman() || PowerTheme.localThor()) {
            // every hero has the equipment tab (the Processor works for all of them)
            return new int[] {TAB_PROGRESS, TAB_EQUIP, TAB_CONFIG};
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
        pose.translate(x + (PW - SB) / 2.0F, y + PH / 2.0F + (1.0F - eased) * 10.0F, 0.0F);
        pose.scale(grow, grow, 1.0F);
        pose.translate(-(PW - SB) / 2.0F, -PH / 2.0F, 0.0F);

        drawBackdrop(graphics);
        drawSidebar(graphics, mx, my);
        drawPageHeader(graphics);

        int current = activeTab();
        if (current == TAB_PROGRESS) {
            drawProgressTab(graphics, mx, my);
        } else if (current == TAB_EQUIP) {
            drawEquipTab(graphics, mx, my);
        } else if (PowerTheme.localYas()) {
            drawYasConfig(graphics, mx, my);
        } else if (PowerTheme.localSpider()) {
            drawSpiderConfig(graphics, mx, my);
        } else if (PowerTheme.localWonder()) {
            drawWonderConfig(graphics, mx, my);
        } else if (PowerTheme.localScarlet()) {
            configRow(graphics, 0, "gui.flash.scarlet.cfg.aura", "gui.flash.scarlet.cfg.aura.desc", mx, my);
            drawSwitch(graphics, "ssw0", switchX(), cfgY(0) + 8, ScarletClient.localAura());
        } else if (PowerTheme.localThor()) {
            drawThorConfig(graphics, mx, my);
        } else if (PowerTheme.localSuperman()) {
            drawSupermanConfig(graphics, mx, my);
        } else if (PowerTheme.localHulk()) {
            drawHulkConfig(graphics, mx, my);
        } else {
            drawConfigTab(graphics, mx, my);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // The console: backdrop, sidebar, page header
    // ------------------------------------------------------------------

    private static float hash(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 2147483647.0F;
    }

    /** A soft round light (stacked discs of rows), kept inside the panel. */
    private static void blob(GuiGraphics g, int cx, int cy, int r, int color, float alpha) {
        int col = withAlpha(color, alpha);
        for (int layer = 1; layer <= 4; layer++) {
            int rr = Math.max(2, r * layer / 4);
            for (int dy = -rr; dy < rr; dy += 2) {
                int hw = (int) Math.sqrt(Math.max(0, rr * rr - dy * dy));
                int y0 = Math.max(0, cy + dy);
                int y1 = Math.min(PH, cy + dy + 2);
                if (y1 > y0) {
                    g.fill(Math.max(-SB, cx - hw), y0, Math.min(PW, cx + hw), y1, col);
                }
            }
        }
    }

    /** A box with its corners cut round by r pixels. */
    private static void round(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5D;
            int in = r - (int) Math.round(Math.sqrt(Math.max(0.0D, r * r - d * d)));
            g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    private void drawBackdrop(GuiGraphics g) {
        float s = seconds();
        int a = accent();
        // the outer glow and the lit edge
        for (int i = 7; i >= 1; i--) {
            g.fill(-SB - i, -i, PW + i, PH + i, withAlpha(a, 0.035F * (8 - i)));
        }
        g.fill(-SB - 1, -1, PW + 1, PH + 1, withAlpha(a, 0.75F));
        g.fillGradient(-SB, 0, PW, PH, 0xFF0F1218, 0xFF06070A);
        // drifting light
        blob(g, (int) (PW * 0.72F + Math.sin(s * 0.31F) * 70.0F), (int) (PH * 0.32F + Math.cos(s * 0.23F) * 45.0F),
                170, a, 0.035F);
        blob(g, (int) (PW * 0.22F + Math.cos(s * 0.27F) * 80.0F), (int) (PH * 0.82F + Math.sin(s * 0.19F) * 30.0F),
                130, accentDark(), 0.06F);
        // the fine grid
        for (int gx = 0; gx < PW; gx += 24) {
            g.fill(gx, 0, gx + 1, PH, 0x06FFFFFF);
        }
        for (int gy = 0; gy < PH; gy += 24) {
            g.fill(0, gy, PW, gy + 1, 0x06FFFFFF);
        }
        // a sweep of light crossing the panel now and then
        float sweep = (s * 0.11F) % 1.0F;
        int sx = (int) (-SB - 260 + (PW + SB + 520) * sweep);
        for (int row = 0; row < PH; row += 2) {
            int off = row / 2;
            int x0 = Math.max(-SB, sx + off);
            int x1 = Math.min(PW, sx + off + 46);
            if (x1 > x0) {
                g.fill(x0, row, x1, row + 2, 0x05FFFFFF);
            }
            int c0 = Math.max(-SB, sx + off + 16);
            int c1 = Math.min(PW, sx + off + 26);
            if (c1 > c0) {
                g.fill(c0, row, c1, row + 2, 0x07FFFFFF);
            }
        }
        // motes of light rising
        for (int i = 0; i < 44; i++) {
            float spd = 0.015F + hash(i * 3 + 2) * 0.045F;
            float yy = (hash(i * 13 + 5) - s * spd) % 1.0F;
            if (yy < 0.0F) {
                yy += 1.0F;
            }
            int px = -SB + (int) (hash(i * 7 + 1) * (PW + SB - 2)) + (int) (Math.sin(s * 0.7F + i) * 4.0F);
            int py = (int) (yy * (PH - 2));
            float tw = 0.5F + 0.5F * (float) Math.sin(s * 2.2F + i * 1.7F);
            int size = i % 6 == 0 ? 2 : 1;
            g.fill(px, py, px + size, py + size, withAlpha(a, 0.15F + 0.45F * tw));
        }
        // the sidebar's own pane
        g.fillGradient(-SB, 0, 0, PH, withAlpha(accentDark(), 0.5F), 0xB006070A);
        g.fill(-1, 0, 0, PH, withAlpha(a, 0.3F));
        for (int i = 0; i < PH; i += 6) {
            g.fill(-1, i, 0, i + 2, withAlpha(a, 0.25F + 0.25F * (float) Math.sin(s * 3.0F - i * 0.08F)));
        }
        // lit corners of the whole console
        int c = withAlpha(a, 0.9F);
        int len = 14 + (int) (4.0F * Math.sin(s * 2.0F));
        g.fill(-SB, 0, -SB + len, 2, c);
        g.fill(-SB, 0, -SB + 2, len, c);
        g.fill(PW - len, 0, PW, 2, c);
        g.fill(PW - 2, 0, PW, len, c);
        g.fill(-SB, PH - 2, -SB + len, PH, c);
        g.fill(-SB, PH - len, -SB + 2, PH, c);
        g.fill(PW - len, PH - 2, PW, PH, c);
        g.fill(PW - 2, PH - len, PW, PH, c);
    }

    /** The hero's symbol (about 26 x 26) at x, y. */
    private void drawEmblem(GuiGraphics g, int x, int y) {
        String[] own = HERO_ICONS.get(theme().id());
        if (own != null) {
            drawPixelIcon(g, x, y, own, accent(), heroIconSecond(theme().id()));
        } else if (theme().icon() == PowerTheme.Icon.BOLT) {
            drawBolt(g, x + 2, y - 1, accent());
        } else if (theme().icon() == PowerTheme.Icon.FIST) {
            drawFist(g, x, y, accent());
        } else if (theme().icon() == PowerTheme.Icon.SHIELD) {
            drawShield(g, x, y, accent());
        } else {
            drawNeutralIcon(g, x, y, accent());
        }
    }

    /** What the hero is right now (the pill under his name). */
    private Component badge() {
        boolean hulkPower = PowerTheme.localHulk();
        boolean hulkForm = hulkPower && com.flash.hulk.HulkState.isTransformed(Minecraft.getInstance().player);
        boolean superPower = PowerTheme.localSuperman();
        boolean superForm = superPower && com.flash.superman.SupermanState.suited(Minecraft.getInstance().player);
        boolean thorPower = PowerTheme.localThor();
        boolean thorForm = thorPower && com.flash.thor.ThorState.suited(Minecraft.getInstance().player);
        boolean yasPower = PowerTheme.localYas() || PowerTheme.localScarlet() || PowerTheme.localOwnModes();
        return yasPower ? Component.translatable(PowerTheme.localOwnModes() ? "gui.flash." + ownHero() + ".badge"
                : PowerTheme.localScarlet() ? "gui.flash.scarlet.badge" : "gui.flash.yas.badge") : thorPower
                ? Component.translatable(thorForm ? "gui.flash.thor.badge_thor" : "gui.flash.thor.badge_civil")
                : superPower
                ? Component.translatable(superForm ? "gui.flash.superman.badge_superman" : "gui.flash.superman.badge_clark")
                : hulkPower
                ? Component.translatable(hulkForm ? "gui.flash.hulk.badge_hulk" : "gui.flash.hulk.badge_banner")
                : FlashClientData.hasPowers()
                ? (FlashClientData.isTemporary()
                    ? Component.translatable("gui.flash.progress.temporary",
                        formatTime(FlashClientData.getTemporaryRemaining()))
                    : Component.translatable("gui.flash.progress.active_badge",
                        FlashClientData.getSelectedLevel()))
                : Component.translatable("gui.flash.progress.no_powers_badge");
    }

    private static boolean powered() {
        return FlashClientData.hasPowers() || PowerTheme.localHulk() || PowerTheme.localSuperman()
                || PowerTheme.localThor() || PowerTheme.localYas() || PowerTheme.localScarlet()
                || PowerTheme.localOwnModes();
    }

    private static int navX() {
        return -SB + 10;
    }

    private static int navW() {
        return SB - 20;
    }

    private static int navY(int index) {
        return NAV_Y + index * (NAV_H + NAV_GAP);
    }

    private float navIndicator = -1.0F;

    private void drawSidebar(GuiGraphics g, int mx, int my) {
        float s = seconds();
        int a = accent();
        int cx = -SB / 2;
        int ey = 48;
        // the emblem in its turning rings
        float breathe = 0.5F + 0.5F * (float) Math.sin(s * 2.0F);
        blob(g, cx, ey, 38, a, 0.05F + 0.04F * breathe);
        for (int i = 0; i < 18; i++) {
            double ang = s * 0.7D + i * Math.PI * 2.0D / 18.0D;
            int dx = cx + (int) Math.round(Math.cos(ang) * 33.0D);
            int dy = ey + (int) Math.round(Math.sin(ang) * 33.0D);
            int sz = i % 3 == 0 ? 2 : 1;
            g.fill(dx - sz / 2, dy - sz / 2, dx - sz / 2 + sz, dy - sz / 2 + sz,
                    withAlpha(a, i % 3 == 0 ? 0.95F : 0.45F));
        }
        for (int i = 0; i < 28; i++) {
            double ang = -s * 0.45D + i * Math.PI * 2.0D / 28.0D;
            int dx = cx + (int) Math.round(Math.cos(ang) * 26.0D);
            int dy = ey + (int) Math.round(Math.sin(ang) * 26.0D);
            g.fill(dx, dy, dx + 1, dy + 1, withAlpha(a, 0.3F + 0.3F * (float) Math.sin(s * 3.0F + i)));
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        float sc = 1.45F + 0.05F * breathe;
        pose.translate(cx, ey, 0.0F);
        pose.scale(sc, sc, 1.0F);
        pose.translate(-13.0F, -13.0F, 0.0F);
        drawEmblem(g, 0, 0);
        pose.popPose();

        // the name and what he is
        Component title = Component.translatable(theme().titleKey());
        float ts = Math.min(1.2F, (SB - 16.0F) / Math.max(1, this.font.width(title)));
        int tw = Math.round(this.font.width(title) * ts);
        scaledText(g, title, cx - tw / 2, 92, ts, COLOR_TEXT);
        java.util.List<net.minecraft.util.FormattedCharSequence> sub = this.font.split(
                Component.translatable(theme().subtitleKey()), SB - 18);
        for (int i = 0; i < Math.min(2, sub.size()); i++) {
            int w = this.font.width(sub.get(i));
            g.drawString(this.font, sub.get(i), cx - w / 2, 105 + i * 10, COLOR_MUTED, false);
        }
        Component badge = badge();
        int color = powered() ? a : COLOR_MUTED;
        String bt = this.font.plainSubstrByWidth(badge.getString(), SB - 34);
        int bw = this.font.width(bt) + 20;
        int bx = cx - bw / 2;
        int by = 127;
        round(g, bx, by, bw, 15, 5, withAlpha(color, 0.6F));
        round(g, bx + 1, by + 1, bw - 2, 13, 4, 0xF00B0D12);
        float pulse = 0.5F + 0.5F * (float) Math.sin(s * 4.0F);
        g.fill(bx + 6, by + 6, bx + 9, by + 9, lerpColor(withAlpha(color, 0.35F), color, pulse));
        g.drawString(this.font, bt, bx + 13, by + 4, color, false);

        // the pages
        int[] visible = visibleTabs();
        int activeIndex = 0;
        for (int i = 0; i < visible.length; i++) {
            if (visible[i] == activeTab()) {
                activeIndex = i;
            }
        }
        float target = navY(activeIndex);
        if (this.navIndicator < 0.0F) {
            this.navIndicator = target;
        }
        this.navIndicator += (target - this.navIndicator) * Math.min(1.0F, this.dt * 14.0F);
        int iy = Math.round(this.navIndicator);
        round(g, navX(), iy, navW(), NAV_H, 5, withAlpha(a, 0.22F));
        round(g, navX() + 1, iy + 1, navW() - 2, NAV_H - 2, 4, withAlpha(accentDark(), 0.85F));
        g.fill(navX(), iy + 5, navX() + 2, iy + NAV_H - 5, a);
        g.fill(navX() + navW() - 9, iy + 10, navX() + navW() - 6, iy + 14, a);
        for (int i = 0; i < visible.length; i++) {
            int id = visible[i];
            int ny = navY(i);
            boolean active = id == activeTab();
            float hv = hover("nav" + id, !active && inside(mx, my, navX(), ny, navW(), NAV_H));
            if (hv > 0.0F) {
                round(g, navX(), ny, navW(), NAV_H, 5, withAlpha(0xFF1E232C, 0.8F * hv));
            }
            int shift = Math.round(3.0F * hv);
            int tc = active ? 0xFFFFFFFF : lerpColor(COLOR_MUTED, COLOR_TEXT, hv);
            navIcon(g, id, navX() + 10 + shift, ny + 8, active ? a : lerpColor(COLOR_DIM, a, hv));
            g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(TAB_KEYS[id]).getString(),
                    navW() - 40), navX() + 26 + shift, ny + 8, tc, false);
        }

        // the hints and the credits at the foot
        Component hint = PowerTheme.localThor()
                ? Component.translatable("gui.flash.footer.thor", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage())
                : PowerTheme.localSuperman()
                ? Component.translatable("gui.flash.footer.superman", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage())
                : PowerTheme.localHulk()
                ? Component.translatable("gui.flash.footer.hulk", FlashKeys.HULK_TRANSFORM.getTranslatedKeyMessage())
                : !FlashClientData.hasPowers()
                ? Component.translatable("gui.flash.footer.neutral")
                : Component.translatable(FlashClientConfig.SCROLL_MODE.get()
                    ? "gui.flash.footer.scroll" : "gui.flash.footer.keys");
        float hs = 0.7F;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(hint,
                (int) ((SB - 18) / hs));
        int shown = Math.min(4, lines.size());
        int hy = PH - 40 - shown * 7;
        g.fill(-SB + 10, hy - 6, -10, hy - 5, withAlpha(a, 0.25F));
        pose.pushPose();
        pose.translate(-SB + 10, hy, 0.0F);
        pose.scale(hs, hs, 1.0F);
        for (int i = 0; i < shown; i++) {
            g.drawString(this.font, lines.get(i), 0, i * 10, COLOR_DIM, false);
        }
        pose.popPose();
        if (FlashClientData.hasPowers() || theme() != PowerTheme.NEUTRAL) {
            float cs = 0.6F;
            java.util.List<net.minecraft.util.FormattedCharSequence> cl = this.font.split(
                    Component.translatable(creditsKey(theme().id())), (int) ((SB - 18) / cs));
            pose.pushPose();
            pose.translate(-SB + 10, PH - 30, 0.0F);
            pose.scale(cs, cs, 1.0F);
            for (int i = 0; i < Math.min(3, cl.size()); i++) {
                g.drawString(this.font, cl.get(i), 0, i * 10, 0xFF4A515B, false);
            }
            pose.popPose();
        }
    }

    /** A tiny glyph for each page. */
    private static void navIcon(GuiGraphics g, int tabId, int x, int y, int c) {
        if (tabId == TAB_PROGRESS) {
            g.fill(x, y + 5, x + 2, y + 8, c);
            g.fill(x + 3, y + 2, x + 5, y + 8, c);
            g.fill(x + 6, y, x + 8, y + 8, c);
        } else if (tabId == TAB_CONFIG) {
            g.fill(x + 2, y, x + 6, y + 8, c);
            g.fill(x, y + 2, x + 8, y + 6, c);
            g.fill(x + 3, y + 3, x + 5, y + 5, 0xFF0B0D12);
        } else {
            g.fill(x + 2, y, x + 6, y + 1, c);
            g.fill(x + 1, y + 1, x + 7, y + 2, c);
            g.fill(x, y + 2, x + 2, y + 6, c);
            g.fill(x + 6, y + 2, x + 8, y + 6, c);
            g.fill(x + 1, y + 6, x + 7, y + 7, c);
            g.fill(x + 2, y + 7, x + 6, y + 8, c);
        }
    }

    /** The page's own header: the hero over the page's name, an energy line under it. */
    private void drawPageHeader(GuiGraphics g) {
        float s = seconds();
        int a = accent();
        String crumb = Component.translatable(theme().titleKey()).getString().toUpperCase(java.util.Locale.ROOT);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(PAD, 14, 0.0F);
        pose.scale(0.7F, 0.7F, 1.0F);
        g.drawString(this.font, crumb, 0, 0, withAlpha(a, 0.85F), false);
        pose.popPose();
        Component page = Component.translatable(TAB_KEYS[activeTab()]);
        scaledText(g, page, PAD, 26, 1.7F, COLOR_TEXT);
        // the energy line: grows in, a light runs along it
        int lx0 = PAD;
        int lx1 = PW - PAD;
        int ly = 74;
        for (int x0 = lx0; x0 < lx1; x0 += 4) {
            float k = (x0 - lx0) / (float) (lx1 - lx0);
            g.fill(x0, ly, Math.min(lx1, x0 + 4), ly + 1, withAlpha(a, 0.55F * (1.0F - k) + 0.08F));
        }
        float run = (s * 0.35F) % 1.0F;
        int rx = lx0 + Math.round((lx1 - lx0) * run);
        g.fill(Math.max(lx0, rx - 24), ly - 1, Math.min(lx1, rx + 24), ly + 2, withAlpha(a, 0.35F));
        g.fill(Math.max(lx0, rx - 6), ly - 1, Math.min(lx1, rx + 6), ly + 2, withAlpha(0xFFFFFFFF, 0.8F));
        // a few live readings at the right of the header
        int dots = 5;
        for (int i = 0; i < dots; i++) {
            float on = 0.5F + 0.5F * (float) Math.sin(s * 3.0F - i * 0.7F);
            g.fill(PW - PAD - 6 - i * 8, 32, PW - PAD - 2 - i * 8, 36, withAlpha(a, 0.2F + 0.7F * on));
        }
    }

    private static String creditsKey(String hero) {
        return "gui.flash.credits." + switch (hero) {
            case "negative", "v9" -> "flash";
            case "red_hulk" -> "hulk";
            default -> hero;
        };
    }

    /** One small line of credits, right-aligned at {@code right}, cut to {@code width}. */
    private void drawCredits(GuiGraphics g, String key, int right, int y, int width) {
        float scale = 0.75F;
        String text = this.font.plainSubstrByWidth(Component.translatable(key).getString(), (int) (width / scale));
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(right - this.font.width(text) * scale, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(this.font, text, 0, 0, COLOR_DIM, false);
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // Progress tab
    // ------------------------------------------------------------------

    private void drawProgressTab(GuiGraphics g, int mx, int my) {
        if (PowerTheme.localYas() || PowerTheme.localScarlet() || PowerTheme.localOwnModes()) {
            drawYasTab(g, mx, my);
            return;
        }
        if (theme() == PowerTheme.LEX) {
            drawLexTab(g, mx, my);
            return;
        }
        if (PowerTheme.localThor()) {
            drawThorTab(g, mx, my);
            return;
        }
        if (PowerTheme.localSuperman()) {
            drawSupermanTab(g, mx, my);
            return;
        }
        if (PowerTheme.localHulk()) {
            drawHulkTab(g, mx, my);
            return;
        }
        if (!FlashClientData.hasPowers()) {
            drawHeroGuide(g, mx, my);
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
        int rowStep = Math.min(SKILL_ROW, (PH - 20 - top) / total);
        for (int k = 0; k < total; k++) {
            int ry = top + row * rowStep;
            int rh = rowStep - 2;
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
                net.minecraft.client.KeyMapping key = HulkClient.key(a);
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
    // Superman page
    // ------------------------------------------------------------------

    private static final String[] SUPER_PASSIVES = {"super_strength", "steel_body", "solar"};

    private void drawSupermanTab(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean form = mc.player != null && com.flash.superman.SupermanState.suited(mc.player);
        int level = Math.max(1, com.flash.superman.SupermanState.level());
        float xp = Math.max(0.0F, Math.min(1.0F, com.flash.superman.SupermanState.xp()
                / com.flash.superman.SupermanPowers.MAX_XP));
        boolean infinite = com.flash.superman.SupermanState.infiniteEnergy();
        float energy = infinite ? 1.0F : Math.max(0.0F, Math.min(1.0F, com.flash.superman.SupermanState.energy()
                / com.flash.superman.SupermanPowers.MAX_ENERGY));
        int half = (LEFT_W - 8) / 2;
        statCard(g, PAD, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.superman.level"),
                Component.translatable("gui.flash.superman.level_value", level),
                Component.literal(String.format(java.util.Locale.ROOT, "x%.2f",
                        0.55F + 0.05F * level)));
        statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.superman.form"),
                Component.translatable(form ? "gui.flash.superman.form_superman" : "gui.flash.superman.form_clark"),
                Component.translatable("gui.flash.hulk.key_short", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage()));

        sectionTitle(g, Component.translatable("gui.flash.hulk.levels"), PAD, GRID_Y - 13);
        int cols = 5;
        int cw = (LEFT_W - GRID_GAP * (cols - 1)) / cols;
        int ch = 30;
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds() * 3.2F);
        for (int l = 1; l <= com.flash.superman.SupermanPowers.MAX_LEVEL; l++) {
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

        // strength and solar energy
        double punch = com.flash.superman.SupermanPowers.punchDamage(level);
        int sy = GRID_Y + 2 * (ch + GRID_GAP) + 6;
        int sh = PROGRESS_Y - 6 - sy;
        card(g, PAD, sy, LEFT_W, sh, 0.0F);
        g.fill(PAD, sy + 6, PAD + 2, sy + sh - 6, accent());
        g.drawString(this.font, Component.translatable("gui.flash.superman.strength"), PAD + 10, sy + 6, COLOR_MUTED, false);
        scaledText(g, Component.literal(String.format(java.util.Locale.ROOT, "x%.1f", punch)), PAD + 10, sy + 17,
                1.4F, accent());
        int resist = com.flash.power.HeroBalance.suitedCut(level);
        Component dmg = Component.translatable("gui.flash.superman.punch_damage",
                String.format(java.util.Locale.ROOT, "%.1f", punch),
                String.format(java.util.Locale.ROOT, "%.1f", punch / 2.0D));
        g.drawString(this.font, dmg, PAD + LEFT_W - 10 - this.font.width(dmg), sy + 6, COLOR_TEXT, false);
        Component res = Component.translatable("gui.flash.superman.resistance", resist);
        g.drawString(this.font, res, PAD + LEFT_W - 10 - this.font.width(res), sy + 17, COLOR_MUTED, false);

        // progress to the next level and the energy bar
        int y = PROGRESS_Y;
        card(g, PAD, y, LEFT_W, CONTENT_BOTTOM - y, 0.0F);
        if (this.shownProgress < 0.0F) {
            this.shownProgress = 0.0F;
        }
        this.shownProgress += (xp - this.shownProgress) * Math.min(1.0F, this.dt * 5.0F);
        boolean maxed = level >= com.flash.superman.SupermanPowers.MAX_LEVEL;
        Component energyText = Component.translatable(infinite ? "gui.flash.superman.energy_infinite"
                : "gui.flash.superman.energy", Math.round(energy * 100.0F));
        g.drawString(this.font, energyText, PAD + 10, y + 6, com.flash.superman.SupermanState.inSun()
                ? 0xFFFFD84A : COLOR_TEXT, false);
        drawBar(g, PAD + 10, y + 17, LEFT_W - 20, energy, energy < 0.15F ? 0xFFFF3A3A : 0xFFFFC21A);
        Component next = Component.translatable(maxed ? "gui.flash.superman.maxed" : "gui.flash.superman.next",
                level + 1, Math.round(xp * 100.0F));
        g.drawString(this.font, next, PAD + 10, y + 27, COLOR_MUTED, false);
        if (!maxed) {
            drawBar(g, PAD + 10, y + 38, LEFT_W - 20, this.shownProgress, accent());
        }

        // passives and abilities, with level, key and cooldown
        sectionTitle(g, Component.translatable("gui.flash.progress.skills"), RIGHT_X, CONTENT_Y);
        int top = CONTENT_Y + 14;
        com.flash.superman.SupermanAbility[] all = com.flash.superman.SupermanAbility.values();
        int total = all.length + SUPER_PASSIVES.length;
        int rowStep = Math.min(SKILL_ROW, (PH - 22 - top) / total);
        int rh = rowStep - 1;
        int textY = (rh - 8) / 2;
        Component tooltip = null;
        for (int k = 0; k < total; k++) {
            int ry = top + k * rowStep;
            Component name;
            Component desc;
            Component right;
            int req = 1;
            int cooldown = 0;
            if (k < SUPER_PASSIVES.length) {
                name = Component.translatable("ability.flash.superman." + SUPER_PASSIVES[k]);
                desc = Component.translatable("ability.flash.superman." + SUPER_PASSIVES[k] + ".description");
                right = Component.translatable("gui.flash.hulk.passive");
            } else {
                com.flash.superman.SupermanAbility a = all[k - SUPER_PASSIVES.length];
                name = a.displayName();
                desc = a.description();
                req = a.level();
                cooldown = com.flash.superman.SupermanState.cooldown(a, now);
                net.minecraft.client.KeyMapping key = SupermanClient.key(a);
                right = cooldown > 0 ? Component.literal(formatTime(cooldown))
                        : key != null ? key.getTranslatedKeyMessage() : Component.empty();
            }
            boolean unlocked = level >= req;
            if (!unlocked) {
                right = Component.translatable("gui.flash.progress.level_short", req);
            }
            boolean hovered = inside(mx, my, RIGHT_X, ry, RIGHT_W, rh);
            float hv = hover("ssk" + k, hovered);
            card(g, RIGHT_X, ry, RIGHT_W, rh, hv);
            if (unlocked) {
                drawCheck(g, RIGHT_X + 6, ry + Math.max(1, textY), accent());
            } else {
                drawLock(g, RIGHT_X + 6, ry + Math.max(0, textY - 1), COLOR_DIM);
            }
            int rw = this.font.width(right);
            g.drawString(this.font, this.font.plainSubstrByWidth(name.getString(), RIGHT_W - 30 - rw),
                    RIGHT_X + 18, ry + textY + 1, unlocked ? COLOR_TEXT : COLOR_DIM, false);
            g.drawString(this.font, right, RIGHT_X + RIGHT_W - 6 - rw, ry + textY + 1,
                    cooldown > 0 ? COLOR_ORANGE : unlocked ? accent() : COLOR_DIM, false);
            if (hovered) {
                tooltip = desc;
            }
        }
        if (tooltip != null) {
            g.renderTooltip(this.font, this.font.split(tooltip, 220), mx, my);
        }
    }

    private static final String[] THOR_PASSIVES = {"worthy", "asgardian_body", "storm"};

    private void drawThorTab(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean form = mc.player != null && com.flash.thor.ThorState.suited(mc.player);
        int level = Math.max(1, com.flash.thor.ThorState.level());
        float xp = Math.max(0.0F, Math.min(1.0F, com.flash.thor.ThorState.xp()
                / com.flash.thor.ThorPowers.MAX_XP));
        boolean infinite = com.flash.thor.ThorState.infiniteEnergy();
        float energy = infinite ? 1.0F : Math.max(0.0F, Math.min(1.0F, com.flash.thor.ThorState.energy()
                / com.flash.thor.ThorPowers.MAX_ENERGY));
        int half = (LEFT_W - 8) / 2;
        statCard(g, PAD, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.thor.level"),
                Component.translatable("gui.flash.superman.level_value", level),
                Component.literal(String.format(java.util.Locale.ROOT, "x%.2f",
                        0.55F + 0.05F * level)));
        statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.superman.form"),
                Component.translatable(form ? "gui.flash.thor.form_thor" : "gui.flash.thor.form_civil"),
                Component.translatable("gui.flash.hulk.key_short", FlashKeys.TOGGLE_SUIT.getTranslatedKeyMessage()));

        sectionTitle(g, Component.translatable("gui.flash.thor.levels"), PAD, GRID_Y - 13);
        int cols = 5;
        int cw = (LEFT_W - GRID_GAP * (cols - 1)) / cols;
        int ch = 30;
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds() * 3.2F);
        for (int l = 1; l <= com.flash.thor.ThorPowers.MAX_LEVEL; l++) {
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

        // strength and solar energy
        double punch = com.flash.thor.ThorPowers.punchDamage(level);
        int sy = GRID_Y + 2 * (ch + GRID_GAP) + 6;
        int sh = PROGRESS_Y - 6 - sy;
        card(g, PAD, sy, LEFT_W, sh, 0.0F);
        g.fill(PAD, sy + 6, PAD + 2, sy + sh - 6, accent());
        g.drawString(this.font, Component.translatable("gui.flash.thor.strength"), PAD + 10, sy + 6, COLOR_MUTED, false);
        scaledText(g, Component.literal(String.format(java.util.Locale.ROOT, "x%.1f", punch)), PAD + 10, sy + 17,
                1.4F, accent());
        int resist = com.flash.power.HeroBalance.suitedCut(level);
        Component dmg = Component.translatable("gui.flash.superman.punch_damage",
                String.format(java.util.Locale.ROOT, "%.1f", punch),
                String.format(java.util.Locale.ROOT, "%.1f", punch / 2.0D));
        g.drawString(this.font, dmg, PAD + LEFT_W - 10 - this.font.width(dmg), sy + 6, COLOR_TEXT, false);
        Component res = Component.translatable("gui.flash.thor.resistance", resist);
        g.drawString(this.font, res, PAD + LEFT_W - 10 - this.font.width(res), sy + 17, COLOR_MUTED, false);

        // progress to the next level and the energy bar
        int y = PROGRESS_Y;
        card(g, PAD, y, LEFT_W, CONTENT_BOTTOM - y, 0.0F);
        if (this.shownProgress < 0.0F) {
            this.shownProgress = 0.0F;
        }
        this.shownProgress += (xp - this.shownProgress) * Math.min(1.0F, this.dt * 5.0F);
        boolean maxed = level >= com.flash.thor.ThorPowers.MAX_LEVEL;
        Component energyText = Component.translatable(infinite ? "gui.flash.thor.energy_infinite"
                : "gui.flash.thor.energy", Math.round(energy * 100.0F));
        g.drawString(this.font, energyText, PAD + 10, y + 6, com.flash.thor.ThorState.storm() > 0
                ? 0xFF8FD8FF : COLOR_TEXT, false);
        drawBar(g, PAD + 10, y + 17, LEFT_W - 20, energy, energy < 0.15F ? 0xFFFF3A3A : 0xFF6FC3FF);
        Component next = Component.translatable(maxed ? "gui.flash.superman.maxed" : "gui.flash.thor.next",
                level + 1, Math.round(xp * 100.0F));
        g.drawString(this.font, next, PAD + 10, y + 27, COLOR_MUTED, false);
        if (!maxed) {
            drawBar(g, PAD + 10, y + 38, LEFT_W - 20, this.shownProgress, accent());
        }

        // passives and abilities, with level, key and cooldown
        sectionTitle(g, Component.translatable("gui.flash.progress.skills"), RIGHT_X, CONTENT_Y);
        int top = CONTENT_Y + 14;
        com.flash.thor.ThorAbility[] all = java.util.Arrays.stream(com.flash.thor.ThorAbility.values())
                .filter(a -> !a.hidden()).toArray(com.flash.thor.ThorAbility[]::new);
        int total = all.length + THOR_PASSIVES.length;
        int rowStep = Math.min(SKILL_ROW, (PH - 22 - top) / total);
        int rh = rowStep - 1;
        int textY = (rh - 8) / 2;
        Component tooltip = null;
        for (int k = 0; k < total; k++) {
            int ry = top + k * rowStep;
            Component name;
            Component desc;
            Component right;
            int req = 1;
            int cooldown = 0;
            if (k < THOR_PASSIVES.length) {
                name = Component.translatable("ability.flash.thor." + THOR_PASSIVES[k]);
                desc = Component.translatable("ability.flash.thor." + THOR_PASSIVES[k] + ".description");
                right = Component.translatable("gui.flash.hulk.passive");
            } else {
                com.flash.thor.ThorAbility a = all[k - THOR_PASSIVES.length];
                int weapon = com.flash.thor.ThorState.weapon(Minecraft.getInstance().player);
                name = a.displayName(weapon);
                desc = a.description(weapon);
                req = a.level();
                cooldown = com.flash.thor.ThorState.cooldown(a, now);
                net.minecraft.client.KeyMapping key = ThorClient.key(a);
                right = cooldown > 0 ? Component.literal(formatTime(cooldown))
                        : key != null ? key.getTranslatedKeyMessage() : Component.empty();
            }
            boolean unlocked = level >= req;
            if (!unlocked) {
                right = Component.translatable("gui.flash.progress.level_short", req);
            }
            boolean hovered = inside(mx, my, RIGHT_X, ry, RIGHT_W, rh);
            float hv = hover("tsk" + k, hovered);
            card(g, RIGHT_X, ry, RIGHT_W, rh, hv);
            if (unlocked) {
                drawCheck(g, RIGHT_X + 6, ry + Math.max(1, textY), accent());
            } else {
                drawLock(g, RIGHT_X + 6, ry + Math.max(0, textY - 1), COLOR_DIM);
            }
            int rw = this.font.width(right);
            g.drawString(this.font, this.font.plainSubstrByWidth(name.getString(), RIGHT_W - 30 - rw),
                    RIGHT_X + 18, ry + textY + 1, unlocked ? COLOR_TEXT : COLOR_DIM, false);
            g.drawString(this.font, right, RIGHT_X + RIGHT_W - 6 - rw, ry + textY + 1,
                    cooldown > 0 ? COLOR_ORANGE : unlocked ? accent() : COLOR_DIM, false);
            if (hovered) {
                tooltip = desc;
            }
        }
        if (tooltip != null) {
            g.renderTooltip(this.font, this.font.split(tooltip, 220), mx, my);
        }
    }

    /** Lex Luthor's suit: the battery, the passives and the Mark 6 systems with their keys and cooldowns. */
    // ------------------------------------------------------------------
    // Yas
    // ------------------------------------------------------------------

    /** Her powers: those of Hand of God (its keys and its ability wheel), in its own words. */
    private static final String[] YAS_POWERS = {"celeste", "temporal", "realidade", "materia", "telecinese",
            "supervelocidade", "visao_termica", "void", "eletrocinese"};
    private static final String[] YAS_PASSIVES_OF_YAS = {"flight", "toughness", "punch", "regen"};
    private static final String[] SCARLET_PASSIVES = {"flight", "toughness"};
    private static final String[] STORM_PASSIVES = {"flight", "lightning_body", "toughness"};
    private static final String[] SPIDER_PASSIVES = {"web_swing", "wall_crawl", "spider_sense", "agility",
            "healing"};
    private static final String[] DOOM_PASSIVES = {"armour", "thrusters", "repair", "ascension"};
    private static final String[] DOOM_GOD_PASSIVES = {"divinity", "thrusters", "repair", "beyonder"};
    private static final String[] WONDER_PASSIVES = {"flight", "demigoddess", "bracelets", "healing"};
    private static final String[] ULTRON_PASSIVES = {"flight", "vibranium", "machine", "network"};

    /** Storm, Spider-Man or Doom (heroes with modes of their own): their tab, their texts. */
    private static boolean stormTab() {
        return PowerTheme.localOwnModes();
    }

    /** The id of the texts of the hero with modes of his own ("storm", "spider", "doom"). */
    private static String ownHero() {
        return PowerTheme.localSpider() ? "spider" : PowerTheme.localDoom()
                ? (DoomClient.god(Minecraft.getInstance().player) ? "doom_god" : "doom")
                : PowerTheme.localWonder() ? "wonder" : PowerTheme.localUltron() ? "ultron" : "storm";
    }

    /** Yas or the Scarlet Witch (the same tab, their own passives and modes). */
    private static boolean scarletTab() {
        return PowerTheme.localScarlet();
    }

    private static String[] yasPassives() {
        return PowerTheme.localSpider() ? SPIDER_PASSIVES : PowerTheme.localDoom()
                ? (DoomClient.god(Minecraft.getInstance().player) ? DOOM_GOD_PASSIVES : DOOM_PASSIVES)
                : PowerTheme.localWonder() ? WONDER_PASSIVES : PowerTheme.localUltron() ? ULTRON_PASSIVES
                : stormTab() ? STORM_PASSIVES : scarletTab() ? SCARLET_PASSIVES : YAS_PASSIVES_OF_YAS;
    }

    private static String passivePrefix() {
        return stormTab() ? "ability.flash." + ownHero() + "." : scarletTab() ? "ability.flash.scarlet."
                : "ability.flash.yas.";
    }

    private static Component yasKey(int i) {
        Component use = FlashKeys.YAS_USE.getTranslatedKeyMessage();
        return YasAbilities.usesVariant(i)
                ? use.copy().append(" / ").append(FlashKeys.YAS_VARIANT.getTranslatedKeyMessage()) : use;
    }

    private static int yasRowTop() {
        return CONTENT_Y + 14;
    }

    private static int yasRowStep() {
        int total = yasPassives().length + YasAbilities.count();
        return Math.min(SKILL_ROW, (PH - 22 - yasRowTop()) / total);
    }

    /** A click on a mode in the K menu picks it. */
    private boolean clickYasTab(int mx, int my) {
        int step = yasRowStep();
        for (int i = 0; i < YasAbilities.count(); i++) {
            int ry = yasRowTop() + (yasPassives().length + i) * step;
            if (inside(mx, my, RIGHT_X, ry, RIGHT_W, step - 1)) {
                if (YasAbilities.selected() != i) {
                    YasAbilities.select(i);
                    click();
                }
                return true;
            }
        }
        return false;
    }

    private void drawYasTab(GuiGraphics g, int mx, int my) {
        int half = (LEFT_W - 8) / 2;
        String h = stormTab() ? "gui.flash." + ownHero() + "." : scarletTab() ? "gui.flash.scarlet." : "gui.flash.yas.";
        statCard(g, PAD, CONTENT_Y, half, STAT_H, Component.translatable(h + "stat_toughness"),
                Component.translatable(h + "stat_toughness_value"),
                Component.translatable(h + "stat_toughness_hint"));
        if (scarletTab() || stormTab()) {
            statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable(h + "stat_flight"),
                    Component.translatable(h + "stat_flight_value"), Component.translatable(h + "stat_flight_hint"));
        } else {
            statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.yas.stat_punch"),
                    Component.literal("+" + Math.round(com.flash.yas.YasPowers.PUNCH_BONUS)),
                    Component.translatable("gui.flash.yas.stat_punch_hint"));
        }
        int sy = GRID_Y - 13;
        sectionTitle(g, Component.translatable(h + "about"), PAD, sy);
        int cy = sy + 14;
        card(g, PAD, cy, LEFT_W, CONTENT_BOTTOM - cy, 0.0F);
        int ty = cy + 8;
        for (String k : new String[] {h + "about1", h + "about2", h + "about3", h + "about4"}) {
            for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.translatable(k),
                    LEFT_W - 20)) {
                if (ty > CONTENT_BOTTOM - 22) {
                    break;
                }
                g.drawString(this.font, line, PAD + 10, ty, COLOR_MUTED, false);
                ty += 10;
            }
            ty += 3;
        }
        drawCredits(g, stormTab() ? "gui.flash.credits." + ownHero() : scarletTab() ? "gui.flash.credits.scarlet"
                : "gui.flash.credits.yas", PAD + LEFT_W - 8,
                CONTENT_BOTTOM - 12, LEFT_W - 16);

        sectionTitle(g, Component.translatable("gui.flash.progress.skills"), RIGHT_X, CONTENT_Y);
        int top = CONTENT_Y + 14;
        int total = yasPassives().length + YasAbilities.count();
        int rowStep = Math.min(SKILL_ROW, (PH - 22 - top) / total);
        int rh = rowStep - 1;
        int textY = (rh - 8) / 2;
        Component tooltip = null;
        for (int k = 0; k < total; k++) {
            int ry = top + k * rowStep;
            Component name;
            Component desc;
            Component right;
            if (k < yasPassives().length) {
                name = Component.translatable(passivePrefix() + yasPassives()[k]);
                desc = Component.translatable(passivePrefix() + yasPassives()[k] + ".description");
                right = Component.translatable("gui.flash.hulk.passive");
            } else {
                int i = k - yasPassives().length;
                name = YasAbilities.name(i);
                desc = YasAbilities.desc(i);
                right = yasKey(i);
            }
            boolean hovered = inside(mx, my, RIGHT_X, ry, RIGHT_W, rh);
            boolean chosen = k >= yasPassives().length && YasAbilities.selected() == k - yasPassives().length;
            float hv = hover("ysk" + k, hovered);
            card(g, RIGHT_X, ry, RIGHT_W, rh, chosen ? 1.0F : hv);
            if (chosen) {
                g.fill(RIGHT_X, ry + 2, RIGHT_X + 2, ry + rh - 2, accent());
            }
            drawCheck(g, RIGHT_X + 6, ry + Math.max(1, textY), accent());
            int rw = this.font.width(right);
            g.drawString(this.font, this.font.plainSubstrByWidth(name.getString(), RIGHT_W - 30 - rw),
                    RIGHT_X + 18, ry + textY + 1, COLOR_TEXT, false);
            g.drawString(this.font, right, RIGHT_X + RIGHT_W - 6 - rw, ry + textY + 1, accent(), false);
            if (hovered) {
                tooltip = desc;
            }
        }
        if (tooltip != null) {
            g.renderTooltip(this.font, this.font.split(tooltip, 220), mx, my);
        }
    }

    private void drawYasConfig(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.yas.cfg.lightning", "gui.flash.yas.cfg.lightning.desc", mx, my);
        drawSwitch(g, "ysw0", switchX(), cfgY(0) + 8, YasClient.localLightning());
        configRow(g, 1, "gui.flash.yas.cfg.eyes", "gui.flash.yas.cfg.eyes.desc", mx, my);
        drawSwitch(g, "ysw1", switchX(), cfgY(1) + 8, YasClient.localEyes());
        configRow(g, 2, "gui.flash.yas.cfg.destroy", "gui.flash.yas.cfg.destroy.desc", mx, my);
        drawSwitch(g, "ysw2", switchX(), cfgY(2) + 8, YasClient.localDestroy);
    }

    private boolean clickYasConfig(int mx, int my) {
        boolean lightning = YasClient.localLightning();
        boolean eyes = YasClient.localEyes();
        if (inRow(mx, my, 0)) {
            lightning = !lightning;
        } else if (inRow(mx, my, 1)) {
            eyes = !eyes;
        } else if (inRow(mx, my, 2)) {
            YasClient.localDestroy = !YasClient.localDestroy;
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.YasDestroyPacket(YasClient.localDestroy));
            click();
            return true;
        } else {
            return false;
        }
        YasClient.setLocalLook(lightning, eyes);
        FlashNetwork.CHANNEL.sendToServer(new com.flash.network.YasSettingsPacket(lightning, eyes));
        click();
        return true;
    }

    private void drawLexTab(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        float energy = mc.player == null ? 0.0F : com.flash.item.LexArmorItem.energy(
                mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        int half = (LEFT_W - 8) / 2;
        statCard(g, PAD, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.lex.battery"),
                Component.literal(Math.round(energy) + "%"),
                Component.translatable("gui.flash.lex.battery_hint"));
        statCard(g, PAD + half + 8, CONTENT_Y, half, STAT_H, Component.translatable("gui.flash.lex.level"),
                Component.translatable("gui.flash.lex.level_value"),
                Component.translatable("gui.flash.lex.level_hint"));
        int sy = GRID_Y - 13;
        sectionTitle(g, Component.translatable("gui.flash.lex.about"), PAD, sy);
        int cy = sy + 14;
        card(g, PAD, cy, LEFT_W, CONTENT_BOTTOM - cy, 0.0F);
        int ty = cy + 8;
        for (String k : new String[] {"gui.flash.lex.about1", "gui.flash.lex.about2", "gui.flash.lex.about3",
                "gui.flash.lex.about4", "gui.flash.lex.about5"}) {
            for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.translatable(k),
                    LEFT_W - 20)) {
                if (ty > CONTENT_BOTTOM - 12) {
                    break;
                }
                g.drawString(this.font, line, PAD + 10, ty, COLOR_MUTED, false);
                ty += 10;
            }
            ty += 3;
        }
        drawBar(g, PAD + 10, CONTENT_BOTTOM - 14, LEFT_W - 20, energy / 100.0F,
                energy < 20.0F ? 0xFFFF3A3A : 0xFF6CFF6C);

        sectionTitle(g, Component.translatable("gui.flash.progress.skills"), RIGHT_X, CONTENT_Y);
        int top = CONTENT_Y + 14;
        com.flash.lex.LexAbility[] all = com.flash.lex.LexAbility.values();
        String[] passives = {"thrusters", "strength", "armor"};
        int total = all.length + passives.length;
        int rowStep = Math.min(SKILL_ROW, (PH - 22 - top) / total);
        int rh = rowStep - 1;
        int textY = (rh - 8) / 2;
        Component tooltip = null;
        for (int k = 0; k < total; k++) {
            int ry = top + k * rowStep;
            Component name;
            Component desc;
            Component right;
            int cooldown = 0;
            if (k < passives.length) {
                name = Component.translatable("ability.flash.lex." + passives[k]);
                desc = Component.translatable("ability.flash.lex." + passives[k] + ".description");
                right = Component.translatable("gui.flash.hulk.passive");
            } else {
                com.flash.lex.LexAbility a = all[k - passives.length];
                name = a.displayName();
                desc = a.description();
                cooldown = com.flash.lex.LexState.cooldown(a, now);
                net.minecraft.client.KeyMapping key = LexClient.key(a);
                right = cooldown > 0 ? Component.literal(formatTime(cooldown))
                        : key != null ? key.getTranslatedKeyMessage() : Component.empty();
            }
            boolean hovered = inside(mx, my, RIGHT_X, ry, RIGHT_W, rh);
            float hv = hover("lsk" + k, hovered);
            card(g, RIGHT_X, ry, RIGHT_W, rh, hv);
            drawCheck(g, RIGHT_X + 6, ry + Math.max(1, textY), accent());
            int rw = this.font.width(right);
            g.drawString(this.font, this.font.plainSubstrByWidth(name.getString(), RIGHT_W - 30 - rw),
                    RIGHT_X + 18, ry + textY + 1, COLOR_TEXT, false);
            g.drawString(this.font, right, RIGHT_X + RIGHT_W - 6 - rw, ry + textY + 1,
                    cooldown > 0 ? COLOR_ORANGE : accent(), false);
            if (hovered) {
                tooltip = desc;
            }
        }
        if (tooltip != null) {
            g.renderTooltip(this.font, this.font.split(tooltip, 220), mx, my);
        }
    }

    private void drawThorConfig(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.thor.cfg.destroy", "gui.flash.thor.cfg.destroy.desc", mx, my);
        drawSwitch(g, "tsw0", switchX(), cfgY(0) + 8, com.flash.thor.ThorState.destroyBlocks());
        configRow(g, 1, "gui.flash.config.hud", "gui.flash.superman.cfg.hud.desc", mx, my);
        drawSwitch(g, "tsw1", switchX(), cfgY(1) + 8, FlashClientConfig.SHOW_ABILITY_HUD.get());
        configRow(g, 2, "gui.flash.thor.cfg.energy", "gui.flash.thor.cfg.energy.desc", mx, my);
        configRow(g, 3, "gui.flash.superman.cfg.suit", "gui.flash.thor.cfg.suit.desc", mx, my);
        int suit = com.flash.thor.ThorSuits.clamp(com.flash.thor.ThorState.suit(this.minecraft.player));
        drawPicker(g, cfgY(3) + 7, mx, my, com.flash.thor.ThorSuits.name(suit).getString(), suit,
                com.flash.thor.ThorSuits.IDS.length);
        configRow(g, 4, "gui.flash.thor.cfg.helmet", "gui.flash.thor.cfg.helmet.desc", mx, my);
        drawSwitch(g, "tsw4", switchX(), cfgY(4) + 8, !com.flash.thor.ThorState.helmetOff(this.minecraft.player));
    }

    private boolean clickThorConfig(int mx, int my) {
        if (inRow(mx, my, 0)) {
            boolean destroy = !com.flash.thor.ThorState.destroyBlocks();
            com.flash.thor.ThorState.setDestroyOptimistic(destroy);
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.ThorSettingsPacket(
                    com.flash.network.ThorSettingsPacket.DESTROY, destroy ? 1 : 0));
            click();
            return true;
        }
        if (inRow(mx, my, 1)) {
            FlashClientConfig.toggle(FlashClientConfig.SHOW_ABILITY_HUD);
            click();
            return true;
        }
        if (inRow(mx, my, 3)) {
            int step = pickerStep(mx, my, 3);
            if (step != 0 && this.minecraft.player != null) {
                int n = com.flash.thor.ThorSuits.IDS.length;
                int idx = Math.floorMod(com.flash.thor.ThorState.suit(this.minecraft.player) + step, n);
                com.flash.thor.ThorState.setLocalLook(this.minecraft.player.getUUID(), idx, null);
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.ThorSettingsPacket(
                        com.flash.network.ThorSettingsPacket.SUIT, idx));
                click();
            }
            return true;
        }
        if (inRow(mx, my, 4) && this.minecraft.player != null) {
            boolean off = !com.flash.thor.ThorState.helmetOff(this.minecraft.player);
            com.flash.thor.ThorState.setLocalLook(this.minecraft.player.getUUID(),
                    com.flash.thor.ThorState.suit(this.minecraft.player), off);
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.ThorSettingsPacket(
                    com.flash.network.ThorSettingsPacket.HELMET, off ? 1 : 0));
            click();
            return true;
        }
        return inRow(mx, my, 2);
    }

    /** Spider-Man's settings: the HUD, and his suit (Legends: Superheroes' suits). */
    private void drawSpiderConfig(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.config.hud", "gui.flash.superman.cfg.hud.desc", mx, my);
        drawSwitch(g, "spw0", switchX(), cfgY(0) + 8, FlashClientConfig.SHOW_ABILITY_HUD.get());
        configRow(g, 1, "gui.flash.spider.cfg.suit", "gui.flash.spider.cfg.suit.desc", mx, my);
        int suit = com.flash.spider.SpiderSuits.clamp(SpiderClient.suit(this.minecraft.player));
        drawPicker(g, cfgY(1) + 7, mx, my, com.flash.spider.SpiderSuits.name(suit).getString(), suit,
                com.flash.spider.SpiderSuits.IDS.length);
    }

    private boolean clickSpiderConfig(int mx, int my) {
        if (inRow(mx, my, 0)) {
            FlashClientConfig.toggle(FlashClientConfig.SHOW_ABILITY_HUD);
            click();
            return true;
        }
        if (inRow(mx, my, 1)) {
            int step = pickerStep(mx, my, 1);
            if (step != 0 && this.minecraft.player != null) {
                int n = com.flash.spider.SpiderSuits.IDS.length;
                SpiderClient.pickSuit(Math.floorMod(SpiderClient.suit(this.minecraft.player) + step, n));
                click();
            }
            return true;
        }
        return false;
    }

    /** Wonder Woman's settings: the HUD, and her suit (with the tiara or without it). */
    private void drawWonderConfig(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.config.hud", "gui.flash.superman.cfg.hud.desc", mx, my);
        drawSwitch(g, "wsw0", switchX(), cfgY(0) + 8, FlashClientConfig.SHOW_ABILITY_HUD.get());
        configRow(g, 1, "gui.flash.wonder.cfg.suit", "gui.flash.wonder.cfg.suit.desc", mx, my);
        int suit = WonderClient.suitIndex(this.minecraft.player);
        drawPicker(g, cfgY(1) + 7, mx, my, Component.translatable("suit.flash."
                + com.flash.wonder.WonderPowers.SUITS[suit]).getString(), suit, com.flash.wonder.WonderPowers.SUITS.length);
    }

    private boolean clickWonderConfig(int mx, int my) {
        if (inRow(mx, my, 0)) {
            FlashClientConfig.toggle(FlashClientConfig.SHOW_ABILITY_HUD);
            click();
            return true;
        }
        if (inRow(mx, my, 1)) {
            int step = pickerStep(mx, my, 1);
            if (step != 0 && this.minecraft.player != null) {
                int n = com.flash.wonder.WonderPowers.SUITS.length;
                WonderClient.send(com.flash.wonder.WonderAbilities.SUIT,
                        Math.floorMod(WonderClient.suitIndex(this.minecraft.player) + step, n));
                click();
            }
            return true;
        }
        return false;
    }

    private void drawSupermanConfig(GuiGraphics g, int mx, int my) {
        configRow(g, 0, "gui.flash.superman.cfg.destroy", "gui.flash.superman.cfg.destroy.desc", mx, my);
        drawSwitch(g, "ssw0", switchX(), cfgY(0) + 8, com.flash.superman.SupermanState.destroyBlocks());
        configRow(g, 1, "gui.flash.config.hud", "gui.flash.superman.cfg.hud.desc", mx, my);
        drawSwitch(g, "ssw1", switchX(), cfgY(1) + 8, FlashClientConfig.SHOW_ABILITY_HUD.get());
        configRow(g, 2, "gui.flash.superman.cfg.energy", "gui.flash.superman.cfg.energy.desc", mx, my);
        configRow(g, 3, "gui.flash.superman.cfg.suit", "gui.flash.superman.cfg.suit.desc", mx, my);
        int suit = com.flash.superman.SupermanSuits.clamp(com.flash.superman.SupermanState.suit(this.minecraft.player));
        drawPicker(g, cfgY(3) + 7, mx, my, com.flash.superman.SupermanSuits.name(suit).getString(), suit,
                com.flash.superman.SupermanSuits.IDS.length);
        configRow(g, 4, "gui.flash.superman.cfg.combo", "gui.flash.superman.cfg.combo." + com.flash.superman.SupermanCombos
                .id(com.flash.superman.SupermanState.combo()), mx, my);
        int combo = com.flash.superman.SupermanState.combo();
        drawPicker(g, cfgY(4) + 7, mx, my, com.flash.superman.SupermanCombos.name(combo).getString(), combo,
                com.flash.superman.SupermanCombos.COUNT);
        configRow(g, 5, "gui.flash.superman.cfg.cut", "gui.flash.superman.cfg.cut.desc", mx, my);
        drawSwitch(g, "ssw5", switchX(), cfgY(5) + 8, com.flash.superman.SupermanState.laserCut());
    }

    private static int suitPickerX() {
        return PW - PAD - 12 - 2 * SEG_W;
    }

    /** Selector: arrows on the sides, the name and the number in the middle. */
    private void drawPicker(GuiGraphics g, int y, int mx, int my, String label, int idx, int count) {
        int x = suitPickerX();
        int w = 2 * SEG_W;
        g.fill(x, y, x + w, y + SEG_H, COLOR_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + SEG_H - 1, COLOR_TRACK);
        boolean hl = inside(mx, my, x, y, 20, SEG_H);
        boolean hr = inside(mx, my, x + w - 20, y, 20, SEG_H);
        g.fill(x + 1, y + 1, x + 20, y + SEG_H - 1, hl ? accentDark() : withAlpha(accent(), 0.18F));
        g.fill(x + w - 20, y + 1, x + w - 1, y + SEG_H - 1, hr ? accentDark() : withAlpha(accent(), 0.18F));
        g.drawCenteredString(this.font, "<", x + 10, y + 6, hl ? 0xFFFFFFFF : accent());
        g.drawCenteredString(this.font, ">", x + w - 10, y + 6, hr ? 0xFFFFFFFF : accent());
        String name = this.font.plainSubstrByWidth(label, w - 70);
        String num = (idx + 1) + "/" + count;
        g.drawCenteredString(this.font, name, x + w / 2 - 8, y + 6, COLOR_TEXT);
        g.drawString(this.font, num, x + w - 24 - this.font.width(num), y + 6, COLOR_MUTED, false);
    }

    /** -1 / +1 when an arrow of the selector on that row is clicked, else 0. */
    private static int pickerStep(int mx, int my, int row) {
        int x = suitPickerX();
        int y = cfgY(row) + 7;
        int w = 2 * SEG_W;
        return inside(mx, my, x, y, w / 2, SEG_H) ? -1 : inside(mx, my, x + w / 2, y, w / 2, SEG_H) ? 1 : 0;
    }

    private boolean clickSupermanConfig(int mx, int my) {
        if (inRow(mx, my, 0)) {
            boolean destroy = !com.flash.superman.SupermanState.destroyBlocks();
            com.flash.superman.SupermanState.setDestroyOptimistic(destroy);
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanSettingsPacket(destroy));
            click();
            return true;
        }
        if (inRow(mx, my, 1)) {
            FlashClientConfig.toggle(FlashClientConfig.SHOW_ABILITY_HUD);
            click();
            return true;
        }
        if (inRow(mx, my, 3)) {
            int step = pickerStep(mx, my, 3);
            if (step != 0 && this.minecraft.player != null) {
                int n = com.flash.superman.SupermanSuits.IDS.length;
                int idx = Math.floorMod(com.flash.superman.SupermanState.suit(this.minecraft.player) + step, n);
                com.flash.superman.SupermanState.setSuit(this.minecraft.player.getUUID(), idx, -1, 0L);
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanSuitPacket(idx));
                click();
            }
            return true;
        }
        if (inRow(mx, my, 4)) {
            int step = pickerStep(mx, my, 4);
            if (step != 0) {
                int idx = Math.floorMod(com.flash.superman.SupermanState.combo() + step,
                        com.flash.superman.SupermanCombos.COUNT);
                com.flash.superman.SupermanState.setOptions(idx, com.flash.superman.SupermanState.laserCut());
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanSettingsPacket(
                        com.flash.network.SupermanSettingsPacket.COMBO, idx));
                click();
            }
            return true;
        }
        if (inRow(mx, my, 5)) {
            boolean cut = !com.flash.superman.SupermanState.laserCut();
            com.flash.superman.SupermanState.setOptions(com.flash.superman.SupermanState.combo(), cut);
            FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanSettingsPacket(
                    com.flash.network.SupermanSettingsPacket.LASER_CUT, cut ? 1 : 0));
            click();
            return true;
        }
        return inRow(mx, my, 2);
    }

    /** Pixel shield (about 26 x 26): the diamond with an S. */
    /**
     * The heroes' own symbols, 13 x 13 drawn at 2 px: '#' the accent, '+' the second colour, 'o' white. Superman keeps
     * his shield, the Flash his bolt, the Hulk his fist.
     */
    private static final java.util.Map<String, String[]> HERO_ICONS = java.util.Map.ofEntries(
            java.util.Map.entry("thor", new String[] {
                    ".###########.", ".#+++++++++#.", ".#+++++++++#.", ".###########.", "......o......",
                    "......o......", "......#......", "......#......", "......#......", "......#......",
                    "......#......", ".....###.....", "......#......"}),
            java.util.Map.entry("yas", new String[] {
                    "......#......", "......#......", "...#..#..#...", "....#+#+#....", ".....+o+.....",
                    "##+++ooo+++##", ".....+o+.....", "....#+#+#....", "...#..#..#...", "......#......",
                    "......#......", ".............", "............."}),
            java.util.Map.entry("scarlet", new String[] {
                    "......#......", ".....###.....", "#....#+#....#", "##..#+++#..##", "#+#.#+o+#.#+#",
                    "#++##+++##++#", "#+++++++++++#", ".#+++++++++#.", "..#########..", ".............",
                    ".............", ".............", "............."}),
            java.util.Map.entry("storm", new String[] {
                    "....####.....", "..##++++##...", ".#++++++++##.", "#++++++++++#.", "#++++++++++#.",
                    ".##########..", "....##.......", "...##........", "..######.....", ".....##......",
                    "....##.......", "...##........", "..#.........."}),
            java.util.Map.entry("spider", new String[] {
                    "#....#.#....#", ".#...#.#...#.", "..#..###..#..", "...#.###.#...", "....#####....",
                    "#####+++#####", "....#+++#....", "...#.###.#...", "..#..###..#..", ".#...#.#...#.",
                    "#....#.#....#", ".............", "............."}),
            java.util.Map.entry("doom", new String[] {
                    "..#########..", ".#+++++++++#.", "#+++++++++++#", "#+##+++++##+#", "#+o#+++++#o+#",
                    "#+++++#+++++#", "#+++++#+++++#", "#++#######++#", "#++#.....#++#", ".#+#######+#.",
                    "..#+++++++#..", "...#######...", "............."}),
            java.util.Map.entry("doom_god", new String[] {
                    "#..#..#..#..#", "##.##.#.##.##", "#############", "#+++++++++++#", "#+##+++++##+#",
                    "#+o#+++++#o+#", "#+++++#+++++#", "#++#######++#", ".#+#.....#+#.", "..#########..",
                    ".............", ".............", "............."}),
            java.util.Map.entry("wonder", new String[] {
                    "#...#...#...#", "#...#...#...#", ".#.#.#.#.#.#.", ".#.#.#.#.#.#.", "..#...#...#..",
                    "..#...#...#..", ".............", "+++++++++++++", ".+++++++++++.", "..+++++++++..",
                    "...+++++++...", ".............", "............."}),
            java.util.Map.entry("lex", new String[] {
                    "....#####....", "..##+++++##..", ".#++#++++++#.", "#+++#+++++++#", "#+++#+++++++#",
                    "#+++#+++++++#", "#+++#+++++++#", "#+++######++#", ".#+++++++++#.", "..##+++++##..",
                    "....#####....", ".............", "............."}),
            java.util.Map.entry("ultron", new String[] {
                    "....#.#.#....", "..#########..", ".#+++++++++#.", "#+++++#+++++#", "#+##++#++##+#",
                    "#+o#++#++#o+#", "#+++++#+++++#", ".#+++###+++#.", ".#+#o#o#o#+#.", "..#+#####+#..",
                    "...#+++++#...", "....#####....", "............."}));

    private static int heroIconSecond(String id) {
        return switch (id) {
            case "thor" -> 0xFF9AA4B4;
            case "yas" -> 0xFFFFF6D8;
            case "scarlet" -> 0xFF8E1B2E;
            case "storm" -> 0xFF5A6E88;
            case "spider" -> 0xFF14234A;
            case "doom", "doom_god" -> 0xFF3A4A3E;
            case "wonder" -> 0xFFE8333C;
            case "lex" -> 0xFF4A2A70;
            case "ultron" -> 0xFF6A6E7A;
            default -> 0xFF333333;
        };
    }

    private static void drawPixelIcon(GuiGraphics g, int x, int y, String[] rows, int main, int second) {
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                int col = ch == '#' ? main : ch == '+' ? second : ch == 'o' ? 0xFFFFFFFF : 0;
                if (col != 0) {
                    g.fill(x + c * 2, y + r * 2, x + c * 2 + 2, y + r * 2 + 2, col);
                }
            }
        }
    }

    private void drawShield(GuiGraphics g, int x, int y, int c) {
        int gold = 0xFFFFC21A;
        int red = 0xFFE8262E;
        // pentagon: wide top, narrowing to the bottom point
        for (int row = 0; row < 26; row++) {
            int inset = row < 6 ? 6 - row : (row - 6) * 13 / 20;
            if (13 - inset <= 0) {
                break;
            }
            g.fill(x + inset, y + row, x + 26 - inset, y + row + 1, gold);
            if (row > 1 && row < 24 && 26 - 2 * inset > 4) {
                g.fill(x + inset + 2, y + row, x + 24 - inset, y + row + 1, red);
            }
        }
        Component s = Component.literal("S");
        scaledText(g, s, x + 13 - Math.round(this.font.width(s) * 1.3F / 2.0F), y + 6, 1.3F, gold);
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
                g.fill(x + i, y, x + i + 1, y + 4, lerpColor(FlashHudStyle.from(), FlashHudStyle.to(), f));
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
            return true; // information only: the Hulk always uses Alt + scroll
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
        int a = accent();
        // a little lit diamond, the title, a line fading out after it
        g.fill(x + 2, y, x + 3, y + 7, a);
        g.fill(x + 1, y + 1, x + 4, y + 6, a);
        g.fill(x, y + 3, x + 5, y + 4, a);
        g.fill(x + 2, y + 3, x + 3, y + 4, 0xFFFFFFFF);
        g.drawString(this.font, text, x + 9, y, COLOR_TEXT, false);
        int lx = x + 9 + this.font.width(text) + 6;
        for (int i = 0; i < 16; i++) {
            g.fill(lx + i * 4, y + 3, lx + i * 4 + 3, y + 4, withAlpha(a, 0.5F * (1.0F - i / 16.0F)));
        }
    }

    /** Neutral screen shown while the player has no power. */
    // ------------------------------------------------------------------
    // No powers: the heroes of the mod and how to get each one
    // ------------------------------------------------------------------

    /** Hero id (its advancement and its texts gui.flash.guide.<id>), number of steps. */
    private static final String[] GUIDE = {"flash", "hulk", "red_hulk", "superman", "thor", "lex", "yas", "scarlet",
            "storm", "spiderman", "doom", "wonderwoman"};
    private static final int[] GUIDE_STEPS = {4, 4, 4, 4, 3, 4, 5, 4, 4, 4, 5, 4};
    private static final int GUIDE_LIST_W = 168;
    private static final int GUIDE_ROW_H = 19;
    private static final int GUIDE_ROW_GAP = 1;
    private static int guideHero;
    /** How far the hero list is scrolled (pixels). */
    private static int guideScroll;

    private static int guideMaxScroll() {
        return Math.max(0, GUIDE.length * (GUIDE_ROW_H + GUIDE_ROW_GAP) - GUIDE_ROW_GAP - (CONTENT_BOTTOM - CONTENT_Y));
    }

    private static net.minecraft.world.item.ItemStack guideIcon(int i) {
        return new net.minecraft.world.item.ItemStack(switch (i) {
            case 0 -> com.flash.registry.ModItems.FLASH_RING.get();
            case 1 -> com.flash.registry.ModItems.HULK_ESSENCE_CAPSULE.get();
            case 2 -> com.flash.registry.ModItems.RED_HULK_ESSENCE_CAPSULE.get();
            case 3 -> com.flash.registry.ModItems.KRYPTONIAN_BLOOD_CAPSULE.get();
            case 4 -> com.flash.registry.ModItems.MJOLNIR.get();
            case 5 -> com.flash.registry.ModItems.LEX_HELMET.get();
            case 7 -> net.minecraft.world.item.Items.REDSTONE;
            case 8 -> net.minecraft.world.item.Items.LIGHTNING_ROD;
            case 9 -> net.minecraft.world.item.Items.COBWEB;
            case 10 -> net.minecraft.world.item.Items.ENCHANTING_TABLE;
            case 11 -> net.minecraft.world.item.Items.SHIELD;
            default -> net.minecraft.world.item.Items.NETHER_STAR;
        });
    }

    private static int guideRowY(int i) {
        return CONTENT_Y + i * (GUIDE_ROW_H + GUIDE_ROW_GAP) - guideScroll;
    }

    private void drawHeroGuide(GuiGraphics g, int mx, int my) {
        // the list (scrolls: the wheel over it, or dragging nothing: the bar shows where it is)
        guideScroll = Math.max(0, Math.min(guideMaxScroll(), guideScroll));
        g.enableScissor(Math.round((this.leftPos + PAD) * this.uiScale), Math.round((this.topPos + CONTENT_Y)
                * this.uiScale), Math.round((this.leftPos + PAD + GUIDE_LIST_W) * this.uiScale),
                Math.round((this.topPos + CONTENT_BOTTOM) * this.uiScale));
        boolean overList = inside(mx, my, PAD, CONTENT_Y, GUIDE_LIST_W, CONTENT_BOTTOM - CONTENT_Y);
        for (int i = 0; i < GUIDE.length; i++) {
            int y = guideRowY(i);
            if (y + GUIDE_ROW_H < CONTENT_Y || y > CONTENT_BOTTOM) {
                continue;
            }
            boolean hover = overList && inside(mx, my, PAD, y, GUIDE_LIST_W, GUIDE_ROW_H);
            boolean sel = i == guideHero;
            card(g, PAD, y, GUIDE_LIST_W, GUIDE_ROW_H, sel ? 1.0F : hover ? 0.5F : 0.0F);
            if (sel) {
                g.fill(PAD, y + 4, PAD + 2, y + GUIDE_ROW_H - 4, accent());
            }
            g.renderItem(guideIcon(i), PAD + 9, y + (GUIDE_ROW_H - 16) / 2);
            g.drawString(this.font, Component.translatable("gui.flash.guide." + GUIDE[i]), PAD + 32, y + 1,
                    sel ? COLOR_TEXT : COLOR_MUTED, false);
            g.drawString(this.font, Component.translatable("gui.flash.guide." + GUIDE[i] + ".sub"), PAD + 32,
                    y + 10, COLOR_DIM, false);
            if (HeroProgressClient.has(GUIDE[i])) {
                g.drawString(this.font, "✔", PAD + GUIDE_LIST_W - 14, y + (GUIDE_ROW_H - 8) / 2, accent(), false);
            }
        }
        g.disableScissor();
        int max = guideMaxScroll();
        if (max > 0) {
            int track = CONTENT_BOTTOM - CONTENT_Y;
            int thumb = Math.max(16, track * track / (track + max));
            int ty = CONTENT_Y + (track - thumb) * guideScroll / max;
            int bx = PAD + GUIDE_LIST_W + 2;
            g.fill(bx, CONTENT_Y, bx + 3, CONTENT_BOTTOM, 0x40FFFFFF);
            g.fill(bx, ty, bx + 3, ty + thumb, accent());
        }
        // the manual of the hero picked
        int x = PAD + GUIDE_LIST_W + 8;
        int w = PW - PAD - x;
        int h = CONTENT_BOTTOM - CONTENT_Y;
        card(g, x, CONTENT_Y, w, h, 0.0F);
        String id = GUIDE[guideHero];
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + 10, CONTENT_Y + 8, 0.0F);
        pose.scale(1.5F, 1.5F, 1.0F);
        g.renderItem(guideIcon(guideHero), 0, 0);
        pose.popPose();
        scaledText(g, Component.translatable("gui.flash.guide." + id), x + 40, CONTENT_Y + 9, 1.3F, COLOR_TEXT);
        boolean got = HeroProgressClient.has(id);
        Component status = Component.translatable(got ? "gui.flash.guide.got" : "gui.flash.guide.locked");
        g.drawString(this.font, status, x + w - 10 - this.font.width(status), CONTENT_Y + 12,
                got ? accent() : COLOR_DIM, false);
        g.drawString(this.font, Component.translatable("gui.flash.guide." + id + ".sub"), x + 40, CONTENT_Y + 23,
                COLOR_DIM, false);
        drawCredits(g, creditsKey(id), x + w - 8, CONTENT_BOTTOM - 10, w - 16);
        int y = CONTENT_Y + 40;
        int textW = w - 34;
        for (int step = 1; step <= GUIDE_STEPS[guideHero]; step++) {
            y = guideStep(g, x, y, textW, step, Component.translatable("gui.flash.guide." + id + "." + step,
                    HeroProgressClient.chaosChance()));
            if ("thor".equals(id) && step == 1) {
                y = drawWorthyChecklist(g, x + 26, y, w - 36);
            }
            if ("yas".equals(id) && step == 1) {
                y = drawYasHeroes(g, x + 26, y, w - 36);
            }
            if ("yas".equals(id) && step == 2) {
                y = drawYasItems(g, x + 26, y, w - 36);
            }
            if ("scarlet".equals(id) && step == 1) {
                y = drawNowCard(g, x + 26, y, w - 36, true,
                        Component.translatable("gui.flash.guide.scarlet.now", HeroProgressClient.chaosChance()));
            }
            if ("storm".equals(id) && step == 2) {
                Minecraft mc = Minecraft.getInstance();
                boolean thunder = mc.level != null && mc.level.isThundering();
                y = drawNowCard(g, x + 26, y, w - 36, thunder, Component.translatable(thunder
                        ? "gui.flash.guide.storm.thunder" : "gui.flash.guide.storm.no_thunder"));
            }
        }
    }

    /** A numbered step: the number in a little square, the text wrapped beside it. */
    private int guideStep(GuiGraphics g, int x, int y, int textW, int n, Component text) {
        g.fill(x + 10, y, x + 21, y + 11, withAlpha(accentDark(), 0.9F));
        String num = String.valueOf(n);
        g.drawString(this.font, num, x + 16 - this.font.width(num) / 2, y + 2, accent(), false);
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, textW);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 27, y + 2, COLOR_MUTED, false);
            y += 10;
        }
        return y + 6;
    }

    /** The way to be worthy: raid won, 100 hostile creatures (with the count), worthy. */
    private int drawWorthyChecklist(GuiGraphics g, int x, int y, int w) {
        boolean village = HeroProgressClient.has(com.flash.power.HeroAdvancements.VILLAGE);
        boolean hunter = HeroProgressClient.has(com.flash.power.HeroAdvancements.HUNTER);
        boolean worthy = HeroProgressClient.has(com.flash.power.HeroAdvancements.WORTHY);
        int kills = Math.min(com.flash.power.HeroAdvancements.KILLS_NEEDED, HeroProgressClient.kills());
        card(g, x, y - 2, w, 54, 0.0F);
        checkLine(g, x + 8, y + 3, village, Component.translatable("gui.flash.guide.thor.village"));
        checkLine(g, x + 8, y + 15, hunter, Component.translatable("gui.flash.guide.thor.hunter", kills));
        drawBar(g, x + 20, y + 26, w - 30, kills / (float) com.flash.power.HeroAdvancements.KILLS_NEEDED,
                hunter ? accent() : 0xFFB0B8C4);
        checkLine(g, x + 8, y + 39, worthy, Component.translatable("gui.flash.guide.thor.worthy"));
        return y + 60;
    }

    /** The heroes the transcendence asks for, in three columns. */
    private int drawYasHeroes(GuiGraphics g, int x, int y, int w) {
        String[] heroes = com.flash.power.HeroAdvancements.HEROES;
        int cols = 3;
        int rows = (heroes.length + cols - 1) / cols;
        card(g, x, y - 2, w, rows * 10 + 6, 0.0F);
        for (int i = 0; i < heroes.length; i++) {
            int cx = x + 8 + (i % cols) * (w / cols);
            int cy = y + 2 + (i / cols) * 10;
            String name = this.font.plainSubstrByWidth(Component.translatable(
                    "worthy".equals(heroes[i]) ? "gui.flash.guide.thor.worthy" : "gui.flash.guide." + heroes[i])
                    .getString(), w / cols - 22);
            checkLine(g, cx, cy, HeroProgressClient.has(heroes[i]), Component.literal(name));
        }
        return y + rows * 10 + 10;
    }

    /** What must be in the inventory (checked on the player's inventory now), in three columns. */
    private int drawYasItems(GuiGraphics g, int x, int y, int w) {
        net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
        if (p == null) {
            return y;
        }
        net.minecraft.world.item.Item[][] needs = {
                {com.flash.registry.ModItems.FLASH_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.HULK_ESSENCE_CAPSULE.get(),
                        com.flash.registry.ModItems.RED_HULK_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.KRYPTONIAN_BLOOD_CAPSULE.get()},
                {com.flash.registry.ModItems.THOR_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.SCARLET_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.STORM_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.SPIDER_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.DOOM_ESSENCE_CAPSULE.get()},
                {com.flash.registry.ModItems.WONDER_ESSENCE_CAPSULE.get()},
                {net.minecraft.world.item.Items.DRAGON_EGG},
                {net.minecraft.world.item.Items.NETHER_STAR}};
        int cols = 3;
        int rows = (needs.length + 1 + cols - 1) / cols;
        card(g, x, y - 2, w, rows * 10 + 6, 0.0F);
        for (int i = 0; i < needs.length; i++) {
            boolean has = false;
            for (net.minecraft.world.item.Item it : needs[i]) {
                has |= p.getInventory().countItem(it) > 0;
            }
            int cx = x + 8 + (i % cols) * (w / cols);
            int cy = y + 2 + (i / cols) * 10;
            String name = this.font.plainSubstrByWidth(Component.translatable("gui.flash.guide.yas.need." + i)
                    .getString(), w / cols - 22);
            checkLine(g, cx, cy, has, Component.literal(name));
        }
        int lex = 0;
        for (net.minecraft.world.item.Item it : new net.minecraft.world.item.Item[] {
                com.flash.registry.ModItems.LEX_HELMET.get(), com.flash.registry.ModItems.LEX_CHESTPLATE.get(),
                com.flash.registry.ModItems.LEX_LEGGINGS.get(), com.flash.registry.ModItems.LEX_BOOTS.get()}) {
            if (p.getInventory().countItem(it) > 0) {
                lex++;
            }
        }
        int li = needs.length;
        String lexName = this.font.plainSubstrByWidth(Component.translatable("gui.flash.guide.yas.lex", lex)
                .getString(), w / cols - 22);
        checkLine(g, x + 8 + (li % cols) * (w / cols), y + 2 + (li / cols) * 10, lex == 4, Component.literal(lexName));
        return y + rows * 10 + 10;
    }

    /** One line about now (the chance of the chaos, the weather), in a small card. */
    private int drawNowCard(GuiGraphics g, int x, int y, int w, boolean good, Component text) {
        card(g, x, y - 2, w, 14, 0.0F);
        checkLine(g, x + 8, y + 1, good, text);
        return y + 18;
    }

    private void checkLine(GuiGraphics g, int x, int y, boolean done, Component text) {
        g.drawString(this.font, done ? "✔" : "✘", x, y, done ? accent() : 0xFFFF5A5A, false);
        g.drawString(this.font, text, x + 12, y, done ? COLOR_TEXT : COLOR_MUTED, false);
    }

    /** Click in the hero guide: picks a hero. */
    private boolean clickHeroGuide(int mx, int my) {
        if (my < CONTENT_Y || my > CONTENT_BOTTOM) {
            return false;
        }
        for (int i = 0; i < GUIDE.length; i++) {
            if (inside(mx, my, PAD, guideRowY(i), GUIDE_LIST_W, GUIDE_ROW_H)) {
                if (guideHero != i) {
                    guideHero = i;
                    click();
                }
                return true;
            }
        }
        return false;
    }

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
        g.fillGradient(x + 1, y + 5, x + 3, y + h - 5, accent(), withAlpha(accent(), 0.1F));
        g.drawString(this.font, label, x + 10, y + 7, COLOR_MUTED, false);
        // the value shrinks to fit the card; the extra goes beside it when there is room, else on the label line
        // (cut to the room left there), so the texts never run over each other or out of the card
        float scale = Math.min(1.3F, (w - 20.0F) / Math.max(1, this.font.width(value)));
        scaledText(g, value, x + 10, y + 20, scale, COLOR_TEXT);
        int valueEnd = x + 10 + Math.round(this.font.width(value) * scale);
        int ew = this.font.width(extra);
        if (x + w - 8 - ew >= valueEnd + 8) {
            g.drawString(this.font, extra, x + w - 8 - ew, y + 24, accent(), false);
        } else {
            int room = w - 26 - this.font.width(label);
            String cut = this.font.plainSubstrByWidth(extra.getString(), Math.max(0, room));
            if (cut.length() < extra.getString().length() && cut.length() > 1) {
                cut = this.font.plainSubstrByWidth(extra.getString(), Math.max(0, room - this.font.width("…")))
                        + "…";
            }
            g.drawString(this.font, cut, x + w - 8 - this.font.width(cut), y + 7, accent(), false);
        }
    }

    /** A card of dark glass: lit from the top in the hero's colour, its corners lit, brighter on highlight. */
    private static void card(GuiGraphics g, int x, int y, int w, int h, float highlight) {
        int a = accent();
        round(g, x, y, w, h, 3, lerpColor(0x30FFFFFF, withAlpha(a, 0.85F), highlight));
        round(g, x + 1, y + 1, w - 2, h - 2, 2, lerpColor(0xF20E1117, 0xF4151A22, highlight));
        g.fillGradient(x + 2, y + 1, x + w - 2, y + Math.min(h - 1, 16), withAlpha(a, 0.09F + 0.14F * highlight),
                0x00000000);
        g.fill(x + 3, y + 1, x + w - 3, y + 2, 0x14FFFFFF);
        int c = withAlpha(a, 0.5F + 0.5F * highlight);
        g.fill(x + 1, y + 1, x + 6, y + 2, c);
        g.fill(x + 1, y + 1, x + 2, y + 6, c);
        g.fill(x + w - 6, y + h - 2, x + w - 1, y + h - 1, c);
        g.fill(x + w - 2, y + h - 6, x + w - 1, y + h - 1, c);
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
                // the level in use: lit through, its halo breathing out
                int grow = Math.round(2.0F + 2.0F * pulse);
                round(g, cx - grow, cy - grow, w + 2 * grow, h + 2 * grow, 5, withAlpha(accent(), 0.12F + 0.12F * pulse));
                round(g, cx - 1, cy - 1, w + 2, h + 2, 4, accent());
                round(g, cx, cy, w, h, 3, accentDark());
                g.fillGradient(cx + 1, cy + 1, cx + w - 1, cy + h - 1, withAlpha(accent(), 0.55F), 0x00000000);
                g.fill(cx + 2, cy + 1, cx + w - 2, cy + 2, 0x70FFFFFF);
            } else {
                card(g, cx, cy, w, h, hv);
                if (unlocked) {
                    g.fill(cx + 1, cy + h - 2, cx + w - 1, cy + h - 1, withAlpha(COLOR_GREEN, 0.35F + 0.4F * hv));
                }
            }

            Component number = Component.literal(Integer.toString(level));
            float scale = w >= 44 ? 1.5F : 1.25F;
            int nw = Math.round(this.font.width(number) * scale);
            int color = inUse ? 0xFFFFFFFF : unlocked ? lerpColor(COLOR_TEXT, 0xFFFFFFFF, hv) : COLOR_DIM;
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

    /** A glowing bar: ticks every tenth, the fill lit along its top, a light running in it, a hot tip. */
    private void drawBar(GuiGraphics g, int x, int y, int w, float fraction, int color) {
        int h = 8;
        round(g, x, y, w, h, 4, 0xFF07080B);
        round(g, x, y, w, 1, 0, 0x12FFFFFF);
        for (int i = 1; i < 10; i++) {
            int tx = x + w * i / 10;
            g.fill(tx, y + 2, tx + 1, y + h - 2, 0x18FFFFFF);
        }
        int filled = Math.round(w * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 2) {
            round(g, x, y - 1, filled, h + 2, 4, withAlpha(color, 0.25F));
            round(g, x, y, filled, h, 4, darker(color));
            g.fillGradient(x + 1, y + 1, x + filled - 1, y + h - 1, color, darker(color));
            g.fill(x + 2, y + 1, x + filled - 2, y + 2, 0x60FFFFFF);
            float sweep = (seconds() * 0.6F) % 1.0F;
            int sx = x + Math.round(filled * sweep);
            g.fill(Math.max(x + 1, sx - 10), y + 1, Math.min(x + filled - 1, sx + 10), y + h - 1, 0x22FFFFFF);
            g.fill(Math.max(x + 1, sx - 3), y + 1, Math.min(x + filled - 1, sx + 3), y + h - 1, 0x40FFFFFF);
            float pulse = 0.6F + 0.4F * (float) Math.sin(seconds() * 5.0F);
            g.fill(x + filled - 3, y - 1, x + filled, y + h + 1, withAlpha(0xFFFFFFFF, 0.6F * pulse));
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

    /** A glowing pill switch: the knob slides over and lights up. */
    private void drawSwitch(GuiGraphics g, String key, int x, int y, boolean on) {
        float t = ease(key, on, 12.0F);
        int a = accent();
        if (t > 0.01F) {
            round(g, x - 2, y - 2, SWITCH_W + 4, SWITCH_H + 4, 10, withAlpha(a, 0.18F * t));
        }
        round(g, x, y, SWITCH_W, SWITCH_H, 9, lerpColor(0x40FFFFFF, a, t));
        round(g, x + 1, y + 1, SWITCH_W - 2, SWITCH_H - 2, 8, lerpColor(0xFF0A0C10, accentDark(), t));
        g.fillGradient(x + 3, y + 2, x + SWITCH_W - 3, y + SWITCH_H / 2, withAlpha(0xFFFFFFFF, 0.06F + 0.08F * t),
                0x00000000);
        int knob = SWITCH_H - 6;
        int kx = x + 3 + Math.round((SWITCH_W - 6 - knob) * t);
        if (t > 0.01F) {
            round(g, kx - 2, y + 1, knob + 4, knob + 4, 7, withAlpha(a, 0.4F * t));
        }
        round(g, kx, y + 3, knob, knob, 6, lerpColor(0xFF5B626C, 0xFFFFFFFF, t));
        g.fill(kx + 3, y + 5, kx + knob - 3, y + 6, withAlpha(0xFFFFFFFF, 0.6F));
    }

    /** Two choices on one glowing pill; the lit half slides over to the one picked. */
    private void drawSegments(GuiGraphics g, String key, int y, String leftKey, String rightKey,
                              boolean right, int mx, int my) {
        float t = ease(key, right, 14.0F);
        int a = accent();
        int x0 = segX(0);
        round(g, x0, y, 2 * SEG_W, SEG_H, 6, 0x40FFFFFF);
        round(g, x0 + 1, y + 1, 2 * SEG_W - 2, SEG_H - 2, 5, 0xFF0A0C10);
        int hx = x0 + Math.round(SEG_W * t);
        round(g, hx + 1, y + 1, SEG_W - 2, SEG_H - 2, 5, withAlpha(a, 0.55F));
        round(g, hx + 2, y + 2, SEG_W - 4, SEG_H - 4, 4, accentDark());
        g.fillGradient(hx + 3, y + 2, hx + SEG_W - 3, y + SEG_H / 2, withAlpha(a, 0.35F), 0x00000000);
        g.fill(hx + 8, y + SEG_H - 3, hx + SEG_W - 8, y + SEG_H - 2, a);
        String[] keys = {leftKey, rightKey};
        for (int i = 0; i < 2; i++) {
            int sx = segX(i);
            boolean active = (i == 1) == right;
            float hv = hover(key + i, !active && inside(mx, my, sx, y, SEG_W, SEG_H));
            String label = this.font.plainSubstrByWidth(Component.translatable(keys[i]).getString(), SEG_W - 8);
            int color = active ? 0xFFFFFFFF : lerpColor(COLOR_MUTED, COLOR_TEXT, hv);
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

    private static boolean flashEquipment() {
        return !(PowerTheme.localHulk() || PowerTheme.localSuperman() || PowerTheme.localThor()
                || theme() == PowerTheme.LEX);
    }

    /**
     * Lex's suit: the pieces worn, the battery (with the time of normal use it still gives), how to charge it, and the
     * state of its systems.
     */
    private void drawLexSuitCard(GuiGraphics g, int x, int y, int w, int h) {
        card(g, x, y, w, h, 0.0F);
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.player.Player p = mc.player;
        if (p == null) {
            return;
        }
        net.minecraft.world.item.ItemStack chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        float energy = com.flash.item.LexArmorItem.isLex(chest) ? com.flash.item.LexArmorItem.energy(chest) : 0.0F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + 10, y + 8, 0.0F);
        pose.scale(1.5F, 1.5F, 1.0F);
        g.renderItem(new net.minecraft.world.item.ItemStack(com.flash.registry.ModItems.LEX_HELMET.get()), 0, 0);
        pose.popPose();
        g.drawString(this.font, Component.translatable("gui.flash.lex.suit_title"), x + 40, y + 10, COLOR_TEXT, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                Component.translatable("gui.flash.lex.suit_sub").getString(), w - 50), x + 40, y + 22, COLOR_DIM,
                false);
        // the four pieces
        int ry = y + 40;
        g.drawString(this.font, Component.translatable("gui.flash.lex.pieces"), x + 10, ry, COLOR_MUTED, false);
        ry += 12;
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
        net.minecraft.world.item.Item[] pieces = {com.flash.registry.ModItems.LEX_HELMET.get(),
                com.flash.registry.ModItems.LEX_CHESTPLATE.get(), com.flash.registry.ModItems.LEX_LEGGINGS.get(),
                com.flash.registry.ModItems.LEX_BOOTS.get()};
        int px = x + 10;
        for (int i = 0; i < 4; i++) {
            boolean on = com.flash.item.LexArmorItem.isLex(p.getItemBySlot(slots[i]));
            g.fill(px - 1, ry - 1, px + 19, ry + 19, on ? withAlpha(accentDark(), 0.9F) : 0x40000000);
            g.renderItem(new net.minecraft.world.item.ItemStack(pieces[i]), px + 1, ry + 1);
            g.drawString(this.font, on ? "✔" : "✘", px + 13, ry + 12, on ? accent() : 0xFFFF5A5A, true);
            px += 24;
        }
        ry += 26;
        // battery
        g.drawString(this.font, Component.translatable("gui.flash.lex.battery"), x + 10, ry, COLOR_MUTED, false);
        String pct = Math.round(energy) + "%";
        g.drawString(this.font, pct, x + w - 10 - this.font.width(pct), ry, energy < 20.0F ? 0xFFFF6060 : accent(),
                false);
        ry += 11;
        drawBar(g, x + 10, ry, w - 20, energy / 100.0F, energy < 20.0F ? 0xFFFF3A3A : 0xFF6CFF6C);
        ry += 9;
        int minutes = Math.round(energy / 100.0F * 20.0F);
        g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.flash.lex.autonomy",
                minutes).getString(), w - 20), x + 10, ry, COLOR_DIM, false);
        ry += 16;
        // charging
        g.renderItem(new net.minecraft.world.item.ItemStack(com.flash.registry.ModItems.FLASH_SUIT_STAND.get()), x + 10,
                ry);
        g.drawString(this.font, "+", x + 30, ry + 4, accent(), false);
        g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE), x + 38, ry);
        g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.flash.lex.charge_how")
                .getString(), w - 70), x + 60, ry + 4, COLOR_MUTED, false);
        ry += 22;
        // systems
        String[][] states = {
                {"gui.flash.lex.state_power", com.flash.lex.LexState.flag(p, com.flash.lex.LexSuit.F_ON) ? "on" : "off"},
                {"gui.flash.lex.state_faceplate", com.flash.lex.LexState.flag(p, com.flash.lex.LexSuit.F_OPEN)
                        ? "open" : "closed"},
                {"gui.flash.lex.state_overdrive", com.flash.lex.LexState.flag(p, com.flash.lex.LexSuit.F_OVERDRIVE)
                        ? "on" : "off"}};
        for (String[] st : states) {
            if (ry + 10 > y + h - 14) {
                break;
            }
            g.drawString(this.font, Component.translatable(st[0]), x + 10, ry, COLOR_MUTED, false);
            Component v = Component.translatable("gui.flash.lex.state." + st[1]);
            boolean good = st[1].equals("on") || st[1].equals("closed");
            g.drawString(this.font, v, x + w - 10 - this.font.width(v), ry, good ? accent() : COLOR_DIM, false);
            ry += 11;
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.flash.lex.indestructible")
                .getString(), w - 20), x + 10, y + h - 13, 0xFFFFC857, false);
    }

    /** The Processor card: the machine of every hero and what it makes. */
    private void drawProcessorCard(GuiGraphics g, int x, int y, int w, int h) {
        card(g, x, y, w, h, 0.0F);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + 10, y + 8, 0.0F);
        pose.scale(1.5F, 1.5F, 1.0F);
        g.renderItem(new net.minecraft.world.item.ItemStack(com.flash.registry.ModItems.TACHYON_EXTRACTOR.get()), 0, 0);
        pose.popPose();
        g.drawString(this.font, Component.translatable("block.flash.tachyon_extractor"), x + 40, y + 10, COLOR_TEXT,
                false);
        g.drawString(this.font, this.font.plainSubstrByWidth(
                Component.translatable("gui.flash.equip.processor_help").getString(), w - 50), x + 40, y + 22,
                COLOR_DIM, false);
        Object[][] rows = {
                {com.flash.registry.ModItems.TORN_SUIT.get(), "gui.flash.equip.processor_suit",
                        com.flash.registry.ModItems.TACHYON.get()},
                {net.minecraft.world.item.Items.NETHER_WART, "gui.flash.equip.processor_fungi",
                        com.flash.registry.ModItems.CORRUPTED_MATTER.get()},
                {com.flash.registry.ModItems.KRYPTONIAN_CELL.get(), "gui.flash.equip.processor_cells",
                        com.flash.registry.ModItems.KRYPTONIAN_BLOOD_CAPSULE.get()},
                {com.flash.registry.ModItems.HULK_ESSENCE_CAPSULE.get(), "gui.flash.equip.processor_gamma",
                        com.flash.registry.ModItems.RED_HULK_ESSENCE_CAPSULE.get()}};
        int ry = y + 40;
        for (Object[] r : rows) {
            if (ry + 18 > y + h) {
                break;
            }
            g.renderItem(new net.minecraft.world.item.ItemStack((net.minecraft.world.item.Item) r[0]), x + 10, ry);
            g.drawString(this.font, "→", x + 30, ry + 4, accent(), false);
            g.renderItem(new net.minecraft.world.item.ItemStack((net.minecraft.world.item.Item) r[2]), x + 40, ry);
            g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable((String) r[1]).getString(),
                    w - 70), x + 62, ry + 4, COLOR_MUTED, false);
            ry += 20;
        }
    }

    private void drawEquipTab(GuiGraphics g, int mx, int my) {
        if (!flashEquipment()) {
            // the other heroes: the Processor, next to the preview of the hero (Lex: his suit instead)
            if (theme() == PowerTheme.LEX) {
                drawLexSuitCard(g, PAD, CONTENT_Y, EQ_W, CONTENT_BOTTOM - CONTENT_Y);
            } else {
                drawProcessorCard(g, PAD, CONTENT_Y, EQ_W, CONTENT_BOTTOM - CONTENT_Y);
            }
            int vx = PAD + EQ_W + 10;
            int vw = PW - PAD - vx;
            card(g, vx, CONTENT_Y, vw, CONTENT_BOTTOM - CONTENT_Y, 0.0F);
            g.fillGradient(vx + 1, CONTENT_Y + 1, vx + vw - 1, CONTENT_BOTTOM - 1,
                    withAlpha(accentDark(), 0.8F), 0x00000000);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int px = vx + vw / 2;
                int py = CONTENT_BOTTOM - 26;
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, px, py, 80, (float) (px - mx), (float) (py - 120 - my), mc.player);
            }
            return;
        }
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

        // live preview of the player (ring on the hand, amplifier on the chest), the Processor under it
        int vx = PAD + EQ_W + 10;
        int vw = PW - PAD - vx;
        int procH = 112;
        int previewBottom = CONTENT_BOTTOM - procH - 8;
        card(g, vx, CONTENT_Y, vw, previewBottom - CONTENT_Y, 0.0F);
        g.fillGradient(vx + 1, CONTENT_Y + 1, vx + vw - 1, previewBottom - 1,
                withAlpha(accentDark(), 0.8F), 0x00000000);
        g.fill(vx + 10, previewBottom - 12, vx + vw - 10, previewBottom - 11, withAlpha(accent(), 0.4F));
        drawProcessorCard(g, vx, previewBottom + 8, vw, procH);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int px = vx + vw / 2;
            int py = previewBottom - 12;
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, px, py, 45, (float) (px - mx), (float) (py - 70 - my), mc.player);
        }
    }

    private void slot(GuiGraphics g, int x, int y, boolean filled, net.minecraft.world.item.Item item) {
        float t = ease("slot" + item.hashCode(), filled, 10.0F);
        if (t > 0.01F) {
            round(g, x - 3, y - 3, SLOT + 6, SLOT + 6, 8, withAlpha(accent(), 0.15F * t));
        }
        round(g, x, y, SLOT, SLOT, 6, lerpColor(0x30FFFFFF, accent(), t));
        round(g, x + 1, y + 1, SLOT - 2, SLOT - 2, 5, 0xFF07080B);
        g.fillGradient(x + 2, y + 2, x + SLOT - 2, y + SLOT - 2, withAlpha(accent(), 0.3F * t), 0x00000000);
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
            round(g, x, y, w, h, 5, 0x30FFFFFF);
            round(g, x + 1, y + 1, w - 2, h - 2, 4, 0xFF0A0C10);
        } else {
            if (hv > 0.01F) {
                round(g, x - 2, y - 2, w + 4, h + 4, 7, withAlpha(accent(), 0.25F * hv));
            }
            round(g, x, y, w, h, 5, lerpColor(withAlpha(accent(), 0.6F), accent(), hv));
            round(g, x + 1, y + 1, w - 2, h - 2, 4, lerpColor(0xFF0E1117, accentDark(), 0.4F + 0.6F * hv));
            g.fillGradient(x + 2, y + 1, x + w - 2, y + h / 2, withAlpha(accent(), 0.2F + 0.25F * hv), 0x00000000);
        }
        String text = this.font.plainSubstrByWidth(label.getString(), w - 8);
        g.drawString(this.font, text, x + (w - this.font.width(text)) / 2, y + (h - 8) / 2,
                enabled ? lerpColor(COLOR_TEXT, 0xFFFFFFFF, hv) : COLOR_DIM, false);
    }

    private boolean clickEquip(double mx, double my) {
        if (!flashEquipment()) {
            return false;
        }
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
            if (inside(ix, iy, navX(), navY(i), navW(), NAV_H)) {
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
        if (activeTab() == TAB_PROGRESS && (PowerTheme.localYas() || PowerTheme.localScarlet()
                || PowerTheme.localOwnModes())
                && clickYasTab(ix, iy)) {
            return true;
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localSpider()) {
            return clickSpiderConfig(ix, iy) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localWonder()) {
            return clickWonderConfig(ix, iy) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localYas()) {
            return clickYasConfig(ix, iy) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localScarlet()) {
            if (inRow(ix, iy, 0)) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.ScarletSettingsPacket(
                        !ScarletClient.localAura()));
                click();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localThor()) {
            return clickThorConfig(ix, iy) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab() == TAB_CONFIG && PowerTheme.localSuperman()) {
            return clickSupermanConfig(ix, iy) || super.mouseClicked(mouseX, mouseY, button);
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

        if (activeTab() == TAB_PROGRESS && !FlashClientData.hasPowers() && theme() != PowerTheme.LEX
                && !PowerTheme.localYas() && !PowerTheme.localScarlet() && !PowerTheme.localOwnModes()
                && !PowerTheme.localThor() && !PowerTheme.localSuperman() && !PowerTheme.localHulk()
                && clickHeroGuide(ix, iy)) {
            return true;
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
        if (FlashClientForgeEvents.selectorAvailable() && !FlashClientConfig.SCROLL_MODE.get()
                && !PowerTheme.localHulk()) {
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
        if (!Screen.hasAltDown() && !FlashClientData.hasPowers() && delta != 0.0D) {
            double mx = mouseX / this.uiScale - this.leftPos;
            double my = mouseY / this.uiScale - this.topPos;
            if (inside((int) mx, (int) my, PAD, CONTENT_Y, GUIDE_LIST_W + 6, CONTENT_BOTTOM - CONTENT_Y)) {
                guideScroll = Math.max(0, Math.min(guideMaxScroll(), guideScroll - (int) Math.signum(delta) * 20));
                return true;
            }
        }
        if ((FlashClientConfig.SCROLL_MODE.get() || PowerTheme.localHulk())
                && minecraft.player != null
                && Screen.hasAltDown()
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

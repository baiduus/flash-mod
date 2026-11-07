package com.flash.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared look of the Flash HUD: everything uses the orange-to-yellow gradient
 * (dark red to bright red for the Negative Acceleration Force).
 */
final class FlashHudStyle {

    private static final int ORANGE = 0xFFFF6A00;
    private static final int YELLOW = 0xFFFFD21F;
    private static final int RED_DARK = 0xFF9E0B12;
    private static final int RED_BRIGHT = 0xFFFF3B30;
    static final int PANEL = 0xB0000000;
    static final int PANEL_DIM = 0xD0000000;
    static final int TEXT_MUTED = 0xFFCFC6B8;
    static final int WHITE = 0xFFFFFFFF;

    private FlashHudStyle() {
    }

    /** Start colour of the gradient: orange, or dark red for the Negative force. */
    static final int HULK_GREEN = 0xFF3FD84A;
    static final int HULK_PURPLE = 0xFFA040F0;
    static final int RED_HULK_LIGHT = 0xFFFF5A3C;
    static final int RED_HULK_DARK = 0xFF8E0C10;

    static final int SUPER_BLUE = 0xFF2F7BFF;
    static final int SUPER_RED = 0xFFFF3A3A;

    static int from() {
        if (PowerTheme.localSuperman()) {
            return SUPER_BLUE;
        }
        if (PowerTheme.localHulk()) {
            return PowerTheme.localRedHulk() ? RED_HULK_LIGHT : HULK_GREEN;
        }
        if (FlashClientVisualLevels.isLocalV9()) {
            return 0xFF1A5CFF;
        }
        return FlashClientVisualLevels.isLocalNegative() ? RED_DARK : ORANGE;
    }

    /** End colour of the gradient: yellow, or bright red for the Negative force. */
    static int to() {
        if (PowerTheme.localSuperman()) {
            return SUPER_RED;
        }
        if (PowerTheme.localHulk()) {
            return PowerTheme.localRedHulk() ? RED_HULK_DARK : HULK_PURPLE;
        }
        if (FlashClientVisualLevels.isLocalV9()) {
            return 0xFF7FE0FF;
        }
        return FlashClientVisualLevels.isLocalNegative() ? RED_BRIGHT : YELLOW;
    }

    static int lerp(float t) {
        int a = from();
        int c = to();
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) (((a >> 16) & 0xFF) * (1.0F - t) + ((c >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1.0F - t) + ((c >> 8) & 0xFF) * t);
        int b = (int) ((a & 0xFF) * (1.0F - t) + (c & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Vertical bar, orange on the bottom and yellow on the top. */
    static void barVertical(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, to(), from());
    }

    /** Horizontal bar, orange on the left to yellow on the right. */
    static void barHorizontal(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 0; i < w; i++) {
            g.fill(x + i, y, x + i + 1, y + h, lerp(w <= 1 ? 0.0F : i / (float) (w - 1)));
        }
    }

    /** Text colored character by character from orange to yellow. */
    static void gradientText(GuiGraphics g, Font font, String text, int x, int y) {
        int n = text.length();
        int cx = x;
        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(font, ch, cx, y, lerp(n <= 1 ? 0.0F : i / (float) (n - 1)), true);
            cx += font.width(ch);
        }
    }
}

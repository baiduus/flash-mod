package com.flash.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared look of the Flash HUD: everything uses the orange-to-yellow gradient.
 */
final class FlashHudStyle {

    static final int ORANGE = 0xFFFF6A00;
    static final int YELLOW = 0xFFFFD21F;
    static final int PANEL = 0xB0000000;
    static final int PANEL_DIM = 0xD0000000;
    static final int TEXT_MUTED = 0xFFCFC6B8;
    static final int WHITE = 0xFFFFFFFF;

    private FlashHudStyle() {
    }

    static int lerp(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) (((ORANGE >> 16) & 0xFF) * (1.0F - t) + ((YELLOW >> 16) & 0xFF) * t);
        int g = (int) (((ORANGE >> 8) & 0xFF) * (1.0F - t) + ((YELLOW >> 8) & 0xFF) * t);
        int b = (int) ((ORANGE & 0xFF) * (1.0F - t) + (YELLOW & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Vertical bar, orange on the bottom and yellow on the top. */
    static void barVertical(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, YELLOW, ORANGE);
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

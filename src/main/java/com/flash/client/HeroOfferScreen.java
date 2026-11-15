package com.flash.client;

import com.flash.network.FlashNetwork;
import com.flash.network.HeroAnswerPacket;
import com.flash.network.HeroSyncPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The question of a new power: the bite of the cave spider (Spider-Man, a web in the corners) or the grimoire of
 * Latveria (Doctor Doom, green runes). Yes / no (HeroAnswerPacket); closing it is refusing.
 */
public class HeroOfferScreen extends Screen {

    private static final int W = 300;
    private static final int H = 150;
    private final int hero;
    private final String key;
    private boolean answered;
    private int ticks;

    /** Opened by HeroOfferPacket (here, so the packet never names a client class on a server). */
    public static void open(int hero) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new HeroOfferScreen(hero));
    }

    public HeroOfferScreen(int hero) {
        super(Component.translatable("gui.flash." + (hero == HeroSyncPacket.SPIDER ? "spider" : "doom")
                + "_offer.title"));
        this.hero = hero;
        this.key = "gui.flash." + (hero == HeroSyncPacket.SPIDER ? "spider" : "doom") + "_offer.";
    }

    @Override
    protected void init() {
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable(this.key + "yes"), b -> answer(true))
                .bounds(x + 20, y + H - 32, 125, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable(this.key + "no"), b -> answer(false))
                .bounds(x + W - 145, y + H - 32, 125, 20).build());
    }

    private void answer(boolean yes) {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new HeroAnswerPacket(this.hero, yes));
        }
        this.onClose();
    }

    @Override
    public void removed() {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new HeroAnswerPacket(this.hero, false));
        }
        super.removed();
    }

    @Override
    public void tick() {
        this.ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        float t = this.ticks + partial;
        boolean spider = this.hero == HeroSyncPacket.SPIDER;
        float pulse = 0.75F + 0.25F * Mth.sin(t * 0.25F);
        int border = spider ? lerp(0xFF7A1418, 0xFFE8333C, pulse) : lerp(0xFF1C5A28, 0xFF6CFF8A, pulse);
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, border);
        g.fill(x, y, x + W, y + H, spider ? 0xF00A0F22 : 0xF0081208);
        g.fillGradient(x, y, x + W, y + 30, spider ? 0x60E8333C : 0x604FD86A, 0x00000000);
        if (spider) {
            web(g, x + 2, y + 2, 1, 1);
            web(g, x + W - 2, y + 2, -1, 1);
        } else {
            rune(g, x + 14, y + 14, t);
            rune(g, x + W - 14, y + 14, -t);
        }
        Component title = this.title;
        g.drawString(this.font, title, x + (W - this.font.width(title)) / 2, y + 10, spider ? 0xFFFFD6D8 : 0xFFD6FFDD,
                true);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable(this.key + "text"), W - 30);
        int ty = y + 30;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 15, ty, 0xFFE0E4EC, false);
            ty += 11;
        }
        Component q = Component.translatable(this.key + "question");
        g.drawString(this.font, q, x + (W - this.font.width(q)) / 2, y + H - 50, 0xFFFFFFFF, true);
        super.render(g, mx, my, partial);
    }

    /** A little web in a corner (sx, sy: which way it opens). */
    private static void web(GuiGraphics g, int x, int y, int sx, int sy) {
        int c = 0xA0E8E8F0;
        for (int i = 0; i < 18; i++) {
            g.fill(x + sx * i, y, x + sx * i + 1, y + 1, c);
            g.fill(x, y + sy * i, x + 1, y + sy * i + 1, c);
            g.fill(x + sx * (i * 7 / 10), y + sy * (i * 7 / 10), x + sx * (i * 7 / 10) + 1, y + sy * (i * 7 / 10) + 1,
                    c);
        }
        for (int r = 5; r <= 15; r += 5) {
            for (int k = 0; k <= r; k++) {
                int px = x + sx * k;
                int py = y + sy * (r - k);
                g.fill(px, py, px + 1, py + 1, 0x80E8E8F0);
            }
        }
    }

    /** A turning rune of green light. */
    private static void rune(GuiGraphics g, int cx, int cy, float t) {
        for (int k = 0; k < 6; k++) {
            double a = t * 0.08D + Math.PI * 2.0D * k / 6.0D;
            int px = cx + (int) Math.round(Math.cos(a) * 8.0D);
            int py = cy + (int) Math.round(Math.sin(a) * 8.0D);
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xC06CFF8A);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFB8FFC6);
    }

    private static int lerp(int a, int b, float f) {
        f = Mth.clamp(f, 0.0F, 1.0F);
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * f) << 16 | (int) (ag + (bg - ag) * f) << 8
                | (int) (ab + (bb - ab) * f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

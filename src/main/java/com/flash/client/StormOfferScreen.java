package com.flash.client;

import com.flash.network.FlashNetwork;
import com.flash.network.StormAnswerPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** The lightning that struck him asks: receive the power of the storms (Storm) or refuse (StormAnswerPacket). */
public class StormOfferScreen extends Screen {

    private static final int W = 300;
    private static final int H = 150;
    private boolean answered;
    private int ticks;

    /** Opened by StormOfferPacket (here, so the packet never names a client class on a server). */
    public static void open() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new StormOfferScreen());
    }

    public StormOfferScreen() {
        super(Component.translatable("gui.flash.storm_offer.title"));
    }

    @Override
    protected void init() {
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.storm_offer.yes"), b -> answer(true))
                .bounds(x + 20, y + H - 32, 125, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.storm_offer.no"), b -> answer(false))
                .bounds(x + W - 145, y + H - 32, 125, 20).build());
    }

    private void answer(boolean yes) {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new StormAnswerPacket(yes));
        }
        this.onClose();
    }

    /** Closing it (Esc) is refusing. */
    @Override
    public void removed() {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new StormAnswerPacket(false));
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
        // the border flickers as lightning does
        float flicker = 0.75F + 0.25F * Mth.sin(t * 0.9F) * Mth.sin(t * 0.37F);
        int border = lerp(0xFF2E6FB8, 0xFFBFE6FF, flicker);
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, border);
        g.fill(x, y, x + W, y + H, 0xF0050B16);
        g.fillGradient(x, y, x + W, y + 30, 0x6080C8FF, 0x0080C8FF);
        drawArc(g, x + 8, y + 4, t);
        drawArc(g, x + W - 8, y + 4, t + 7.0F);
        Component title = Component.translatable("gui.flash.storm_offer.title");
        g.drawString(this.font, title, x + (W - this.font.width(title)) / 2, y + 10, 0xFFCFEFFF, true);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable("gui.flash.storm_offer.text"),
                W - 30);
        int ty = y + 30;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 15, ty, 0xFFD6E4F0, false);
            ty += 11;
        }
        Component q = Component.translatable("gui.flash.storm_offer.question");
        g.drawString(this.font, q, x + (W - this.font.width(q)) / 2, y + H - 50, 0xFFFFFFFF, true);
        super.render(g, mx, my, partial);
    }

    /** A little zigzag bolt in a corner of the window, changing shape now and then. */
    private static void drawArc(GuiGraphics g, int x, int y, float t) {
        int seed = (int) (t / 3.0F);
        int px = x;
        for (int i = 0; i < 5; i++) {
            int h = (int) (Math.abs(Mth.sin(seed * 12.9898F + i * 78.233F)) * 6.0F) - 3;
            int nx = x + h;
            int top = y + i * 4;
            g.fill(Math.min(px, nx), top, Math.max(px, nx) + 1, top + 1, 0xC0BFE6FF);
            g.fill(nx, top, nx + 1, top + 4, 0xE0E8F6FF);
            px = nx;
        }
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

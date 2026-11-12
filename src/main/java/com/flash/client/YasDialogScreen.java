package com.flash.client;

import com.flash.network.FlashNetwork;
import com.flash.network.YasAnswerPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The Guardian's question: does he wish to transcend? Yes or no (YasAnswerPacket). */
public class YasDialogScreen extends Screen {

    private static final int W = 300;
    private static final int H = 150;
    private final int guardian;
    private boolean answered;

    public YasDialogScreen(int guardian) {
        super(Component.translatable("gui.flash.yas.dialog.title"));
        this.guardian = guardian;
    }

    @Override
    protected void init() {
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.yas.dialog.yes"), b -> answer(true))
                .bounds(x + 20, y + H - 32, 125, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.yas.dialog.no"), b -> answer(false))
                .bounds(x + W - 145, y + H - 32, 125, 20).build());
    }

    private void answer(boolean yes) {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new YasAnswerPacket(this.guardian, yes));
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g);
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFFE8D27A);
        g.fill(x, y, x + W, y + H, 0xF0100C18);
        g.fillGradient(x, y, x + W, y + 30, 0x40FFF4B0, 0x00FFF4B0);
        Component title = Component.translatable("gui.flash.yas.dialog.title");
        g.drawString(this.font, title, x + (W - this.font.width(title)) / 2, y + 10, 0xFFFFF4C0, true);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable("gui.flash.yas.dialog.text"),
                W - 30);
        int ty = y + 30;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 15, ty, 0xFFD8D2E8, false);
            ty += 11;
        }
        Component q = Component.translatable("gui.flash.yas.dialog.question");
        g.drawString(this.font, q, x + (W - this.font.width(q)) / 2, y + H - 50, 0xFFFFFFFF, true);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

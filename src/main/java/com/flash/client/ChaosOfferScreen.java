package com.flash.client;

import com.flash.network.FlashNetwork;
import com.flash.network.ScarletAnswerPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The energy of chaos asks: give in to chaos (the Scarlet Witch's powers) or refuse (ScarletAnswerPacket). */
public class ChaosOfferScreen extends Screen {

    private static final int W = 300;
    private static final int H = 150;
    private boolean answered;

    public ChaosOfferScreen() {
        super(Component.translatable("gui.flash.chaos.title"));
    }

    @Override
    protected void init() {
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.chaos.yes"), b -> answer(true))
                .bounds(x + 20, y + H - 32, 125, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.chaos.no"), b -> answer(false))
                .bounds(x + W - 145, y + H - 32, 125, 20).build());
    }

    private void answer(boolean yes) {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new ScarletAnswerPacket(yes));
        }
        this.onClose();
    }

    /** Closing it (Esc) is refusing. */
    @Override
    public void removed() {
        if (!this.answered) {
            this.answered = true;
            FlashNetwork.CHANNEL.sendToServer(new ScarletAnswerPacket(false));
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFFE0263A);
        g.fill(x, y, x + W, y + H, 0xF0140408);
        g.fillGradient(x, y, x + W, y + 30, 0x60E0263A, 0x00E0263A);
        Component title = Component.translatable("gui.flash.chaos.title");
        g.drawString(this.font, title, x + (W - this.font.width(title)) / 2, y + 10, 0xFFFF6A7A, true);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable("gui.flash.chaos.text"), W - 30);
        int ty = y + 30;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 15, ty, 0xFFE8D2D6, false);
            ty += 11;
        }
        Component q = Component.translatable("gui.flash.chaos.question");
        g.drawString(this.font, q, x + (W - this.font.width(q)) / 2, y + H - 50, 0xFFFFFFFF, true);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.flash.client.infinity;

import com.flash.infinity.InfinityServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Mind Stone's disguise: a Minecraft username to wear that player's skin and name; Reset for your own. */
public class DisguiseScreen extends Screen {

    private EditBox name;

    public DisguiseScreen() {
        super(Component.translatable("gui.flash.infinity.disguise"));
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;
        this.name = new EditBox(this.font, x - 75, y - 20, 150, 18, Component.empty());
        this.name.setMaxLength(16);
        this.addRenderableWidget(this.name);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.infinity.disguise_apply"),
                b -> send(this.name.getValue())).bounds(x - 75, y + 4, 72, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.infinity.disguise_reset"),
                b -> send("")).bounds(x + 3, y + 4, 72, 20).build());
        this.setInitialFocus(this.name);
    }

    private void send(String v) {
        InfinityClient.send(InfinityServer.DISGUISE, v);
        this.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            send(this.name.getValue());
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFE060);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

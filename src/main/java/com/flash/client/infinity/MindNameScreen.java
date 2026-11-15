package com.flash.client.infinity;

import com.flash.infinity.InfinityServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read a mind by name (nothing in sight, the Soul Stone's locating unlocked). */
public class MindNameScreen extends Screen {

    private EditBox name;

    public MindNameScreen() {
        super(Component.translatable("gui.flash.infinity.mind_read"));
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;
        this.name = new EditBox(this.font, x - 75, y - 20, 150, 18, Component.translatable(
                "gui.flash.infinity.entity_name"));
        this.name.setMaxLength(64);
        this.addRenderableWidget(this.name);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.infinity.read"), b -> apply())
                .bounds(x - 75, y + 4, 72, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(x + 3, y + 4, 72, 20).build());
        this.setInitialFocus(this.name);
    }

    private void apply() {
        if (!this.name.getValue().isBlank()) {
            InfinityClient.send(InfinityServer.MIND_NAME, this.name.getValue());
        }
        this.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            apply();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, Component.translatable("gui.flash.infinity.entity_name"), this.width / 2,
                this.height / 2 - 36, 0xFFFFE060);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

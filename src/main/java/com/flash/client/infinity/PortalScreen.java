package com.flash.client.infinity;

import com.flash.infinity.InfinityServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Space Stone's portal menu (the original's PortalScreen, its 172x172 background): a dimension (Tab completes it
 * from the ones the server has) and coordinates; Open makes the pair of portals.
 */
public class PortalScreen extends Screen {

    private static final ResourceLocation BG = new ResourceLocation("flash", "textures/gui/portal.png");
    private EditBox dim;
    private EditBox x;
    private EditBox y;
    private EditBox z;

    public PortalScreen() {
        super(Component.translatable("gui.flash.infinity.portal"));
    }

    @Override
    protected void init() {
        int l = (this.width - 172) / 2;
        int t = (this.height - 172) / 2;
        Minecraft mc = Minecraft.getInstance();
        this.dim = new EditBox(this.font, l + 16, t + 49, 140, 16, Component.empty());
        this.dim.setMaxLength(128);
        this.dim.setValue(mc.level == null ? "minecraft:overworld" : mc.level.dimension().location().toString());
        this.x = coord(l + 16, t + 75, mc.player == null ? 0 : (int) mc.player.getX());
        this.y = coord(l + 66, t + 75, mc.player == null ? 64 : (int) mc.player.getY());
        this.z = coord(l + 116, t + 75, mc.player == null ? 0 : (int) mc.player.getZ());
        this.addRenderableWidget(this.dim);
        this.addRenderableWidget(this.x);
        this.addRenderableWidget(this.y);
        this.addRenderableWidget(this.z);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.flash.infinity.portal_open"), b -> open())
                .bounds(l + 36, t + 104, 100, 20).build());
        this.setInitialFocus(this.dim);
    }

    private EditBox coord(int px, int py, int value) {
        EditBox b = new EditBox(this.font, px, py, 40, 16, Component.empty());
        b.setValue(String.valueOf(value));
        b.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,8}"));
        return b;
    }

    private List<String> dimensions() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (var k : mc.getConnection().levels()) {
                out.add(k.location().toString());
            }
        }
        return out;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 258 && this.dim.isFocused()) {
            // Tab: the next dimension matching what is typed
            String typed = this.dim.getValue();
            List<String> d = dimensions();
            for (int i = 0; i < d.size(); i++) {
                if (d.get(i).startsWith(typed) && !d.get(i).equals(typed)) {
                    this.dim.setValue(d.get(i));
                    return true;
                }
            }
            if (!d.isEmpty()) {
                int i = d.indexOf(typed);
                this.dim.setValue(d.get((i + 1) % d.size()));
            }
            return true;
        }
        if (key == 257) {
            open();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void open() {
        try {
            String v = this.dim.getValue().trim() + "|" + Integer.parseInt(this.x.getValue()) + "|"
                    + Integer.parseInt(this.y.getValue()) + "|" + Integer.parseInt(this.z.getValue());
            InfinityClient.send(InfinityServer.PORTAL, v);
            this.onClose();
        } catch (NumberFormatException ignored) {
            // a coordinate left empty
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g);
        int l = (this.width - 172) / 2;
        int t = (this.height - 172) / 2;
        g.blit(BG, l, t, 0, 0, 172, 172, 172, 172);
        g.drawCenteredString(this.font, this.title, this.width / 2, t + 22, 0xFFFFFFFF);
        g.drawString(this.font, Component.translatable("gui.flash.infinity.portal_dim"), l + 16, t + 39, 0xFFB0D0FF,
                false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.flash.client.infinity;

import com.flash.infinity.InfPowers;
import com.flash.infinity.InfinityServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * An ability wheel (the Reality Stone's weather): its abilities round the centre; clicking one (or letting go of the
 * ability key over it) uses it.
 */
public class WheelScreen extends Screen {

    private final List<InfPowers.Ability> items = new ArrayList<>();

    public WheelScreen(InfPowers.Ability wheel) {
        super(InfinityScreen.title(wheel));
        for (String id : com.flash.infinity.InfinityPowers.strings(wheel.json.get("abilities"))) {
            InfPowers.Ability a = wheel.power.ability(id);
            if (a != null) {
                this.items.add(a);
            }
        }
    }

    private int hovered(int mx, int my) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        double dx = mx - cx;
        double dy = my - cy;
        if (dx * dx + dy * dy < 20 * 20 || this.items.isEmpty()) {
            return -1;
        }
        double ang = Math.atan2(dy, dx) + Math.PI / 2.0D;
        ang = (ang + Math.PI * 2.0D) % (Math.PI * 2.0D);
        return (int) (ang / (Math.PI * 2.0D / this.items.size())) % this.items.size();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int h = hovered(mx, my);
        for (int i = 0; i < this.items.size(); i++) {
            double ang = (i + 0.5D) * Math.PI * 2.0D / this.items.size() - Math.PI / 2.0D;
            int x = cx + (int) (Math.cos(ang) * 60.0D);
            int y = cy + (int) (Math.sin(ang) * 60.0D);
            g.fill(x - 14, y - 14, x + 14, y + 14, i == h ? 0xC0FF3040 : 0x90202030);
            InfPowers.Ability a = this.items.get(i);
            if (a.icon != null && a.icon.endsWith(".png")) {
                g.blit(new ResourceLocation(a.icon), x - 8, y - 8, 16, 16, 0.0F, 0.0F, 1, 1, 1, 1);
            }
            if (i == h) {
                g.drawCenteredString(this.font, InfinityScreen.title(a), cx, cy + 90, 0xFFFFFFFF);
            }
        }
        g.drawCenteredString(this.font, this.title, cx, cy - 4, 0xFFFF8080);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int h = hovered((int) mx, (int) my);
        if (h >= 0) {
            InfinityClient.send(InfinityServer.PRESS, this.items.get(h).key());
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

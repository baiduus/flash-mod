package com.flash.client.infinity;

import com.flash.infinity.GauntletMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The Infinity Gauntlet's menu: the gauntlet drawn with its six stone slots, the inventory under it. */
public class GauntletScreen extends AbstractContainerScreen<GauntletMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("flash",
            "textures/gui/infinity_gauntlet.png");

    public GauntletScreen(GauntletMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
        this.inventoryLabelY = GauntletMenu.INV_Y - 11;
        this.inventoryLabelX = GauntletMenu.INV_X;
        this.titleLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g);
        super.render(g, mx, my, partial);
        this.renderTooltip(g, mx, my);
    }
}

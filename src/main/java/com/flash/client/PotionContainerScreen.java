package com.flash.client;

import com.flash.menu.PotionContainerMenu;
import com.flash.transformation.PotionRequirement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * "Recipiente de Poções" screen. It preserves the version 1 layout and style,
 * but labels the block correctly, shows connection state and highlights each
 * missing or invalid potion.
 */
public class PotionContainerScreen extends AbstractContainerScreen<PotionContainerMenu> {

    private static final int PANEL_BORDER = 0xFF7A0C0C;
    private static final int PANEL_BACKGROUND = 0xFF25252B;
    private static final int PANEL_INNER = 0xFF313138;
    private static final int SLOT_BORDER = 0xFF0C0C0E;
    private static final int SLOT_FILL = 0xFF4A4A52;
    private static final int SLOT_INNER = 0xFF1B1B1F;
    private static final int COLOR_GREEN = 0xFF35D06A;
    private static final int COLOR_RED = 0xFFFF5A3C;
    private static final int COLOR_ORANGE = 0xFFFFB84D;
    private static final int COLOR_MUTED = 0xFFB8B8C0;
    private static final int COLOR_GOLD = 0xFFFFD400;

    public PotionContainerScreen(PotionContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, SLOT_BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL_BACKGROUND);
        graphics.fill(x + 6, y + 6, x + this.imageWidth - 6, y + 16, PANEL_INNER);
        graphics.fill(x + 8, y + 66, x + this.imageWidth - 8, y + 67, PANEL_BORDER);

        for (int index = 0; index < PotionRequirement.values().length; index++) {
            PotionRequirement requirement = PotionRequirement.bySlot(index);
            boolean valid = requirement != null
                    && requirement.matches(this.menu.slots.get(index).getItem());
            int border = valid ? COLOR_GREEN : COLOR_RED;
            drawSlot(graphics, x + 61 + index * 18, y + 34, border);
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 7 + column * 18, y + 83 + row * 18, SLOT_BORDER);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, x + 7 + column * 18, y + 141, SLOT_BORDER);
        }

        boolean ready = this.isReady();
        graphics.fill(x + 8, y + 57, x + this.imageWidth - 8, y + 58, ready ? COLOR_GREEN : COLOR_RED);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;

        graphics.drawString(font, this.title, this.titleLabelX, this.titleLabelY, COLOR_GOLD, false);

        boolean connected = this.menu.isConnected();
        Component connection = Component.translatable(
                connected ? "gui.flash.container.connected" : "gui.flash.container.disconnected");
        graphics.drawString(font, connection,
                this.imageWidth - 8 - font.width(connection), 6,
                connected ? COLOR_GREEN : COLOR_ORANGE, false);

        String[] labels = {"VEL", "REG", "FOR"};
        for (int index = 0; index < labels.length; index++) {
            int labelX = 62 + index * 18 + 9 - font.width(labels[index]) / 2;
            graphics.drawString(font, labels[index], labelX, 24, COLOR_MUTED, false);
        }

        boolean ready = this.isReady();
        Component status = Component.translatable(
                ready ? "gui.flash.container.ready" : "gui.flash.container.incomplete");
        graphics.drawString(font, status, 8, 57, ready ? COLOR_GREEN : COLOR_RED, false);

        String count = validCount() + "/3";
        graphics.drawString(font, count,
                this.imageWidth - 8 - font.width(count), 57, ready ? COLOR_GREEN : COLOR_MUTED, false);

        graphics.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                COLOR_MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredSlot != null && this.hoveredSlot.index < 3) {
            PotionRequirement requirement = PotionRequirement.bySlot(this.hoveredSlot.index);
            if (requirement != null && !requirement.matches(this.hoveredSlot.getItem())) {
                graphics.renderTooltip(
                        this.font,
                        Component.translatable("gui.flash.container.requires", requirement.displayName()),
                        mouseX,
                        mouseY
                );
            }
        }
    }

    private boolean isReady() {
        return validCount() >= 3;
    }

    private int validCount() {
        int count = 0;
        for (PotionRequirement requirement : PotionRequirement.values()) {
            ItemStack stack = this.menu.slots.get(requirement.slot()).getItem();
            if (requirement.matches(stack)) {
                count++;
            }
        }
        return count;
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, int borderColor) {
        graphics.fill(x, y, x + 18, y + 18, borderColor);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, SLOT_INNER);
    }
}

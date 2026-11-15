package com.flash.client.infinity;

import com.flash.client.FlashKeys;
import com.flash.infinity.InfPowers;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The stones' bar (bottom left): the ability picked (Alt + scroll), its key and cooldown, the ones before and
 * after it.
 */
public final class InfinityHud implements IGuiOverlay {

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        List<InfPowers.Ability> bar = InfinityClient.bar();
        if (bar.isEmpty()) {
            return;
        }
        InfPowers.Ability sel = InfinityClient.selected();
        int i = Math.max(0, bar.indexOf(sel));
        Font font = mc.font;
        int w = 150;
        int x = 6;
        int y = height - 74;
        g.fill(x, y, x + w, y + 46, 0xA0101018);
        g.fill(x, y, x + 2, y + 46, 0xFFB088FF);
        Component name = InfinityScreen.title(sel);
        g.drawString(font, font.plainSubstrByWidth(name.getString(), w - 34), x + 6, y + 4, 0xFFFFFFFF, true);
        String key = FlashKeys.YAS_USE.getTranslatedKeyMessage().getString();
        String kind = Component.translatable("gui.flash.infinity.key_" + sel.key.name().toLowerCase()).getString();
        g.drawString(font, font.plainSubstrByWidth("[" + key + "] " + kind, w - 10), x + 6, y + 15, 0xFF9AD0FF,
                false);
        int left = InfinityClient.cooldownLeft(sel.key());
        boolean on = InfinityClient.on(sel.key()) || InfinityClient.toggled(sel.key());
        if (left > 0) {
            int len = Math.max(1, InfinityClient.cooldownLength(sel.key()));
            g.fill(x + 6, y + 27, x + w - 6, y + 29, 0xFF303040);
            g.fill(x + 6, y + 27, x + 6 + (w - 12) * (len - left) / len, y + 29, 0xFFB088FF);
        } else if (on) {
            g.drawString(font, Component.translatable("gui.flash.infinity.active"), x + 6, y + 25, 0xFFFFE060, false);
        }
        String nav = (i + 1) + "/" + bar.size() + "  Alt+scroll";
        g.drawString(font, nav, x + 6, y + 35, 0xFF8080A0, false);
        String power = InfPowers.get(sel.power.id) == null ? "" : sel.power.name;
        g.drawString(font, font.plainSubstrByWidth(power, 60), x + w - 4 - Math.min(60, font.width(power)), y + 35,
                0xFFE0C080, false);
    }
}

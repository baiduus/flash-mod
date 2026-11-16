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

    /** Where the panel starts (the energy bars go above it). */
    public static int top(int height) {
        int n = Math.min(InfinityClient.bar().size(), 8);
        return n == 0 ? height - 34 : height - 34 - (30 + n * 10);
    }

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
        String mode = InfinityClient.mode();
        com.flash.infinity.InfinityStone st = com.flash.infinity.InfinityStone.byId(mode);
        int color = st == null ? 0xFFE0C080 : 0xFF000000 | st.glint;
        Font font = mc.font;
        int shown = Math.min(bar.size(), 8);
        int w = 170;
        int x = 6;
        int h = 30 + shown * 10;
        int y = height - 34 - h;
        g.fill(x, y, x + w, y + h, 0xA0101018);
        g.fill(x, y, x + 2, y + h, color);
        // the mode (the stone) and how to change it
        g.drawString(font, font.plainSubstrByWidth(InfinityClient.modeName(mode), w - 60), x + 6, y + 4, color, true);
        String nav = (InfinityClient.modes().indexOf(mode) + 1) + "/" + InfinityClient.modes().size() + " Alt+scroll";
        g.drawString(font, nav, x + w - 4 - font.width(nav), y + 4, 0xFF8080A0, false);
        // its abilities: the chosen one marked
        int first = Math.max(0, Math.min(bar.indexOf(sel) - shown / 2, bar.size() - shown));
        for (int k = 0; k < shown; k++) {
            InfPowers.Ability a = bar.get(first + k);
            int ly = y + 16 + k * 10;
            boolean chosen = a == sel;
            boolean on = InfinityClient.on(a.key()) || InfinityClient.toggled(a.key());
            int left = InfinityClient.cooldownLeft(a.key());
            if (chosen) {
                g.fill(x + 4, ly - 1, x + w - 4, ly + 9, 0x50FFFFFF & (color | 0x50000000));
            }
            String name = InfinityScreen.title(a).getString();
            int tc = chosen ? 0xFFFFFFFF : left > 0 ? 0xFF707080 : 0xFFB0B0C0;
            g.drawString(font, font.plainSubstrByWidth((chosen ? "> " : "  ") + name, w - 40), x + 6, ly, tc, false);
            String tag = left > 0 ? ((left + 19) / 20) + "s" : on ? Component.translatable("gui.flash.infinity.active")
                    .getString() : "";
            g.drawString(font, tag, x + w - 6 - font.width(tag), ly, on ? 0xFFFFE060 : 0xFF8080A0, false);
        }
        String keys = "[" + FlashKeys.YAS_USE.getTranslatedKeyMessage().getString() + "] "
                + Component.translatable("gui.flash.infinity.short_" + sel.key.name().toLowerCase()).getString()
                + "  [" + FlashKeys.YAS_VARIANT.getTranslatedKeyMessage().getString() + "] "
                + Component.translatable("gui.flash.infinity.next").getString();
        g.drawString(font, font.plainSubstrByWidth(keys, w - 10), x + 6, y + h - 11, 0xFF9AD0FF, false);
    }
}

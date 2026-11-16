package com.flash.client.infinity;

import com.flash.infinity.InfPowers;
import com.flash.infinity.InfinityAbilities;
import com.flash.infinity.InfinityPowers;
import com.flash.infinity.InfinityServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The stones' ability trees (the original's power screens): one tab per power the player has now, its abilities at
 * their places in the tree, linked to the ones they come after. Click one to unlock it (Aspects of Existence, levels,
 * as the original asks; what else it needs is listed on hovering), click a switch to flip it. The ability key and
 * Alt + scroll use them (InfinityClient).
 */
public class InfinityScreen extends Screen {

    private static final int W = 420;
    private static final int H = 260;
    private static final int UNIT = 46;
    private static final int NODE = 24;
    private static String tab;
    private float panX;
    private float panY;
    private boolean dragging;

    public InfinityScreen() {
        super(Component.translatable("gui.flash.infinity.title"));
    }

    public static Component title(InfPowers.Ability a) {
        String t = a.title;
        if (t.startsWith("{")) {
            return Component.literal(a.id);
        }
        // translated where the language has it (English: the original's own titles)
        String key = "infinity.ability." + t.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key)
                : Component.literal(t);
    }

    static Component desc(InfPowers.Ability a) {
        JsonElement d = a.description;
        if (d == null || d.isJsonNull()) {
            return null;
        }
        if (d.isJsonObject()) {
            JsonObject o = d.getAsJsonObject();
            if (o.has("translate")) {
                return Component.translatable(o.get("translate").getAsString());
            }
            if (o.has("text")) {
                return Component.literal(o.get("text").getAsString());
            }
            return null;
        }
        String s = d.getAsString();
        return "null".equals(s) ? null : Component.literal(s);
    }

    private List<InfPowers.Power> tabs() {
        List<InfPowers.Power> out = new ArrayList<>();
        for (String id : InfinityClient.powers()) {
            InfPowers.Power p = InfPowers.get(id);
            if (p != null) {
                boolean any = false;
                for (InfPowers.Ability a : p.abilities.values()) {
                    any |= a.inTree();
                }
                if (any) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private InfPowers.Power current() {
        List<InfPowers.Power> t = tabs();
        for (InfPowers.Power p : t) {
            if (p.id.equals(tab)) {
                return p;
            }
        }
        if (t.isEmpty()) {
            return null;
        }
        tab = t.get(0).id;
        return t.get(0);
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private int nodeX(InfPowers.Ability a) {
        return left() + W / 2 + (int) (a.guiX * UNIT + this.panX) - NODE / 2 + 50;
    }

    private int nodeY(InfPowers.Ability a) {
        return top() + H / 2 + (int) (a.guiY * UNIT + this.panY) - NODE / 2 + 10;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g);
        int x0 = left();
        int y0 = top();
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF6A5ACD);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0101018);
        List<InfPowers.Power> tabs = tabs();
        InfPowers.Power cur = current();
        // the tabs (the powers the player has now)
        int ty = y0 + 6;
        for (InfPowers.Power p : tabs) {
            boolean sel = p == cur;
            g.fill(x0 + 4, ty, x0 + 96, ty + 20, sel ? 0xFF2A2A44 : 0xFF181826);
            icon(g, p.icon, x0 + 6, ty + 2);
            g.drawString(this.font, this.font.plainSubstrByWidth(p.name, 70), x0 + 24, ty + 6,
                    sel ? 0xFFFFFFFF : 0xFFA0A0B0, false);
            ty += 22;
        }
        int aspects = this.minecraft.player == null ? 0 : InfinityServer.count(this.minecraft.player);
        g.drawString(this.font, Component.translatable("gui.flash.infinity.aspects", aspects), x0 + 6, y0 + H - 22,
                0xFFE0C0FF, false);
        g.drawString(this.font, Component.translatable("gui.flash.infinity.levels",
                this.minecraft.player == null ? 0 : this.minecraft.player.experienceLevel), x0 + 6, y0 + H - 11,
                0xFF80FF80, false);
        if (cur == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.flash.infinity.none"), x0 + W / 2,
                    y0 + H / 2, 0xFFC0C0C0);
            super.render(g, mx, my, partial);
            return;
        }
        int ax = x0 + 100;
        g.fill(ax, y0 + 4, x0 + W - 4, y0 + H - 4, 0xFF0A0A12);
        g.drawCenteredString(this.font, cur.name, (ax + x0 + W) / 2, y0 + 8, 0xFFFFE080);
        g.enableScissor(ax, y0 + 18, x0 + W - 4, y0 + H - 4);
        // the links: each ability to the ones it comes after
        for (InfPowers.Ability a : cur.abilities.values()) {
            if (!a.inTree()) {
                continue;
            }
            for (JsonObject c : a.unlocking) {
                if ("palladium:ability_unlocked".equals(c.has("type") ? c.get("type").getAsString() : "")) {
                    InfPowers.Ability b = cur.ability(c.get("ability").getAsString());
                    if (b != null && b.inTree()) {
                        line(g, nodeX(a) + NODE / 2, nodeY(a) + NODE / 2, nodeX(b) + NODE / 2, nodeY(b) + NODE / 2,
                                InfinityPowers.unlocked(InfinityClient.VIEW, b) ? 0xFFB088FF : 0xFF404050);
                    }
                }
            }
        }
        InfPowers.Ability hover = null;
        for (InfPowers.Ability a : cur.abilities.values()) {
            if (!a.inTree()) {
                continue;
            }
            int nx = nodeX(a);
            int ny = nodeY(a);
            boolean unlocked = InfinityPowers.unlocked(InfinityClient.VIEW, a);
            boolean buyable = InfinityPowers.buyable(InfinityClient.VIEW, a);
            int frame = unlocked ? (InfinityClient.on(a.key()) ? 0xFFFFE060 : 0xFFB088FF)
                    : buyable ? 0xFF60A060 : 0xFF404050;
            g.fill(nx - 1, ny - 1, nx + NODE + 1, ny + NODE + 1, frame);
            g.fill(nx, ny, nx + NODE, ny + NODE, unlocked ? 0xFF24203A : 0xFF16161E);
            icon(g, a.icon, nx + 4, ny + 4);
            if ("flash:toggle".equals(a.type) && unlocked) {
                boolean st = InfinityAbilities.toggleState(InfinityClient.VIEW, a);
                g.fill(nx + NODE - 7, ny + NODE - 7, nx + NODE - 1, ny + NODE - 1, st ? 0xFF40E040 : 0xFFE04040);
            }
            if (!unlocked) {
                g.fill(nx, ny, nx + NODE, ny + NODE, 0x90000000);
            }
            if (mx >= nx && mx < nx + NODE && my >= ny && my < ny + NODE && mx >= ax && my >= y0 + 18) {
                hover = a;
            }
        }
        g.disableScissor();
        super.render(g, mx, my, partial);
        if (hover != null) {
            g.renderTooltip(this.font, tooltip(hover), mx, my);
        }
    }

    private List<FormattedCharSequence> tooltip(InfPowers.Ability a) {
        List<Component> lines = new ArrayList<>();
        lines.add(title(a).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        Component d = desc(a);
        if (d != null) {
            lines.add(d.copy().withStyle(ChatFormatting.GRAY));
        }
        boolean unlocked = InfinityPowers.unlocked(InfinityClient.VIEW, a);
        if (unlocked) {
            lines.add(Component.translatable("gui.flash.infinity.unlocked").withStyle(ChatFormatting.LIGHT_PURPLE));
            if ("flash:toggle".equals(a.type)) {
                lines.add(Component.translatable("gui.flash.infinity.click_toggle").withStyle(ChatFormatting.AQUA));
            }
        } else {
            if (!a.buys.isEmpty() && !InfinityClient.VIEW.purchased(a.key())) {
                if (a.aspects() > 0) {
                    lines.add(Component.translatable("gui.flash.infinity.cost", a.aspects())
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                if (a.levels() > 0) {
                    lines.add(Component.translatable("gui.flash.infinity.cost_levels", a.levels())
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            for (JsonObject c : a.unlocking) {
                boolean ok = InfinityPowers.test(InfinityClient.VIEW, a, c);
                MutableComponent need = need(a, c);
                if (need != null) {
                    lines.add(Component.literal(ok ? "✔ " : "✘ ").append(need)
                            .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
            }
            if (InfinityPowers.buyable(InfinityClient.VIEW, a)) {
                lines.add(Component.translatable("gui.flash.infinity.click_buy").withStyle(ChatFormatting.YELLOW));
            }
        }
        if (a.inBar()) {
            lines.add(Component.translatable("gui.flash.infinity.key_" + a.key.name().toLowerCase())
                    .withStyle(ChatFormatting.DARK_AQUA));
            if (a.cooldown > 0) {
                lines.add(Component.translatable("gui.flash.infinity.cooldown", a.cooldown / 20.0F)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            out.addAll(this.font.split(c, 220));
        }
        return out;
    }

    /** What a condition asks, in words (null: not shown). */
    private static MutableComponent need(InfPowers.Ability a, JsonObject c) {
        String type = c.has("type") ? c.get("type").getAsString() : "";
        switch (type) {
            case "palladium:ability_unlocked": {
                InfPowers.Ability b = a.power.ability(c.get("ability").getAsString());
                return Component.translatable("gui.flash.infinity.need_ability", b == null
                        ? Component.literal(c.get("ability").getAsString()) : title(b));
            }
            case "palladium:has_power": {
                String id = c.get("power").getAsString();
                InfPowers.Power p = InfPowers.get(id);
                return Component.translatable("gui.flash.infinity.need_power", p == null ? id : p.name);
            }
            case "palladium:crouching":
                return Component.translatable("gui.flash.infinity.need_crouch");
            case "palladium:not":
                return Component.translatable("gui.flash.infinity.need_not");
            case "palladium:or":
                return Component.translatable("gui.flash.infinity.need_or");
            case "palladium:item_in_slot":
                return Component.translatable("gui.flash.infinity.need_item");
            case "palladium:objective_score":
                return Component.translatable("gui.flash.infinity.need_score", c.get("objective").getAsString());
            case "flash:setting":
                return Component.translatable("gui.flash.infinity.need_setting");
            default:
                return Component.literal(type.substring(type.indexOf(':') + 1).replace('_', ' '));
        }
    }

    private void icon(GuiGraphics g, String icon, int x, int y) {
        if (icon == null) {
            return;
        }
        try {
            if (icon.endsWith(".png")) {
                g.blit(new ResourceLocation(icon), x, y, 16, 16, 0.0F, 0.0F, 1, 1, 1, 1);
                return;
            }
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(icon));
            if (it != null) {
                g.renderItem(new ItemStack(it), x, y);
            }
        } catch (RuntimeException ignored) {
            // a bad icon id: nothing drawn
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        // an L: across, then down
        g.fill(Math.min(x0, x1), y0 - 1, Math.max(x0, x1) + 1, y0 + 1, color);
        g.fill(x1 - 1, Math.min(y0, y1), x1 + 1, Math.max(y0, y1) + 1, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = left();
        int y0 = top();
        int ty = y0 + 6;
        for (InfPowers.Power p : tabs()) {
            if (mx >= x0 + 4 && mx < x0 + 96 && my >= ty && my < ty + 20) {
                tab = p.id;
                this.panX = 0.0F;
                this.panY = 0.0F;
                click();
                return true;
            }
            ty += 22;
        }
        InfPowers.Power cur = current();
        if (cur != null && mx >= x0 + 100) {
            for (InfPowers.Ability a : cur.abilities.values()) {
                if (!a.inTree()) {
                    continue;
                }
                int nx = nodeX(a);
                int ny = nodeY(a);
                if (mx >= nx && mx < nx + NODE && my >= ny && my < ny + NODE) {
                    if (InfinityPowers.buyable(InfinityClient.VIEW, a)) {
                        InfinityClient.send(InfinityServer.BUY, a.key());
                        click();
                    } else if ("flash:toggle".equals(a.type) && InfinityPowers.unlocked(InfinityClient.VIEW, a)) {
                        InfinityClient.send(InfinityServer.SETTING, a.key());
                        click();
                    }
                    return true;
                }
            }
            this.dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging) {
            this.panX += (float) dx;
            this.panY += (float) dy;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

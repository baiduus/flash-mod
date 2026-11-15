package com.flash.client.infinity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

/**
 * A mind read (the original's snapshot): row 1 the name tag (type, health, armour, owner, target...) and a potion per
 * effect; row 2 the equipment; then its inventory. Nothing can be taken.
 */
public class MindCardScreen extends Screen {

    private final CompoundTag d;
    private final List<ItemStack> top = new ArrayList<>();
    private final List<ItemStack> gear = new ArrayList<>();
    private final List<ItemStack> inv = new ArrayList<>();
    private final List<Component> info = new ArrayList<>();

    public MindCardScreen(CompoundTag d) {
        super(Component.literal(d.getString("name")));
        this.d = d;
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.setHoverName(Component.literal(d.getString("name")).withStyle(ChatFormatting.YELLOW));
        this.top.add(tag);
        this.info.add(Component.literal(d.getString("name")).withStyle(ChatFormatting.YELLOW));
        this.info.add(Component.translatable("gui.flash.infinity.mind_type", d.getString("type")));
        this.info.add(Component.translatable("gui.flash.infinity.mind_health", String.format("%.1f",
                d.getFloat("health")), String.format("%.1f", d.getFloat("max"))));
        if (d.getFloat("absorption") > 0.0F) {
            this.info.add(Component.translatable("gui.flash.infinity.mind_absorption", d.getFloat("absorption")));
        }
        this.info.add(Component.translatable("gui.flash.infinity.mind_armor", d.getInt("armor")));
        if (d.contains("owner")) {
            this.info.add(Component.translatable("gui.flash.infinity.mind_owner", d.getString("owner")));
        }
        if (d.contains("target")) {
            this.info.add(Component.translatable("gui.flash.infinity.mind_target", d.getString("target")));
        }
        if (d.getBoolean("drained")) {
            this.info.add(Component.translatable("gui.flash.infinity.mind_drained").withStyle(ChatFormatting.GOLD));
        }
        if (d.contains("food")) {
            this.info.add(Component.translatable("gui.flash.infinity.mind_food", d.getInt("food"), d.getInt("level")));
        }
        if (d.contains("where")) {
            this.info.add(Component.translatable("gui.flash.infinity.mind_where", d.getString("where"))
                    .withStyle(ChatFormatting.AQUA));
        }
        for (Tag t : d.getList("effects", Tag.TAG_COMPOUND)) {
            MobEffectInstance e = MobEffectInstance.load((CompoundTag) t);
            if (e != null) {
                ItemStack pot = new ItemStack(Items.POTION);
                PotionUtils.setCustomEffects(pot, List.of(e));
                pot.setHoverName(Component.translatable(e.getDescriptionId()).append(" " + (e.getAmplifier() + 1)));
                this.top.add(pot);
            }
        }
        for (Tag t : d.getList("gear", Tag.TAG_COMPOUND)) {
            this.gear.add(ItemStack.of((CompoundTag) t));
        }
        for (Tag t : d.getList("inventory", Tag.TAG_COMPOUND)) {
            this.inv.add(ItemStack.of((CompoundTag) t));
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int w = 176;
        int rows = 2 + (this.inv.size() + 8) / 9;
        int h = 24 + rows * 18 + 8;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFD300);
        g.fill(x, y, x + w, y + h, 0xF0201C10);
        g.drawString(this.font, Component.translatable("gui.flash.infinity.mind_read"), x + 8, y + 6, 0xFFFFE060,
                false);
        ItemStack hovered = ItemStack.EMPTY;
        boolean tagHover = false;
        int[] row = {0};
        List<List<ItemStack>> lines = new ArrayList<>();
        lines.add(this.top);
        lines.add(this.gear);
        for (int i = 0; i < this.inv.size(); i += 9) {
            lines.add(this.inv.subList(i, Math.min(this.inv.size(), i + 9)));
        }
        for (List<ItemStack> line : lines) {
            for (int i = 0; i < line.size() && i < 9; i++) {
                int sx = x + 8 + i * 18;
                int sy = y + 20 + row[0] * 18;
                g.fill(sx, sy, sx + 16, sy + 16, 0x60000000);
                g.renderItem(line.get(i), sx, sy);
                g.renderItemDecorations(this.font, line.get(i), sx, sy);
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    hovered = line.get(i);
                    tagHover = row[0] == 0 && i == 0;
                }
            }
            row[0]++;
        }
        super.render(g, mx, my, pt);
        if (tagHover) {
            g.renderComponentTooltip(this.font, this.info, mx, my);
        } else if (!hovered.isEmpty()) {
            g.renderTooltip(this.font, hovered, mx, my);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

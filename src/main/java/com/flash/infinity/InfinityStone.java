package com.flash.infinity;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The six Infinity Stones (gauntlet_stones of the original: colours, tooltip colour and order, their power). */
public enum InfinityStone {
    POWER("power_stone", 0xC32AD1, 0xF12AFF, ChatFormatting.LIGHT_PURPLE, 0),
    SPACE("space_stone", 0x0255FF, 0x6AC5FF, ChatFormatting.AQUA, 1),
    REALITY("reality_stone", 0xFF0130, 0xFF0130, ChatFormatting.RED, 2),
    SOUL("soul_stone", 0xDE7300, 0xFF8B00, ChatFormatting.GOLD, 3),
    TIME("time_stone", 0x13CF55, 0x12E772, ChatFormatting.GREEN, 4),
    MIND("mind_stone", 0xFFD300, 0xDAFF0A, ChatFormatting.YELLOW, 5);

    public final String id;
    public final int color;
    public final int glint;
    public final ChatFormatting tooltip;
    public final int order;

    InfinityStone(String id, int color, int glint, ChatFormatting tooltip, int order) {
        this.id = id;
        this.color = color;
        this.glint = glint;
        this.tooltip = tooltip;
        this.order = order;
    }

    /** Its power's id (the ability tree): the same as the stone's. */
    public String power() {
        return this.id;
    }

    public Item item() {
        return switch (this) {
            case POWER -> InfinityItems.POWER_STONE.get();
            case SPACE -> InfinityItems.SPACE_STONE.get();
            case REALITY -> InfinityItems.REALITY_STONE.get();
            case SOUL -> InfinityItems.SOUL_STONE.get();
            case TIME -> InfinityItems.TIME_STONE.get();
            case MIND -> InfinityItems.MIND_STONE.get();
        };
    }

    public static InfinityStone of(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof InfinityStoneItem st)) {
            return null;
        }
        return st.stone;
    }

    public static InfinityStone byId(String id) {
        for (InfinityStone s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    static Supplier<Item> supplier(InfinityStone s) {
        return s::item;
    }
}

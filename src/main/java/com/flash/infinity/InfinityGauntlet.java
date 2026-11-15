package com.flash.infinity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Infinity Gauntlet: the stones set in it (kept on the item, {@value #STONES}), its skin, and the conditions
 * about it. Worn in a hand (the offhand is its place: the original's Curios hands slot), it gives the gauntlet's
 * power and the power of every stone in it.
 */
public final class InfinityGauntlet {

    public static final String STONES = "flash_stones";
    public static final String SKIN = "flash_skin";

    private InfinityGauntlet() {
    }

    public static boolean isGauntlet(ItemStack s) {
        return s.is(InfinityItems.INFINITY_GAUNTLET.get());
    }

    /** The gauntlet worn (the offhand first, else the main hand), or empty. */
    public static ItemStack worn(Player p) {
        if (isGauntlet(p.getOffhandItem())) {
            return p.getOffhandItem();
        }
        return isGauntlet(p.getMainHandItem()) ? p.getMainHandItem() : ItemStack.EMPTY;
    }

    public static List<InfinityStone> stones(ItemStack g) {
        List<InfinityStone> out = new ArrayList<>();
        CompoundTag t = g.getTag();
        if (t == null || !t.contains(STONES)) {
            return out;
        }
        for (Tag e : t.getList(STONES, Tag.TAG_STRING)) {
            InfinityStone s = InfinityStone.byId(e.getAsString());
            if (s != null && !out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    public static void setStones(ItemStack g, List<InfinityStone> stones) {
        ListTag l = new ListTag();
        for (InfinityStone s : stones) {
            l.add(StringTag.valueOf(s.id));
        }
        g.getOrCreateTag().put(STONES, l);
    }

    public static boolean has(ItemStack g, InfinityStone s) {
        return stones(g).contains(s);
    }

    public static String skin(ItemStack g) {
        CompoundTag t = g.getTag();
        return t == null ? "" : t.getString(SKIN);
    }

    static void addPowers(Player p, Set<String> out) {
        ItemStack g = worn(p);
        if (g.isEmpty()) {
            return;
        }
        for (InfinityStone s : stones(g)) {
            out.add(s.power());
        }
    }

    static boolean test(InfinityPowers.View v, InfPowers.Ability a, JsonObject c, String type) {
        Player p = v.player();
        ItemStack g = worn(p);
        switch (type) {
            case "flash:gauntlet_slot": {
                if (g.isEmpty()) {
                    return false;
                }
                for (InfinityStone s : stones(g)) {
                    if (InfinityPowers.matches(new ItemStack(s.item()), c.get("item"))) {
                        return true;
                    }
                }
                return false;
            }
            case "flash:stone_equipped":
                return !g.isEmpty() && c.has("stone") && has(g, InfinityStone.byId(strip(c.get("stone"))));
            case "flash:gauntlet_visible":
                return !g.isEmpty();
            case "flash:gauntlet_arm": {
                if (g.isEmpty()) {
                    return false;
                }
                HumanoidArm arm = "left".equals(c.get("arm").getAsString()) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                InteractionHand hand = g == p.getOffhandItem() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                HumanoidArm worn = hand == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
                return worn == arm;
            }
            case "flash:gauntlet_skin": {
                String want = c.has("skin") ? c.get("skin").getAsString() : "*";
                String have = g.isEmpty() ? "" : skin(g);
                return "*".equals(want) ? !have.isEmpty() : want.equals(have);
            }
            case "flash:held_stone":
                return InfinityStone.of(p.getMainHandItem()) != null || InfinityStone.of(p.getOffhandItem()) != null;
            case "flash:custom_gauntlet":
                return false;
            default:
                return false;
        }
    }

    private static String strip(JsonElement e) {
        String s = e.getAsString();
        return s.substring(s.indexOf(':') + 1);
    }
}

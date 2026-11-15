package com.flash.infinity;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Orb of Morag: the shell that hides a stone (the original's Orb: opened by turning its rings, the stone put in
 * or taken out, its signal hidden from the Infinity Tracker). The ring puzzle: Etapa 2.
 */
public final class InfinityOrb {

    private InfinityOrb() {
    }

    public static ItemStack held(Player p, String slot) {
        if ("mainhand".equals(slot)) {
            return p.getMainHandItem().is(InfinityItems.ORB.get()) ? p.getMainHandItem() : ItemStack.EMPTY;
        }
        if ("offhand".equals(slot)) {
            return p.getOffhandItem().is(InfinityItems.ORB.get()) ? p.getOffhandItem() : ItemStack.EMPTY;
        }
        ItemStack m = held(p, "mainhand");
        return m.isEmpty() ? held(p, "offhand") : m;
    }

    /** {"slot": ..., "stone": true/false}: an orb there, with or without a stone in it. */
    static boolean test(Player p, JsonObject c) {
        ItemStack o = held(p, c.has("slot") ? c.get("slot").getAsString() : null);
        if (o.isEmpty()) {
            return false;
        }
        return !c.has("stone") || c.get("stone").getAsBoolean() == (InfinityContainers.stoneIn(o) != null);
    }
}

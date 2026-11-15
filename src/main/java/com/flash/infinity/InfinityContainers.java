package com.flash.infinity;

import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The items that hold a stone (the original's stone_containers: the Tesseract the Space Stone, the Scepter the Mind
 * Stone, the Eye of Agamotto the Time Stone, the Aether the Reality Stone; the Orb and the Gauntlet hold theirs too),
 * the powers the stones inside give, and the original's conditions about them.
 * <p>
 * The stone inside is kept on the item ({@value #STONE}: a stone's id; "" none); with nothing written the container
 * holds its own stone.
 */
public final class InfinityContainers {

    public static final String STONE = "flash_stone";

    private InfinityContainers() {
    }

    /** The stone a container holds by itself. */
    public static InfinityStone defaultStone(ItemStack s) {
        Item it = s.getItem();
        if (it == InfinityItems.TESSERACT.get()) {
            return InfinityStone.SPACE;
        }
        if (it == InfinityItems.SCEPTER.get()) {
            return InfinityStone.MIND;
        }
        if (it == InfinityItems.EYE_OF_AGAMOTTO.get()) {
            return InfinityStone.TIME;
        }
        if (it == InfinityItems.AETHER.get()) {
            return InfinityStone.REALITY;
        }
        return null;
    }

    public static boolean isContainer(ItemStack s) {
        return !s.isEmpty() && (defaultStone(s) != null || s.is(InfinityItems.ORB.get()));
    }

    /** The stone inside (null: none). */
    public static InfinityStone stoneIn(ItemStack s) {
        if (s.isEmpty()) {
            return null;
        }
        CompoundTag tag = s.getTag();
        if (tag != null && tag.contains(STONE)) {
            return InfinityStone.byId(tag.getString(STONE));
        }
        return defaultStone(s);
    }

    public static void setStone(ItemStack s, InfinityStone stone) {
        s.getOrCreateTag().putString(STONE, stone == null ? "" : stone.id);
    }

    /**
     * The stones' powers from what holds them: the Scepter in a hand (its stone, ScepterPowerProvider), the Eye of
     * Agamotto in a hand or worn (EyeTimeStonePowerProvider: open, its stone), the Gauntlet's stones (InfinityGauntlet).
     */
    static void addPowers(Player p, Set<String> out) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(InfinityItems.SCEPTER.get()) || s.is(InfinityItems.EMPTY_SCEPTER.get())) {
                InfinityStone st = stoneIn(s);
                if (st != null) {
                    out.add(st.power());
                }
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.CHEST}) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.is(InfinityItems.EYE_OF_AGAMOTTO.get()) && eyeOpen(s)) {
                InfinityStone st = stoneIn(s);
                if (st != null) {
                    out.add(st.power());
                }
            }
        }
        InfinityGauntlet.addPowers(p, out);
    }

    public static boolean eyeOpen(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.getBoolean("flash_open");
    }

    /** A Stormbreaker near (its power cuts the Soul Stone's protection): Thor's thrown Stormbreaker, 10 blocks. */
    static boolean stormbreakerNear(Player p) {
        return p.getPersistentData().getBoolean("flash_stormbreaker_near");
    }

    private static boolean holdingStone(Player p, String slot) {
        if ("offhand".equals(slot)) {
            return InfinityStone.of(p.getOffhandItem()) != null;
        }
        if ("mainhand".equals(slot)) {
            return InfinityStone.of(p.getMainHandItem()) != null;
        }
        return InfinityStone.of(p.getMainHandItem()) != null || InfinityStone.of(p.getOffhandItem()) != null;
    }

    /** The original's own conditions (the rest: InfinityPowers.test). */
    static boolean test(InfinityPowers.View v, InfPowers.Ability a, JsonObject c, String type) {
        Player p = v.player();
        switch (type) {
            case "flash:holding_stone":
                return holdingStone(p, c.has("slot") ? c.get("slot").getAsString() : null);
            case "flash:held_arm": {
                HumanoidArm arm = "left".equals(c.get("arm").getAsString()) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                InteractionHand hand = p.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                return InfinityPowers.matches(p.getItemInHand(hand), c.get("item"));
            }
            case "flash:eye_of_agamotto": {
                ItemStack eye = eye(p);
                if (eye.isEmpty()) {
                    return false;
                }
                if (c.has("open") && c.get("open").getAsBoolean() != eyeOpen(eye)) {
                    return false;
                }
                return !c.has("stone") || c.get("stone").getAsBoolean() == (stoneIn(eye) != null);
            }
            case "flash:orb":
                return InfinityOrb.test(p, c);
            default:
                return InfinityGauntlet.test(v, a, c, type);
        }
    }

    /** The Eye worn or held. */
    public static ItemStack eye(Player p) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND}) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.is(InfinityItems.EYE_OF_AGAMOTTO.get()) || s.is(InfinityItems.EMPTY_EYE_OF_AGAMOTTO.get())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}

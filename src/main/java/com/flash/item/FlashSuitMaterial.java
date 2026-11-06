package com.flash.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Armor material of the Flash suits. Its vanilla defense is zero on purpose so
 * equipping a suit never adds armor bars; the real protection (above netherite)
 * comes from {@link FlashSuitEvents}.
 */
public enum FlashSuitMaterial implements ArmorMaterial {
    POSITIVE("flash:flash_suit"),
    NEGATIVE("flash:negative_suit"),
    /** Black suit of Zoom (Hunter Zolomon). */
    ZOOM("flash:zoom_suit");

    private final String name;

    FlashSuitMaterial(String name) {
        this.name = name;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 1;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}

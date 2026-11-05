package com.flash.transformation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

/**
 * The three potion slots of the machine and their validation rules.
 * Each slot accepts only its own potion; the whole set is validated again when
 * redstone activates the machine.
 */
public enum PotionRequirement {

    SPEED(0, MobEffects.MOVEMENT_SPEED, "potion.flash.speed"),
    REGENERATION(1, MobEffects.REGENERATION, "potion.flash.regeneration"),
    STRENGTH(2, MobEffects.DAMAGE_BOOST, "potion.flash.strength");

    private final int slot;
    private final MobEffect effect;
    private final String translationKey;

    PotionRequirement(int slot, MobEffect effect, String translationKey) {
        this.slot = slot;
        this.effect = effect;
        this.translationKey = translationKey;
    }

    public int slot() {
        return this.slot;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public Component displayName() {
        return Component.translatable(this.translationKey);
    }

    /**
     * Accepts drinkable, splash and lingering potions, but only if the bottle
     * carries exactly the required effect at amplifier 0 (Speed I,
     * Regeneration I or Strength I).
     */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
            return false;
        }
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
        if (effects.size() != 1) {
            return false;
        }
        MobEffectInstance instance = effects.get(0);
        return instance.getEffect() == this.effect
                && instance.getAmplifier() == 0
                && instance.getDuration() > 0;
    }

    public static PotionRequirement bySlot(int slot) {
        for (PotionRequirement requirement : values()) {
            if (requirement.slot == slot) {
                return requirement;
            }
        }
        return null;
    }

    public static List<PotionRequirement> missing(NonNullList<ItemStack> items) {
        List<PotionRequirement> missing = new ArrayList<>();
        for (PotionRequirement requirement : values()) {
            if (requirement.slot >= items.size() || !requirement.matches(items.get(requirement.slot))) {
                missing.add(requirement);
            }
        }
        return missing;
    }

    public static Component describe(List<PotionRequirement> requirements) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < requirements.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(", "));
            }
            result.append(requirements.get(i).displayName());
        }
        return result;
    }
}

package com.flash.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Lex Luthor's green and purple war suit: the powers of Superman (level 5, weaker) out of a battery. The energy
 * lives in the chestplate (0-100 %); it charges on the mod's armour stand fed with redstone (2 % a second, full in
 * 50 s) and lasts about 15 minutes of normal use, shared by everything the suit does (see LexSuit).
 */
public class LexArmorItem extends ArmorItem {

    public static final String ENERGY = "LexEnergy";
    public static final float MAX_ENERGY = 100.0F;

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 11;
                case CHESTPLATE -> 16;
                case LEGGINGS -> 15;
                case BOOTS -> 13;
            } * 40;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 3;
                case CHESTPLATE -> 8;
                case LEGGINGS -> 6;
                case BOOTS -> 3;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 12;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT);
        }

        @Override
        public String getName() {
            return "flash:lex";
        }

        @Override
        public float getToughness() {
            return 2.5F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.1F;
        }
    };

    public LexArmorItem(ArmorItem.Type type) {
        super(MATERIAL, type, new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    /** The plain armour model draws nothing: the suit is the Mark 6 model (LexSuitLayer). */
    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, EquipmentSlot slot,
                                  String type) {
        return "flash:textures/models/armor/lex_empty.png";
    }

    /** Indestructible: the pieces never wear out. */
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, 0);
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount, T entity,
                                                                            java.util.function.Consumer<T> onBroken) {
        return 0;
    }

    /** Dropped, the piece does not burn, blow up or vanish either. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        if (!entity.isInvulnerable()) {
            entity.setInvulnerable(true);
        }
        return false;
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    public static boolean isLex(ItemStack stack) {
        return stack.getItem() instanceof LexArmorItem;
    }

    /** The four pieces on. */
    public static boolean wearingFull(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            if (!isLex(player.getItemBySlot(slot))) {
                return false;
            }
        }
        return true;
    }

    public static float energy(ItemStack chest) {
        return chest.hasTag() ? chest.getTag().getFloat(ENERGY) : 0.0F;
    }

    public static void setEnergy(ItemStack chest, float value) {
        chest.getOrCreateTag().putFloat(ENERGY, Math.max(0.0F, Math.min(MAX_ENERGY, value)));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return this.getType() == ArmorItem.Type.CHESTPLATE || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (this.getType() == ArmorItem.Type.CHESTPLATE) {
            return Math.round(13.0F * energy(stack) / MAX_ENERGY);
        }
        return super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (this.getType() == ArmorItem.Type.CHESTPLATE) {
            return energy(stack) < 20.0F ? 0xFFE04040 : 0xFF6CFF6C;
        }
        return super.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.lex_armor").withStyle(ChatFormatting.DARK_PURPLE,
                ChatFormatting.ITALIC));
        if (this.getType() == ArmorItem.Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.flash.lex_energy", Math.round(energy(stack)))
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("tooltip.flash.lex_armor.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.lex_armor.3").withStyle(ChatFormatting.GOLD));
    }
}

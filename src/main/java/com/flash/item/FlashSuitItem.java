package com.flash.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/** One piece of the Flash suit: indestructible, fire-proof and not enchantable. */
public class FlashSuitItem extends ArmorItem {

    private final boolean negative;

    public FlashSuitItem(FlashSuitMaterial material, Type type) {
        super(material, type, new Item.Properties().stacksTo(1).fireResistant());
        this.negative = material == FlashSuitMaterial.NEGATIVE;
    }

    public boolean isNegativeSuit() {
        return this.negative;
    }

    @Override
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
                    net.minecraft.world.entity.EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                return com.flash.client.FlashSuitClient.model(
                        slot == net.minecraft.world.entity.EquipmentSlot.LEGS);
            }
        });
    }

    /** Client option: the helmet may use the texture with a standard open mouth. */
    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
                                  net.minecraft.world.entity.EquipmentSlot slot, String type) {
        if (slot == net.minecraft.world.entity.EquipmentSlot.HEAD && type == null
                && com.flash.config.FlashClientConfig.MASK_OPEN_MOUTH.get()) {
            return "flash:textures/models/armor/"
                    + (this.negative ? "negative_suit" : "flash_suit") + "_open_layer_1.png";
        }
        return null;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flash.suit.protection")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.suit.fire")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flash.suit.unbreakable")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Number of suit pieces currently worn by the entity. */
    public static int countWorn(net.minecraft.world.entity.LivingEntity entity) {
        int count = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot
                : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR
                    && entity.getItemBySlot(slot).getItem() instanceof FlashSuitItem) {
                count++;
            }
        }
        return count;
    }
}

package com.flash.item;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * Wonder Woman's sword (Legends: Superheroes' model), conjured into her hand (WonderAbilities.SUMMON_ARMS): never
 * wears out, and is gone when let go of (WonderArms).
 */
public class AmazonSwordItem extends SwordItem {

    public AmazonSwordItem() {
        super(Tiers.NETHERITE, 9, -2.2F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.flash.client.WonderItemRenderer.get();
            }
        });
    }
}

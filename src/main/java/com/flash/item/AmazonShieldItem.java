package com.flash.item;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;

/**
 * Wonder Woman's shield (Legends: Superheroes' model), conjured into her other hand: it blocks as a shield does,
 * never wears out, and is gone when let go of (WonderArms).
 */
public class AmazonShieldItem extends ShieldItem {

    public AmazonShieldItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
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

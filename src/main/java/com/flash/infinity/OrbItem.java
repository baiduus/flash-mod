package com.flash.infinity;

import net.minecraft.world.item.Item;

/** The Orb of Morag: drawn in hand by its animated model (client OrbItemRenderer). */
public class OrbItem extends Item {

    public OrbItem(Properties p) {
        super(p);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.flash.client.infinity.OrbItemRenderer.get();
            }
        });
    }
}

package com.flash.registry;

import com.flash.FlashMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlashMod.MODID);

    public static final RegistryObject<CreativeModeTab> FLASH = CREATIVE_MODE_TABS.register(
            "flash",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flash"))
                    .icon(() -> new ItemStack(ModItems.TRANSFORMATION_CHAMBER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TRANSFORMATION_CHAMBER.get());
                        output.accept(ModItems.POTION_CONTAINER.get());
                        output.accept(ModItems.ACCELERATION_STONE.get());
                        output.accept(ModItems.ACCELERATION_CRYSTAL.get());
                        output.accept(potion(Potions.SWIFTNESS));
                        output.accept(potion(Potions.REGENERATION));
                        output.accept(potion(Potions.STRENGTH));
                    })
                    .build());

    private static ItemStack potion(Potion potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private ModCreativeTabs() {
    }
}

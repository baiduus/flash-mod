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
                        output.accept(ModItems.KRYPTONIAN_BABY.get());
                        output.accept(ModItems.KRYPTONIAN_DEBRIS.get());
                        output.accept(ModItems.POTION_CONTAINER.get());
                        output.accept(ModItems.ACCELERATION_STONE.get());
                        output.accept(ModItems.ACCELERATION_CRYSTAL.get());
                        output.accept(ModItems.TACHYON_EXTRACTOR.get());
                        output.accept(ModItems.TORN_SUIT.get());
                        output.accept(ModItems.TACHYON.get());
                        output.accept(ModItems.CORRUPTED_MATTER.get());
                        output.accept(ModItems.NEGATIVE_CAPSULE.get());
                        output.accept(ModItems.SYRINGE.get());
                        output.accept(com.flash.item.SyringeItem.filled(ModItems.SYRINGE.get(),
                                com.flash.item.SyringeContents.NEGATIVE_FORCE));
                        output.accept(ModItems.V9_CAPSULE.get());
                        output.accept(com.flash.item.SyringeItem.filled(ModItems.SYRINGE.get(),
                                com.flash.item.SyringeContents.V9));
                        output.accept(ModItems.NEGATIVE_STONE.get());
                        output.accept(ModItems.NEGATIVE_CRYSTAL.get());
                        output.accept(ModItems.FLASH_SUIT_HELMET.get());
                        output.accept(ModItems.FLASH_SUIT_CHESTPLATE.get());
                        output.accept(ModItems.FLASH_SUIT_LEGGINGS.get());
                        output.accept(ModItems.FLASH_SUIT_BOOTS.get());
                        output.accept(ModItems.NEGATIVE_SUIT_HELMET.get());
                        output.accept(ModItems.NEGATIVE_SUIT_CHESTPLATE.get());
                        output.accept(ModItems.NEGATIVE_SUIT_LEGGINGS.get());
                        output.accept(ModItems.NEGATIVE_SUIT_BOOTS.get());
                        output.accept(ModItems.ZOOM_SUIT_HELMET.get());
                        output.accept(ModItems.ZOOM_SUIT_CHESTPLATE.get());
                        output.accept(ModItems.ZOOM_SUIT_LEGGINGS.get());
                        output.accept(ModItems.ZOOM_SUIT_BOOTS.get());
                        output.accept(ModItems.PHANTOM_SUIT_HELMET.get());
                        output.accept(ModItems.PHANTOM_SUIT_CHESTPLATE.get());
                        output.accept(ModItems.PHANTOM_SUIT_LEGGINGS.get());
                        output.accept(ModItems.PHANTOM_SUIT_BOOTS.get());
                        output.accept(ModItems.PHANTOM_FRAGMENT.get());
                        output.accept(ModItems.ACCELERATION_PHANTOM_SPAWN_EGG.get());
                        output.accept(ModItems.FLASH_RING.get());
                        output.accept(ModItems.TACHYON_AMPLIFIER.get());
                        output.accept(ModItems.FLASH_SUIT_STAND.get());
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

    /** Everything of the Hulk (gamma power). */
    public static final RegistryObject<CreativeModeTab> HULK = CREATIVE_MODE_TABS.register(
            "hulk",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flash.hulk"))
                    .icon(() -> new ItemStack(ModItems.GAMMA_CHAMBER.get()))
                    .withTabsBefore(FLASH.getKey())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.GAMMA_CHAMBER.get());
                        output.accept(ModItems.GAMMA_REACTOR.get());
                        output.accept(ModItems.GAMMA_CAPSULE.get());
                        output.accept(ModItems.SYRINGE.get());
                        output.accept(com.flash.item.SyringeItem.filled(ModItems.SYRINGE.get(),
                                com.flash.item.SyringeContents.GAMMA));
                    })
                    .build());

    private ModCreativeTabs() {
    }
}

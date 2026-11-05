package com.flash.registry;

import com.flash.FlashMod;
import com.flash.menu.PotionContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FlashMod.MODID);

    public static final RegistryObject<MenuType<PotionContainerMenu>> POTION_CONTAINER =
            MENUS.register("potion_container", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new PotionContainerMenu(windowId, inventory, data.readBlockPos())));

    private ModMenus() {
    }
}

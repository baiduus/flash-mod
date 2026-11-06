package com.flash.entity;

import com.flash.FlashMod;
import com.flash.registry.ModEntities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FlashEntityEvents {

    private FlashEntityEvents() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FLASH_SUIT_STAND.get(), LivingEntity.createLivingAttributes().build());
    }
}

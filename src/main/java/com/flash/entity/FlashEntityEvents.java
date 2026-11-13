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
        event.put(ModEntities.ACCELERATION_PHANTOM.get(), AccelerationPhantom.createAttributes().build());
        event.put(ModEntities.YAS_GUARDIAN.get(), com.flash.yas.YasGuardian.createAttributes().build());
        event.put(ModEntities.HUMAN.get(), HumanNpc.createAttributes().build());
    }
}

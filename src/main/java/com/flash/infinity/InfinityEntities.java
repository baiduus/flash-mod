package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.infinity.entity.BlackHole;
import com.flash.infinity.entity.SpacePortal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The Infinity Stones' entities. */
public final class InfinityEntities {

    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,
            FlashMod.MODID);

    /** One end of a Space Stone portal (the original's portal armour stands). */
    public static final RegistryObject<EntityType<SpacePortal>> SPACE_PORTAL = TYPES.register("space_portal",
            () -> EntityType.Builder.<SpacePortal>of(SpacePortal::new, MobCategory.MISC).sized(1.6F, 2.5F)
                    .clientTrackingRange(10).updateInterval(20).fireImmune().build("space_portal"));

    public static final RegistryObject<EntityType<BlackHole>> BLACK_HOLE = TYPES.register("black_hole",
            () -> EntityType.Builder.<BlackHole>of(BlackHole::new, MobCategory.MISC).sized(1.0F, 1.0F)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build("black_hole"));

    private InfinityEntities() {
    }
}

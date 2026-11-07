package com.flash.registry;

import com.flash.FlashMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle types: the animated gamma radiation of the Hulk asset pack. */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FlashMod.MODID);

    public static final RegistryObject<SimpleParticleType> GAMMA =
            PARTICLE_TYPES.register("gamma_radiation", () -> new SimpleParticleType(false));

    // Superman pack effects (textures/particle/superman)
    public static final RegistryObject<SimpleParticleType> SM_CLOUD =
            PARTICLE_TYPES.register("sm_cloud", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_FROST_CLOUD =
            PARTICLE_TYPES.register("sm_frost_cloud", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_SNOW =
            PARTICLE_TYPES.register("sm_snow", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_FROST =
            PARTICLE_TYPES.register("sm_frost", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_HEAT_GLOW =
            PARTICLE_TYPES.register("sm_heat_glow", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_SOLAR_GLOW =
            PARTICLE_TYPES.register("sm_solar_glow", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_SPARKLE =
            PARTICLE_TYPES.register("sm_sparkle", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_EMBER =
            PARTICLE_TYPES.register("sm_ember", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_PUFF =
            PARTICLE_TYPES.register("sm_puff", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_DUST =
            PARTICLE_TYPES.register("sm_dust", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_VAPOR =
            PARTICLE_TYPES.register("sm_vapor", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SM_SOLAR_RAY =
            PARTICLE_TYPES.register("sm_solar_ray", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}

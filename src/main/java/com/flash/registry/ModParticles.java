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

    private ModParticles() {
    }
}

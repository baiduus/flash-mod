package com.flash.registry;

import com.flash.FlashMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Effects of the Superman pack: frozen (freeze breath) and bleeding (sonic punch, combo). */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, FlashMod.MODID);

    /** Frozen: much slower movement and attacks, and the body freezes over. */
    public static final RegistryObject<MobEffect> FROZEN = EFFECTS.register("frozen", () -> new Frozen()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, "5c1b7a0e-3d2f-4e61-9a8b-7c6d5e4f3a21", -0.2D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_SPEED, "5c1b7a0e-3d2f-4e61-9a8b-7c6d5e4f3a22", -0.15D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** Bleeding: one damage every second (every half second from level II). */
    public static final RegistryObject<MobEffect> BLEEDING = EFFECTS.register("bleeding", Bleeding::new);

    private ModEffects() {
    }

    private static final class Frozen extends MobEffect {
        Frozen() {
            super(MobEffectCategory.HARMFUL, 0x7FD8FF);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            // keeps the vanilla frozen look (blue overlay, shaking) while the effect lasts
            entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze() + 4));
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }
    }

    private static final class Bleeding extends MobEffect {
        Bleeding() {
            super(MobEffectCategory.HARMFUL, 0xB0161E);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            entity.hurt(entity.damageSources().magic(), 1.0F);
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK,
                                net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(),
                        6, 0.2D, 0.3D, 0.2D, 0.1D);
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            int interval = amplifier > 0 ? 10 : 20;
            return duration % interval == 0;
        }
    }
}

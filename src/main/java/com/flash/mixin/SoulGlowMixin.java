package com.flash.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Soul Stone's sight: every creature round its holder glows (for them only). */
@Mixin(Minecraft.class)
public abstract class SoulGlowMixin {

    @Inject(method = "m_91314_(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true,
            remap = false)
    private void flash$soulSight(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (com.flash.client.infinity.SoulClient.glows(e)) {
            cir.setReturnValue(true);
        }
    }
}

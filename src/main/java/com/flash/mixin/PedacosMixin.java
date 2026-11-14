package com.flash.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hand of God's pieces of a torn creature vanish as soon as "it came back" (its UUID in the world): a player torn
 * apart by the Scarlet Witch never leaves the world, so his pieces stay while he is torn (ScarletTornClient).
 */
@Mixin(targets = "handofgod.client.Pedacos", remap = false)
public abstract class PedacosMixin {

    @Inject(method = "voltou", at = @At("HEAD"), cancellable = true, remap = false)
    private void flash$stillTorn(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (com.flash.client.ScarletTornClient.torn(e)) {
            cir.setReturnValue(false);
        }
    }
}

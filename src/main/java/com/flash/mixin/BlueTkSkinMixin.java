package com.flash.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hand of God's telekinetic skin, light blue on what the Space Stone holds (and its holder). */
@Mixin(targets = "handofgod.client.render.PeleEnergia", remap = false)
public abstract class BlueTkSkinMixin {

    private static final float[] FLASH_BLUE = {0.36F, 0.8F, 1.0F};
    private static boolean flash$blue;

    @ModifyVariable(method = "desenhar", at = @At("HEAD"), argsOnly = true, remap = false)
    private static Entity flash$whose(Entity e) {
        flash$blue = com.flash.client.infinity.SoulGray.blue(e);
        return e;
    }

    @Inject(method = "desenhar", at = @At("RETURN"), remap = false)
    private static void flash$done(CallbackInfo ci) {
        flash$blue = false;
    }

    @Inject(method = "cor", at = @At("RETURN"), cancellable = true, remap = false)
    private static void flash$color(CallbackInfoReturnable<float[]> cir) {
        if (flash$blue) {
            cir.setReturnValue(FLASH_BLUE);
        }
    }
}

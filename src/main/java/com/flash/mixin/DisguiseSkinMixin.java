package com.flash.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Mind Stone's disguise: the skin of the player whose name was picked. */
@Mixin(AbstractClientPlayer.class)
public abstract class DisguiseSkinMixin {

    @Inject(method = "m_108560_()Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true,
            remap = false)
    private void flash$disguise(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = com.flash.client.infinity.MindClient.skin((AbstractClientPlayer) (Object) this);
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}

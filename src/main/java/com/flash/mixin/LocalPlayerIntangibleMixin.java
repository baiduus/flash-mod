package com.flash.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The intangible local player is not pushed out of the blocks it walks through. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerIntangibleMixin {

    @Inject(method = "m_8107_()V", at = @At("HEAD"), remap = false)
    private void flash$intangible(CallbackInfo ci) {
        LocalPlayer p = (LocalPlayer) (Object) this;
        if (com.flash.infinity.MindStone.intangible(p)) {
            p.noPhysics = true;
        }
    }
}

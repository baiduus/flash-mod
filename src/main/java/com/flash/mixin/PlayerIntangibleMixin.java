package com.flash.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Mind Stone's intangibility: the player passes through blocks (Player.tick resets noPhysics: set it again). */
@Mixin(Player.class)
public abstract class PlayerIntangibleMixin {

    @Inject(method = "m_8107_()V", at = @At("HEAD"), remap = false)
    private void flash$intangible(CallbackInfo ci) {
        Player p = (Player) (Object) this;
        if (com.flash.infinity.MindStone.intangible(p)) {
            p.noPhysics = true;
        }
    }
}

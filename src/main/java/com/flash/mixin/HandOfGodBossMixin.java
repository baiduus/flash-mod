package com.flash.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hand of God's target checks of Matter manipulation, the Void and the Telekinesis: for Yas only, bosses (the Ender
 * Dragon, the Wither, the excluded and multi-part creatures) are valid targets too (YasBosses). Everyone else goes
 * through the checks untouched.
 */
@Mixin(targets = {"handofgod.desintegracao.Desintegracao", "handofgod.sombra.Sombras",
        "handofgod.telecinese.Telecinese"}, remap = false)
public abstract class HandOfGodBossMixin {

    @Inject(method = "alvoInvalido", at = @At("HEAD"), cancellable = true, remap = false)
    private static void flash$bosses(ServerPlayer p, Entity e, CallbackInfoReturnable<String> cir) {
        if (com.flash.yas.YasBosses.allowed(p, e)) {
            cir.setReturnValue(null);
        }
    }
}

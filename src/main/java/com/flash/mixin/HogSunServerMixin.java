package com.flash.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hand of God's Celestial wants an empty main hand: not for the Power Stone's holder. */
@Mixin(targets = "handofgod.servidor.GerenteSol", remap = false)
public abstract class HogSunServerMixin {

    @Redirect(method = {"validarInicio", "aoTickNivel"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;m_21205_()Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    private static ItemStack flash$stoneHand(ServerPlayer p) {
        return com.flash.infinity.HogBridge.sunHolder(p) ? ItemStack.EMPTY : p.getMainHandItem();
    }
}

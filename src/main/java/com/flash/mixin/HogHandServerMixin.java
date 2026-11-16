package com.flash.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hand of God's Telekinesis wants an empty main hand: not for the Space Stone's holder (the stone is the hand). */
@Mixin(targets = "handofgod.telecinese.Telecinese", remap = false)
public abstract class HogHandServerMixin {

    @Redirect(method = "motivoRecusa", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;m_21205_()Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    private static ItemStack flash$stoneHand(ServerPlayer p) {
        return com.flash.infinity.HogBridge.tkHolder(p) ? ItemStack.EMPTY : p.getMainHandItem();
    }
}

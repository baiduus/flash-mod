package com.flash.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hand of God's client: the Power Stone's holder may pull the sun with something in his hand (ClienteGesto). */
@Mixin(targets = "handofgod.client.ClienteGesto", remap = false)
public abstract class HogSunClientMixin {

    @Redirect(method = "motivoNaoInicia", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;m_21205_()Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    private static ItemStack flash$stoneHand(LocalPlayer p) {
        return com.flash.client.infinity.InfinityClient.powers().contains("power_stone") ? ItemStack.EMPTY
                : p.getMainHandItem();
    }
}

package com.flash.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hand of God's telekinetic hand glow, light blue for whoever holds by the Space Stone. */
@Mixin(targets = "handofgod.client.MaoTelecinese", remap = false)
public abstract class BlueTkHandMixin {

    private static final float[] FLASH_BLUE = {0.36F, 0.8F, 1.0F};
    private static final float[] FLASH_RED = {1.0F, 0.26F, 0.46F};
    private static boolean flash$blueHand;

    @ModifyVariable(method = "desenharDedos", at = @At("HEAD"), argsOnly = true, remap = false)
    private static AbstractClientPlayer flash$whose(AbstractClientPlayer p) {
        flash$blueHand = com.flash.client.infinity.SoulGray.blue(p);
        return p;
    }

    @Redirect(method = "desenharDedos", at = @At(value = "FIELD", target = "Lhandofgod/client/MaoTelecinese;VERMELHO:[F",
            opcode = Opcodes.GETSTATIC), remap = false)
    private static float[] flash$fingers() {
        return flash$blueHand ? FLASH_BLUE : FLASH_RED;
    }

    @Redirect(method = "brilhoBraco", at = @At(value = "FIELD", target = "Lhandofgod/client/MaoTelecinese;VERMELHO:[F",
            opcode = Opcodes.GETSTATIC), remap = false)
    private static float[] flash$arm() {
        return flash$blueHand || com.flash.client.infinity.SoulGray.blue(Minecraft.getInstance().player)
                ? FLASH_BLUE : FLASH_RED;
    }
}

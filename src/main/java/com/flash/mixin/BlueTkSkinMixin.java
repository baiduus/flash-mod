package com.flash.mixin;

import com.flash.client.infinity.SoulGray;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hand of God's telekinetic skin, light blue on what the Space Stone holds (and its holder): its texture swapped. */
@Mixin(targets = "handofgod.client.render.PeleEnergia", remap = false)
public abstract class BlueTkSkinMixin {

    private static final float[] FLASH_WHITE = {1.0F, 1.0F, 1.0F};

    @ModifyVariable(method = "desenhar", at = @At("HEAD"), argsOnly = true, remap = false)
    private static Entity flash$whose(Entity e) {
        SoulGray.hogBlue = SoulGray.blue(e);
        return e;
    }

    @Inject(method = "desenhar", at = @At("RETURN"), remap = false)
    private static void flash$done(CallbackInfo ci) {
        SoulGray.hogBlue = false;
    }

    @Redirect(method = "emitir", at = @At(value = "FIELD",
            target = "Lhandofgod/client/render/PeleEnergia;TIPO:Lnet/minecraft/client/renderer/RenderType;",
            opcode = Opcodes.GETSTATIC), remap = false)
    private static RenderType flash$type() {
        RenderType own = flash$own("TIPO");
        return SoulGray.hogBlue ? SoulGray.blueType(own) : own;
    }

    @Inject(method = "cor", at = @At("RETURN"), cancellable = true, remap = false)
    private static void flash$color(CallbackInfoReturnable<float[]> cir) {
        if (SoulGray.hogBlue) {
            cir.setReturnValue(FLASH_WHITE);
        }
    }

    private static RenderType flash$own(String field) {
        try {
            return (RenderType) Class.forName("handofgod.client.render.PeleEnergia").getField(field).get(null);
        } catch (ReflectiveOperationException e) {
            return RenderType.translucent();
        }
    }
}

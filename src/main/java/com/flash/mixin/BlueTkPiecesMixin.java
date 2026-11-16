package com.flash.mixin;

import com.flash.client.infinity.SoulGray;
import java.util.UUID;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pieces of a creature torn apart by the Space Stone's telekinesis: their energy light blue too. */
@Mixin(targets = "handofgod.client.Pedacos", remap = false)
public abstract class BlueTkPiecesMixin {

    @Shadow(remap = false)
    @Final
    UUID uuid;

    @ModifyVariable(method = "criar", at = @At("HEAD"), argsOnly = true, remap = false)
    private static Entity flash$torn(Entity e) {
        if (SoulGray.blue(e)) {
            SoulGray.tornBlue(e.getUUID());
        }
        return e;
    }

    @Inject(method = "desenhar", at = @At("HEAD"), remap = false)
    private void flash$start(CallbackInfo ci) {
        SoulGray.hogBlue = SoulGray.isTornBlue(this.uuid);
    }

    @Inject(method = "desenhar", at = @At("RETURN"), remap = false)
    private void flash$end(CallbackInfo ci) {
        SoulGray.hogBlue = false;
    }

    @Redirect(method = "desenhar", at = @At(value = "FIELD",
            target = "Lhandofgod/client/render/PeleEnergia;TIPO:Lnet/minecraft/client/renderer/RenderType;",
            opcode = Opcodes.GETSTATIC), remap = false)
    private RenderType flash$type() {
        RenderType own = flash$own("TIPO");
        return SoulGray.hogBlue ? SoulGray.blueType(own) : own;
    }

    @Redirect(method = "desenhar", at = @At(value = "FIELD",
            target = "Lhandofgod/client/render/PeleEnergia;TIPO_BRILHO:Lnet/minecraft/client/renderer/RenderType;",
            opcode = Opcodes.GETSTATIC), remap = false)
    private RenderType flash$glow() {
        RenderType own = flash$own("TIPO_BRILHO");
        return SoulGray.hogBlue ? SoulGray.blueGlowType(own) : own;
    }

    private static RenderType flash$own(String field) {
        try {
            return (RenderType) Class.forName("handofgod.client.render.PeleEnergia").getField(field).get(null);
        } catch (ReflectiveOperationException e) {
            return RenderType.translucent();
        }
    }
}

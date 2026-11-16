package com.flash.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Soul Stone's drained: drawn greyish (their colour faded), mobs and the bodies of players alike. */
@Mixin(LivingEntityRenderer.class)
public abstract class SoulGrayMixin {

    private static final String MODEL = "Lnet/minecraft/client/model/EntityModel;m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    @Inject(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), remap = false)
    private void flash$soulGrayStart(LivingEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf,
                                     int light, CallbackInfo ci) {
        com.flash.client.infinity.SoulGray.current = com.flash.client.infinity.SoulGray.is(e);
    }

    @ModifyArg(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = MODEL,
            remap = false), index = 4, remap = false)
    private float flash$soulGrayR(float v) {
        return com.flash.client.infinity.SoulGray.current ? v * 0.55F : v;
    }

    @ModifyArg(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = MODEL,
            remap = false), index = 5, remap = false)
    private float flash$soulGrayG(float v) {
        return com.flash.client.infinity.SoulGray.current ? v * 0.55F : v;
    }

    @ModifyArg(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = MODEL,
            remap = false), index = 6, remap = false)
    private float flash$soulGrayB(float v) {
        return com.flash.client.infinity.SoulGray.current ? v * 0.6F : v;
    }
}

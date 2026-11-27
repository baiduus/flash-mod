package com.flash.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after the game (and playerAnimator) posed a player's model: Spider-Man's poses (SpiderAnim: the swing, the
 * walls, the flips...) and Doctor Doom's arms when he casts (DoomPose). (SRG name of setupAnim, no refmap, as the
 * mod's other mixins.)
 */
@Mixin(PlayerModel.class)
public abstract class SpiderPoseMixin {

    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), remap = false)
    private void flash$heroPoses(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                 float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        com.flash.client.SpiderAnim.apply(model, entity, ageInTicks);
        com.flash.client.DoomPose.apply(model, entity, ageInTicks);
        com.flash.client.WonderPose.apply(model, entity, ageInTicks);
        com.flash.client.SupermanPose.apply(model, entity, ageInTicks);
        com.flash.client.ThorArmPose.apply(model, entity, ageInTicks);
    }
}

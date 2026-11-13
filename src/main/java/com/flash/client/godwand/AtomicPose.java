package com.flash.client.godwand;

import com.flash.godwand.AtomicSequence;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** I am Atomic's pose (God Wand's): the arms rise through the charge, wind up and spread at the word. */
final class AtomicPose {

    private AtomicPose() {
    }

    static void apply(HumanoidModel<?> model, LivingEntity entity, AtomicRenderer.Frame frame, double age) {
        float raise = AtomicSequence.armRaise(age);
        float spread = AtomicSequence.armSpread(age);
        float start = AtomicSequence.activation(age);
        float windup = AtomicSequence.armWindup(age);
        float facing = AtomicSequence.facing(age);
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart grip = right ? model.rightArm : model.leftArm;
        ModelPart support = right ? model.leftArm : model.rightArm;
        float weight = 1.0F - AtomicSequence.phase(age, 774.0, 802.0);
        float style = AtomicSequence.phase(age, 320.0, 360.0) * weight;
        float oldGrip = grip.xRot;
        float oldSupport = support.xRot;
        grip.xRot = AtomicSequence.armPitch(age);
        grip.yRot = (right ? 1 : -1) * (-0.64F * raise * (1.0F - windup) * (1.0F - spread) + 0.55F * windup) * weight;
        grip.zRot = (right ? 1 : -1) * (0.02F * raise * (1.0F - spread) * (1.0F - windup) - 0.72F * windup + 1.15F * spread);
        float supportUp = Mth.lerp(raise, -0.12F - 1.3F * start, -2.16F);
        supportUp += (-1.05F - supportUp) * windup;
        support.xRot = supportUp * (1.0F - spread) - 0.12F * spread;
        support.zRot = (right ? -1 : 1) * (0.02F * raise * (1.0F - spread) * (1.0F - windup) - 0.88F * windup + 1.32F * spread) * weight;
        support.yRot = (right ? 1 : -1) * (0.64F * raise * (1.0F - spread) * (1.0F - windup) - 0.525F * windup) * weight;
        grip.xRot = Mth.lerp(weight, oldGrip, grip.xRot);
        support.xRot = Mth.lerp(weight, oldSupport, support.xRot);
        grip.zRot *= weight;
        model.body.yRot = model.body.yRot * (1.0F - facing) - 0.12F * style * (1.0F - facing) + 0.2F * windup;
        model.body.zRot = -0.035F * style * (1.0F - facing);
        float idle = frame == null ? 0.0F
                : AtomicSequence.holdIdle(frame.waitTicks()) * (1.0F - AtomicSequence.phase(age, 406.0, 410.0));
        grip.xRot += idle;
        support.xRot -= idle * 0.8F;
        model.body.zRot += idle * 0.2F;
        Vec3 look = AtomicSequence.headLook(age, entity.getId() + (frame == null ? 0.0 : frame.startedAt() * 0.01));
        float glance = AtomicSequence.phase(age, 0.0, 14.0) * weight;
        model.head.xRot = Mth.lerp(glance, model.head.xRot, (float) look.x) * (1.0F - facing);
        model.head.yRot = AtomicSequence.blendHeadYaw(model.head.yRot, (float) look.y, glance) * (1.0F - facing);
        model.head.zRot = Mth.lerp(glance, model.head.zRot, (float) look.z) * (1.0F - facing);
        model.hat.copyFrom(model.head);
    }
}

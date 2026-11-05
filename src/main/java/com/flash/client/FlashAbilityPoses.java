package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client-side third-person animations of the Flash.
 *
 * <ul>
 *   <li><b>Wind hands</b>: both arms straight forward.</li>
 *   <li><b>Flash run</b>: leaned body, arms swept back and pumping, high knees.
 *       It plays for speed levels 1-10 while running and during the abilities
 *       that make the player run (tornado, lightning charge, mass punch).</li>
 *   <li><b>Molecular vibration</b>: the whole body shakes quickly and pulses
 *       while the ability is held.</li>
 * </ul>
 *
 * The poses are selected in {@code RenderPlayerEvent.Pre}; vanilla
 * {@code setupAnim} then runs the transformers below, which overwrite the limb
 * rotations.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashAbilityPoses {

    public static final HumanoidModel.ArmPose WIND_HANDS =
            HumanoidModel.ArmPose.create("FLASH_WIND_HANDS", true,
                    (model, entity, arm) -> {
                        if (arm == HumanoidArm.RIGHT) {
                            model.rightArm.xRot = -1.55F;
                            model.rightArm.yRot = -0.10F;
                            model.rightArm.zRot = 0.0F;
                        } else {
                            model.leftArm.xRot = -1.55F;
                            model.leftArm.yRot = 0.10F;
                            model.leftArm.zRot = 0.0F;
                        }
                    });

    public static final HumanoidModel.ArmPose RUN =
            HumanoidModel.ArmPose.create("FLASH_RUN", false,
                    (model, entity, arm) -> {
                        if (!(entity instanceof Player player)) {
                            return;
                        }
                        double seconds = Util.getMillis() / 1000.0D + (entity.getId() % 7) * 0.13D;
                        float phase = (float) (seconds * Math.PI * 2.0D * FlashRunAnimation.frequency(player));
                        float swing = Mth.sin(phase);
                        float swingAbs = Mth.cos(phase);
                        if (arm == HumanoidArm.RIGHT) {
                            // Arms swept back, pumping against the legs.
                            model.rightArm.xRot = 1.05F - swing * 0.55F;
                            model.rightArm.yRot = 0.0F;
                            model.rightArm.zRot = 0.14F + swingAbs * 0.04F;
                            // Legs and head are written once per frame (right arm pass).
                            model.rightLeg.xRot = swing * 1.25F;
                            model.leftLeg.xRot = -swing * 1.25F;
                            model.rightLeg.yRot = 0.0F;
                            model.leftLeg.yRot = 0.0F;
                            model.head.xRot -= (float) Math.toRadians(FlashRunAnimation.lean(player)) * 0.8F;
                        } else {
                            model.leftArm.xRot = 1.05F + swing * 0.55F;
                            model.leftArm.yRot = 0.0F;
                            model.leftArm.zRot = -0.14F - swingAbs * 0.04F;
                        }
                    });

    public static final HumanoidModel.ArmPose VIBRATE =
            HumanoidModel.ArmPose.create("FLASH_VIBRATE", false,
                    (model, entity, arm) -> {
                        double t = Util.getMillis() / 1000.0D;
                        float tremble = (float) Math.sin(t * 90.0D) * 0.07F;
                        if (arm == HumanoidArm.RIGHT) {
                            model.rightArm.xRot = -0.15F + tremble;
                            model.rightArm.yRot = 0.0F;
                            model.rightArm.zRot = 1.05F + tremble;
                        } else {
                            model.leftArm.xRot = -0.15F - tremble;
                            model.leftArm.yRot = 0.0F;
                            model.leftArm.zRot = -1.05F - tremble;
                        }
                    });

    private static boolean pushedRun;
    private static boolean pushedPhase;

    private FlashAbilityPoses() {
    }

    /** Called first (Pre): choose poses and push the transform for lean / vibration. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof AbstractClientPlayer client)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        PoseStack stack = event.getPoseStack();
        pushedRun = false;
        pushedPhase = false;
        boolean phasing = FlashClientVisualLevels.isActive(client, FlashAbility.PHASING.ordinal());

        if (phasing) {
            model.rightArmPose = VIBRATE;
            model.leftArmPose = VIBRATE;
        } else if (FlashClientVisualLevels.isActive(client, FlashAbility.WIND_HANDS.ordinal())) {
            model.rightArmPose = WIND_HANDS;
            model.leftArmPose = WIND_HANDS;
        } else if (FlashRunAnimation.isActive(client)) {
            model.rightArmPose = RUN;
            model.leftArmPose = RUN;
            double radians = Math.toRadians(
                    Mth.rotLerp(event.getPartialTick(), client.yBodyRotO, client.yBodyRot));
            // Axis perpendicular to the facing direction: leans the body forward.
            Vector3f axis = new Vector3f((float) Math.cos(radians), 0.0F, (float) Math.sin(radians));
            stack.pushPose();
            stack.mulPose(new Quaternionf().fromAxisAngleDeg(axis, FlashRunAnimation.lean(client)));
            pushedRun = true;
        }

        if (phasing) {
            double t = Util.getMillis() / 1000.0D;
            float jitterX = (float) (Math.sin(t * 83.0D) * 0.035D + Math.sin(t * 37.0D) * 0.02D);
            float jitterY = (float) (Math.sin(t * 71.0D + 1.3D) * 0.02D);
            float jitterZ = (float) (Math.sin(t * 59.0D + 2.1D) * 0.035D);
            float pulse = 1.0F + (float) Math.sin(t * 24.0D) * 0.025F;
            stack.pushPose();
            stack.translate(jitterX, jitterY, jitterZ);
            stack.scale(pulse, 1.0F, pulse);
            pushedPhase = true;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (pushedPhase) {
            event.getPoseStack().popPose();
            pushedPhase = false;
        }
        if (pushedRun) {
            event.getPoseStack().popPose();
            pushedRun = false;
        }
    }
}

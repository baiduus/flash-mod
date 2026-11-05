package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import com.mojang.blaze3d.vertex.PoseStack;
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

/**
 * Client-side third-person animations of the Flash.
 *
 * <ul>
 *   <li><b>Wind hands</b>: both arms straight forward.</li>
 *   <li><b>Molecular vibration</b>: the normal Minecraft pose is kept, but the
 *       body is drawn every frame at a random spot to the left or right of its
 *       real position, alternating sides, so it looks blurred like the
 *       Flashpoint "vibrating" effect.</li>
 * </ul>
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

    private static boolean pushedPhase;
    private static float vibrateSign = 1.0F;

    private FlashAbilityPoses() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof AbstractClientPlayer client)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        PoseStack stack = event.getPoseStack();
        pushedPhase = false;

        if (FlashClientVisualLevels.isActive(client, FlashAbility.WIND_HANDS.ordinal())) {
            model.rightArmPose = WIND_HANDS;
            model.leftArmPose = WIND_HANDS;
        }

        if (FlashClientVisualLevels.isActive(client, FlashAbility.PHASING.ordinal())) {
            double radians = Math.toRadians(
                    Mth.rotLerp(event.getPartialTick(), client.yBodyRotO, client.yBodyRot));
            // Horizontal axis perpendicular to the way the body faces.
            float sideX = (float) -Math.sin(radians + Math.PI / 2.0D);
            float sideZ = (float) Math.cos(radians + Math.PI / 2.0D);
            vibrateSign = -vibrateSign;
            float amount = vibrateSign * (0.10F + client.getRandom().nextFloat() * 0.10F);
            stack.pushPose();
            stack.translate(sideX * amount, 0.0F, sideZ * amount);
            pushedPhase = true;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (pushedPhase) {
            event.getPoseStack().popPose();
            pushedPhase = false;
        }
    }
}

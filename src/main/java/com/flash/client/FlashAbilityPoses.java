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

    private static float animTime(net.minecraft.world.entity.LivingEntity entity) {
        return entity.tickCount + net.minecraft.client.Minecraft.getInstance().getFrameTime();
    }

    /** Barrage: fast alternating straight punches with both fists. */
    public static final HumanoidModel.ArmPose BARRAGE_PUNCHES =
            HumanoidModel.ArmPose.create("FLASH_BARRAGE", true,
                    (model, entity, arm) -> {
                        float phase = animTime(entity) * 2.4F;
                        boolean right = arm == HumanoidArm.RIGHT;
                        float ext = Math.max(0.0F, Mth.sin(phase + (right ? 0.0F : Mth.PI)));
                        net.minecraft.client.model.geom.ModelPart part = right ? model.rightArm : model.leftArm;
                        part.xRot = -1.25F - 0.35F * ext;
                        part.yRot = (right ? -1.0F : 1.0F) * (0.30F * ext - 0.10F);
                        part.zRot = 0.0F;
                        part.z = -3.2F * ext;
                    });

    /** Punch rush: wide open-hand slaps, swinging sideways, one hand after the other. */
    public static final HumanoidModel.ArmPose SLAPS =
            HumanoidModel.ArmPose.create("FLASH_SLAPS", true,
                    (model, entity, arm) -> {
                        float phase = animTime(entity) * 1.3F;
                        boolean right = arm == HumanoidArm.RIGHT;
                        float swing = Mth.sin(phase + (right ? 0.0F : Mth.PI));
                        net.minecraft.client.model.geom.ModelPart part = right ? model.rightArm : model.leftArm;
                        part.xRot = -1.35F + 0.15F * Math.abs(swing);
                        // from the outside across the body and back
                        part.yRot = (right ? 1.0F : -1.0F) * 0.25F - (right ? 1.0F : -1.0F) * 0.95F * Math.max(0.0F, swing);
                        part.zRot = (right ? 1.0F : -1.0F) * 0.35F * Math.max(0.0F, -swing);
                        part.z = -1.2F * Math.max(0.0F, swing);
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

        if (FlashClientVisualLevels.isActive(client, FlashAbility.BARRAGE.ordinal())) {
            model.rightArmPose = BARRAGE_PUNCHES;
            model.leftArmPose = BARRAGE_PUNCHES;
        } else if (FlashClientVisualLevels.isActive(client, FlashAbility.DASH_PUNCH.ordinal())) {
            model.rightArmPose = SLAPS;
            model.leftArmPose = SLAPS;
        }

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

    /** First person: the visible hand keeps punching / slapping while the ability runs. */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        int every = FlashClientVisualLevels.isActive(mc.player, FlashAbility.BARRAGE.ordinal()) ? 3
                : FlashClientVisualLevels.isActive(mc.player, FlashAbility.DASH_PUNCH.ordinal()) ? 5 : 0;
        if (every > 0 && mc.player.tickCount % every == 0) {
            // local only (no packet): the server already tells everyone else
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, false);
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

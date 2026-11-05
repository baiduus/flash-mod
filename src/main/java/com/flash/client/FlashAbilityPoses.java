package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side third-person poses for channelled abilities.
 *
 * <p>Wind Hands uses a custom Forge arm pose instead of a vanilla pose so both
 * arms point straight forward, palms open, while the conical wind bursts are
 * being fired. The pose is selected from the ability mask synced by the server
 * and is applied in {@code RenderPlayerEvent.Pre}; the vanilla
 * {@code setupAnim} then applies the custom transformer.</p>
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

    private FlashAbilityPoses() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof AbstractClientPlayer client)) {
            return;
        }
        if (!FlashClientVisualLevels.isActive(client, FlashAbility.WIND_HANDS.ordinal())) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.rightArmPose = WIND_HANDS;
        model.leftArmPose = WIND_HANDS;
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.entity.FlashSuitStand;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Renders {@link FlashSuitStand} with the mod's own stand model and the worn suit on top. */
public class FlashSuitStandRenderer extends LivingEntityRenderer<FlashSuitStand, FlashSuitStandModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(FlashMod.MODID, "textures/entity/suit_stand.png");

    public FlashSuitStandRenderer(EntityRendererProvider.Context context) {
        super(context, new FlashSuitStandModel(context.bakeLayer(FlashSuitStandModel.LAYER)), 0.45F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ARMOR_STAND_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ARMOR_STAND_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new FlashGeoSuitLayer<>(this));
        this.addLayer(new LexSuitLayer<>(this));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(FlashSuitStand stand) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(FlashSuitStand stand, PoseStack poseStack, float bob,
                                  float bodyYaw, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        // the little wobble when hit, like a vanilla stand
        float hit = (float) (stand.level().getGameTime() - stand.lastHit) + partialTick;
        if (hit < 5.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(net.minecraft.util.Mth.sin(hit / 1.5F * (float) Math.PI) * 3.0F));
        }
    }

    @Override
    protected boolean shouldShowName(FlashSuitStand stand) {
        return stand.isCustomNameVisible() && super.shouldShowName(stand);
    }
}

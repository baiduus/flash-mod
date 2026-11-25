package com.flash.client;

import com.flash.FlashMod;
import com.flash.ultron.UltronSentry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * A sentry of Ultron's Legion: a smaller Ultron. The humanoid model under it only gives the pose (its texture is
 * empty); UltronLayer draws the body.
 */
public class UltronSentryRenderer extends HumanoidMobRenderer<UltronSentry, HumanoidModel<UltronSentry>> {

    private static final ResourceLocation EMPTY = new ResourceLocation(FlashMod.MODID,
            "textures/entity/ultron_sentry_base.png");

    public UltronSentryRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4F);
        this.addLayer(new UltronLayer<>(this));
    }

    @Override
    protected void scale(UltronSentry sentry, PoseStack pose, float partial) {
        pose.scale(0.85F, 0.85F, 0.85F);
    }

    @Override
    public ResourceLocation getTextureLocation(UltronSentry sentry) {
        return EMPTY;
    }
}

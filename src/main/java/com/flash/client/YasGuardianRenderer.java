package com.flash.client;

import com.flash.FlashMod;
import com.flash.yas.YasGuardian;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Guardian: a hooded figure in white and gold, eyes of light, floating slowly up and down over its altar. */
public class YasGuardianRenderer extends HumanoidMobRenderer<YasGuardian, HumanoidModel<YasGuardian>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID,
            "textures/entity/yas_guardian.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation(FlashMod.MODID,
            "textures/entity/yas_guardian_eyes.png"));

    public YasGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.0F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public void render(YasGuardian entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        pose.pushPose();
        pose.translate(0.0D, 0.25D + Mth.sin((entity.tickCount + partial) * 0.06F) * 0.12D, 0.0D);
        super.render(entity, yaw, partial, pose, buffers, 0xF000F0);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(YasGuardian entity) {
        return TEXTURE;
    }
}

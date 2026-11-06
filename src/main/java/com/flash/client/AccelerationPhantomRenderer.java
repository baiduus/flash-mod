package com.flash.client;

import com.flash.FlashMod;
import com.flash.entity.AccelerationPhantom;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The ghost of the Acceleration Force: humanoid body with zombie arms, its own skin. */
public class AccelerationPhantomRenderer
        extends HumanoidMobRenderer<AccelerationPhantom, AccelerationPhantomRenderer.Model> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(FlashMod.MODID, "textures/entity/acceleration_phantom.png");

    public AccelerationPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AccelerationPhantom phantom) {
        return TEXTURE;
    }

    /** Humanoid model that holds its arms out like a zombie (raised higher while attacking). */
    public static class Model extends HumanoidModel<AccelerationPhantom> {

        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(AccelerationPhantom phantom, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(phantom, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, phantom.isAggressive(),
                    this.attackTime, ageInTicks);
        }
    }
}

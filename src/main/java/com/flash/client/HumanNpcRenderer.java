package com.flash.client;

import com.flash.entity.HumanNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Skeleton;

/**
 * A human of the Manipulation of Reality: Steve (wide arms) or Alex (slim). While growing back from a skeleton the
 * bones are drawn and the flesh comes over them, raw red at first, swelling to the skin; from a zombie the green rot
 * fades from the skin.
 */
public class HumanNpcRenderer extends LivingEntityRenderer<HumanNpc, PlayerModel<HumanNpc>> {

    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/player/wide/steve.png");
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/player/slim/alex.png");
    private static final ResourceLocation BONES = new ResourceLocation("textures/entity/skeleton/skeleton.png");

    private final PlayerModel<HumanNpc> wide;
    private final PlayerModel<HumanNpc> slim;
    private final SkeletonModel<Skeleton> skeleton;

    public HumanNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.skeleton = new SkeletonModel<>(context.bakeLayer(ModelLayers.SKELETON));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(HumanNpc e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = e.alex() ? this.slim : this.wide;
        float g = e.growth(partial);
        if (g >= 1.0F || e.from() == 0) {
            super.render(e, yaw, partial, pose, buffers, light);
            return;
        }
        // the growth: drawn by hand (the bones, then the flesh over them)
        pose.pushPose();
        float body = net.minecraft.util.Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - body));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);
        int overlay = OverlayTexture.NO_OVERLAY;
        float shake = (1.0F - g) * 0.02F;
        if (e.from() == 1) {
            this.skeleton.young = false;
            this.skeleton.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BONES)), light, overlay,
                    1.0F, 1.0F, 1.0F, 1.0F);
            // the flesh: swelling over the bones, red to skin
            float f = Math.max(0.0F, (g - 0.1F) / 0.9F);
            if (f > 0.0F) {
                float s = 0.55F + 0.45F * f;
                pose.pushPose();
                pose.translate((Math.random() - 0.5D) * shake, 0.75F * (1.0F - s), (Math.random() - 0.5D) * shake);
                pose.scale(s, 1.0F, s);
                float red = 1.0F;
                float gb = 0.25F + 0.75F * f * f;
                drawPerson(e, pose, buffers, light, red, gb, gb, Math.min(1.0F, f * 1.6F));
                pose.popPose();
            }
        } else {
            // from rot: green to skin
            float r = 0.45F + 0.55F * g;
            drawPerson(e, pose, buffers, light, r, 0.85F + 0.15F * g, r, 1.0F);
        }
        pose.popPose();
    }

    private void drawPerson(HumanNpc e, PoseStack pose, MultiBufferSource buffers, int light, float r, float g,
                            float b, float a) {
        this.model.young = false;
        this.model.attackTime = 0.0F;
        this.model.setupAnim(e, 0.0F, 0.0F, e.tickCount, 0.0F, 0.0F);
        this.model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(e))), light,
                OverlayTexture.NO_OVERLAY, r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(HumanNpc e) {
        return e.alex() ? ALEX : STEVE;
    }

    @Override
    protected boolean shouldShowName(HumanNpc e) {
        return e.hasCustomName() && super.shouldShowName(e);
    }
}

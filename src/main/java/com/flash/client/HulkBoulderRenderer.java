package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.entity.HulkBoulder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The thrown rock: the rubble model of the Hulk asset pack, tumbling in the air. */
public class HulkBoulderRenderer extends EntityRenderer<HulkBoulder> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/entity/hulk/rubble.png");
    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/hulk_rubble_throw.geo.json");
    private static GeoModel model;

    public HulkBoulderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    public static void reset() {
        model = null;
    }

    @Override
    public void render(HulkBoulder entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        pose.pushPose();
        pose.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTick) * 25.0F));
        float s = 0.6F;
        // model space has y pointing down
        pose.scale(-s, -s, s);
        pose.translate(-model.centerX() / 16.0F, -model.centerY() / 16.0F, -model.centerZ() / 16.0F);
        model.reset();
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HulkBoulder entity) {
        return TEXTURE;
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.entity.MjolnirEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The thrown Mjolnir: the 3D model of the pack spinning head over handle, the lightning layer over it. */
public class MjolnirRenderer extends EntityRenderer<MjolnirEntity> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/thor/mjolnir.png");
    public static final ResourceLocation LIGHTNING = new ResourceLocation(FlashMod.MODID,
            "textures/thor/mjolnir_lightning.png");
    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/thor/mjolnir.geo.json");
    private static GeoModel model;

    public MjolnirRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    public static void reset() {
        model = null;
    }

    @Override
    public void render(MjolnirEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        if (model == null) {
            return;
        }
        var v = entity.getDeltaMovement();
        float heading = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        pose.pushPose();
        pose.translate(0.0D, 0.25D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(heading));
        // head first, tumbling
        pose.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTick) * 40.0F));
        float s = 0.9F;
        pose.scale(-s, -s, s);
        pose.translate(-model.centerX() / 16.0F, -model.centerY() / 16.0F, -model.centerZ() / 16.0F);
        model.reset();
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                0, 1.0F, 1.0F, 1.0F, 1.0F);
        float glow = 0.6F + 0.4F * Mth.sin((entity.tickCount + partialTick) * 0.8F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHTNING)), 0xF000F0,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, glow);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MjolnirEntity entity) {
        return TEXTURE;
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.entity.LexMissile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Lex's missile: the homing missile model of Legends: Superheroes (in the suit's purple, green lights), nose first
 * along its flight, turning slowly around its axis.
 */
public class LexMissileRenderer extends EntityRenderer<LexMissile> {

    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/lex/missile.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/lex/missile.png");
    private static GeoModel model;

    public LexMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.1F;
    }

    public static void reset() {
        model = null;
    }

    @Override
    public void render(LexMissile entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        if (model == null) {
            return;
        }
        var v = entity.getDeltaMovement();
        float heading = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        float pitch = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
        pose.pushPose();
        pose.translate(0.0D, 0.15D, 0.0D);
        // the model is long along +x (the nose): turn it to the flight direction
        pose.mulPose(Axis.YP.rotationDegrees(heading - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(pitch));
        pose.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTick) * 18.0F));
        float s = 0.28F;
        pose.scale(-s, -s, s);
        pose.translate(-model.centerX() / 16.0F, -model.centerY() / 16.0F, -model.centerZ() / 16.0F);
        model.reset();
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), 0xF000F0,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LexMissile entity) {
        return TEXTURE;
    }
}

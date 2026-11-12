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
    private static final ResourceLocation[] GEO = {
            new ResourceLocation(FlashMod.MODID, "geo/thor/mjolnir.geo.json"),
            new ResourceLocation(FlashMod.MODID, "geo/thor/mjolnirmcu.geo.json"),
            new ResourceLocation(FlashMod.MODID, "geo/thor/mjolnir.geo.json"),
            new ResourceLocation(FlashMod.MODID, "geo/thor/stormbreaker.geo.json")};
    private static final ResourceLocation[] TEXTURES = {TEXTURE,
            new ResourceLocation(FlashMod.MODID, "textures/thor/mjolnir_mcu.png"),
            new ResourceLocation(FlashMod.MODID, "textures/thor/mjolnir_cosmic_king.png"),
            new ResourceLocation(FlashMod.MODID, "textures/thor/stormbreaker_entity.png")};
    private static final ResourceLocation[] GLOWS = {LIGHTNING,
            new ResourceLocation(FlashMod.MODID, "textures/thor/mjolnir_mcu_lightning.png"),
            new ResourceLocation(FlashMod.MODID, "textures/thor/mjolnir_cosmic_king_lightning.png"),
            new ResourceLocation(FlashMod.MODID, "textures/thor/stormbreaker_entity_lit.png")};
    private static final GeoModel[] MODELS = new GeoModel[GEO.length];

    public MjolnirRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    public static void reset() {
        java.util.Arrays.fill(MODELS, null);
    }

    @Override
    public void render(MjolnirEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        int w = Math.max(0, Math.min(GEO.length - 1, entity.weapon()));
        if (MODELS[w] == null) {
            MODELS[w] = GeoModel.load(GEO[w], null);
        }
        GeoModel model = MODELS[w];
        if (model == null) {
            return;
        }
        pose.pushPose();
        if (entity.statue()) {
            // lying on the ground: standing on its head, the handle up
            pose.translate(0.0D, 0.47D, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(entity.getId() * 37 % 360));
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        } else {
            // flying straight: head first along the velocity, no tumbling
            var v = entity.getDeltaMovement();
            float heading = (float) Math.toDegrees(Math.atan2(v.x, v.z));
            float pitch = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
            pose.translate(0.0D, 0.25D, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(heading));
            pose.mulPose(Axis.XP.rotationDegrees(90.0F - pitch));
        }
        // on the ground about the size it has in the hand; a bit bigger in flight, to be seen
        float s = entity.statue() ? 0.5F : 0.7F;
        pose.scale(-s, -s, s);
        pose.translate(-model.centerX() / 16.0F, -model.centerY() / 16.0F, -model.centerZ() / 16.0F);
        model.reset();
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[w])), light, OverlayTexture.NO_OVERLAY,
                0, 1.0F, 1.0F, 1.0F, 1.0F);
        float glow = 0.6F + 0.4F * Mth.sin((entity.tickCount + partialTick) * 0.8F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOWS[w])), 0xF000F0,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, glow);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /**
     * The classic Mjolnir standing on its head with the handle up, the size it has when thrown to the ground
     * (the hammer stuck in the crater: MjolnirStoneRenderer).
     */
    public static void drawStatue(PoseStack pose, MultiBufferSource buffers, int light, float yaw, float time) {
        if (MODELS[0] == null) {
            MODELS[0] = GeoModel.load(GEO[0], null);
        }
        GeoModel model = MODELS[0];
        if (model == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0D, 0.47D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        float s = 0.5F;
        pose.scale(-s, -s, s);
        pose.translate(-model.centerX() / 16.0F, -model.centerY() / 16.0F, -model.centerZ() / 16.0F);
        model.reset();
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[0])), light,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        float glow = 0.6F + 0.4F * Mth.sin(time * 0.8F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOWS[0])), 0xF000F0,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, glow);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MjolnirEntity entity) {
        return TEXTURE;
    }
}

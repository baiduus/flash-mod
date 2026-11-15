package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Wonder Woman's shield on her back (not while it is thrown) and the Lasso of Truth coiled at her right hip, while
 * she wears her suit. Also draws the shield in flight (WonderVfx).
 */
public class WonderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation SHIELD_GEO = new ResourceLocation(FlashMod.MODID,
            "geo/wonder/amazon_shield.geo.json");
    private static final ResourceLocation SHIELD_TEX = new ResourceLocation(FlashMod.MODID,
            "textures/wonder/amazon_shield.png");
    private static final ResourceLocation LASSO_GEO = new ResourceLocation(FlashMod.MODID,
            "geo/wonder/lasso_of_truth.geo.json");
    private static final ResourceLocation LASSO_TEX = new ResourceLocation(FlashMod.MODID,
            "textures/wonder/lasso_of_truth.png");

    private static GeoModel shield;
    private static GeoModel lasso;

    public WonderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void resetModels() {
        shield = null;
        lasso = null;
    }

    private static GeoModel shield() {
        if (shield == null) {
            shield = GeoModel.load(SHIELD_GEO, null);
        }
        return shield;
    }

    private static GeoModel lasso() {
        if (lasso == null) {
            lasso = GeoModel.load(LASSO_GEO, null);
        }
        return lasso;
    }

    /** The shield centred on the pose, its face along +z (model space, y down), {@code scale} times its size. */
    static void drawShield(PoseStack pose, MultiBufferSource buffers, int light, float scale, boolean standing) {
        GeoModel m = shield();
        if (m == null) {
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        // the disc lies flat (its dome up): stood up, the dome toward +z
        if (standing) {
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }
        pose.translate(-m.centerX() / 16.0F, -m.centerY() / 16.0F, -m.centerZ() / 16.0F);
        m.reset();
        m.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SHIELD_TEX)), light, OverlayTexture.NO_OVERLAY,
                0, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (!WonderClient.isWonder(player) || !WonderClient.suited(player) || player.isInvisible()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        if (!WonderVfx.shieldOut(player.getId())) {
            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.translate(0.0D, 5.5D / 16.0D, 3.0D / 16.0D);
            drawShield(pose, buffers, light, 0.78F, true);
            pose.popPose();
        }
        GeoModel l = lasso();
        if (l != null) {
            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.translate(-4.4D / 16.0D, 10.5D / 16.0D, 0.0D);
            float s = 0.42F;
            pose.scale(s, s, s);
            pose.translate(-l.centerX() / 16.0F, -l.centerY() / 16.0F, -l.centerZ() / 16.0F);
            l.reset();
            l.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(LASSO_TEX)), light,
                    OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
    }
}

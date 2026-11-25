package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Ultron's body (geo/ultron.geo.json, tools/gen_ultron.py: made on the Lex suit's skeleton) drawn over the player
 * who is Ultron, and over the sentries of his Legion: each root bone follows its part of the humanoid model. The red
 * lights (bones "glow*") shine, brighter while he fires his beam.
 */
public class UltronLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/ultron.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/entity/ultron.png");
    private static GeoModel model;

    public UltronLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void reset() {
        model = null;
    }

    static GeoModel model() {
        if (model == null) {
            model = GeoModel.load(GEO, null, true);
        }
        return model;
    }

    public static void hideSkin(PlayerModel<?> m) {
        m.head.visible = false;
        m.hat.visible = false;
        m.body.visible = false;
        m.jacket.visible = false;
        m.rightArm.visible = false;
        m.rightSleeve.visible = false;
        m.leftArm.visible = false;
        m.leftSleeve.visible = false;
        m.rightLeg.visible = false;
        m.rightPants.visible = false;
        m.leftLeg.visible = false;
        m.leftPants.visible = false;
    }

    private static boolean shows(LivingEntity e) {
        return e instanceof com.flash.ultron.UltronSentry || UltronClient.isUltron(e);
    }

    static ModelPart partFor(HumanoidModel<?> m, String root) {
        return switch (root.toLowerCase(Locale.ROOT).replace("_", "")) {
            case "head" -> m.head;
            case "rightarm" -> m.rightArm;
            case "leftarm" -> m.leftArm;
            case "rightleg" -> m.rightLeg;
            case "leftleg" -> m.leftLeg;
            default -> m.body;
        };
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !shows(entity)) {
            return;
        }
        GeoModel m = model();
        if (m == null) {
            return;
        }
        m.reset();
        M parent = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        float pulse = 0.82F + 0.18F * (float) Math.sin(ageInTicks * 0.15F);
        float glow = UltronClient.beaming(entity) ? 1.0F : pulse;
        for (GeoModel.Bone root : m.roots()) {
            ModelPart part = partFor(parent, root.name);
            drawBone(m, root, part, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay,
                    0, 1.0F);
            drawBone(m, root, part, pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 0xF000F0,
                    overlay, 1, glow);
        }
    }

    static void drawBone(GeoModel model, GeoModel.Bone bone, ModelPart part, PoseStack pose,
                         com.mojang.blaze3d.vertex.VertexConsumer consumer, int light, int overlay, int group,
                         float alpha) {
        pose.pushPose();
        pose.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
            pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            pose.scale(part.xScale, part.yScale, part.zScale);
        }
        pose.translate(-bone.pivotX() / 16.0F, -bone.pivotY() / 16.0F, -bone.pivotZ() / 16.0F);
        model.renderBone(bone, pose, consumer, light, overlay, group, alpha, alpha, alpha, 1.0F, true);
        pose.popPose();
    }

    /** First person: his arm instead of the player's. */
    public static void drawArm(PoseStack pose, MultiBufferSource buffers, int light,
                               net.minecraft.client.player.AbstractClientPlayer player,
                               PlayerModel<net.minecraft.client.player.AbstractClientPlayer> parent,
                               HumanoidArm arm) {
        if (!UltronClient.isUltron(player)) {
            return;
        }
        GeoModel m = model();
        if (m == null) {
            return;
        }
        m.reset();
        parent.attackTime = 0.0F;
        parent.crouching = false;
        parent.swimAmount = 0.0F;
        parent.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ModelPart part = arm == HumanoidArm.RIGHT ? parent.rightArm : parent.leftArm;
        part.xRot = 0.0F;
        String want = arm == HumanoidArm.RIGHT ? "rightarm" : "leftarm";
        for (GeoModel.Bone root : m.roots()) {
            if (root.name.toLowerCase(Locale.ROOT).replace("_", "").equals(want)) {
                drawBone(m, root, part, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                        OverlayTexture.NO_OVERLAY, 0, 1.0F);
                drawBone(m, root, part, pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)),
                        0xF000F0, OverlayTexture.NO_OVERLAY, 1, 0.9F);
            }
        }
    }
}

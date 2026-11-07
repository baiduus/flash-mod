package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.flash.superman.SupermanSuits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * The Superman suit chosen in the K menu, drawn with the pack's 3D model (geo/superman/&lt;suit&gt;.geo.json) on the
 * body parts of the player while in the Superman form: layer_0 (top, arms, boots), layer_1 (trousers), glowing eyes
 * during the heat vision, helmet overlay and the cape.
 */
public class SupermanSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final Map<String, GeoModel> MODELS = new HashMap<>();
    private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    public SupermanSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void reset() {
        MODELS.clear();
    }

    private static GeoModel model(String id) {
        return MODELS.computeIfAbsent(id, k -> GeoModel.load(new ResourceLocation(FlashMod.MODID,
                "geo/superman/" + k + ".geo.json"), null));
    }

    private static ResourceLocation tex(String id, String name) {
        return new ResourceLocation(FlashMod.MODID, "textures/superman_suits/" + id + "/" + name + ".png");
    }

    /** Suit worn by this player now, or null (not in the Superman form). */
    public static String worn(AbstractClientPlayer player) {
        if (!SupermanState.isSuperman(player)) {
            return null;
        }
        return SupermanSuits.id(SupermanState.suit(player));
    }

    private static ModelPart part(PlayerModel<?> m, String bone) {
        return switch (bone) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            default -> m.leftLeg;
        };
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        String id = worn(player);
        if (id == null) {
            return;
        }
        GeoModel model = model(id);
        if (model == null) {
            return;
        }
        model.reset();
        for (String b : BONES) {
            GeoModel.Bone bone = model.bone(b);
            if (bone != null) {
                bone.sx *= 1.03F;
                bone.sy *= 1.03F;
                bone.sz *= 1.03F;
            }
        }
        PlayerModel<AbstractClientPlayer> parent = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        RenderType top = RenderType.entityCutoutNoCull(tex(id, "layer_0"));
        RenderType bottom = RenderType.entityCutoutNoCull(tex(id, "layer_1"));
        for (String b : new String[] {"body", "right_leg", "left_leg"}) {
            draw(model, parent, b, pose, buffers, bottom, light, overlay);
        }
        for (String b : BONES) {
            draw(model, parent, b, pose, buffers, top, light, overlay);
        }
        if (SupermanSuits.hasOverlay(id)) {
            draw(model, parent, "head", pose, buffers, RenderType.entityTranslucent(tex(id, "helmet_overlay")), light,
                    overlay);
        }
        if (SupermanState.flag(player, SupermanPowers.F_HEAT) || SupermanState.flag(player, SupermanPowers.F_LASER)) {
            draw(model, parent, "head", pose, buffers, RenderType.entityTranslucentEmissive(tex(id, "eyes")),
                    0xF000F0, overlay);
        }
        if (SupermanSuits.hasCape(id) && !player.isSpectator()) {
            cape(pose, buffers, light, player, partialTick, tex(id, "cape"));
        }
    }

    private static void draw(GeoModel model, PlayerModel<?> parent, String boneName, PoseStack pose,
                             MultiBufferSource buffers, RenderType type, int light, int overlay) {
        GeoModel.Bone bone = model.bone(boneName);
        if (bone == null) {
            return;
        }
        ModelPart part = part(parent, boneName);
        if (!part.visible) {
            return;
        }
        pose.pushPose();
        pose.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
            pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            pose.scale(part.xScale, part.yScale, part.zScale);
        }
        pose.translate(-bone.pivotX() / 16.0F, -bone.pivotY() / 16.0F, -bone.pivotZ() / 16.0F);
        model.renderBone(bone, pose, buffers.getBuffer(type), light, overlay, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
        pose.popPose();
    }

    /** The vanilla cape math (CapeLayer) with the suit's cape texture. */
    private void cape(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float pt,
                      ResourceLocation texture) {
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.125F);
        double d0 = Mth.lerp(pt, player.xCloakO, player.xCloak) - Mth.lerp(pt, player.xo, player.getX());
        double d1 = Mth.lerp(pt, player.yCloakO, player.yCloak) - Mth.lerp(pt, player.yo, player.getY());
        double d2 = Mth.lerp(pt, player.zCloakO, player.zCloak) - Mth.lerp(pt, player.zo, player.getZ());
        float f = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        double d3 = Mth.sin(f * Mth.DEG_TO_RAD);
        double d4 = -Mth.cos(f * Mth.DEG_TO_RAD);
        float f1 = Mth.clamp((float) d1 * 10.0F, -6.0F, 32.0F);
        float f2 = Mth.clamp((float) (d0 * d3 + d2 * d4) * 100.0F, 0.0F, 150.0F);
        float f3 = Mth.clamp((float) (d0 * d4 - d2 * d3) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(pt, player.oBob, player.bob);
        f1 += Mth.sin(Mth.lerp(pt, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * bob;
        if (player.isCrouching()) {
            f1 += 25.0F;
        }
        pose.mulPose(Axis.XP.rotationDegrees(6.0F + f2 / 2.0F + f1));
        pose.mulPose(Axis.ZP.rotationDegrees(f3 / 2.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - f3 / 2.0F));
        this.getParentModel().renderCloak(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** First person: the suit sleeve over the hand just drawn. */
    public static void drawArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                               PlayerModel<AbstractClientPlayer> parent, HumanoidArm arm) {
        String id = worn(player);
        if (id == null) {
            return;
        }
        GeoModel model = model(id);
        if (model == null) {
            return;
        }
        model.reset();
        String b = arm == HumanoidArm.RIGHT ? "right_arm" : "left_arm";
        GeoModel.Bone bone = model.bone(b);
        if (bone != null) {
            bone.sx *= 1.03F;
            bone.sy *= 1.03F;
            bone.sz *= 1.03F;
        }
        draw(model, parent, b, pose, buffers, RenderType.entityCutoutNoCull(tex(id, "layer_0")), light,
                OverlayTexture.NO_OVERLAY);
    }
}

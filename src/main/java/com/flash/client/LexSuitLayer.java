package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.item.LexArmorItem;
import com.flash.lex.LexState;
import com.flash.lex.LexSuit;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Lex Luthor's war suit drawn with the Iron Man Mark 6 model of Legends: Superheroes, in Lex's green and purple,
 * piece by piece (helmet, chest with the arms, legs) on whoever wears it: players and the armour stand. The
 * lights (eyes, arc reactor, repulsors) glow, brighter in overdrive; the faceplate opens.
 */
public class LexSuitLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/lex/mark6.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/lex/mark6.png");
    private static final ResourceLocation GLOW = new ResourceLocation(FlashMod.MODID, "textures/lex/mark6_glow.png");
    private static GeoModel model;

    public LexSuitLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void reset() {
        model = null;
    }

    private static GeoModel model() {
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        return model;
    }

    private static EquipmentSlot slotFor(String root) {
        return switch (root.toLowerCase(Locale.ROOT).replace("_", "")) {
            case "head" -> EquipmentSlot.HEAD;
            case "rightleg", "leftleg" -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.CHEST;
        };
    }

    private static ModelPart partFor(HumanoidModel<?> m, String root) {
        return switch (root.toLowerCase(Locale.ROOT).replace("_", "")) {
            case "head" -> m.head;
            case "rightarm" -> m.rightArm;
            case "leftarm" -> m.leftArm;
            case "rightleg" -> m.rightLeg;
            case "leftleg" -> m.leftLeg;
            default -> m.body;
        };
    }

    private static boolean wears(LivingEntity e, EquipmentSlot slot) {
        if (LexArmorItem.isLex(e.getItemBySlot(slot))) {
            return true;
        }
        // the boots alone still show the legs of the suit
        return slot == EquipmentSlot.LEGS && LexArmorItem.isLex(e.getItemBySlot(EquipmentSlot.FEET));
    }

    public static boolean wearsAny(LivingEntity e) {
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            if (LexArmorItem.isLex(e.getItemBySlot(s))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !wearsAny(entity)) {
            return;
        }
        GeoModel m = model();
        if (m == null) {
            return;
        }
        m.reset();
        boolean open = LexState.flag(entity, LexSuit.F_OPEN);
        if (open) {
            for (GeoModel.Bone b : m.allBones()) {
                if (b.name.equals("HelmetOpenBase")) {
                    b.visible = false;
                }
            }
        }
        M parent = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        RenderType body = RenderType.entityCutoutNoCull(TEXTURE);
        boolean on = LexState.flag(entity, LexSuit.F_ON);
        boolean over = LexState.flag(entity, LexSuit.F_OVERDRIVE);
        float glow = !on ? 0.25F : over ? 1.0F : 0.8F;
        RenderType lights = RenderType.entityTranslucentEmissive(GLOW);
        for (GeoModel.Bone root : m.roots()) {
            if (!wears(entity, slotFor(root.name))) {
                continue;
            }
            ModelPart part = partFor(parent, root.name);
            drawBone(m, root, part, pose, buffers, body, light, overlay, 1.0F);
            drawBone(m, root, part, pose, buffers, lights, 0xF000F0, overlay, glow);
        }
    }

    private static void drawBone(GeoModel model, GeoModel.Bone bone, ModelPart part, PoseStack pose,
                                 MultiBufferSource buffers, RenderType type, int light, int overlay, float alpha) {
        pose.pushPose();
        pose.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
            pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            pose.scale(part.xScale, part.yScale, part.zScale);
        }
        pose.translate(-bone.pivotX() / 16.0F, -bone.pivotY() / 16.0F, -bone.pivotZ() / 16.0F);
        model.renderBone(bone, pose, buffers.getBuffer(type), light, overlay, 0, 1.0F, 1.0F, 1.0F, alpha, true);
        pose.popPose();
    }

    /** First person: the gauntlet over the hand. */
    public static void drawArm(PoseStack pose, MultiBufferSource buffers, int light,
                               net.minecraft.client.player.AbstractClientPlayer player,
                               net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> parent,
                               HumanoidArm arm) {
        if (!LexArmorItem.isLex(player.getItemBySlot(EquipmentSlot.CHEST))) {
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
                drawBone(m, root, part, pose, buffers, RenderType.entityCutoutNoCull(TEXTURE), light,
                        OverlayTexture.NO_OVERLAY, 1.0F);
                drawBone(m, root, part, pose, buffers, RenderType.entityTranslucentEmissive(GLOW), 0xF000F0,
                        OverlayTexture.NO_OVERLAY, LexState.flag(player, LexSuit.F_ON) ? 0.8F : 0.25F);
            }
        }
    }
}

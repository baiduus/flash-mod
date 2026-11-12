package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.item.LexArmorItem;
import com.flash.lex.LexState;
import com.flash.lex.LexSuit;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.Minecraft;
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
    private static final ResourceLocation ANIMS = new ResourceLocation(FlashMod.MODID, "geo/lex/mark6.animation.json");
    private static GeoModel model;
    private static java.util.Map<String, com.flash.client.geo.GeoAnimation> anims;

    public LexSuitLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void reset() {
        model = null;
        anims = null;
    }

    private static void play(GeoModel m, String name, float seconds) {
        if (anims == null) {
            anims = com.flash.client.geo.GeoAnimation.load(ANIMS);
        }
        com.flash.client.geo.GeoAnimation a = anims.get(name);
        if (a != null && seconds > 0.0F) {
            a.apply(m, seconds);
        }
    }

    /**
     * The moving parts of the Mark 6 with the animations of Legends: the faceplate lifting, the back and calf flaps
     * of the fast flight, the wrist lasers, the wrist launcher of the missiles and the overdrive (which moves all of
     * them at once, so the others only add what it does not already give).
     */
    private static void animate(GeoModel m, LivingEntity e, float partial) {
        play(m, "helmet_open", LexFlight.part(e, LexFlight.P_FACEPLATE, partial) * 0.25F);
        float od = LexFlight.part(e, LexFlight.P_OVERDRIVE, partial);
        play(m, "overdrive", od * 0.25F);
        play(m, "thrusters", Math.max(0.0F, LexFlight.part(e, LexFlight.P_THRUSTERS, partial) - od) * 0.125F);
        play(m, "laser_both", Math.max(0.0F, LexFlight.part(e, LexFlight.P_LASERS, partial) - od) * 0.1667F);
        play(m, "mini_missiles", Math.max(0.0F, LexFlight.part(e, LexFlight.P_PODS, partial) - od) * 0.25F);
    }

    /** The skin under the suit is hidden (it showed through the plates); the face only with the faceplate up. */
    public static void hideSkin(net.minecraft.client.model.PlayerModel<?> m, LivingEntity e, float partial) {
        if (LexArmorItem.isLex(e.getItemBySlot(EquipmentSlot.HEAD))) {
            m.hat.visible = false;
            m.head.visible = LexFlight.part(e, LexFlight.P_FACEPLATE, partial) > 0.02F;
        }
        if (LexArmorItem.isLex(e.getItemBySlot(EquipmentSlot.CHEST))) {
            m.body.visible = false;
            m.jacket.visible = false;
            m.rightArm.visible = false;
            m.rightSleeve.visible = false;
            m.leftArm.visible = false;
            m.leftSleeve.visible = false;
        }
        if (wears(e, EquipmentSlot.LEGS)) {
            m.rightLeg.visible = false;
            m.rightPants.visible = false;
            m.leftLeg.visible = false;
            m.leftPants.visible = false;
        }
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
        animate(m, entity, partialTick);
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
        float fly = LexFlight.part(entity, LexFlight.P_FLY, partialTick);
        if (fly > 0.01F && on) {
            float fast = LexFlight.part(entity, LexFlight.P_THRUSTERS, partialTick);
            thrusters(parent, pose, buffers, fly, fast, ageInTicks, over);
        }
    }

    /**
     * The thrusters' flames of solid light, green with a white core like the suit's other lights: from the boots
     * (long in the fast flight) and from the palms, flickering.
     */
    private static void thrusters(HumanoidModel<?> parent, PoseStack pose, MultiBufferSource buffers, float fly,
                                  float fast, float age, boolean over) {
        com.mojang.blaze3d.vertex.VertexConsumer c = buffers.getBuffer(RenderType.lightning());
        float flicker = 0.85F + 0.15F * (float) Math.sin(age * 2.7F) * (float) Math.cos(age * 1.3F);
        float boot = (6.0F + 9.0F * fast) * fly * flicker * (over ? 1.25F : 1.0F);
        float palm = (3.5F + 2.5F * fast) * fly * flicker;
        // (in each part: its pivot, then the foot / the palm, in pixels; y grows down the limb)
        flame(c, pose, parent.rightLeg, 0.0F, 12.0F, boot, 2.0F);
        flame(c, pose, parent.leftLeg, 0.0F, 12.0F, boot, 2.0F);
        flame(c, pose, parent.rightArm, -1.0F, 10.2F, palm, 1.4F);
        flame(c, pose, parent.leftArm, 1.0F, 10.2F, palm, 1.4F);
    }

    private static void flame(com.mojang.blaze3d.vertex.VertexConsumer c, PoseStack pose, ModelPart part, float x,
                              float y, float len, float width) {
        pose.pushPose();
        part.translateAndRotate(pose);
        org.joml.Matrix4f m = pose.last().pose();
        float px = x / 16.0F;
        float py = y / 16.0F;
        float l = len / 16.0F;
        float w = width / 16.0F;
        // glow, body and white core: each two crossed planes, both faces
        cross(c, m, px, py, l, w * 1.6F, 0.35F, 1.0F, 0.45F, 0.28F);
        cross(c, m, px, py, l * 0.75F, w, 0.55F, 1.0F, 0.6F, 0.55F);
        cross(c, m, px, py, l * 0.45F, w * 0.45F, 1.0F, 1.0F, 1.0F, 0.9F);
        pose.popPose();
    }

    private static void cross(com.mojang.blaze3d.vertex.VertexConsumer c, org.joml.Matrix4f m, float x, float y,
                              float len, float w, float r, float g, float b, float a) {
        float tip = w * 0.15F;
        for (int plane = 0; plane < 2; plane++) {
            float ax = plane == 0 ? 1.0F : 0.0F;
            float az = plane == 0 ? 0.0F : 1.0F;
            float[][] v = {
                    {x - ax * w, y, -az * w}, {x + ax * w, y, az * w},
                    {x + ax * tip, y + len, az * tip}, {x - ax * tip, y + len, -az * tip}};
            for (int face = 0; face < 2; face++) {
                for (int i = 0; i < 4; i++) {
                    float[] p = v[face == 0 ? i : 3 - i];
                    float alpha = p[1] > y + 1.0E-4F ? 0.0F : a;
                    c.vertex(m, p[0], p[1], p[2]).color(r, g, b, alpha).endVertex();
                }
            }
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
        animate(m, player, Minecraft.getInstance().getFrameTime());
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

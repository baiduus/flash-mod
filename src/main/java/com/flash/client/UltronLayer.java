package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoAnimation;
import com.flash.client.geo.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import java.util.Map;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Ultron's body (tools/gen_ultron_lex.py): the Lex suit's Mark 6 itself recoloured silver and gunmetal, its lights
 * red (a glow texture drawn full-bright over it), and a head of his own (geo/ultron_head) that never opens. Drawn over
 * the player who is Ultron and over the drones of his Legion, each root bone on its part of the humanoid model.
 * In flight the back and calf flaps open as the Mark 6's do in the fast flight, and the thrusters burn from the boots
 * and the palms (longer in the fast flight, blazing in Ultron Prime).
 */
public class UltronLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/ultron.geo.json");
    private static final ResourceLocation HEAD = new ResourceLocation(FlashMod.MODID, "geo/ultron_head.geo.json");
    private static final ResourceLocation ANIMS = new ResourceLocation(FlashMod.MODID, "geo/lex/mark6.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/entity/ultron.png");
    private static final ResourceLocation GLOW = new ResourceLocation(FlashMod.MODID,
            "textures/entity/ultron_glow.png");
    private static final ResourceLocation HEAD_TEXTURE = new ResourceLocation(FlashMod.MODID,
            "textures/entity/ultron_head.png");
    private static GeoModel model;
    private static GeoModel head;
    private static Map<String, GeoAnimation> anims;

    public UltronLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void reset() {
        model = null;
        head = null;
        anims = null;
    }

    static GeoModel model() {
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        return model;
    }

    static GeoModel head() {
        if (head == null) {
            head = GeoModel.load(HEAD, null, true);
        }
        return head;
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

    private static void animate(GeoModel m, LivingEntity e, float partial) {
        if (anims == null) {
            anims = GeoAnimation.load(ANIMS);
        }
        float fast = UltronFlight.fast(e, partial);
        if (fast > 0.0F) {
            GeoAnimation a = anims.get("thrusters");
            if (a != null) {
                a.apply(m, fast * 0.125F);
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !shows(entity)) {
            return;
        }
        GeoModel m = model();
        GeoModel h = head();
        if (m == null || h == null) {
            return;
        }
        m.reset();
        h.reset();
        animate(m, entity, partialTick);
        M parent = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        boolean prime = UltronClient.prime(entity);
        float pulse = 0.8F + 0.2F * (float) Math.sin(ageInTicks * 0.16F);
        float glow = prime ? 1.0F : UltronClient.beaming(entity) ? 1.0F : pulse;
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (GeoModel.Bone root : m.roots()) {
            drawBone(m, root, partFor(parent, root.name), pose, solid, light, overlay, 0, 1.0F);
        }
        VertexConsumer lights = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        for (GeoModel.Bone root : m.roots()) {
            drawBone(m, root, partFor(parent, root.name), pose, lights, 0xF000F0, overlay, 0, glow);
        }
        VertexConsumer hs = buffers.getBuffer(RenderType.entityCutoutNoCull(HEAD_TEXTURE));
        for (GeoModel.Bone root : h.roots()) {
            drawBone(h, root, parent.head, pose, hs, light, overlay, 0, 1.0F);
        }
        VertexConsumer hl = buffers.getBuffer(RenderType.entityTranslucentEmissive(HEAD_TEXTURE));
        for (GeoModel.Bone root : h.roots()) {
            drawBone(h, root, parent.head, pose, hl, 0xF000F0, overlay, 1, glow);
        }
        float fly = UltronFlight.fly(entity, partialTick);
        if (fly > 0.01F) {
            thrusters(parent, pose, buffers, fly, UltronFlight.fast(entity, partialTick), ageInTicks, prime);
        }
    }

    static void drawBone(GeoModel model, GeoModel.Bone bone, ModelPart part, PoseStack pose, VertexConsumer consumer,
                         int light, int overlay, int group, float alpha) {
        pose.pushPose();
        pose.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
            pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            pose.scale(part.xScale, part.yScale, part.zScale);
        }
        pose.translate(-bone.pivotX() / 16.0F, -bone.pivotY() / 16.0F, -bone.pivotZ() / 16.0F);
        model.renderBone(bone, pose, consumer, light, overlay, group, 1.0F, 1.0F, 1.0F, alpha, true);
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // the thrusters: red-orange flames with a white core, from the boots and the palms
    // ------------------------------------------------------------------

    private static void thrusters(HumanoidModel<?> parent, PoseStack pose, MultiBufferSource buffers, float fly,
                                  float fast, float age, boolean prime) {
        VertexConsumer c = buffers.getBuffer(RenderType.lightning());
        float flicker = 0.85F + 0.15F * (float) Math.sin(age * 2.9F) * (float) Math.cos(age * 1.7F);
        float boot = (6.5F + 10.0F * fast) * fly * flicker * (prime ? 1.35F : 1.0F);
        float palm = (3.5F + 3.0F * fast) * fly * flicker * (prime ? 1.2F : 1.0F);
        flame(c, pose, parent.rightLeg, 0.0F, 12.0F, boot, 2.1F);
        flame(c, pose, parent.leftLeg, 0.0F, 12.0F, boot, 2.1F);
        flame(c, pose, parent.rightArm, -1.0F, 10.2F, palm, 1.4F);
        flame(c, pose, parent.leftArm, 1.0F, 10.2F, palm, 1.4F);
    }

    private static void flame(VertexConsumer c, PoseStack pose, ModelPart part, float x, float y, float len,
                              float width) {
        pose.pushPose();
        part.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        float px = x / 16.0F;
        float py = y / 16.0F;
        float l = len / 16.0F;
        float w = width / 16.0F;
        cross(c, m, px, py, l, w * 1.7F, 1.0F, 0.25F, 0.12F, 0.3F);
        cross(c, m, px, py, l * 0.75F, w, 1.0F, 0.55F, 0.25F, 0.55F);
        cross(c, m, px, py, l * 0.45F, w * 0.45F, 1.0F, 0.95F, 0.85F, 0.9F);
        pose.popPose();
    }

    private static void cross(VertexConsumer c, Matrix4f m, float x, float y, float len, float w, float r, float g,
                              float b, float a) {
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
                drawBone(m, root, part, pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)),
                        0xF000F0, OverlayTexture.NO_OVERLAY, 0, 0.9F);
            }
        }
    }
}

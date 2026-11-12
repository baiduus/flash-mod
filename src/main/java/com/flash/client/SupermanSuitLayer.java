package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.flash.superman.SupermanSuits;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = FlashMod.MODID,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT)
/**
 * The Superman suit chosen in the K menu, drawn with the pack's 3D model (geo/superman/&lt;suit&gt;.geo.json) on the
 * body parts of the player while in the Superman form: layer_0 (top, arms, boots), layer_1 (trousers), glowing eyes
 * during the heat vision, helmet overlay and the cloth cape ({@link SupermanCape}). It replaces the skin.
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

    /** The suit replaces the skin: the player's own model parts are hidden while it is worn. */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer player) || worn(player) == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
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

    private static GeoModel model(String id) {
        return MODELS.computeIfAbsent(id, k -> GeoModel.load(new ResourceLocation(FlashMod.MODID,
                (thor(k) ? "geo/thor/" : "geo/superman/") + k + ".geo.json"), null));
    }

    private static ResourceLocation tex(String id, String name) {
        return new ResourceLocation(FlashMod.MODID, (thor(id) ? "textures/thor_suits/" : "textures/superman_suits/")
                + id + "/" + name + ".png");
    }

    /** Thor suits (same layer: 3D model on the body, physics cape) are told apart by their id. */
    private static boolean thor(String id) {
        return id.startsWith("thor");
    }

    /** Suit worn by this player now, or null (not in the Superman form). */
    public static String worn(AbstractClientPlayer player) {
        if (com.flash.thor.ThorState.isThor(player)) {
            return com.flash.thor.ThorSuits.id(com.flash.thor.ThorState.suit(player));
        }
        if (!SupermanState.isSuperman(player)) {
            return null;
        }
        return SupermanSuits.id(SupermanState.suit(player));
    }

    /** The elbow and knee bends of the playing animation (as Man of Tomorrow syncs them to its suit). */
    private static void bends(GeoModel model, AbstractClientPlayer player) {
        try {
            if (!(player instanceof dev.kosmx.playerAnim.impl.IAnimatedPlayer animated)) {
                return;
            }
            var anim = animated.playerAnimator_getAnimation();
            if (anim == null || !anim.isActive()) {
                return;
            }
            String[][] map = {{"right_arm", "rightArm"}, {"left_arm", "leftArm"}, {"right_leg", "rightLeg"},
                    {"left_leg", "leftLeg"}};
            for (String[] m : map) {
                GeoModel.Bone bone = rootFor(model, m[0]);
                var bend = anim.getBend(m[1]);
                if (bone != null && bend != null && bend.getRight() != null) {
                    bone.bendAxis = bend.getLeft() != null ? bend.getLeft() : 0.0F;
                    bone.bendValue = bend.getRight();
                }
            }
        } catch (Throwable ignored) {
            // no bends then
        }
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
        bends(model, player);
        PlayerModel<AbstractClientPlayer> parent = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        String layer0 = "layer_0";
        if (thor(id) && com.flash.thor.ThorState.helmetOff(player)) {
            // the helmet taken off (the K menu): its bones hidden, the texture without it when the suit has one
            for (GeoModel.Bone b : model.allBones()) {
                if (b.name.startsWith("Helmet") || b.name.startsWith("Wing")) {
                    b.visible = false;
                }
            }
            if (com.flash.thor.ThorSuits.hasHelmetless(id)) {
                layer0 = "layer_0_helmetless";
            }
        }
        RenderType top = RenderType.entityCutoutNoCull(tex(id, layer0));
        RenderType bottom = RenderType.entityCutoutNoCull(tex(id, "layer_1"));
        if (thor(id)) {
            // the Thor models: every top bone (whatever its name: "head", "Head", "RightLeg", a loose "Cape"...)
            // goes on its body part, with the trousers layer under the main one on all of them (some suits paint
            // the belt or the hips on layer_1)
            for (GeoModel.Bone root : model.roots()) {
                drawBone(model, root, partFor(parent, root.name), pose, buffers, bottom, light, overlay);
            }
            for (GeoModel.Bone root : model.roots()) {
                drawBone(model, root, partFor(parent, root.name), pose, buffers, top, light, overlay);
            }
        } else {
            for (String b : new String[] {"right_leg", "left_leg"}) {
                draw(model, parent, b, pose, buffers, bottom, light, overlay);
            }
            for (String b : BONES) {
                draw(model, parent, b, pose, buffers, top, light, overlay);
            }
        }
        if (SupermanSuits.hasOverlay(id)) {
            draw(model, parent, "head", pose, buffers, RenderType.entityTranslucent(tex(id, "helmet_overlay")), light,
                    overlay);
        }
        boolean thorGlow = thor(id) && (com.flash.thor.ThorState.flag(player, com.flash.thor.ThorPowers.F_FURY)
                || com.flash.thor.ThorState.flag(player, com.flash.thor.ThorPowers.F_BLAST)
                || com.flash.thor.ThorState.flag(player, com.flash.thor.ThorPowers.F_ULTIMATE)
                || com.flash.thor.ThorState.flag(player, com.flash.thor.ThorPowers.F_FURY));
        if (thorGlow || (!thor(id) && (SupermanState.flag(player, SupermanPowers.F_HEAT)
                || SupermanState.flag(player, SupermanPowers.F_LASER)))) {
            draw(model, parent, "head", pose, buffers, RenderType.entityTranslucentEmissive(tex(id, "eyes")),
                    0xF000F0, overlay);
        }
        if ((thor(id) ? com.flash.thor.ThorSuits.hasCape(id) : SupermanSuits.hasCape(id)) && !player.isSpectator()) {
            SupermanCape.render(pose, buffers, light, player, this.getParentModel(), partialTick, tex(id, "cape"));
        }
    }

    /** The vanilla part a top bone follows, from its name (any case, with or without "_"). */
    private static ModelPart partFor(PlayerModel<?> m, String bone) {
        String n = bone.toLowerCase(java.util.Locale.ROOT).replace("_", "");
        return switch (n) {
            case "head" -> m.head;
            case "rightarm" -> m.rightArm;
            case "leftarm" -> m.leftArm;
            case "rightleg" -> m.rightLeg;
            case "leftleg" -> m.leftLeg;
            default -> m.body;
        };
    }

    /** The top bone standing for a body part (head, right_arm...), whatever its spelling in the model. */
    private static GeoModel.Bone rootFor(GeoModel model, String part) {
        GeoModel.Bone exact = model.bone(part);
        if (exact != null) {
            return exact;
        }
        String want = part.replace("_", "");
        for (GeoModel.Bone r : model.roots()) {
            if (r.name.toLowerCase(java.util.Locale.ROOT).replace("_", "").equals(want)) {
                return r;
            }
        }
        return null;
    }

    private static void draw(GeoModel model, PlayerModel<?> parent, String boneName, PoseStack pose,
                             MultiBufferSource buffers, RenderType type, int light, int overlay) {
        GeoModel.Bone bone = rootFor(model, boneName);
        if (bone == null) {
            return;
        }
        drawBone(model, bone, part(parent, boneName), pose, buffers, type, light, overlay);
    }

    private static void drawBone(GeoModel model, GeoModel.Bone bone, ModelPart part, PoseStack pose,
                                 MultiBufferSource buffers, RenderType type, int light, int overlay) {
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
        // the arm as the hand render sets it up (renderHand), without the skin under it
        parent.attackTime = 0.0F;
        parent.crouching = false;
        parent.swimAmount = 0.0F;
        parent.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ModelPart part = part(parent, b);
        part.xRot = 0.0F;
        draw(model, parent, b, pose, buffers, RenderType.entityCutoutNoCull(tex(id, "layer_0")), light,
                OverlayTexture.NO_OVERLAY);
    }
}

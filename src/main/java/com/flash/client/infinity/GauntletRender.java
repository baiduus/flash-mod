package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.infinity.InfPowers;
import com.flash.infinity.InfinityGauntlet;
import com.flash.infinity.InfinityStone;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * How the Infinity Gauntlet looks: worn (the offhand) on its arm with the worn model, held in the main hand with the
 * _mainhand model, in first person on the arm that holds it; its skin's model and texture (data/.../gauntlet_skins),
 * the glow, and each stone in its slot (gauntlet_slots/slot_N, the stone texture tinted with the stone's colour).
 * Arms swap for left-handed players.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class GauntletRender {

    private static final ResourceLocation STONE_TEX = new ResourceLocation(FlashMod.MODID,
            "textures/item/infinity_stone.png");
    private static final String DEFAULT_MODEL = "flash:geo/infinity/gauntlet";
    private static final String DEFAULT_TEX = "flash:textures/infinity_models/gauntlet.png";
    private static final Map<String, GeoModel> MODELS = new HashMap<>();
    private static final Map<String, Look> LOOKS = new HashMap<>();

    private GauntletRender() {
    }

    public static void reset() {
        MODELS.clear();
        LOOKS.clear();
    }

    private record Look(String model, ResourceLocation texture, ResourceLocation glow, boolean glint) {
    }

    private static boolean exists(ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }

    private static Look look(String skin) {
        return LOOKS.computeIfAbsent(skin, s -> {
            String model = DEFAULT_MODEL;
            ResourceLocation tex = new ResourceLocation(DEFAULT_TEX);
            ResourceLocation glow = null;
            boolean glint = false;
            if (!s.isEmpty()) {
                JsonObject o = InfinityGauntlet.skins().get(s);
                JsonObject r = o != null && o.has("render") && o.get("render").isJsonObject()
                        ? o.getAsJsonObject("render") : new JsonObject();
                String m = r.has("model") ? r.get("model").getAsString() : "flash:geo/infinity/" + s;
                if (exists(new ResourceLocation(m + ".geo.json"))) {
                    model = m;
                }
                ResourceLocation t = new ResourceLocation(r.has("texture") ? r.get("texture").getAsString()
                        : "flash:textures/infinity_models/" + s + ".png");
                if (exists(t)) {
                    tex = t;
                }
                JsonElement g = r.get("glow");
                if (g != null) {
                    ResourceLocation gt = new ResourceLocation(tex.getNamespace(), tex.getPath().replace(".png",
                            "_glow.png"));
                    if (g.isJsonObject()) {
                        JsonObject go = g.getAsJsonObject();
                        glint = InfPowers.bool(go, "enchantment_glint", false);
                        if (go.has("texture")) {
                            gt = new ResourceLocation(go.get("texture").getAsString());
                        }
                    }
                    if ((g.isJsonPrimitive() && g.getAsBoolean() || g.isJsonObject()) && exists(gt)) {
                        glow = gt;
                    }
                }
            }
            return new Look(model, tex, glow, glint);
        });
    }

    private static GeoModel model(String path) {
        return MODELS.computeIfAbsent(path, p -> {
            ResourceLocation rl = new ResourceLocation(p + ".geo.json");
            return exists(rl) ? GeoModel.load(rl, null) : null;
        });
    }

    // ------------------------------------------------------------------
    // Drawing on an arm
    // ------------------------------------------------------------------

    /**
     * The gauntlet's arm bone and its stones, on {@code part} (the arm it is on). {@code mainhand}: the held model,
     * built on the right arm; otherwise the worn one, on the left. When the real arm is the other one, mirrored.
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, int light, ItemStack g, ModelPart part,
                     HumanoidArm arm, boolean mainhand) {
        Look look = look(InfinityGauntlet.skin(g));
        String suffix = mainhand ? "_mainhand" : "";
        GeoModel m = model(look.model + suffix);
        if (m == null) {
            m = model(DEFAULT_MODEL + suffix);
            if (m == null) {
                return;
            }
        }
        String boneName = mainhand ? "right_arm" : "left_arm";
        HumanoidArm modelArm = mainhand ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        boolean mirror = arm != modelArm;
        m.reset();
        GeoModel.Bone bone = m.bone(boneName);
        if (bone == null) {
            return;
        }
        pose.pushPose();
        attach(pose, part, mirror, bone);
        m.renderBone(bone, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(look.texture)), light,
                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
        if (look.glow != null) {
            m.renderBone(bone, pose, buffers.getBuffer(RenderType.eyes(look.glow)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
        }
        if (look.glint) {
            m.renderBone(bone, pose, buffers.getBuffer(RenderType.armorEntityGlint()), light,
                    OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
        }
        pose.popPose();
        // the stones, each in its slot
        InfinityStone[] slots = InfinityGauntlet.slots(g);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                continue;
            }
            GeoModel sm = model("flash:geo/infinity/gauntlet_slots/slot_" + i + suffix);
            if (sm == null) {
                continue;
            }
            sm.reset();
            GeoModel.Bone stone = sm.bone("stone");
            GeoModel.Bone armBone = sm.bone(boneName);
            if (stone == null || armBone == null) {
                continue;
            }
            int c = slots[i].color;
            float r = ((c >> 16) & 0xFF) / 255.0F;
            float gr = ((c >> 8) & 0xFF) / 255.0F;
            float b = (c & 0xFF) / 255.0F;
            pose.pushPose();
            attach(pose, part, mirror, armBone);
            sm.renderBone(stone, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(STONE_TEX)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 0, r, gr, b, 1.0F, true);
            sm.renderBone(stone, pose, buffers.getBuffer(RenderType.armorEntityGlint()), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
            pose.popPose();
        }
    }

    private static void attach(PoseStack pose, ModelPart part, boolean mirror, GeoModel.Bone bone) {
        float x = part.x;
        float yr = part.yRot;
        float zr = part.zRot;
        if (mirror) {
            pose.scale(-1.0F, 1.0F, 1.0F);
            x = -x;
            yr = -yr;
            zr = -zr;
        }
        pose.translate(x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        if (part.xRot != 0.0F || yr != 0.0F || zr != 0.0F) {
            pose.mulPose(new Quaternionf().rotationZYX(zr, yr, part.xRot));
        }
        pose.translate(-bone.pivotX() / 16.0F, -bone.pivotY() / 16.0F, -bone.pivotZ() / 16.0F);
    }

    private static HumanoidArm armOf(AbstractClientPlayer p, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
    }

    // ------------------------------------------------------------------
    // Third person: the player layer
    // ------------------------------------------------------------------

    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb,
                           float limbAmount, float partial, float age, float yaw, float pitch) {
            if (p.isInvisible()) {
                return;
            }
            PlayerModel<AbstractClientPlayer> m = this.getParentModel();
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack s = p.getItemInHand(hand);
                if (!InfinityGauntlet.isGauntlet(s)) {
                    continue;
                }
                HumanoidArm arm = armOf(p, hand);
                ModelPart part = arm == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
                draw(pose, buffers, light, s, part, arm, hand == InteractionHand.MAIN_HAND);
            }
        }
    }

    // ------------------------------------------------------------------
    // First person: the arm with the gauntlet on it (the item itself is drawn nowhere in hand)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack s = event.getItemStack();
        Minecraft mc = Minecraft.getInstance();
        if (!InfinityGauntlet.isGauntlet(s) || mc.player == null || mc.player.isInvisible()
                || !(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);
        AbstractClientPlayer p = mc.player;
        HumanoidArm arm = armOf(p, event.getHand());
        PoseStack pose = event.getPoseStack();
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        boolean right = arm != HumanoidArm.LEFT;
        float f = right ? 1.0F : -1.0F;
        float f1 = Mth.sqrt(swing);
        float f2 = -0.3F * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * Mth.sin(f1 * ((float) Math.PI * 2.0F));
        float f4 = -0.4F * Mth.sin(swing * (float) Math.PI);
        pose.pushPose();
        pose.translate(f * (f2 + 0.64000005F), f3 - 0.6F + equip * -0.6F, f4 - 0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(swing * swing * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);
        PlayerModel<AbstractClientPlayer> m = renderer.getModel();
        if (right) {
            renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), p);
        } else {
            renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), p);
        }
        ModelPart part = right ? m.rightArm : m.leftArm;
        draw(pose, event.getMultiBufferSource(), event.getPackedLight(), s, part, arm,
                event.getHand() == InteractionHand.MAIN_HAND);
        pose.popPose();
    }
}

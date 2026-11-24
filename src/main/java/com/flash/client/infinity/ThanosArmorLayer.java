package com.flash.client.infinity;

import com.flash.client.geo.GeoModel;
import com.flash.infinity.InfinityExtras;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** Thanos' armours drawn by their models (the original's geckolib render layers), each bone on its body part. */
public class ThanosArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final Map<String, GeoModel> MODELS = new HashMap<>();
    private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private static final ResourceLocation EYE_GLOW = new ResourceLocation("flash",
            "textures/infinity_models/eye_of_agamotto_e.png");

    private static final ResourceLocation EYE_ANIMS = new ResourceLocation("flash",
            "animations/infinity/eye_of_agamotto.animation.json");
    private static Map<String, com.flash.client.geo.GeoAnimation> eyeAnims;
    /** Per entity: {open (1/0), game time the Eye last opened or closed}. */
    private static final Map<Integer, double[]> EYE_STATE = new HashMap<>();

    private static com.flash.client.geo.GeoAnimation eyeAnim(String name) {
        if (eyeAnims == null) {
            eyeAnims = com.flash.client.geo.GeoAnimation.load(EYE_ANIMS);
        }
        return eyeAnims.get(name);
    }

    /** The Eye's lids and blades: the original's open / close animations, held on their last frame. */
    private static void animateEye(GeoModel m, LivingEntity e, boolean open, float partial) {
        double now = e.level().getGameTime() + partial;
        double[] st = EYE_STATE.get(e.getId());
        if (st == null) {
            // first seen: already in place
            st = new double[] {open ? 1 : 0, now - 400.0D};
            if (EYE_STATE.size() > 256) {
                EYE_STATE.clear();
            }
            EYE_STATE.put(e.getId(), st);
        } else if ((st[0] == 1) != open) {
            st[0] = open ? 1 : 0;
            st[1] = now;
        }
        float seconds = (float) ((now - st[1]) / 20.0D);
        com.flash.client.geo.GeoAnimation a = eyeAnim(open ? "open" : "close");
        if (a != null) {
            a.apply(m, seconds);
        }
    }

    private static void hide(GeoModel m, String name, boolean hidden) {
        GeoModel.Bone b = m.bone(name);
        if (b != null) {
            b.visible = !hidden;
        }
    }

    public ThanosArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static GeoModel model(String id) {
        return MODELS.computeIfAbsent(id, k -> {
            try {
                return GeoModel.load(new ResourceLocation(k + ".geo.json"), null);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    private ModelPart part(String bone) {
        M m = this.getParentModel();
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
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limb, float limbAmount,
                       float partial, float age, float yaw, float pitch) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            ItemStack s = entity.getItemBySlot(slot);
            if (!(s.getItem() instanceof InfinityExtras.GeoArmor a)) {
                continue;
            }
            GeoModel m = model(a.model);
            if (m == null) {
                continue;
            }
            m.reset();
            GeoModel chain = null;
            boolean eye = s.is(com.flash.infinity.InfinityItems.EYE_OF_AGAMOTTO.get())
                    || s.is(com.flash.infinity.InfinityItems.EMPTY_EYE_OF_AGAMOTTO.get());
            boolean stone = false;
            if (eye) {
                // the Eye: its lids open while it is open, its stone only when there is one
                boolean open = com.flash.infinity.InfinityContainers.eyeOpen(s);
                stone = com.flash.infinity.InfinityContainers.stoneIn(s) != null;
                animateEye(m, entity, open, partial);
                hide(m, "time_stone", !stone);
                GeoModel.Bone ts = m.bone("time_stone");
                if (ts != null && stone) {
                    // the stone fills the whole inside of the Eye (1.7 x 2 pixels, between the frame and its back
                    // plate); the lids still close over it
                    ts.sx *= 2.1F;
                    ts.sy *= 2.5F;
                    ts.sz *= 0.375F;
                    ts.az -= 0.22F;
                }
                // the chain round the neck (the original's armour model layer)
                chain = model("flash:geo/infinity/eye_of_agamotto_chain");
                if (chain != null) {
                    chain.reset();
                }
            }
            RenderType main = RenderType.armorCutoutNoCull(new ResourceLocation(a.texture));
            // each pass takes its buffer right before drawing: asking for another render type ends the shared
            // buffer of the one before, so a kept consumer would draw into the wrong pass (the Eye with its stone
            // came out with only the glow)
            for (int pass = 0; pass < 3; pass++) {
                if (pass == 1 && !(eye && stone) || pass == 2 && !s.hasFoil()) {
                    continue;
                }
                for (String name : BONES) {
                    GeoModel.Bone bone = m.bone(name);
                    if (bone == null) {
                        continue;
                    }
                    ModelPart p = part(name);
                    pose.pushPose();
                    pose.translate(p.x / 16.0F, p.y / 16.0F, p.z / 16.0F);
                    if (p.xRot != 0.0F || p.yRot != 0.0F || p.zRot != 0.0F) {
                        pose.mulPose(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot));
                    }
                    pose.translate(-bone.pivotX() / 16.0F, -bone.pivotY() / 16.0F, -bone.pivotZ() / 16.0F);
                    if (pass == 0) {
                        m.renderBone(bone, pose, buffers.getBuffer(main), light, OverlayTexture.NO_OVERLAY, 0,
                                1.0F, 1.0F, 1.0F, 1.0F, true);
                        GeoModel.Bone cb = chain != null ? chain.bone(name) : null;
                        if (cb != null) {
                            chain.renderBone(cb, pose, buffers.getBuffer(main), light, OverlayTexture.NO_OVERLAY, 0,
                                    1.0F, 1.0F, 1.0F, 1.0F, true);
                        }
                    } else if (pass == 1) {
                        m.renderBone(bone, pose, buffers.getBuffer(RenderType.eyes(EYE_GLOW)), 0xF000F0,
                                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
                        GeoModel.Bone ts = "body".equals(name) ? m.bone("time_stone") : null;
                        if (ts != null) {
                            // the stone's shimmer (the original's stone glint layers)
                            m.renderBone(ts, pose, buffers.getBuffer(RenderType.armorEntityGlint()), 0xF000F0,
                                    OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
                        }
                    } else {
                        m.renderBone(bone, pose, buffers.getBuffer(RenderType.armorEntityGlint()), light,
                                OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
                    }
                    pose.popPose();
                }
            }
        }
    }
}

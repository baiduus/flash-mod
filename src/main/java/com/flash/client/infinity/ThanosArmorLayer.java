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
            if (!(s.getItem() instanceof InfinityExtras.ThanosArmor a)) {
                continue;
            }
            GeoModel m = model(a.model);
            if (m == null) {
                continue;
            }
            m.reset();
            var vc = buffers.getBuffer(RenderType.armorCutoutNoCull(new ResourceLocation(a.texture)));
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
                m.renderBone(bone, pose, vc, light, OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
                if (s.hasFoil()) {
                    m.renderBone(bone, pose, buffers.getBuffer(RenderType.armorEntityGlint()), light,
                            OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
                }
                pose.popPose();
            }
        }
    }
}

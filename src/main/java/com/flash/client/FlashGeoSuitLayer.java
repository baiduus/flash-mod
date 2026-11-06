package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.item.FlashSuitItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * The Flash suits drawn with the 3D model of the Flash asset pack ({@code flash_suit.geo.json}:
 * lightning ears, emblem, belt, muscles), piece by piece on the body parts of the wearer, so
 * the suit follows every pose (including the speedster run).
 *
 * <p>Textures per suit: {@code <suit>_layer_1} (head, body, arms, boots),
 * {@code <suit>_layer_2} (trousers), {@code <suit>_open_layer_1} (mask with the mouth open)
 * and {@code <suit>_eyes} (glowing lenses).</p>
 */
public class FlashGeoSuitLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/flash_suit.geo.json");
    private static GeoModel model;

    public FlashGeoSuitLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void reset() {
        model = null;
    }

    private static ResourceLocation texture(String suit, String layer) {
        return new ResourceLocation(FlashMod.MODID, "textures/models/armor/" + suit + "_" + layer + ".png");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        M parent = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        model.reset();
        String head = suit(entity.getItemBySlot(EquipmentSlot.HEAD));
        String chest = suit(entity.getItemBySlot(EquipmentSlot.CHEST));
        String legs = suit(entity.getItemBySlot(EquipmentSlot.LEGS));
        String feet = suit(entity.getItemBySlot(EquipmentSlot.FEET));
        if (legs != null) {
            RenderType type = RenderType.entityCutoutNoCull(texture(legs, "layer_2"));
            draw(pose, buffers, type, light, overlay, "body", parent.body);
            draw(pose, buffers, type, light, overlay, "right_leg", parent.leftLeg);
            draw(pose, buffers, type, light, overlay, "left_leg", parent.rightLeg);
        }
        if (feet != null) {
            RenderType type = RenderType.entityCutoutNoCull(texture(feet, "layer_1"));
            draw(pose, buffers, type, light, overlay, "right_leg", parent.leftLeg);
            draw(pose, buffers, type, light, overlay, "left_leg", parent.rightLeg);
        }
        if (chest != null) {
            RenderType type = RenderType.entityCutoutNoCull(texture(chest, "layer_1"));
            draw(pose, buffers, type, light, overlay, "body", parent.body);
            // the pack's "right_arm" sits at +x, where the model's left arm is
            draw(pose, buffers, type, light, overlay, "right_arm", parent.leftArm);
            draw(pose, buffers, type, light, overlay, "left_arm", parent.rightArm);
        }
        if (head != null) {
            boolean open = com.flash.config.FlashClientConfig.MASK_OPEN_MOUTH.get();
            draw(pose, buffers, RenderType.entityCutoutNoCull(texture(head, open ? "open_layer_1" : "layer_1")),
                    light, overlay, "head", parent.head);
            if (!open && !"zoom_suit".equals(head)) {
                draw(pose, buffers, RenderType.entityTranslucentEmissive(texture(head, "eyes")), 0xF000F0, overlay,
                        "head", parent.head);
            }
        }
    }

    /** Suit name of a worn Flash suit piece ("flash_suit", "negative_suit", ...), or null. */
    private static String suit(ItemStack stack) {
        if (!(stack.getItem() instanceof FlashSuitItem item)) {
            return null;
        }
        String name = ((ArmorItem) item).getMaterial().getName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    /** One bone of the suit placed on a body part (its pivot lands on the part's pivot). */
    private static void draw(PoseStack pose, MultiBufferSource buffers, RenderType type, int light, int overlay,
                             String boneName, ModelPart part) {
        GeoModel.Bone bone = model.bone(boneName);
        if (bone == null) {
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
}

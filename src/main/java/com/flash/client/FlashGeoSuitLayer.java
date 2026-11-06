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
        if (entity.isInvisible() || entity instanceof net.minecraft.client.player.AbstractClientPlayer p
                && FlashSpeedsterPose.isPosing(p)) {
            return; // the speedster run draws the suit itself (drawAnimated)
        }
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        model.reset();
        M parent = this.getParentModel();
        drawSuit(entity, pose, buffers, light, (p, bone) -> {
            // the pack's "right_arm" / "right_leg" sit at +x, where the model's left limbs are
            ModelPart part = switch (bone) {
                case "head" -> parent.head;
                case "body" -> parent.body;
                case "right_arm" -> parent.leftArm;
                case "left_arm" -> parent.rightArm;
                case "right_leg" -> parent.leftLeg;
                default -> parent.rightLeg;
            };
            p.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
            if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
                p.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
            }
            GeoModel.Bone b = model.bone(bone);
            p.translate(-b.pivotX() / 16.0F, -b.pivotY() / 16.0F, -b.pivotZ() / 16.0F);
        });
    }

    /**
     * Speedster run: the suit bones carry the animation themselves (the root transform is
     * already on {@code pose}); {@code animate} fills the bones of the suit model.
     */
    public static void drawAnimated(LivingEntity entity, PoseStack pose, MultiBufferSource buffers, int light,
                                    java.util.function.Consumer<GeoModel> animate) {
        if (model == null) {
            model = GeoModel.load(GEO, null);
        }
        model.reset();
        animate.accept(model);
        drawSuit(entity, pose, buffers, light, (p, bone) -> {
        });
    }

    /** Where a suit bone goes before it is drawn. */
    private interface Placement {
        void place(PoseStack pose, String bone);
    }

    private static void drawSuit(LivingEntity entity, PoseStack pose, MultiBufferSource buffers, int light,
                                 Placement placement) {
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        // slightly bigger than the body under it, so the skin never shows through
        for (String b : new String[] {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            GeoModel.Bone bone = model.bone(b);
            if (bone != null) {
                bone.sx *= 1.04F;
                bone.sy *= 1.04F;
                bone.sz *= 1.04F;
            }
        }
        String head = suit(entity.getItemBySlot(EquipmentSlot.HEAD));
        String chest = suit(entity.getItemBySlot(EquipmentSlot.CHEST));
        String legs = suit(entity.getItemBySlot(EquipmentSlot.LEGS));
        String feet = suit(entity.getItemBySlot(EquipmentSlot.FEET));
        if (legs != null) {
            RenderType type = RenderType.entityCutoutNoCull(texture(legs, "layer_2"));
            draw(pose, buffers, type, light, overlay, "body", placement);
            draw(pose, buffers, type, light, overlay, "right_leg", placement);
            draw(pose, buffers, type, light, overlay, "left_leg", placement);
        }
        if (feet != null) {
            RenderType type = RenderType.entityCutoutNoCull(texture(feet, "layer_1"));
            draw(pose, buffers, type, light, overlay, "right_leg", placement);
            draw(pose, buffers, type, light, overlay, "left_leg", placement);
        }
        if (chest != null) {
            RenderType type = RenderType.entityCutoutNoCull(texture(chest, "layer_1"));
            draw(pose, buffers, type, light, overlay, "body", placement);
            draw(pose, buffers, type, light, overlay, "right_arm", placement);
            draw(pose, buffers, type, light, overlay, "left_arm", placement);
        }
        if (head != null) {
            boolean open = com.flash.config.FlashClientConfig.MASK_OPEN_MOUTH.get();
            draw(pose, buffers, RenderType.entityCutoutNoCull(texture(head, open ? "open_layer_1" : "layer_1")),
                    light, overlay, "head", placement);
            if (!open && !"zoom_suit".equals(head)) {
                draw(pose, buffers, RenderType.entityTranslucentEmissive(texture(head, "eyes")), 0xF000F0, overlay,
                        "head", placement);
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

    private static void draw(PoseStack pose, MultiBufferSource buffers, RenderType type, int light, int overlay,
                             String boneName, Placement placement) {
        GeoModel.Bone bone = model.bone(boneName);
        if (bone == null) {
            return;
        }
        pose.pushPose();
        placement.place(pose, boneName);
        model.renderBone(bone, pose, buffers.getBuffer(type), light, overlay, 0, 1.0F, 1.0F, 1.0F, 1.0F, true);
        pose.popPose();
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Wonder Woman's sword and shield in hand (Legends: Superheroes' models, drawn with the mod's geo loader; the item
 * models only hold the hand transforms, as vanilla's shield and trident).
 */
public class WonderItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation SWORD_GEO = new ResourceLocation(FlashMod.MODID,
            "geo/wonder/amazon_sword.geo.json");
    private static final ResourceLocation SWORD_TEX = new ResourceLocation(FlashMod.MODID,
            "textures/wonder/amazon_sword.png");
    /** Where the grip is (bedrock y, pixels): put in the hand. */
    private static final float GRIP_Y = 8.0F;

    private static WonderItemRenderer instance;
    private static GeoModel sword;

    public WonderItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static WonderItemRenderer get() {
        if (instance == null) {
            instance = new WonderItemRenderer();
        }
        return instance;
    }

    public static void resetModels() {
        sword = null;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        boolean hand = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (stack.is(ModItems.AMAZON_SHIELD.get())) {
            // as vanilla's shield: centred on the item's corner, its face toward the front
            pose.pushPose();
            pose.scale(1.0F, -1.0F, -1.0F);
            pose.translate(0.0D, 0.0D, -1.5D / 16.0D);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            WonderLayer.drawShield(pose, buffers, light, 0.4F, true);
            pose.popPose();
            return;
        }
        if (sword == null) {
            sword = GeoModel.load(SWORD_GEO, null);
        }
        if (sword == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        float s = 0.35F;
        // the model's space has y down: the blade up
        pose.scale(s, -s, s);
        float y = hand ? (24.0F - GRIP_Y) : sword.centerY();
        pose.translate(-sword.centerX() / 16.0F, -y / 16.0F, -sword.centerZ() / 16.0F);
        sword.reset();
        sword.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SWORD_TEX)), light, overlay, 0, 1.0F,
                1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Mjolnir in hand: the item model of the pack, whirling around the grip while the right click is held for the
 * throw (held still and up while crouching to charge it).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public class MjolnirItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation MODEL = new ResourceLocation(FlashMod.MODID, "item/mjolnir_model");
    private static MjolnirItemRenderer instance;
    /** Who is being drawn right now (the item renderer is not told). */
    private static LivingEntity holder;

    public MjolnirItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static MjolnirItemRenderer get() {
        if (instance == null) {
            instance = new MjolnirItemRenderer();
        }
        return instance;
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        holder = event.getEntity();
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        holder = null;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        holder = Minecraft.getInstance().player;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(MODEL);
        pose.pushPose();
        LivingEntity who = holder;
        boolean hand = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (hand && who != null && who.isUsingItem() && who.getUseItem().getItem() == stack.getItem()
                && !who.isShiftKeyDown()) {
            // whirling by the strap: around the grip, faster and faster
            int used = who.getTicksUsingItem();
            float speed = Math.min(1.0F, used / 12.0F);
            float angle = (who.tickCount + mc.getFrameTime()) * 50.0F * speed;
            pose.translate(0.5F, 0.15F, 0.5F);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            pose.translate(-0.5F, -0.15F, -0.5F);
        }
        mc.getItemRenderer().renderModelLists(model, stack, light, overlay, pose,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
        pose.popPose();
    }
}

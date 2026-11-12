package com.flash.client;

import com.flash.FlashMod;
import com.flash.item.MjolnirItem;
import com.flash.thor.ThorPowers;
import com.flash.thor.ThorState;
import com.flash.thor.ThorSuits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thor's weapon in hand: the item model of the pack that goes with the suit (classic, MCU or Cosmic King Mjolnir,
 * Stormbreaker, lit when charged), whirling around the grip while the right click is held for the throw (held still
 * while crouching to charge it), during the Hammer Spin and the charge of the ultimates.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public class MjolnirItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation[] MODELS = {
            new ResourceLocation(FlashMod.MODID, "item/thor/mjolnir"),
            new ResourceLocation(FlashMod.MODID, "item/thor/mjolnir_mcu"),
            new ResourceLocation(FlashMod.MODID, "item/thor/mjolnir_cosmic"),
            new ResourceLocation(FlashMod.MODID, "item/thor/stormbreaker")};
    public static final ResourceLocation STORMBREAKER_LIT = new ResourceLocation(FlashMod.MODID,
            "item/thor/stormbreaker_lit");
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
        int weapon = MjolnirItem.weapon(stack);
        LivingEntity who = holder;
        boolean hand = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        Player thor = hand && who instanceof Player p && ThorState.isThor(p) ? p : null;
        boolean lit = weapon == ThorSuits.STORMBREAKER && thor != null
                && (ThorState.flag(thor, ThorPowers.F_CHARGING) || ThorState.flag(thor, ThorPowers.F_FURY)
                || ThorState.flag(thor, ThorPowers.F_FURY) || ThorState.flag(thor, ThorPowers.F_ULTIMATE)
                || (thor == mc.player && ThorState.charge() >= 50.0F));
        BakedModel model = mc.getModelManager().getModel(lit ? STORMBREAKER_LIT
                : MODELS[Math.max(0, Math.min(MODELS.length - 1, weapon))]);
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        model = model.applyTransform(context, pose, left);
        pose.translate(-0.5F, -0.5F, -0.5F);
        float speed = 0.0F;
        if (hand && who != null && who.isUsingItem() && who.getUseItem().getItem() == stack.getItem()
                && !who.isShiftKeyDown()) {
            speed = Math.min(1.0F, who.getTicksUsingItem() / 12.0F);
        } else if (thor != null && (ThorState.flag(thor, ThorPowers.F_SPIN) || ThorState.flag(thor, ThorPowers.F_TWIRL))) {
            speed = 1.0F;
        }
        if (speed > 0.0F && who != null) {
            // whirling by the strap: around the grip, faster and faster
            float angle = (who.tickCount + mc.getFrameTime()) * 50.0F * speed;
            // around the grip, in the plane along the body (the arm stays down at the side)
            pose.translate(0.5F, 0.45F, 0.5F);
            pose.mulPose(Axis.ZP.rotationDegrees(-angle));
            pose.translate(-0.5F, -0.45F, -0.5F);
        }
        mc.getItemRenderer().renderModelLists(model, stack, light, overlay, pose,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
        pose.popPose();
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.hulk.HulkPowers;
import com.flash.hulk.HulkState;
import com.flash.network.FlashNetwork;
import com.flash.network.HulkTransformPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client side of the Hulk: bigger body, green skin layer, green first-person arm and the H key. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class HulkClient {

    private static HulkModel model;
    private static final boolean[] KEY_DOWN = new boolean[com.flash.hulk.HulkAbility.values().length];
    private static int scaledDepth;

    private HulkClient() {
    }

    static HulkModel model() {
        if (model == null) {
            model = new HulkModel(Minecraft.getInstance().getEntityModels().bakeLayer(HulkModel.LAYER));
        }
        return model;
    }

    static void resetModel() {
        model = null;
    }

    /** The Hulk is drawn 1.5 times bigger (the hitbox grows the same, see HulkEvents). */
    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (HulkState.isTransformed(event.getEntity())) {
            event.getPoseStack().pushPose();
            float scale = HulkState.scale(event.getEntity());
            event.getPoseStack().scale(scale, scale, scale);
            scaledDepth++;
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (scaledDepth > 0 && HulkState.isTransformed(event.getEntity())) {
            event.getPoseStack().popPose();
            scaledDepth--;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (FlashKeys.HULK_TRANSFORM.consumeClick()) {
            if (HulkState.has(mc.player)) {
                FlashNetwork.CHANNEL.sendToServer(new HulkTransformPacket());
            }
        }
        // Hulk abilities share the keys of the Flash abilities
        com.flash.hulk.HulkAbility[] abilities = com.flash.hulk.HulkAbility.values();
        boolean active = mc.screen == null && HulkState.isTransformed(mc.player);
        for (int i = 0; i < abilities.length; i++) {
            net.minecraft.client.KeyMapping key = FlashAbilityKeys.mapping(abilities[i].keySlot());
            boolean down = active && key != null && key.isDown();
            if (down && !KEY_DOWN[i]) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.HulkAbilityPacket(i));
            }
            KEY_DOWN[i] = down;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HulkState.resetClient();
        scaledDepth = 0;
    }

    /** First person: the Hulk's green arm over the vanilla one (the pose is already the arm's). */
    static void drawArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                        PlayerModel<AbstractClientPlayer> parent, HumanoidArm arm) {
        HulkModel hulk = model();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(HulkModel.TEXTURE));
        if (arm == HumanoidArm.RIGHT) {
            hulk.rightArm.copyFrom(parent.rightArm);
            hulk.rightArm.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        } else {
            hulk.leftArm.copyFrom(parent.leftArm);
            hulk.leftArm.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** Third person: the Hulk's body over the skin. */
    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!HulkState.isTransformed(player) || player.isInvisible()) {
                return;
            }
            HulkModel hulk = model();
            this.getParentModel().copyPropertiesTo(hulk);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(HulkModel.TEXTURE));
            hulk.renderToBuffer(pose, consumer, light, LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}

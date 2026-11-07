package com.flash.client;

import com.flash.FlashMod;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Solar absorption high up (the pack's solar26 look): a flowing golden shell around the body, sunlight sparks
 * streaming in from the sun and a warm golden vignette on the screen of the basking player.
 */
public class SupermanAuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation FLOW = new ResourceLocation(FlashMod.MODID,
            "textures/fx/superman/breath_flow.png");

    public SupermanAuraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Flying, high up and in the sun. */
    static boolean basking(Player player) {
        if (!SupermanState.isSuperman(player) || !(player instanceof AbstractClientPlayer cp)) {
            return false;
        }
        boolean flying = player == Minecraft.getInstance().player ? player.getAbilities().flying
                : SupermanState.flag(player, SupermanPowers.F_FLYING);
        return flying && SupermanPose.basking(cp);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (!basking(player) || player.isInvisible()) {
            return;
        }
        float t = ageInTicks + partialTick;
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.1F);
        pose.pushPose();
        pose.scale(1.06F, 1.04F, 1.06F);
        pose.translate(0.0F, -0.03F, 0.0F);
        this.getParentModel().renderToBuffer(pose,
                buffers.getBuffer(RenderType.energySwirl(FLOW, (t * 0.012F) % 1.0F, (t * 0.02F) % 1.0F)), 0xF000F0,
                OverlayTexture.NO_OVERLAY, 0.55F * pulse, 0.42F * pulse, 0.13F * pulse, 1.0F);
        pose.popPose();
    }

    /** Client side helpers: sparks streaming in and the golden vignette. */
    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
    public static final class Fx {
        private Fx() {
        }

        private static float vignette;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
                return;
            }
            boolean local = mc.player != null && basking(mc.player);
            vignette += ((local ? 1.0F : 0.0F) - vignette) * 0.08F;
            // the sun's direction in the sky
            float angle = mc.level.getSunAngle(1.0F);
            Vec3 sun = new Vec3(-Math.sin(angle), Math.cos(angle), 0.0D).normalize();
            for (Player p : mc.level.players()) {
                if (!basking(p)) {
                    continue;
                }
                Vec3 chest = p.position().add(0.0D, p.getBbHeight() * 0.6D, 0.0D);
                for (int i = 0; i < 3; i++) {
                    Vec3 from = chest.add(sun.scale(4.0D + mc.level.random.nextDouble() * 4.0D))
                            .add((mc.level.random.nextDouble() - 0.5D) * 2.0D, (mc.level.random.nextDouble() - 0.5D) * 2.0D,
                                    (mc.level.random.nextDouble() - 0.5D) * 2.0D);
                    Vec3 v = chest.subtract(from).scale(0.09D);
                    mc.level.addParticle(com.flash.registry.ModParticles.SM_SPARKLE.get(), from.x, from.y, from.z,
                            v.x, v.y, v.z);
                }
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            if (vignette < 0.01F) {
                return;
            }
            GuiGraphics g = event.getGuiGraphics();
            int w = g.guiWidth();
            int h = g.guiHeight();
            int a = (int) (0.42F * vignette * 255.0F);
            int gold = (a << 24) | 0xFFB43A;
            int clear = 0x00FFB43A;
            int band = Math.max(24, h / 5);
            g.fillGradient(0, 0, w, band, gold, clear);
            g.fillGradient(0, h - band, w, h, clear, gold);
            // the sides: thin vertical strips fading inward
            for (int i = 0; i < band; i++) {
                int ai = (int) (a * (1.0F - i / (float) band));
                int c = (ai << 24) | 0xFFB43A;
                g.fill(i, 0, i + 1, h, c);
                g.fill(w - i - 1, 0, w - i, h, c);
            }
        }
    }
}

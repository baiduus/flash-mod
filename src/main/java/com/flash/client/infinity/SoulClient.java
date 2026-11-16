package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.client.VfxDraw;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Soul, Time and Power Stones on the client: every creature glowing round the Soul Stone's holder, the souls bar,
 * the Time Stone's green screen, the Power Stone's level bar and its radiation over its holder.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SoulClient {

    private static final ResourceLocation TIME = new ResourceLocation("flash", "textures/gui/time.png");

    private SoulClient() {
    }

    public static boolean glows(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && e != mc.player && e instanceof LivingEntity
                && InfinityClient.anyOn("palladium:entity_glow") && e.distanceToSqr(mc.player) < 100.0D * 100.0D;
    }

    private static boolean has(String power) {
        return InfinityClient.powers().contains(power);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !event.getOverlay().id().getPath().equals("hotbar")) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        // the Time Stone's green screen
        if (InfinityClient.anyOn("palladium:gui_overlay")) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1.0F, 1.0F, 1.0F, 0.5F);
            g.blit(TIME, 0, 0, w, h, 0.0F, 0.0F, 200, 200, 200, 200);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        int y = InfinityHud.top(h) - 16;
        // the energy bars: souls (Soul Stone), the level (Power Stone), the flow of time (Time Stone)
        if (has("soul_stone")) {
            bar(g, mc, 6, y, InfinityClient.VIEW.score("SoulStorage"), 50, 0xFFFF8B00,
                    Component.translatable("gui.flash.infinity.souls"));
            y -= 14;
        }
        if (has("power_stone")) {
            bar(g, mc, 6, y, InfinityClient.VIEW.score("DamageCycle"), 11, 0xFF9728FF,
                    Component.translatable("gui.flash.infinity.power_level"));
            y -= 14;
        }
        if (has("time_stone") && InfinityClient.VIEW.score("TimeFlow") > 0) {
            bar(g, mc, 6, y, InfinityClient.VIEW.score("TimeFlow"), 11, 0xFF13CF55,
                    Component.translatable("gui.flash.infinity.time_flow"));
        }
    }

    private static void bar(GuiGraphics g, Minecraft mc, int x, int y, int v, int max, int color, Component label) {
        g.drawString(mc.font, label.getString() + " " + v + "/" + max, x, y, color, true);
        g.fill(x, y + 9, x + 100, y + 12, 0xFF202028);
        g.fill(x, y + 9, x + 100 * Math.max(0, Math.min(max, v)) / max, y + 12, color);
    }

    /** The Power Stone's radiation over its unprotected holder (the original's glowing cracks). */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        float pt = event.getPartialTick();
        BufferBuilder buf = null;
        try {
            for (Player p : mc.level.players()) {
                boolean rad = p == mc.player ? InfinityClient.anyOn("flash:power_radiation")
                        || InfinityClient.anyOn("flash:power_cyclone")
                        : InfinityClient.visible(p, "flash:power_radiation")
                        || InfinityClient.visible(p, "flash:power_cyclone");
                if (!rad || p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                    continue;
                }
                if (buf == null) {
                    ps.pushPose();
                    ps.translate(-cam.x, -cam.y, -cam.z);
                    buf = VfxDraw.begin();
                }
                Matrix4f m = ps.last().pose();
                float t = p.tickCount + pt;
                Vec3 c = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                VfxDraw.sphere(buf, m, c, 0.75D, cam, 0.76F, 0.16F, 0.82F, 0.5F, t, 1.5D);
                for (int i = 0; i < 5; i++) {
                    Vec3 a = c.add(Math.sin(t * 0.3D + i) * 0.3D, -0.8D + i * 0.35D, Math.cos(t * 0.2D + i) * 0.3D);
                    VfxDraw.path(buf, m, VfxDraw.jagged(a, a.add(0.3D, 0.3D, -0.2D), (long) t + i, 0.1D, 2), 0.03F,
                            0.95F, 0.4F, 1.0F, 0.9F);
                }
            }
        } finally {
            if (buf != null) {
                VfxDraw.end(buf);
                ps.popPose();
            }
        }
    }
}

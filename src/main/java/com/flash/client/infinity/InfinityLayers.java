package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.client.VfxDraw;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** What the stones draw on players (their render layers): the Space Stone's shield bubble. */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class InfinityLayers {

    private InfinityLayers() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        BufferBuilder buf = null;
        try {
            for (Player p : mc.level.players()) {
                boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
                if (firstPerson || p.isInvisible()) {
                    continue;
                }
                if (InfinityClient.visibleKey(p, "space_stone/render_layer_space_stone_shield")) {
                    if (buf == null) {
                        ps.pushPose();
                        ps.translate(-cam.x, -cam.y, -cam.z);
                        buf = VfxDraw.begin();
                    }
                    Matrix4f m = ps.last().pose();
                    float t = p.tickCount + pt;
                    Vec3 c = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                    VfxDraw.sphere(buf, m, c, 1.35D, cam, 0.2F, 0.45F, 1.0F, 0.7F, t, 2.2D);
                    VfxDraw.hexCircle(buf, m, p.getPosition(pt).add(0.0D, 0.05D, 0.0D), 1.35D, t, 0.3F, 0.6F, 1.0F,
                            0.4F);
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

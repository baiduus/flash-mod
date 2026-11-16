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
                if (p.isInvisible() && p != mc.player) {
                    continue;
                }
                boolean me = p == mc.player;
                if (InfinityClient.visibleKey(p, "space_stone/force_field")
                        || InfinityClient.visibleKey(p, "space_stone/render_layer_space_stone_shield")
                        || me && (InfinityClient.on("space_stone/force_field")
                        || InfinityClient.toggled("space_stone/force_field"))) {
                    if (buf == null) {
                        ps.pushPose();
                        ps.translate(-cam.x, -cam.y, -cam.z);
                        buf = VfxDraw.begin();
                    }
                    Matrix4f m = ps.last().pose();
                    float t = p.tickCount + pt;
                    Vec3 c = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
                    // a blue bubble (as Doom's field, without its runes), seen from inside too
                    double r = 1.9D;
                    float pulse = 0.5F + 0.15F * (float) Math.sin(t * 0.25F);
                    VfxDraw.sphere(buf, m, c, r, cam, 0.25F, 0.6F, 1.0F, 0.55F, t, 2.6D);
                    VfxDraw.ring(buf, m, c, new Vec3(0.0D, 1.0D, 0.0D), r + 0.04D, 0.05D, t * 0.05F, 0.45F, 0.8F,
                            1.0F, pulse);
                    VfxDraw.ring(buf, m, c, new Vec3(1.0D, 0.0D, 0.0D), r + 0.02D, 0.03D, t * -0.04F, 0.45F, 0.8F,
                            1.0F, pulse * 0.6F);
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

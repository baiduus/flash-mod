package com.flash.client.godwand;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** God Wand's impact frames: four full-screen frames at the Supernova's impact, then a white fade. */
public class CustomImpactFrameOverlay {

    public static final ResourceLocation[] FRAMES = {
            new ResourceLocation("flash", "textures/godwand/vfx/impact_frame_0.png"),
            new ResourceLocation("flash", "textures/godwand/vfx/impact_frame_1.png"),
            new ResourceLocation("flash", "textures/godwand/vfx/impact_frame_2.png"),
            new ResourceLocation("flash", "textures/godwand/vfx/impact_frame_3.png")};
    private static int currentTick = 0;
    private static boolean active = false;

    public static void trigger() {
        currentTick = 0;
        active = true;
    }

    public static void reset() {
        active = false;
        currentTick = 0;
    }

    public static void onClientTick() {
        if (active) {
            currentTick++;
            if (currentTick >= 7) {
                active = false;
                currentTick = 0;
            }
        }
    }

    public static void render(GuiGraphics context) {
        if (!active || !GodWandConfig.CLIENT.impactFrames) {
            return;
        }
        int w = context.guiWidth();
        int h = context.guiHeight();
        if (currentTick < 4) {
            ResourceLocation texture = FRAMES[Math.min(FRAMES.length - 1, currentTick * FRAMES.length / 4)];
            context.blit(texture, 0, 0, 0.0F, 0.0F, w, h, w, h);
        } else {
            float fade = Mth.clamp((currentTick - 4) / 3.0F, 0.0F, 1.0F);
            float whiteAlpha = 0.28F * (1.0F - fade) * (1.0F - fade);
            if (whiteAlpha > 0.002F) {
                context.fill(0, 0, w, h, Mth.clamp((int) (whiteAlpha * 255.0F), 0, 255) << 24 | 0xFFFFFF);
            }
        }
    }
}

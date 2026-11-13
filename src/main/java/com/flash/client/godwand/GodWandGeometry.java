package com.flash.client.godwand;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * God Wand's procedural geometry on 1.20.1: the same small state machine (blend, additive, depth, cull, colour or
 * texture), each mesh drawn at once with the matching core shader. Buffers come from a small pool, so several can
 * be filled at the same time as God Wand does.
 */
public final class GodWandGeometry {

    private static final ArrayDeque<BufferBuilder> FREE = new ArrayDeque<>();
    private static final List<BufferBuilder> USED = new ArrayList<>();
    private static boolean blend;
    private static boolean additive;
    private static boolean depth = true;
    private static boolean writeDepth = true;
    private static boolean cull = true;
    private static boolean texture;
    private static ResourceLocation textureId;
    private static float[] color = {1.0F, 1.0F, 1.0F, 1.0F};

    private GodWandGeometry() {
    }

    public static BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        BufferBuilder b = FREE.isEmpty() ? new BufferBuilder(1 << 16) : FREE.pop();
        if (b.building()) {
            b.end();
        }
        b.begin(topology.mode, format);
        USED.add(b);
        return b;
    }

    public static void enableBlend() {
        blend = true;
    }

    public static void disableBlend() {
        blend = false;
    }

    public static void defaultBlendFunc() {
        additive = false;
    }

    public static void additiveBlend() {
        additive = true;
    }

    public static void enableDepthTest() {
        depth = true;
    }

    public static void disableDepthTest() {
        depth = false;
    }

    public static void depthMask(boolean value) {
        writeDepth = value;
    }

    public static void enableCull() {
        cull = true;
    }

    public static void disableCull() {
        cull = false;
    }

    public static void colored() {
        texture = false;
    }

    public static void textured() {
        texture = true;
    }

    public static void setShaderTexture(int unit, ResourceLocation id) {
        textureId = id;
    }

    public static void setShaderColor(float r, float g, float b, float a) {
        color = new float[] {r, g, b, a};
    }

    /** Draws a finished mesh now, with the state set above. */
    public static void draw(BufferBuilder.RenderedBuffer mesh) {
        if (mesh == null) {
            return;
        }
        if (mesh.isEmpty()) {
            mesh.release();
            return;
        }
        if (blend) {
            RenderSystem.enableBlend();
            if (additive) {
                RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                        com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
        } else {
            RenderSystem.disableBlend();
        }
        if (depth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(writeDepth);
        if (cull) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        if (texture && textureId != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, textureId);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        recycle();
    }

    private static void recycle() {
        for (int i = USED.size() - 1; i >= 0; i--) {
            BufferBuilder b = USED.get(i);
            if (!b.building()) {
                USED.remove(i);
                if (FREE.size() < 8) {
                    FREE.push(b);
                }
            }
        }
    }

    public static void flush() {
        recycle();
    }

    public static void reset() {
        for (BufferBuilder b : USED) {
            if (b.building()) {
                b.end().release();
            }
        }
        USED.clear();
        blend = false;
        additive = false;
        depth = true;
        writeDepth = true;
        cull = true;
        texture = false;
        color = new float[] {1.0F, 1.0F, 1.0F, 1.0F};
    }
}

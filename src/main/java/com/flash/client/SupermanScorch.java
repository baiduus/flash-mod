package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Burnt trails of the heat vision (port of the pack's scorch40): the beam paints strokes on the block faces it
 * sweeps; they glow white-orange, cool through red to black char in three seconds, then fade after a minute.
 * Holding the beam on one spot leaves a round burn. The texture is the pack's procedural art (char band, hot groove,
 * char disc, hot disc), generated at start-up.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanScorch {

    // stroke widths (half widths, blocks): normal / laser
    private static final float[] CHAR_HW = {0.18F, 0.4F};
    private static final float[] RIM_HW = {0.165F, 0.36F};
    private static final float[] CORE_HW = {0.075F, 0.17F};
    private static final float[] PERIOD = {1.5F, 3.0F};
    private static final float[] SPOT_MIN = {0.08F, 0.18F};
    private static final float[] SPOT_MAX = {0.3F, 0.5F};
    private static final double[] DWELL_R = {0.26D, 0.4D};
    private static final double STEP = 0.22D;
    private static final double MIN_STEP = 0.06D;
    private static final double JUMP = 1.0D;
    private static final double HOT = 60.0D;
    private static final double LIFE = 1200.0D;
    private static final double FADE = 200.0D;
    private static final int CAP = 4096;
    private static final float CHAR_R = 0.11F;
    private static final float CHAR_G = 0.095F;
    private static final float CHAR_B = 0.085F;

    // cooling ramp (k = age / HOT)
    private static final float[] KS = {0.0F, 0.15F, 0.33F, 0.5F, 0.67F, 0.85F, 1.0F};
    private static final float[] CORE_R = {1.0F, 1.0F, 1.0F, 0.92F, 0.66F, 0.36F, 0.0F};
    private static final float[] CORE_G = {0.86F, 0.68F, 0.44F, 0.22F, 0.07F, 0.025F, 0.0F};
    private static final float[] CORE_B = {0.48F, 0.24F, 0.09F, 0.04F, 0.02F, 0.01F, 0.0F};
    private static final float[] RIM_R = {1.0F, 0.95F, 0.85F, 0.66F, 0.44F, 0.22F, 0.0F};
    private static final float[] RIM_G = {0.3F, 0.22F, 0.14F, 0.07F, 0.03F, 0.01F, 0.0F};
    private static final float[] RIM_B = {0.06F, 0.04F, 0.03F, 0.015F, 0.01F, 0.0F, 0.0F};
    private static final float[] CORE_A = {0.95F, 0.95F, 0.93F, 0.85F, 0.7F, 0.4F, 0.0F};
    private static final float[] RIM_A = {0.8F, 0.8F, 0.75F, 0.62F, 0.45F, 0.22F, 0.0F};

    // texture atlas (256 x 512): rows in pixels
    private static final int TW = 256;
    private static final int TH = 512;

    private SupermanScorch() {
    }

    /** One stroke segment (or a round spot when {@code spot}) lying on a block face. */
    private static final class Mark {
        final Vec3 a;
        final Vec3 b;
        final Vec3 normal;
        final float u0;
        final float u1;
        final int kind;
        final boolean spot;
        float radius;
        final long start;
        final float rot;

        Mark(Vec3 a, Vec3 b, Vec3 normal, float u0, float u1, int kind, boolean spot, float radius, long start,
             float rot) {
            this.a = a;
            this.b = b;
            this.normal = normal;
            this.u0 = u0;
            this.u1 = u1;
            this.kind = kind;
            this.spot = spot;
            this.radius = radius;
            this.start = start;
            this.rot = rot;
        }
    }

    private static final class Pen {
        Vec3 last;
        Vec3 normal;
        long lastTick;
        float u;
        Mark spot;
        Vec3 dwellAt;
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static final Map<UUID, Pen> PENS = new HashMap<>();

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    /** Called every frame a beam touches a block face: paints at most once per tick and player. */
    public static void paint(UUID player, Vec3 hit, Vec3 normal, boolean laser) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        int kind = laser ? 1 : 0;
        Pen pen = PENS.computeIfAbsent(player, id -> new Pen());
        if (pen.lastTick == now) {
            return;
        }
        Vec3 p = hit.add(normal.scale(0.004D));
        boolean sameFace = pen.last != null && pen.normal != null && pen.normal.equals(normal)
                && now - pen.lastTick <= 3L
                && Math.abs(p.subtract(pen.last).dot(normal)) < 0.02D;
        double dist = pen.last == null ? 0.0D : p.distanceTo(pen.last);
        pen.lastTick = now;
        if (!sameFace || dist > JUMP) {
            // new stroke: starts with a small round burn
            pen.last = p;
            pen.normal = normal;
            pen.u = mc.level.random.nextFloat();
            pen.dwellAt = p;
            pen.spot = add(new Mark(p, p, normal, 0, 0, kind, true, SPOT_MIN[kind], now,
                    mc.level.random.nextFloat() * 6.28F));
            return;
        }
        // held on one spot: the round burn grows
        if (pen.dwellAt != null && p.distanceTo(pen.dwellAt) < DWELL_R[kind]) {
            if (pen.spot != null) {
                pen.spot.radius = Math.min(SPOT_MAX[kind], pen.spot.radius + 0.004F);
            }
            if (dist < MIN_STEP) {
                return;
            }
        } else {
            pen.dwellAt = p;
            pen.spot = null;
        }
        if (dist < STEP && now % 3L != 0L) {
            return;
        }
        if (dist < MIN_STEP) {
            return;
        }
        float du = (float) (dist / PERIOD[kind]);
        add(new Mark(pen.last, p, normal, pen.u, pen.u + du, kind, false, 0.0F, now, 0.0F));
        pen.u += du;
        pen.last = p;
        if (mc.level.random.nextFloat() < 0.35F) {
            mc.level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0.0D, 0.03D, 0.0D);
        }
    }

    private static Mark add(Mark m) {
        MARKS.add(m);
        if (MARKS.size() > CAP) {
            MARKS.remove(0);
        }
        return m;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        MARKS.removeIf(m -> now - m.start > LIFE || now < m.start);
        // fresh burns smoke a little
        if (!MARKS.isEmpty() && now % 4L == 0L) {
            for (int i = Math.max(0, MARKS.size() - 40); i < MARKS.size(); i++) {
                Mark m = MARKS.get(i);
                if (now - m.start < HOT && mc.level.random.nextFloat() < 0.15F) {
                    mc.level.addParticle(ParticleTypes.SMOKE, m.b.x, m.b.y, m.b.z, 0.0D, 0.02D, 0.0D);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private static DynamicTexture texture;
    private static ResourceLocation textureId;

    private static ResourceLocation texture() {
        if (textureId == null) {
            float[] art = Art.build();
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, TW, TH, false);
            for (int y = 0; y < TH; y++) {
                for (int x = 0; x < TW; x++) {
                    int v = Math.max(0, Math.min(255, Math.round(art[y * TW + x] * 255.0F)));
                    image.setPixelRGBA(x, y, (v << 24) | (v << 16) | (v << 8) | v);
                }
            }
            texture = new DynamicTexture(image);
            textureId = Minecraft.getInstance().getTextureManager().register("flash_superman_scorch", texture);
        }
        return textureId;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MARKS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float time = mc.level.getGameTime() + event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, texture());
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);

        // 1: char, multiplied into the block colour
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        int count = 0;
        for (Mark mk : MARKS) {
            double age = time - mk.start;
            if (age < 0.0D || cam.distanceToSqr(mk.b) > 64.0D * 64.0D) {
                continue;
            }
            float fade = age > LIFE - FADE ? (float) ((LIFE - age) / FADE) : 1.0F;
            if (fade <= 0.0F) {
                continue;
            }
            float r = CHAR_R + (1.0F - CHAR_R) * (1.0F - fade);
            float g = CHAR_G + (1.0F - CHAR_G) * (1.0F - fade);
            float bl = CHAR_B + (1.0F - CHAR_B) * (1.0F - fade);
            if (mk.spot) {
                spot(b, m, mk, mk.radius * 1.4F, r, g, bl, fade, 0.0F, 128.0F);
            } else {
                stroke(b, m, mk, CHAR_HW[mk.kind], r, g, bl, fade, 8.0F, 56.0F);
            }
            count++;
        }
        if (count > 0) {
            BufferUploader.drawWithShader(b.end());
        } else {
            b.end().release();
        }

        // 2: the glowing rim and core while it cools (premultiplied light)
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        count = 0;
        for (Mark mk : MARKS) {
            double age = time - mk.start;
            if (age < 0.0D || age > HOT || cam.distanceToSqr(mk.b) > 64.0D * 64.0D) {
                continue;
            }
            float k = (float) (age / HOT);
            float ra = ramp(RIM_A, k);
            float ca = ramp(CORE_A, k);
            if (mk.spot) {
                spot(b, m, mk, mk.radius * 1.2F, ramp(RIM_R, k) * ra, ramp(RIM_G, k) * ra, ramp(RIM_B, k) * ra, ra,
                        128.0F, 128.0F);
                spot(b, m, mk, mk.radius * 0.7F, ramp(CORE_R, k) * ca, ramp(CORE_G, k) * ca, ramp(CORE_B, k) * ca,
                        ca, 128.0F, 128.0F);
            } else {
                stroke(b, m, mk, RIM_HW[mk.kind], ramp(RIM_R, k) * ra, ramp(RIM_G, k) * ra, ramp(RIM_B, k) * ra, ra,
                        8.0F, 56.0F);
                stroke(b, m, mk, CORE_HW[mk.kind], ramp(CORE_R, k) * ca, ramp(CORE_G, k) * ca, ramp(CORE_B, k) * ca,
                        ca, 72.0F, 120.0F);
            }
            count++;
        }
        if (count > 0) {
            BufferUploader.drawWithShader(b.end());
        } else {
            b.end().release();
        }

        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static float ramp(float[] table, float k) {
        if (k <= 0.0F) {
            return table[0];
        }
        for (int i = 1; i < KS.length; i++) {
            if (k <= KS[i]) {
                float f = (k - KS[i - 1]) / (KS[i] - KS[i - 1]);
                return table[i - 1] + (table[i] - table[i - 1]) * f;
            }
        }
        return table[table.length - 1];
    }

    /** Stroke quad on the face: u along the stroke (wraps on the band), v across the band rows. */
    private static void stroke(BufferBuilder b, Matrix4f m, Mark mk, float hw, float r, float g, float bl, float a,
                               float rowTop, float rowBottom) {
        Vec3 dir = mk.b.subtract(mk.a);
        if (dir.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 side = mk.normal.cross(dir).normalize().scale(hw);
        // extend a little at both ends so consecutive segments overlap
        Vec3 ext = dir.normalize().scale(hw * 0.4D);
        Vec3 a0 = mk.a.subtract(ext);
        Vec3 a1 = mk.b.add(ext);
        float v0 = rowTop / TH;
        float v1 = rowBottom / TH;
        float u0 = mk.u0;
        float u1 = mk.u1;
        vtx(b, m, a0.subtract(side), r, g, bl, a, u0, v0);
        vtx(b, m, a0.add(side), r, g, bl, a, u0, v1);
        vtx(b, m, a1.add(side), r, g, bl, a, u1, v1);
        vtx(b, m, a1.subtract(side), r, g, bl, a, u1, v0);
    }

    /** Round burn: a disc cell of the atlas (char disc at x 0, hot disc at x 128, rows 128..256). */
    private static void spot(BufferBuilder b, Matrix4f m, Mark mk, float radius, float r, float g, float bl, float a,
                             float cellX, float cellY) {
        Vec3 n = mk.normal;
        Vec3 ref = Math.abs(n.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s = n.cross(ref).normalize();
        Vec3 t = n.cross(s).normalize();
        double cs = Math.cos(mk.rot);
        double sn = Math.sin(mk.rot);
        Vec3 u = s.scale(cs).add(t.scale(sn)).scale(radius);
        Vec3 v = t.scale(cs).subtract(s.scale(sn)).scale(radius);
        float u0 = cellX / TW;
        float u1 = (cellX + 128.0F) / TW;
        float v0 = cellY / TH;
        float v1 = (cellY + 128.0F) / TH;
        Vec3 p = mk.a;
        vtx(b, m, p.subtract(u).subtract(v), r, g, bl, a, u0, v1);
        vtx(b, m, p.add(u).subtract(v), r, g, bl, a, u1, v1);
        vtx(b, m, p.add(u).add(v), r, g, bl, a, u1, v0);
        vtx(b, m, p.subtract(u).add(v), r, g, bl, a, u0, v0);
    }

    private static void vtx(BufferBuilder b, Matrix4f m, Vec3 p, float r, float g, float bl, float a, float u,
                            float v) {
        b.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, bl, a).uv(u, v).endVertex();
    }

    // ------------------------------------------------------------------
    // Procedural art (port of the pack's ScorchArt40)
    // ------------------------------------------------------------------

    static final class Art {
        private Art() {
        }

        static float[] build() {
            float[] a = new float[TW * TH];
            charBand(a, TW);
            grooveBand(a, TW);
            charDisc(a, TW, 0, 128);
            hotDisc(a, TW, 128, 128);
            wisp(a, TW, 0, 256);
            return a;
        }

        private static void charBand(float[] a, int w) {
            int rows = 48;
            for (int x = 0; x < w; x++) {
                float u = x / (float) w;
                float edgeL = fbm1(u, 7, 5);
                float edgeR = fbm1(u, 11, 5);
                for (int r = 0; r < rows; r++) {
                    float v = (r + 0.5F) / rows;
                    float d = Math.abs(v - 0.5F) * 2.0F;
                    float core = 0.35F + 0.13F * (v < 0.5F ? edgeL : edgeR);
                    float rag = noise2(x, r, 9, 4.0F, 8.0F) - 0.5F;
                    float fine = noise2(x, r, 19, 2.0F, 2.0F) - 0.5F;
                    float dr = d + 0.13F * rag + 0.06F * fine;
                    float inCore = 1.0F - smooth(core - 0.06F, core + 0.04F, dr);
                    float mott = 0.8F + 0.2F * noise2(x, r, 13, 16.0F, 6.0F);
                    float soot = 0.2F * (1.0F - smooth(core, 0.85F, d)) * (0.62F + 0.38F * noise2(x, r, 21, 8.0F, 3.0F));
                    float fleck = noise2(x, r, 31, 32.0F, 12.0F);
                    if (fleck > 0.78F && d > core && d < core + 0.26F) {
                        soot = Math.max(soot, 0.45F * smooth(0.78F, 0.95F, fleck) * (1.0F - smooth(core, core + 0.26F, d)));
                    }
                    float val = soot + (0.9F * mott - soot) * inCore;
                    a[(8 + r) * w + x] = clamp(val);
                }
            }
        }

        private static void grooveBand(float[] a, int w) {
            int rows = 48;
            for (int x = 0; x < w; x++) {
                float u = x / (float) w;
                float width = 0.38F + 0.1F * fbm1(u, 17, 4);
                for (int r = 0; r < rows; r++) {
                    float v = (r + 0.5F) / rows;
                    float d = Math.abs(v - 0.5F) * 2.0F;
                    float rag = noise2(x, r, 41, 4.0F, 8.0F) - 0.5F;
                    float val = 1.0F - smooth(width - 0.26F, width + 0.28F, d + 0.12F * rag);
                    float streak = 0.84F + 0.16F * noise2(x, r, 47, 2.0F, 10.0F);
                    a[(72 + r) * w + x] = clamp(val * (d < width ? streak : 1.0F));
                }
            }
        }

        private static void charDisc(float[] a, int w, int x0, int y0) {
            int n = 128;
            float c = (n - 1) * 0.5F;
            float rad = c - 2.0F;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float dx = (x - c) / rad;
                    float dy = (y - c) / rad;
                    float r = (float) Math.sqrt(dx * dx + dy * dy);
                    float ang = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0D) + 0.5D);
                    float edge = 0.56F + 0.045F * fbm1(ang, 57, 5);
                    float rag = noise2(x, y, 59, 4.0F, 4.0F) - 0.5F;
                    float inCore = 1.0F - smooth(edge - 0.08F, edge + 0.06F, r + 0.09F * rag);
                    float mott = 0.8F + 0.2F * noise2(x, y, 61, 12.0F, 12.0F);
                    float soot = 0.3F * (1.0F - smooth(edge, 0.9F, r)) * (0.6F + 0.4F * noise2(x, y, 67, 5.0F, 5.0F));
                    float ray = fbm1(ang, 71, 5);
                    if (ray > 0.4F && r > edge && r < 0.85F) {
                        soot = Math.max(soot, 0.34F * smooth(0.4F, 0.85F, ray) * (1.0F - smooth(edge, 0.85F, r)));
                    }
                    float val = r >= 1.0F ? 0.0F : soot + (0.96F * mott - soot) * inCore;
                    a[(y0 + y) * w + x0 + x] = clamp(val);
                }
            }
        }

        private static void hotDisc(float[] a, int w, int x0, int y0) {
            int n = 128;
            float c = (n - 1) * 0.5F;
            float rad = c - 2.0F;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float dx = (x - c) / rad;
                    float dy = (y - c) / rad;
                    float r = (float) Math.sqrt(dx * dx + dy * dy);
                    float ang = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0D) + 0.5D);
                    float edge = 0.6F + 0.1F * fbm1(ang, 79, 4);
                    float rag = noise2(x, y, 83, 6.0F, 6.0F) - 0.5F;
                    float val = 1.0F - smooth(edge - 0.32F, edge + 0.28F, r + 0.1F * rag);
                    float swirl = 0.85F + 0.15F * noise2(x, y, 89, 10.0F, 10.0F);
                    a[(y0 + y) * w + x0 + x] = r >= 1.0F ? 0.0F : clamp(val * (r < edge ? swirl : 1.0F));
                }
            }
        }

        private static void wisp(float[] a, int w, int x0, int y0) {
            int n = 128;
            float c = (n - 1) * 0.5F;
            float rad = c - 2.0F;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float dx = (x - c) / rad;
                    float dy = (y - c) / rad;
                    float r = (float) Math.sqrt(dx * dx * 1.15F + dy * dy * 0.85F);
                    float f = noise2(x, y, 97, 5.0F, 5.0F) * 0.6F + noise2(x, y, 101, 11.0F, 11.0F) * 0.4F;
                    float val = (1.0F - smooth(0.15F, 1.0F, r + 0.38F * (f - 0.5F))) * (0.55F + 0.45F * f);
                    a[(y0 + y) * w + x0 + x] = r >= 1.0F ? 0.0F : clamp(val * 0.9F);
                }
            }
        }

        private static float clamp(float f) {
            return f < 0.0F ? 0.0F : Math.min(f, 1.0F);
        }

        static float smooth(float e0, float e1, float x) {
            float t = (x - e0) / (e1 - e0);
            t = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
            return t * t * (3.0F - 2.0F * t);
        }

        private static float hash(int x, int y, int seed) {
            int h = x * 374761393 + y * 668265263 + seed * 1274126177;
            h = (h ^ (h >>> 13)) * 1274126177;
            h = h ^ (h >>> 16);
            return (h & 16777215) / 1.6777216E7F;
        }

        private static float noise1(float u, int cells, int seed) {
            float x = u * cells;
            int i = (int) Math.floor(x);
            float f = x - i;
            int i0 = Math.floorMod(i, cells);
            int i1 = Math.floorMod(i + 1, cells);
            float t = f * f * (3.0F - 2.0F * f);
            return hash(i0, 0, seed) * (1.0F - t) + hash(i1, 0, seed) * t;
        }

        private static float fbm1(float u, int seed, int octaves) {
            float sum = 0.0F;
            float amp = 1.0F;
            float norm = 0.0F;
            int cells = 6;
            for (int o = 0; o < octaves; o++) {
                sum += (noise1(u, cells, seed + o * 7) * 2.0F - 1.0F) * amp;
                norm += amp;
                amp *= 0.5F;
                cells *= 2;
            }
            return sum / norm;
        }

        private static float noise2(int px, int py, int seed, float sx, float sy) {
            float x = px / sx;
            float y = py / sy;
            int period = Math.max(1, Math.round(256.0F / sx));
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            float fx = x - ix;
            float fy = y - iy;
            int x0 = Math.floorMod(ix, period);
            int x1 = Math.floorMod(ix + 1, period);
            float tx = fx * fx * (3.0F - 2.0F * fx);
            float ty = fy * fy * (3.0F - 2.0F * fy);
            float a = hash(x0, iy, seed);
            float b = hash(x1, iy, seed);
            float c = hash(x0, iy + 1, seed);
            float d = hash(x1, iy + 1, seed);
            return (a * (1.0F - tx) + b * tx) * (1.0F - ty) + (c * (1.0F - tx) + d * tx) * ty;
        }
    }
}

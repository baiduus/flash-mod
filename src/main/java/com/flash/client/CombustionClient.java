package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.CombustionPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Combustion's look, ported from the Green Lanterns mod (lanternasverdes, its ClientRender.meteoro and the
 * burst of its effect 7): a hero burning down is a meteor (glowing core, cones of fire trailing behind, streaks,
 * rings of heat and embers); in first person a ring of fire around the view with sparks rushing past; the impact
 * is a fiery sphere swelling and fading.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class CombustionClient {

    private static final IntSet BURNING = new IntOpenHashSet();
    private static final List<float[]> BURSTS = new ArrayList<>();
    private static int clock;

    private CombustionClient() {
    }

    public static void receive(CombustionPacket p) {
        switch (p.state()) {
            case 1 -> BURNING.add(p.entity());
            case 0 -> BURNING.remove(p.entity());
            default -> {
                BURNING.remove(p.entity());
                // x, y, z, radius, age
                BURSTS.add(new float[] {p.x(), p.y(), p.z(), p.radius(), 0.0F});
            }
        }
    }

    public static boolean burning(Entity e) {
        return e != null && BURNING.contains(e.getId());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BURNING.clear();
            BURSTS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        clock++;
        BURNING.removeIf(id -> mc.level.getEntity(id) == null);
        for (Iterator<float[]> it = BURSTS.iterator(); it.hasNext(); ) {
            float[] b = it.next();
            if (++b[4] > 14.0F) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BURNING.isEmpty() && BURSTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        float t = clock + pt;
        Vector3f le = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vec3 left = new Vec3(le.x(), le.y(), le.z());
        Vec3 upv = new Vec3(up.x(), up.y(), up.z());
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        try {
            for (int id : BURNING) {
                Entity e = mc.level.getEntity(id);
                if (e == null) {
                    continue;
                }
                Vec3 head = e.getPosition(pt).add(0.0D, 0.9D, 0.0D);
                Vec3 mv = e.getPosition(pt).subtract(new Vec3(e.xo, e.yo, e.zo));
                if (mv.lengthSqr() < 1.0E-4D) {
                    mv = e.getDeltaMovement();
                }
                Vec3 dir = mv.lengthSqr() > 1.0E-4D ? mv.normalize() : new Vec3(0.0D, -1.0D, 0.0D);
                boolean first = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                meteor(buf, m, head, dir, left, upv, t, first, cam, e.getId());
            }
            for (float[] b : BURSTS) {
                float k = Mth.clamp((b[4] + pt) / 14.0F, 0.0F, 1.0F);
                double radius = b[3] * (1.0D + 0.45D * k);
                float a = (1.0F - k) * (1.0F - k);
                sphere(buf, m, new Vec3(b[0], b[1], b[2]), radius, cam, 1.0F, 0.45F, 0.1F, 0.85F * a, t, 2.2D);
            }
            BufferUploader.drawWithShader(buf.end());
        } finally {
            ps.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    // ------------------------------------------------------------------
    // The Lanterns' meteor
    // ------------------------------------------------------------------

    private static void meteor(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 d, Vec3 left, Vec3 up, float t,
                               boolean first, Vec3 cam, int seed) {
        Vec3 ref = Math.abs(d.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = d.cross(ref).normalize();
        Vec3 v = d.cross(u).normalize();
        Vec3 back = d.scale(-1.0D);
        float flicker = 0.85F + 0.15F * Mth.sin(t * 2.3F) * Mth.sin(t * 1.7F + 1.0F);
        Random rnd = new Random(seed * 31L);
        if (first) {
            // a ring of fire around the view, sparks rushing past
            Vec3 f = cam.add(d);
            int seg = 28;
            for (int j = 0; j < seg; j++) {
                double a0 = Math.PI * 2 * j / seg;
                double a1 = Math.PI * 2 * (j + 1) / seg;
                double ri = 0.62D + 0.04D * Math.sin(t * 1.3D + j);
                double ro = 1.15D + 0.12D * Math.sin(t * 2.1D + j * 1.7D);
                Vec3 i0 = f.add(u.scale(Math.cos(a0) * ri)).add(v.scale(Math.sin(a0) * ri));
                Vec3 i1 = f.add(u.scale(Math.cos(a1) * ri)).add(v.scale(Math.sin(a1) * ri));
                Vec3 o0 = f.add(u.scale(Math.cos(a0) * ro)).add(v.scale(Math.sin(a0) * ro));
                Vec3 o1 = f.add(u.scale(Math.cos(a1) * ro)).add(v.scale(Math.sin(a1) * ro));
                vert(buf, m, i0, 1.0F, 0.55F, 0.15F, 0.0F);
                vert(buf, m, i1, 1.0F, 0.55F, 0.15F, 0.0F);
                vert(buf, m, o1, 1.0F, 0.35F, 0.08F, 0.5F * flicker);
                vert(buf, m, o0, 1.0F, 0.35F, 0.08F, 0.5F * flicker);
            }
            for (int k = 0; k < 10; k++) {
                double a = rnd.nextDouble() * Math.PI * 2.0D;
                double phase = (t * 0.9D + rnd.nextDouble() * 4.0D) % 4.0D;
                Vec3 start = cam.add(u.scale(Math.cos(a) * 1.4D)).add(v.scale(Math.sin(a) * 1.4D))
                        .add(d.scale(3.0D - phase * 1.5D));
                streak(buf, m, start, start.add(back.scale(2.5D)), 0.035F, 1.0F, 0.8F, 0.5F, 0.6F);
            }
            return;
        }
        double r0 = 1.25D;
        cone(buf, m, c.add(d.scale(0.2D)), back, u, v, r0 * 1.05D, 3.6D, 15.0D, 1.0F, 0.32F, 0.12F, 0.42F * flicker,
                0.0F, t, 0.0D);
        cone(buf, m, c, back, u, v, r0 * 0.85D, 1.7D, 7.0D, 1.0F, 0.62F, 0.22F, 0.55F * flicker, 0.0F, t, 1.3D);
        cone(buf, m, c, back, u, v, r0 * 0.55D, 0.6D, 3.2D, 1.0F, 0.92F, 0.65F, 0.7F, 0.0F, t, 2.1D);
        for (int k = 0; k < 18; k++) {
            double a = Math.PI * 2 * k / 18.0D + rnd.nextDouble() * 0.3D;
            double r = r0 * (0.75D + rnd.nextDouble() * 0.35D);
            double len = 7.0D + rnd.nextDouble() * 9.0D + Math.sin(t * 0.8D + k) * 1.5D;
            double open = 0.12D + rnd.nextDouble() * 0.14D;
            Vec3 side = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 p0 = c.add(side.scale(r)).add(d.scale(-0.1D));
            Vec3 p1 = p0.add(back.scale(len)).add(side.scale(len * open));
            boolean white = k % 3 == 0;
            streak(buf, m, p0, p1, (float) (0.05D + rnd.nextDouble() * 0.07D), 1.0F, white ? 0.9F : 0.55F,
                    white ? 0.8F : 0.35F, (white ? 0.55F : 0.4F) * flicker);
        }
        for (int k = 0; k < 5; k++) {
            double dist = 9.0D + k * 4.5D;
            double wave = Math.sin(t * 0.3D + k) * 0.6D;
            Vec3 q = c.add(back.scale(dist)).add(u.scale(wave)).add(v.scale(Math.cos(t * 0.25D + k) * 0.5D));
            disc(buf, m, q, left, up, 3.0D + k * 0.9D, 0.8F, 0.3F, 0.3F, 0.9F, 0.45F, 0.45F,
                    0.07F * (1.0F - k / 6.0F));
        }
        for (int k = 0; k < 14; k++) {
            double phase = (t * 0.12D + rnd.nextDouble()) % 1.0D;
            double a = rnd.nextDouble() * Math.PI * 2.0D;
            double r = r0 * (0.6D + rnd.nextDouble() * 1.6D) * (1.0D + phase * 1.5D);
            Vec3 q = c.add(back.scale(1.5D + phase * 14.0D)).add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
            disc(buf, m, q, left, up, 0.06D + 0.05D * (1.0D - phase), 1.0F, 0.7F, 0.4F, 1.0F, 0.95F, 0.8F,
                    0.9F * (float) (1.0D - phase));
        }
        double tremor = 0.05D * Math.sin(t * 3.1D);
        disc(buf, m, c, left, up, r0 * 1.75D + tremor, 1.0F, 0.35F, 0.08F, 1.0F, 0.55F, 0.15F, 0.28F * flicker);
        disc(buf, m, c, left, up, r0 * 1.25D + tremor, 1.0F, 0.55F, 0.12F, 1.0F, 0.85F, 0.35F, 0.5F * flicker);
        disc(buf, m, c, left, up, r0 * 0.95D, 1.0F, 0.8F, 0.3F, 1.0F, 0.97F, 0.75F, 0.75F);
        disc(buf, m, c, left, up, r0 * 0.6D, 1.0F, 0.95F, 0.7F, 1.0F, 1.0F, 1.0F, 0.9F);
        int seg = 20;
        Vec3 front = c.add(d.scale(r0 * 0.55D));
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg;
            double a1 = Math.PI * 2 * (j + 1) / seg;
            double rr = r0 * 1.05D;
            Vec3 b0 = c.add(u.scale(Math.cos(a0) * rr)).add(v.scale(Math.sin(a0) * rr));
            Vec3 b1 = c.add(u.scale(Math.cos(a1) * rr)).add(v.scale(Math.sin(a1) * rr));
            Vec3 tip = front.add(d.scale(r0 * 0.35D));
            vert(buf, m, tip, 1.0F, 0.9F, 0.6F, 0.15F);
            vert(buf, m, b0, 1.0F, 0.4F, 0.1F, 0.55F * flicker);
            vert(buf, m, b1, 1.0F, 0.4F, 0.1F, 0.55F * flicker);
            vert(buf, m, tip, 1.0F, 0.9F, 0.6F, 0.15F);
        }
        lightBurst(buf, m, c, left, up, (float) (r0 * 2.2D), 1.0F, 0.6F, 0.2F, 0.35F * flicker, t * 0.4F);
    }

    private static void cone(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 back, Vec3 u, Vec3 v, double r0, double r1,
                             double len, float cr, float cg, float cb, float a0, float a1, float t, double phase) {
        int seg = 22;
        int rings = 6;
        for (int g = 0; g < rings; g++) {
            double k0 = (double) g / rings;
            double k1 = (double) (g + 1) / rings;
            double ra = r0 + (r1 - r0) * Math.sqrt(k0);
            double rb = r0 + (r1 - r0) * Math.sqrt(k1);
            float aa = a0 + (a1 - a0) * (float) k0;
            float ab = a0 + (a1 - a0) * (float) k1;
            float ca = Mth.lerp((float) k0, 1.0F, 0.75F);
            float cb2 = Mth.lerp((float) k1, 1.0F, 0.75F);
            for (int j = 0; j < seg; j++) {
                double ang0 = Math.PI * 2 * j / seg;
                double ang1 = Math.PI * 2 * (j + 1) / seg;
                double w0 = 1.0D + 0.08D * Math.sin(t * 1.4D + j * 1.3D + g + phase);
                double w1 = 1.0D + 0.08D * Math.sin(t * 1.4D + (j + 1) * 1.3D + g + phase);
                Vec3 da = c.add(back.scale(len * k0));
                Vec3 db = c.add(back.scale(len * k1));
                Vec3 p00 = da.add(u.scale(Math.cos(ang0) * ra * w0)).add(v.scale(Math.sin(ang0) * ra * w0));
                Vec3 p01 = da.add(u.scale(Math.cos(ang1) * ra * w1)).add(v.scale(Math.sin(ang1) * ra * w1));
                Vec3 p10 = db.add(u.scale(Math.cos(ang0) * rb * w0)).add(v.scale(Math.sin(ang0) * rb * w0));
                Vec3 p11 = db.add(u.scale(Math.cos(ang1) * rb * w1)).add(v.scale(Math.sin(ang1) * rb * w1));
                vert(buf, m, p00, cr, cg * ca, cb * ca, aa);
                vert(buf, m, p01, cr, cg * ca, cb * ca, aa);
                vert(buf, m, p11, cr, cg * cb2, cb * cb2, ab);
                vert(buf, m, p10, cr, cg * cb2, cb * cb2, ab);
            }
        }
    }

    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    private static float mix(float c, float k) {
        return c + (1.0F - c) * k;
    }

    private static void streak(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, float w, float r, float g, float bl,
                               float alpha) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4D) {
            return;
        }
        Vec3 dn = d.scale(1.0D / len);
        Vec3 ref = Math.abs(dn.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u).normalize();
        for (int k = 0; k < 3; k++) {
            double ang = Math.PI * k / 3.0D;
            Vec3 s = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang))).scale(w);
            vert(buf, m, a.subtract(s), r, g, bl, 0.0F);
            vert(buf, m, a, r, g, bl, alpha);
            vert(buf, m, b, r, g, bl, alpha);
            vert(buf, m, b.subtract(s), r, g, bl, 0.0F);
            vert(buf, m, a, r, g, bl, alpha);
            vert(buf, m, a.add(s), r, g, bl, 0.0F);
            vert(buf, m, b.add(s), r, g, bl, 0.0F);
            vert(buf, m, b, r, g, bl, alpha);
        }
    }

    private static void disc(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 e1, Vec3 e2, double radius, float rr,
                             float rg, float rb, float ir, float ig, float ib, float alpha) {
        int seg = 16;
        double inner = radius * 0.72D;
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg;
            double a1 = Math.PI * 2 * (j + 1) / seg;
            Vec3 q0 = c.add(e1.scale(Math.cos(a0) * inner)).add(e2.scale(Math.sin(a0) * inner));
            Vec3 q1 = c.add(e1.scale(Math.cos(a1) * inner)).add(e2.scale(Math.sin(a1) * inner));
            Vec3 p0 = c.add(e1.scale(Math.cos(a0) * radius)).add(e2.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(e1.scale(Math.cos(a1) * radius)).add(e2.scale(Math.sin(a1) * radius));
            vert(buf, m, c, mix(ir, 0.3F), mix(ig, 0.3F), mix(ib, 0.3F), alpha);
            vert(buf, m, q0, ir, ig, ib, alpha * 0.9F);
            vert(buf, m, q1, ir, ig, ib, alpha * 0.9F);
            vert(buf, m, c, mix(ir, 0.3F), mix(ig, 0.3F), mix(ib, 0.3F), alpha);
            vert(buf, m, q0, rr, rg, rb, alpha);
            vert(buf, m, p0, rr, rg, rb, alpha);
            vert(buf, m, p1, rr, rg, rb, alpha);
            vert(buf, m, q1, rr, rg, rb, alpha);
        }
    }

    private static void lightBurst(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 left, Vec3 up, float radius, float cr,
                                   float cg, float cb, float alpha, float rot) {
        int rays = 12;
        for (int k = 0; k < rays; k++) {
            double a = rot + Math.PI * 2 * k / rays;
            Vec3 d = left.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 pe = left.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
            double len = radius * (k % 2 == 0 ? 1.0D : 0.62D);
            double wide = radius * 0.07D;
            Vec3 tip = c.add(d.scale(len));
            vert(buf, m, c.add(pe.scale(wide)), mix(cr, 0.7F), mix(cg, 0.7F), mix(cb, 0.45F), alpha);
            vert(buf, m, c.subtract(pe.scale(wide)), mix(cr, 0.7F), mix(cg, 0.7F), mix(cb, 0.45F), alpha);
            vert(buf, m, tip, cr, cg, cb, 0.0F);
            vert(buf, m, tip, cr, cg, cb, 0.0F);
        }
        int seg = 14;
        double rd = radius * 0.28D;
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg;
            double a1 = Math.PI * 2 * (j + 1) / seg;
            Vec3 p0 = c.add(left.scale(Math.cos(a0) * rd)).add(up.scale(Math.sin(a0) * rd));
            Vec3 p1 = c.add(left.scale(Math.cos(a1) * rd)).add(up.scale(Math.sin(a1) * rd));
            vert(buf, m, c, mix(cr, 0.85F), mix(cg, 0.9F), mix(cb, 0.6F), alpha);
            vert(buf, m, p0, cr, cg, cb, 0.0F);
            vert(buf, m, p1, cr, cg, cb, 0.0F);
            vert(buf, m, c, mix(cr, 0.85F), mix(cg, 0.9F), mix(cb, 0.6F), alpha);
        }
    }

    private static void sphere(BufferBuilder buf, Matrix4f m, Vec3 c, double radius, Vec3 cam, float cr, float cg,
                               float cb, float alpha, float t, double rimExp) {
        int lat = 12;
        int lon = 20;
        Vec3[][] p = new Vec3[lat + 1][lon + 1];
        float[][] al = new float[lat + 1][lon + 1];
        float[][] rr = new float[lat + 1][lon + 1];
        float[][] gg = new float[lat + 1][lon + 1];
        float[][] bb = new float[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double phi = Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                double th = Math.PI * 2 * j / lon;
                double wave = 1.0D + 0.04D * Math.sin(3.0D * th + t * 0.35D) * Math.sin(2.0D * phi + t * 0.28D);
                Vec3 n = new Vec3(Math.sin(phi) * Math.cos(th), Math.cos(phi), Math.sin(phi) * Math.sin(th));
                Vec3 pos = c.add(n.scale(radius * wave));
                Vec3 vc = cam.subtract(pos);
                double len = vc.length();
                double fres = len < 1.0E-6D ? 0.0D : 1.0D - Math.abs(n.dot(vc.scale(1.0D / len)));
                p[i][j] = pos;
                al[i][j] = (float) (alpha * (0.12D + 0.88D * Math.pow(fres, rimExp)));
                rr[i][j] = Mth.clamp(cr + 0.22F * (float) Math.sin(th * 2.0D + t * 0.1D), 0.0F, 1.0F);
                gg[i][j] = Mth.clamp(cg + 0.1F * (float) Math.sin(phi * 3.0D - t * 0.12D), 0.0F, 1.0F);
                bb[i][j] = Mth.clamp(cb + 0.3F * (float) Math.sin(phi * 3.0D - t * 0.12D + 1.0D), 0.0F, 1.0F);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                vert(buf, m, p[i][j], rr[i][j], gg[i][j], bb[i][j], al[i][j]);
                vert(buf, m, p[i][j + 1], rr[i][j + 1], gg[i][j + 1], bb[i][j + 1], al[i][j + 1]);
                vert(buf, m, p[i + 1][j + 1], rr[i + 1][j + 1], gg[i + 1][j + 1], bb[i + 1][j + 1], al[i + 1][j + 1]);
                vert(buf, m, p[i + 1][j], rr[i + 1][j], gg[i + 1][j], bb[i + 1][j], al[i + 1][j]);
            }
        }
    }
}

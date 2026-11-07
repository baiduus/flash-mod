package com.flash.client;

import com.flash.FlashMod;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The shaders of the Superman pack.
 *
 * <ul>
 *   <li>{@code superman_heat_vision}: the procedural beam light (white filament in a scarlet corona, eye and impact
 *   flares, the golden solar rim of the Ultimate); {@code superman_heat_vision_fp} the first-person version;
 *   {@code superman_heat_bloom} the wide soft glow ribbon around every beam.</li>
 *   <li>Laser Blast screen effects ({@code superman_laser_*}): one full-screen pass that lights the scene red around
 *   the beams and their impacts and makes the hot air shimmer, then a bloom (bright beam pixels extracted at half
 *   resolution, blurred down and up through four levels, composited back as a crimson glow).</li>
 * </ul>
 *
 * Off when a shader pack (Iris / OptiFine) is active or when a shader fails to load: then the old ribbons are drawn.
 */
public final class SupermanShaders {

    private static ShaderInstance heat;
    private static ShaderInstance heatFp;
    private static ShaderInstance bloom;
    private static ShaderInstance scene;
    private static ShaderInstance extract;
    private static ShaderInstance down;
    private static ShaderInstance up;
    private static ShaderInstance composite;
    private static boolean failed;

    private SupermanShaders() {
    }

    /** Registration (mod bus). */
    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) {
            try {
                var p = event.getResourceProvider();
                event.registerShader(new ShaderInstance(p, rl("superman_heat_vision"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> heat = s);
                event.registerShader(new ShaderInstance(p, rl("superman_heat_vision_fp"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> heatFp = s);
                event.registerShader(new ShaderInstance(p, rl("superman_heat_bloom"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> bloom = s);
                event.registerShader(new ShaderInstance(p, rl("superman_laser_scene"), DefaultVertexFormat.POSITION),
                        s -> scene = s);
                event.registerShader(new ShaderInstance(p, rl("superman_laser_extract"), DefaultVertexFormat.POSITION),
                        s -> extract = s);
                event.registerShader(new ShaderInstance(p, rl("superman_laser_down"), DefaultVertexFormat.POSITION),
                        s -> down = s);
                event.registerShader(new ShaderInstance(p, rl("superman_laser_up"), DefaultVertexFormat.POSITION),
                        s -> up = s);
                event.registerShader(new ShaderInstance(p, rl("superman_laser_composite"),
                        DefaultVertexFormat.POSITION), s -> composite = s);
                failed = false;
            } catch (Exception ex) {
                failed = true;
                com.mojang.logging.LogUtils.getLogger().error("Superman shaders could not be loaded", ex);
            }
        }
    }

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(FlashMod.MODID, name);
    }

    /** True when the beam shaders can be used (loaded, and no shader pack). */
    public static boolean ready() {
        return !failed && heat != null && heatFp != null && bloom != null && !shaderPack();
    }

    private static boolean laserReady() {
        return ready() && scene != null && extract != null && down != null && up != null && composite != null;
    }

    private static Boolean irisPresent;

    private static boolean shaderPack() {
        try {
            if (irisPresent == null) {
                irisPresent = Class.forName("net.irisshaders.iris.api.v0.IrisApi") != null;
            }
            if (irisPresent) {
                Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
                return (Boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
            }
        } catch (Throwable ignored) {
            irisPresent = false;
        }
        try {
            Class<?> of = Class.forName("net.optifine.shaders.Shaders");
            return of.getField("shaderPackLoaded").getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Map<UUID, Long> ULT_START = new HashMap<>();

    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
    public static final class Frame {
        private Frame() {
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !ready()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            try {
                render(event, mc, mc.level);
            } catch (Throwable ex) {
                failed = true;
                com.mojang.logging.LogUtils.getLogger().error("Superman shaders disabled after an error", ex);
                restore(mc.getMainRenderTarget());
            }
        }
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        List<SupermanClient.Beam> beams = SupermanClient.beams();
        boolean solar = false;
        long now = level.getGameTime();
        for (Player p : level.players()) {
            if (SupermanState.isSuperman(p) && SupermanState.flag(p, SupermanPowers.F_ULTIMATE)) {
                ULT_START.putIfAbsent(p.getUUID(), now);
                solar = true;
            } else {
                ULT_START.remove(p.getUUID());
            }
        }
        if (beams.isEmpty() && !solar) {
            return;
        }
        float partial = event.getPartialTick();
        float seconds = (now % 24000L + partial) / 20.0F;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        VIEW.set(event.getPoseStack().last().pose());
        PROJ.set(event.getProjectionMatrix());
        RenderTarget main = mc.getMainRenderTarget();

        boolean laser = false;
        for (SupermanClient.Beam b : beams) {
            laser |= b.laser();
        }
        laser &= laserReady();
        if (laser) {
            scenePass(mc, level, main, beams, cam, partial, seconds);
        }

        // the beams themselves, with the camera rotation as model view
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.mulPoseMatrix(VIEW);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Vector3f left = camera.getLeftVector();
        Vector3f upv = camera.getUpVector();
        drawBeams(beams, cam, left, upv, seconds);
        drawSolar(level, cam, left, upv, partial, seconds, now);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();

        if (laser) {
            bloomPass(mc, level, main, beams, cam, partial);
        }
    }

    // ------------------------------------------------------------------
    // Beams (procedural shaders)
    // ------------------------------------------------------------------

    private static void drawBeams(List<SupermanClient.Beam> beams, Vec3 cam, Vector3f left, Vector3f upv,
                                  float seconds) {
        if (beams.isEmpty()) {
            return;
        }
        // 1: the wide bloom ribbons
        BufferBuilder b = begin();
        for (SupermanClient.Beam beam : beams) {
            if (beam.firstPerson()) {
                continue;
            }
            float width = beam.laser() ? 0.7F : 0.45F;
            for (int e = 0; e < 2; e++) {
                Vec3 from = (e == 0 ? beam.rightEye() : beam.leftEye()).subtract(cam);
                float blue = beam.laser() ? 0.5F + e * 0.1F : 1.0F;
                ribbon(b, from, beam.stop().subtract(cam), width, 1.0F, 1.0F, blue, 0.9F);
            }
        }
        draw(bloom, b, seconds);
        // 2: corona and filament (third person) and the flares at the eyes and on the impact
        b = begin();
        for (SupermanClient.Beam beam : beams) {
            if (beam.firstPerson()) {
                continue;
            }
            float width = beam.laser() ? 0.17F : 0.1F;
            Vec3 stop = beam.stop().subtract(cam);
            for (int e = 0; e < 2; e++) {
                Vec3 from = (e == 0 ? beam.rightEye() : beam.leftEye()).subtract(cam);
                ribbon(b, from, stop, width, 1.0F, 0.0F, 0.0F, 1.0F);
                flare(b, from, left, upv, beam.laser() ? 0.42F : 0.28F, 1.0F, 0.0F, 0.0F, 1.0F);
            }
            flare(b, stop, left, upv, beam.laser() ? 1.9F : 1.15F, 1.0F, 0.0F, 0.0F, 1.0F);
        }
        draw(heat, b, seconds);
        // 3: own beams seen in first person (alpha blended, keeps the view readable)
        b = begin();
        boolean any = false;
        for (SupermanClient.Beam beam : beams) {
            if (!beam.firstPerson()) {
                continue;
            }
            any = true;
            float width = beam.laser() ? 0.22F : 0.14F;
            Vec3 stop = beam.stop().subtract(cam);
            for (int e = 0; e < 2; e++) {
                Vec3 from = (e == 0 ? beam.rightEye() : beam.leftEye()).subtract(cam);
                // first-person ribbon marker of the shader: (1, .25, 0), laser (1, .25, .25 + eye x .1)
                ribbon(b, from, stop, width, 1.0F, 0.25F, beam.laser() ? 0.25F + e * 0.1F : 0.0F, 1.0F);
            }
        }
        if (any) {
            draw(heatFp, b, seconds);
            b = begin();
            for (SupermanClient.Beam beam : beams) {
                if (beam.firstPerson()) {
                    flare(b, beam.stop().subtract(cam), left, upv, beam.laser() ? 1.9F : 1.15F, 1.0F, 0.0F, 0.0F, 1.0F);
                }
            }
            draw(heat, b, seconds);
        } else {
            b.end().release();
        }
    }

    /** Ultimate: the golden luminous rim around the body and the solar core flare. */
    private static void drawSolar(ClientLevel level, Vec3 cam, Vector3f left, Vector3f upv, float partial,
                                  float seconds, long now) {
        if (ULT_START.isEmpty()) {
            return;
        }
        BufferBuilder b = begin();
        for (Player p : level.players()) {
            Long start = ULT_START.get(p.getUUID());
            if (start == null || p.isInvisible()) {
                continue;
            }
            float k = Math.min(1.0F, (now - start + partial) / 100.0F);
            Vec3 c = p.getPosition(partial).add(0.0D, p.getBbHeight() * 0.55D, 0.0D).subtract(cam);
            flare(b, c, left, upv, 3.0F + 1.6F * k, 0.0F, 1.0F, 0.0F, 0.55F + 0.45F * k);
            // (only the golden ring: the core flare made a square on screen)
        }
        draw(heat, b, seconds);
    }

    private static BufferBuilder begin() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        return b;
    }

    private static void draw(ShaderInstance shader, BufferBuilder b, float seconds) {
        BufferBuilder.RenderedBuffer data = b.end();
        if (data.isEmpty()) {
            data.release();
            return;
        }
        shader.safeGetUniform("Phase").set(seconds);
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(data);
    }

    /** Camera-facing strip from {@code a} to {@code b}: UV.x = metres from the start, UV.y = 0..1 across. */
    private static void ribbon(BufferBuilder buf, Vec3 a, Vec3 b, float halfWidth, float r, float g, float bl,
                               float alpha) {
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1.0E-3D) {
            return;
        }
        Vec3 sa = dir.cross(a);
        Vec3 sb = dir.cross(b);
        sa = sa.lengthSqr() < 1.0E-8D ? perpendicular(dir) : sa.normalize();
        sb = sb.lengthSqr() < 1.0E-8D ? sa : sb.normalize();
        sa = sa.scale(halfWidth);
        sb = sb.scale(halfWidth);
        float l = (float) len;
        vtx(buf, a.subtract(sa), r, g, bl, alpha, 0.0F, 0.0F);
        vtx(buf, a.add(sa), r, g, bl, alpha, 0.0F, 1.0F);
        vtx(buf, b.add(sb), r, g, bl, alpha, l, 1.0F);
        vtx(buf, b.subtract(sb), r, g, bl, alpha, l, 0.0F);
    }

    private static Vec3 perpendicular(Vec3 d) {
        Vec3 ref = Math.abs(d.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return d.cross(ref).normalize();
    }

    /** Billboard flare: UV.x = -1 - u, UV.y = v (the shader's flare encoding). */
    private static void flare(BufferBuilder buf, Vec3 at, Vector3f left, Vector3f upv, float size, float r, float g,
                              float b, float a) {
        float h = size * 0.5F;
        Vec3 l = new Vec3(left.x() * h, left.y() * h, left.z() * h);
        Vec3 u = new Vec3(upv.x() * h, upv.y() * h, upv.z() * h);
        vtx(buf, at.add(l).subtract(u), r, g, b, a, -1.0F, 1.0F);
        vtx(buf, at.subtract(l).subtract(u), r, g, b, a, -2.0F, 1.0F);
        vtx(buf, at.subtract(l).add(u), r, g, b, a, -2.0F, 0.0F);
        vtx(buf, at.add(l).add(u), r, g, b, a, -1.0F, 0.0F);
    }

    private static void vtx(BufferBuilder b, Vec3 p, float r, float g, float bl, float a, float u, float v) {
        b.vertex(p.x, p.y, p.z).color(r, g, bl, a).uv(u, v).endVertex();
    }

    // ------------------------------------------------------------------
    // Laser Blast screen effects
    // ------------------------------------------------------------------

    private static TextureTarget sceneCopy;
    private static final TextureTarget[] DOWN = new TextureTarget[4];
    private static final TextureTarget[] UP = new TextureTarget[3];
    private static final float[] UP_LOW = {0.6F, 0.7F, 0.8F};
    private static final FloatBuffer LIGHTS = BufferUtils.createFloatBuffer(24 * 4);
    private static final FloatBuffer FP_PATHS = BufferUtils.createFloatBuffer(8 * 4);
    private static final FloatBuffer PATHS = BufferUtils.createFloatBuffer(64 * 4);
    private static int frameNo;

    private static TextureTarget ensure(TextureTarget t, int w, int h, boolean depth) {
        if (t == null) {
            t = new TextureTarget(w, h, depth, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        }
        return t;
    }

    private static void copyMain(RenderTarget main, boolean depth) {
        sceneCopy = ensure(sceneCopy, main.width, main.height, true);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT | (depth ? GL11.GL_DEPTH_BUFFER_BIT : 0), GL11.GL_NEAREST);
    }

    private static void restore(RenderTarget main) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Light around the camera: 1 in daylight, low at night or in a dark cave. */
    private static float ambient(ClientLevel level, Vec3 cam, float partial) {
        BlockPos pos = BlockPos.containing(cam);
        float sky = level.getBrightness(LightLayer.SKY, pos) / 15.0F * (level.getSkyDarken(partial) * 0.95F + 0.05F);
        float block = Math.min(1.0F, level.getBrightness(LightLayer.BLOCK, pos) / 15.0F * 1.1F);
        return Math.max(0.03F, Math.min(1.0F, Math.max(sky, block) + Math.min(sky, block) * 0.3F));
    }

    private static void fullscreen(ShaderInstance shader, String array, FloatBuffer data, String array2,
                                   FloatBuffer data2) {
        shader.apply();
        if (array != null) {
            upload(shader, array, data);
        }
        if (array2 != null) {
            upload(shader, array2, data2);
        }
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        b.vertex(-1.0D, -1.0D, 0.0D).endVertex();
        b.vertex(3.0D, -1.0D, 0.0D).endVertex();
        b.vertex(-1.0D, 3.0D, 0.0D).endVertex();
        BufferUploader.draw(b.end());
        shader.clear();
    }

    private static void upload(ShaderInstance shader, String name, FloatBuffer data) {
        ProgramManager.glUseProgram(shader.getId());
        int loc = Uniform.glGetUniformLocation(shader.getId(), name);
        if (loc >= 0) {
            data.rewind();
            RenderSystem.glUniform4(loc, data);
        }
    }

    /** Beam ends relative to the camera, merged per player (both eyes), at most 8 lights. */
    private static int buildLights(List<SupermanClient.Beam> beams, Vec3 cam) {
        LIGHTS.clear();
        int n = 0;
        for (SupermanClient.Beam b : beams) {
            if (n >= 8) {
                break;
            }
            float core = b.laser() ? 0.06F : 0.04F;
            Vec3 a = b.rightEye().add(b.leftEye()).scale(0.5D).subtract(cam);
            Vec3 e = b.stop().subtract(cam);
            float intensity = b.laser() ? 1.0F : 0.6F;
            if (b.firstPerson()) {
                // the first metres in front of the eyes would light the screen itself
                Vec3 d = e.subtract(a);
                double len = d.length();
                if (len > 0.001D) {
                    a = a.add(d.scale(Math.min(2.0D, len * 0.3D) / len));
                }
                intensity *= 0.5F;
            }
            float haze = Math.max(0.4F, Math.min(1.0F, core * 12.5F * 1.9F));
            LIGHTS.put((float) a.x).put((float) a.y).put((float) a.z).put(intensity);
            LIGHTS.put((float) e.x).put((float) e.y).put((float) e.z).put(core);
            LIGHTS.put(1.0F).put(1.0F).put(b.firstPerson() ? 1.0F : 0.0F).put(haze);
            n++;
        }
        while (LIGHTS.position() < LIGHTS.capacity()) {
            LIGHTS.put(0.0F);
        }
        LIGHTS.flip();
        return n;
    }

    private static void scenePass(Minecraft mc, ClientLevel level, RenderTarget main, List<SupermanClient.Beam> beams,
                                  Vec3 cam, float partial, float seconds) {
        int lights = buildLights(beams, cam);
        if (lights == 0) {
            return;
        }
        float amb = ambient(level, cam, partial);
        float dark = Math.max(0.0F, Math.min(1.0F, 1.0F - amb));
        float darkCurve = (float) Math.pow(dark, 0.8D);
        copyMain(main, true);
        main.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        Matrix4f invProjView = new Matrix4f(PROJ).mul(VIEW).invert();
        scene.setSampler("SceneSampler", sceneCopy.getColorTextureId());
        scene.setSampler("DepthSampler", sceneCopy.getDepthTextureId());
        scene.safeGetUniform("InvProjView").set(invProjView);
        scene.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        scene.safeGetUniform("Time").set(seconds);
        scene.safeGetUniform("LightParams").set(0.32F + 1.05F * darkCurve,
                Math.min(4.5F, 1.0F / (0.15F + 0.85F * amb)), 0.018F + 0.045F * dark, dark);
        scene.safeGetUniform("FxParams").set(2.5F, 0.05F + 0.08F * dark, (float) lights, 0.0F);
        scene.safeGetUniform("LightParams2").set(0.12F + 0.65F * dark, 0.0F, 0.0F, 0.0F);
        FP_PATHS.clear();
        while (FP_PATHS.position() < FP_PATHS.capacity()) {
            FP_PATHS.put(0.0F);
        }
        FP_PATHS.flip();
        fullscreen(scene, "Lights", LIGHTS, "FpPaths", FP_PATHS);
        restore(main);
    }

    /** Projects a camera-relative point: (x px, y px from the bottom, 1/w), or null behind the near plane. */
    private static float[] project(Vec3 p, int w, int h) {
        Vector4f v = new Vector4f((float) p.x, (float) p.y, (float) p.z, 1.0F);
        VIEW.transform(v);
        if (v.z() > -0.1F) {
            return null;
        }
        PROJ.transform(v);
        float inv = 1.0F / v.w();
        return new float[] {(v.x() * inv * 0.5F + 0.5F) * w, (v.y() * inv * 0.5F + 0.5F) * h, inv};
    }

    /** Clips the segment to the part in front of the camera (view z <= -0.1). */
    private static Vec3[] clip(Vec3 a, Vec3 b) {
        Vector4f va = new Vector4f((float) a.x, (float) a.y, (float) a.z, 1.0F);
        Vector4f vb = new Vector4f((float) b.x, (float) b.y, (float) b.z, 1.0F);
        VIEW.transform(va);
        VIEW.transform(vb);
        float near = -0.1F;
        if (va.z() > near && vb.z() > near) {
            return null;
        }
        if (va.z() > near) {
            double t = (near - va.z()) / (vb.z() - va.z());
            a = a.add(b.subtract(a).scale(t + 1.0E-3D));
        } else if (vb.z() > near) {
            double t = (near - vb.z()) / (va.z() - vb.z());
            b = b.add(a.subtract(b).scale(t + 1.0E-3D));
        }
        return new Vec3[] {a, b};
    }

    private static int buildPaths(List<SupermanClient.Beam> beams, Vec3 cam, int w, int h) {
        PATHS.clear();
        int n = 0;
        float focal = PROJ.m11() * h * 0.5F;
        for (SupermanClient.Beam b : beams) {
            if (!b.laser()) {
                continue;
            }
            for (int e = 0; e < 2 && n < 16; e++) {
                Vec3 eye = (e == 0 ? b.rightEye() : b.leftEye()).subtract(cam);
                Vec3 stop = b.stop().subtract(cam);
                Vec3[] seg = clip(eye, stop);
                if (seg == null) {
                    continue;
                }
                float[] ps = project(seg[0], w, h);
                float[] pe = project(seg[1], w, h);
                if (ps == null || pe == null) {
                    continue;
                }
                float[] impact = project(stop, w, h);
                float[] eyeP = b.firstPerson() ? null : project(eye, w, h);
                float fp = b.firstPerson() ? 1.0F : 0.0F;
                PATHS.put(ps[0]).put(ps[1]).put(pe[0]).put(pe[1]);
                PATHS.put(ps[2]).put(pe[2]).put(focal).put(fp);
                if (impact != null) {
                    PATHS.put(impact[0]).put(impact[1]).put(impact[2]).put(1.0F);
                } else {
                    PATHS.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
                }
                float half = Math.max(0.12F, Math.min(0.7F, 0.06F * 12.5F));
                if (eyeP != null) {
                    PATHS.put(eyeP[0]).put(eyeP[1]).put(eyeP[2]).put(half);
                } else {
                    PATHS.put(0.0F).put(0.0F).put(0.0F).put(half);
                }
                n++;
            }
        }
        while (PATHS.position() < PATHS.capacity()) {
            PATHS.put(0.0F);
        }
        PATHS.flip();
        return n;
    }

    private static void bloomPass(Minecraft mc, ClientLevel level, RenderTarget main, List<SupermanClient.Beam> beams,
                                  Vec3 cam, float partial) {
        int w = main.width;
        int h = main.height;
        int paths = buildPaths(beams, cam, w, h);
        if (paths == 0) {
            return;
        }
        for (int k = 0; k < 4; k++) {
            DOWN[k] = ensure(DOWN[k], Math.max(1, w >> (k + 1)), Math.max(1, h >> (k + 1)), false);
        }
        for (int k = 0; k < 3; k++) {
            UP[k] = ensure(UP[k], DOWN[k].width, DOWN[k].height, false);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        // 1: bright beam pixels at half resolution
        DOWN[0].bindWrite(true);
        extract.setSampler("MainSampler", main.getColorTextureId());
        extract.safeGetUniform("SrcSize").set((float) w, (float) h);
        extract.safeGetUniform("BloomParams").set((float) paths, 0.42F, 0.92F, 0.0F);
        fullscreen(extract, "Paths", PATHS, null, null);
        // 2: down the chain
        for (int k = 1; k < 4; k++) {
            TextureTarget src = DOWN[k - 1];
            DOWN[k].bindWrite(true);
            down.setSampler("Src", src.getColorTextureId());
            down.safeGetUniform("SrcTexel").set(1.0F / src.width, 1.0F / src.height);
            fullscreen(down, null, null, null, null);
        }
        // 3: and back up
        TextureTarget lower = DOWN[3];
        for (int k = 2; k >= 0; k--) {
            UP[k].bindWrite(true);
            up.setSampler("Src", lower.getColorTextureId());
            up.setSampler("Cur", DOWN[k].getColorTextureId());
            up.safeGetUniform("SrcTexel").set(1.0F / lower.width, 1.0F / lower.height);
            up.safeGetUniform("Weights").set(UP_LOW[k], 1.0F);
            fullscreen(up, null, null, null, null);
            lower = UP[k];
        }
        // 4: composite over a copy of the scene
        copyMain(main, false);
        main.bindWrite(true);
        float dark = Math.max(0.0F, Math.min(1.0F, 1.0F - ambient(level, cam, partial)));
        float strength = 0.5F + (0.62F - 0.5F) * dark;
        boolean fp = false;
        for (SupermanClient.Beam b : beams) {
            fp |= b.laser() && b.firstPerson();
        }
        composite.setSampler("SceneSampler", sceneCopy.getColorTextureId());
        composite.setSampler("BloomSampler", UP[0].getColorTextureId());
        composite.safeGetUniform("CompositeParams").set(strength, fp ? 0.18F : 0.0F, (frameNo++ & 7) * 7.13F,
                (float) paths);
        fullscreen(composite, "Paths", PATHS, null, null);
        restore(main);
    }
}

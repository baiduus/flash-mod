package com.flash.client;

import com.flash.FlashMod;
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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
 * A player raised from the dead by the Scarlet Witch coming back (three and a half seconds):
 * <ol>
 * <li>a pillar of scarlet light comes down from the sky onto the spot;</li>
 * <li>a hex circle burns on the ground under him (two rings turning against each other, a hexagram, six runes);</li>
 * <li>when the light touches the ground he is back: a flash, a ring of red force running out on the ground, sparks
 * and souls flying off;</li>
 * <li>red motes spiral up around his body and a scarlet glow wraps him, fading as he stands there alive.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ScarletReviveFx {

    private static final int LIFE = 70;
    private static final int TOUCH = 18;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95F, 0.08F, 0.18F), 1.3F);
    private static final DustParticleOptions BRIGHT = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.5F), 0.9F);
    private static final List<Revive> ACTIVE = new ArrayList<>();
    private static final Random RND = new Random();

    private static final class Revive {
        final Vec3 at;
        final int entity;
        int age;

        Revive(Vec3 at, int entity) {
            this.at = at;
            this.entity = entity;
        }
    }

    private ScarletReviveFx() {
    }

    public static void start(Vec3 at, int entity) {
        ACTIVE.add(new Revive(at, entity));
    }

    /** Where he stands now (he may move while it plays), else the spot. */
    private static Vec3 base(Revive r, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level != null ? mc.level.getEntity(r.entity) : null;
        return e != null && e.position().distanceToSqr(r.at) < 64.0D ? e.getPosition(pt) : r.at;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused() || ACTIVE.isEmpty()) {
            return;
        }
        ClientLevel level = mc.level;
        for (Iterator<Revive> it = ACTIVE.iterator(); it.hasNext(); ) {
            Revive r = it.next();
            r.age++;
            Vec3 c = base(r, 1.0F);
            if (r.age < TOUCH) {
                // the light coming down
                double y = c.y + 40.0D * (1.0D - (double) r.age / TOUCH);
                for (int i = 0; i < 6; i++) {
                    level.addParticle(BRIGHT, c.x + (RND.nextDouble() - 0.5D) * 0.6D, y + RND.nextDouble() * 3.0D,
                            c.z + (RND.nextDouble() - 0.5D) * 0.6D, 0.0D, -0.4D, 0.0D);
                }
            } else if (r.age == TOUCH) {
                // he is back
                level.addParticle(ParticleTypes.FLASH, c.x, c.y + 1.0D, c.z, 0.0D, 0.0D, 0.0D);
                for (int i = 0; i < 120; i++) {
                    double u = RND.nextDouble() * 2.0D - 1.0D;
                    double ang = RND.nextDouble() * Math.PI * 2.0D;
                    double s = Math.sqrt(1.0D - u * u);
                    double v = 0.25D + RND.nextDouble() * 0.35D;
                    level.addParticle(i % 3 == 0 ? ParticleTypes.SOUL : RED, c.x, c.y + 1.0D, c.z,
                            s * Math.cos(ang) * v, Math.abs(u) * v * 0.8D, s * Math.sin(ang) * v);
                }
                for (int i = 0; i < 48; i++) {
                    double ang = i * Math.PI * 2.0D / 48.0D;
                    level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 0.2D, c.z, Math.cos(ang) * 0.7D,
                            0.15D, Math.sin(ang) * 0.7D);
                }
            } else if (r.age < LIFE - 10) {
                // red motes spiralling up his body
                for (int i = 0; i < 3; i++) {
                    double ang = r.age * 0.45D + i * Math.PI * 2.0D / 3.0D;
                    double h = (r.age - TOUCH) % 20 / 20.0D * 2.2D;
                    level.addParticle(i == 0 ? BRIGHT : RED, c.x + Math.cos(ang) * 0.75D, c.y + h,
                            c.z + Math.sin(ang) * 0.75D, 0.0D, 0.03D, 0.0D);
                }
                if (r.age % 4 == 0) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, c.x + (RND.nextDouble() - 0.5D) * 1.6D, c.y + 0.1D,
                            c.z + (RND.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.05D, 0.0D);
                }
            }
            if (r.age >= LIFE) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
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
            for (Revive r : ACTIVE) {
                draw(buf, m, r, pt, cam, left, upv);
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

    private static void draw(BufferBuilder buf, Matrix4f m, Revive r, float pt, Vec3 cam, Vec3 left, Vec3 up) {
        float t = r.age + pt;
        Vec3 c = base(r, pt);
        float fadeIn = Mth.clamp(t / 8.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((LIFE - t) / 14.0F, 0.0F, 1.0F);
        float life = fadeIn * fadeOut;
        // 1. the pillar of light: from the sky down to the ground, then thinning away
        float down = Mth.clamp(t / TOUCH, 0.0F, 1.0F);
        double top = c.y + 48.0D;
        double bottom = c.y + 48.0D * (1.0D - down);
        float after = Mth.clamp((t - TOUCH) / 22.0F, 0.0F, 1.0F);
        float width = (1.0F - after) * (0.55F + 0.2F * Mth.sin(t * 0.9F));
        if (width > 0.01F) {
            pillar(buf, m, c, bottom, top, cam, width * 2.2F, 1.0F, 0.1F, 0.2F, 0.22F * life);
            pillar(buf, m, c, bottom, top, cam, width, 1.0F, 0.35F, 0.42F, 0.55F * life);
            pillar(buf, m, c, bottom, top, cam, width * 0.35F, 1.0F, 0.85F, 0.88F, 0.85F * life);
        }
        // 2. the hex circle on the ground
        double gy = c.y + 0.04D;
        float circle = Mth.clamp(t / 12.0F, 0.0F, 1.0F) * fadeOut;
        double grow = 0.6D + 0.4D * Mth.clamp(t / 12.0F, 0.0F, 1.0F);
        ring(buf, m, c.x, gy, c.z, 2.3D * grow, 0.09D, t * 0.05F, 1.0F, 0.12F, 0.22F, 0.8F * circle);
        ring(buf, m, c.x, gy, c.z, 1.75D * grow, 0.06D, -t * 0.08F, 1.0F, 0.3F, 0.38F, 0.7F * circle);
        hexagram(buf, m, c.x, gy, c.z, 1.7D * grow, t * 0.05F, 1.0F, 0.2F, 0.3F, 0.75F * circle);
        for (int k = 0; k < 6; k++) {
            double a = t * 0.05F + Math.PI * 2.0D * k / 6.0D + Math.PI / 6.0D;
            double rx = c.x + Math.cos(a) * 2.02D * grow;
            double rz = c.z + Math.sin(a) * 2.02D * grow;
            rune(buf, m, rx, gy + 0.01D, rz, 0.17D, a, k, 1.0F, 0.45F, 0.5F, 0.85F * circle);
        }
        // 3. the ring of force running out when the light touches
        if (t >= TOUCH && t < TOUCH + 16.0F) {
            float k = (t - TOUCH) / 16.0F;
            ring(buf, m, c.x, gy + 0.05D, c.z, 0.5D + 9.0D * k, 0.35D * (1.0F - k) + 0.05D, 0.0F, 1.0F, 0.15F, 0.25F,
                    0.85F * (1.0F - k) * (1.0F - k));
            glow(buf, m, c.add(0.0D, 1.0D, 0.0D), left, up, 2.8D * (1.0D + k), 1.0F, 0.5F, 0.55F,
                    0.6F * (1.0F - k));
        }
        // 4. the scarlet glow wrapping him, alive again
        if (t >= TOUCH) {
            float g = Mth.clamp((t - TOUCH) / 6.0F, 0.0F, 1.0F) * fadeOut;
            float pulse = 0.8F + 0.2F * Mth.sin(t * 0.5F);
            pillar(buf, m, c, c.y, c.y + 2.1D, cam, 0.75F * pulse, 1.0F, 0.12F, 0.2F, 0.18F * g);
            glow(buf, m, c.add(0.0D, 1.1D, 0.0D), left, up, 1.5D * pulse, 1.0F, 0.2F, 0.28F, 0.25F * g);
        }
    }

    /** A vertical band of light at x/z of c, always facing the camera. */
    private static void pillar(BufferBuilder buf, Matrix4f m, Vec3 c, double y0, double y1, Vec3 cam, float w,
                               float r, float g, float b, float a) {
        double dx = cam.x - c.x;
        double dz = cam.z - c.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        double sx = -dz / len * w;
        double sz = dx / len * w;
        // soft edges: bright in the middle, clear on the sides
        vert(buf, m, c.x - sx, y0, c.z - sz, r, g, b, 0.0F);
        vert(buf, m, c.x, y0, c.z, r, g, b, a);
        vert(buf, m, c.x, y1, c.z, r, g, b, a * 0.6F);
        vert(buf, m, c.x - sx, y1, c.z - sz, r, g, b, 0.0F);
        vert(buf, m, c.x, y0, c.z, r, g, b, a);
        vert(buf, m, c.x + sx, y0, c.z + sz, r, g, b, 0.0F);
        vert(buf, m, c.x + sx, y1, c.z + sz, r, g, b, 0.0F);
        vert(buf, m, c.x, y1, c.z, r, g, b, a * 0.6F);
    }

    private static void ring(BufferBuilder buf, Matrix4f m, double x, double y, double z, double radius, double w,
                             float rot, float r, float g, float b, float a) {
        int seg = 48;
        for (int j = 0; j < seg; j++) {
            double a0 = rot + Math.PI * 2.0D * j / seg;
            double a1 = rot + Math.PI * 2.0D * (j + 1) / seg;
            double ri = radius - w;
            double ro = radius + w;
            vert(buf, m, x + Math.cos(a0) * ri, y, z + Math.sin(a0) * ri, r, g, b, a * 0.3F);
            vert(buf, m, x + Math.cos(a1) * ri, y, z + Math.sin(a1) * ri, r, g, b, a * 0.3F);
            vert(buf, m, x + Math.cos(a1) * ro, y, z + Math.sin(a1) * ro, r, g, b, a);
            vert(buf, m, x + Math.cos(a0) * ro, y, z + Math.sin(a0) * ro, r, g, b, a);
        }
    }

    /** Two triangles of the hexagram, as flat glowing bars on the ground. */
    private static void hexagram(BufferBuilder buf, Matrix4f m, double x, double y, double z, double radius,
                                 float rot, float r, float g, float b, float a) {
        for (int tri = 0; tri < 2; tri++) {
            for (int k = 0; k < 3; k++) {
                double a0 = rot + tri * Math.PI / 3.0D + Math.PI * 2.0D * k / 3.0D;
                double a1 = rot + tri * Math.PI / 3.0D + Math.PI * 2.0D * (k + 1) / 3.0D;
                bar(buf, m, x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius, x + Math.cos(a1) * radius, y,
                        z + Math.sin(a1) * radius, 0.05D, r, g, b, a);
            }
        }
    }

    /** A little rune: a few strokes, a different shape for each of the six. */
    private static void rune(BufferBuilder buf, Matrix4f m, double x, double y, double z, double s, double a,
                             int kind, float r, float g, float b, float al) {
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        double[][] strokes = switch (kind % 3) {
            case 0 -> new double[][] {{0, -1, 0, 1}, {-0.7, -0.3, 0.7, 0.3}, {-0.7, 0.3, 0.7, -0.3}};
            case 1 -> new double[][] {{-0.6, -1, -0.6, 1}, {-0.6, 0, 0.6, 1}, {-0.6, 0, 0.6, -1}};
            default -> new double[][] {{0, -1, -0.7, 0.6}, {0, -1, 0.7, 0.6}, {-0.7, 0.6, 0.7, 0.6}};
        };
        for (double[] st : strokes) {
            double x0 = x + (st[0] * ca - st[1] * sa) * s;
            double z0 = z + (st[0] * sa + st[1] * ca) * s;
            double x1 = x + (st[2] * ca - st[3] * sa) * s;
            double z1 = z + (st[2] * sa + st[3] * ca) * s;
            bar(buf, m, x0, y, z0, x1, y, z1, 0.025D, r, g, b, al);
        }
    }

    private static void bar(BufferBuilder buf, Matrix4f m, double x0, double y0, double z0, double x1, double y1,
                            double z1, double w, float r, float g, float b, float a) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5D) {
            return;
        }
        double nx = -dz / len * w;
        double nz = dx / len * w;
        vert(buf, m, x0 - nx, y0, z0 - nz, r, g, b, a);
        vert(buf, m, x1 - nx, y1, z1 - nz, r, g, b, a);
        vert(buf, m, x1 + nx, y1, z1 + nz, r, g, b, a);
        vert(buf, m, x0 + nx, y0, z0 + nz, r, g, b, a);
    }

    /** A soft round glow facing the camera. */
    private static void glow(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 left, Vec3 up, double radius, float r,
                             float g, float b, float a) {
        int seg = 20;
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2.0D * j / seg;
            double a1 = Math.PI * 2.0D * (j + 1) / seg;
            Vec3 p0 = c.add(left.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(left.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            vert(buf, m, c.x, c.y, c.z, r, g, b, a);
            vert(buf, m, p0.x, p0.y, p0.z, r, g, b, 0.0F);
            vert(buf, m, p1.x, p1.y, p1.z, r, g, b, 0.0F);
            vert(buf, m, c.x, c.y, c.z, r, g, b, a);
        }
    }

    private static void vert(BufferBuilder buf, Matrix4f m, double x, double y, double z, float r, float g, float b,
                             float a) {
        buf.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, a).endVertex();
    }
}

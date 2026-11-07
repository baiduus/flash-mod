package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Cracks spreading over the ground (like the pack's GroundCracks9) for the hero landing, the charged takeoff and the
 * end of the Super Combo: jagged cracks running out from the impact with side branches, opening over half a second,
 * then fading away.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanCracks {

    private static final int LIFE = 160;
    private static final int OPEN = 10;

    private record Edge(double ax, double az, double ay, double bx, double bz, double by, float width, float delay) {
    }

    private record Mark(long start, List<Edge> edges) {
    }

    private static final List<Mark> MARKS = new ArrayList<>();

    private SupermanCracks() {
    }

    public static void add(Vec3 at, float radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || radius <= 0.0F) {
            return;
        }
        Random rnd = new Random(Double.doubleToLongBits(at.x * 31.0D + at.z * 17.0D + level.getGameTime()));
        List<Edge> edges = new ArrayList<>();
        int arms = 7 + rnd.nextInt(5);
        for (int i = 0; i < arms; i++) {
            double ang = (i + rnd.nextDouble() * 0.6D) * Math.PI * 2.0D / arms;
            double len = radius * (0.55D + rnd.nextDouble() * 0.5D);
            branch(level, rnd, edges, at.x, at.z, at.y, ang, len, 0.16F, 0.0F, 2);
        }
        MARKS.add(new Mark(level.getGameTime(), edges));
        if (MARKS.size() > 24) {
            MARKS.remove(0);
        }
    }

    private static void branch(ClientLevel level, Random rnd, List<Edge> out, double x, double z, double y, double ang,
                               double len, float width, float delay, int depth) {
        double step = 0.45D;
        int n = Math.max(2, (int) (len / step));
        for (int k = 0; k < n; k++) {
            ang += (rnd.nextDouble() - 0.5D) * 0.7D;
            double nx = x + Math.cos(ang) * step;
            double nz = z + Math.sin(ang) * step;
            double ny = groundY(level, nx, y, nz);
            if (Double.isNaN(ny)) {
                return;
            }
            float w = width * (1.0F - 0.75F * k / n);
            out.add(new Edge(x, z, groundY(level, x, y, z), nx, nz, ny, w, delay + k * 0.06F));
            if (depth > 0 && rnd.nextFloat() < 0.18F) {
                branch(level, rnd, out, nx, nz, ny, ang + (rnd.nextBoolean() ? 1 : -1) * (0.5D + rnd.nextDouble() * 0.6D),
                        len * 0.4D, w * 0.7F, delay + k * 0.06F, depth - 1);
            }
            x = nx;
            z = nz;
            y = ny;
        }
    }

    /** Top of the ground near (x, y, z), or NaN over a gap. */
    private static double groundY(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int by = (int) Math.floor(y + 0.5D);
        for (int dy = 1; dy >= -3; dy--) {
            pos.set(Math.floor(x), by + dy - 1, Math.floor(z));
            boolean solid = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
            pos.move(0, 1, 0);
            boolean free = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
            if (solid && free) {
                return by + dy + 0.01D;
            }
        }
        return Double.NaN;
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
        MARKS.removeIf(mk -> time - mk.start() > LIFE);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Mark mk : MARKS) {
            float age = time - mk.start();
            float fade = age > LIFE - 40 ? (LIFE - age) / 40.0F : 1.0F;
            for (Edge e : mk.edges()) {
                float open = Math.max(0.0F, Math.min(1.0F, (age / OPEN - e.delay()) * 2.0F));
                if (open <= 0.0F) {
                    continue;
                }
                double bx = e.ax() + (e.bx() - e.ax()) * open;
                double bz = e.az() + (e.bz() - e.az()) * open;
                double by = e.ay() + (e.by() - e.ay()) * open;
                double dx = bx - e.ax();
                double dz = bz - e.az();
                double l = Math.sqrt(dx * dx + dz * dz);
                if (l < 1.0E-4D) {
                    continue;
                }
                double sx = -dz / l * e.width();
                double sz = dx / l * e.width();
                float a = 0.85F * fade;
                // dark crack with a lighter, dusty edge
                quad(b, m, e.ax(), e.ay(), e.az(), bx, by, bz, sx * 1.8D, sz * 1.8D, 0.35F, 0.31F, 0.27F, a * 0.35F);
                quad(b, m, e.ax(), e.ay(), e.az(), bx, by, bz, sx, sz, 0.05F, 0.045F, 0.04F, a);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void quad(BufferBuilder b, Matrix4f m, double ax, double ay, double az, double bx, double by,
                             double bz, double sx, double sz, float r, float g, float bl, float a) {
        b.vertex(m, (float) (ax - sx), (float) ay, (float) (az - sz)).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (ax + sx), (float) ay, (float) (az + sz)).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (bx + sx), (float) by, (float) (bz + sz)).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (bx - sx), (float) by, (float) (bz - sz)).color(r, g, bl, a).endVertex();
    }
}

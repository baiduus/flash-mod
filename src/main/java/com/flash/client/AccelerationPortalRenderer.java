package com.flash.client;

import com.flash.FlashMod;
import com.flash.block.AccelerationPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Acceleration Force portal, version 2 (made from scratch).
 *
 * <p>A wormhole seen from the front, built from soft procedural textures
 * (tools/gen_portal_v2.py) that are tinted per force:</p>
 * <ol>
 *   <li>a dark void disc that gives the hole its depth and contrast;</li>
 *   <li>a tunnel: six glowing rings stacked behind the portal, smaller and dimmer
 *       the deeper they are, turning at different speeds (real parallax when you
 *       walk around it), ending in a white-hot core;</li>
 *   <li>two counter-rotating spiral layers (additive light);</li>
 *   <li>the rim: a solid ring of light with flame tongues, a second additive
 *       ring and crackling lightning that runs around it;</li>
 *   <li>embers spiralling into the centre, a soft halo and light on the ground.</li>
 * </ol>
 * Positive force = gold / orange / white-hot, negative = crimson. Opening and
 * closing grow and shrink it smoothly.
 */
public final class AccelerationPortalRenderer
        implements BlockEntityRenderer<AccelerationPortalBlockEntity> {

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(FlashMod.MODID, "textures/effect/" + name + ".png");
    }

    private static final RenderType VOID = RenderType.entityTranslucentEmissive(tex("portal2_void"));
    private static final RenderType RIM_SOLID = RenderType.entityTranslucentEmissive(tex("portal2_rim"));
    private static final RenderType TUNNEL = RenderType.eyes(tex("portal2_tunnel"));
    private static final RenderType CORE = RenderType.eyes(tex("portal2_core"));
    private static final RenderType SWIRL = RenderType.eyes(tex("portal2_swirl"));
    private static final RenderType RIM_GLOW = RenderType.eyes(tex("portal2_rim_add"));
    private static final RenderType HALO = RenderType.eyes(tex("portal2_halo"));
    private static final RenderType SPARK = RenderType.eyes(tex("portal2_spark"));

    /** Colours of one force: hot (white-hot), mid (main), deep (outer / far tunnel), dark (void). */
    private record Palette(float[] hot, float[] mid, float[] deep, float[] dark,
                           float[] boltGlow, float[] boltMid, float[] boltCore) {
    }

    private static final Palette POSITIVE = new Palette(
            new float[] {1.00F, 0.96F, 0.80F}, new float[] {1.00F, 0.62F, 0.10F},
            new float[] {0.95F, 0.30F, 0.02F}, new float[] {0.07F, 0.03F, 0.00F},
            new float[] {1.0F, 0.50F, 0.03F, 0.26F}, new float[] {1.0F, 0.80F, 0.18F, 0.70F},
            new float[] {1.0F, 0.98F, 0.80F, 0.98F});
    private static final Palette NEGATIVE = new Palette(
            new float[] {1.00F, 0.86F, 0.80F}, new float[] {1.00F, 0.16F, 0.12F},
            new float[] {0.55F, 0.02F, 0.05F}, new float[] {0.06F, 0.00F, 0.01F},
            new float[] {0.85F, 0.04F, 0.05F, 0.28F}, new float[] {1.0F, 0.20F, 0.14F, 0.72F},
            new float[] {1.0F, 0.86F, 0.82F, 0.98F});

    public AccelerationPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AccelerationPortalBlockEntity portal, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (portal.owner() == null || portal.getLevel() == null) {
            return;
        }
        Palette pal = portal.isNegative() ? NEGATIVE : POSITIVE;
        float ticks = portal.getLevel().getGameTime() + partialTick;
        float t = ticks / 20.0F;
        float age = portal.createdAt() > 0L ? ticks - portal.createdAt() : 100.0F;
        float open = smooth(Mth.clamp(age / 20.0F, 0.0F, 1.0F));
        float close = smooth(Mth.clamp((AccelerationPortalBlockEntity.LIFETIME_TICKS - age) / 16.0F, 0.0F, 1.0F));
        float vis = open * close;
        if (vis <= 0.01F) {
            return;
        }
        float radius = (float) portal.radius();
        float pulse = 0.5F + 0.5F * Mth.sin(t * 2.3F);
        float scale = 0.25F + 0.75F * vis;
        // the whole thing spins up while opening
        float spin = t + (1.0F - open) * 2.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-portal.yaw()));

        // camera in the portal frame: which side it is on (the tunnel goes away from it)
        Vec3 camWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f cam = new Vector3f(
                (float) (camWorld.x - (portal.getBlockPos().getX() + 0.5D)),
                (float) (camWorld.y - (portal.getBlockPos().getY() + 0.5D)),
                (float) (camWorld.z - (portal.getBlockPos().getZ() + 0.5D)));
        cam.rotateY((float) Math.toRadians(portal.yaw()));
        float away = cam.z >= 0.0F ? -1.0F : 1.0F;

        drawGround(poseStack, buffers, portal, radius, vis, pulse, t, pal);

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        Vec3 camera = new Vec3(cam.x / scale, cam.y / scale, cam.z / scale);
        float r = radius * 1.28F; // texture radius: the rim (0.80 of the texture) sits at ~radius

        // 1. void
        disc(poseStack, buffers.getBuffer(VOID), r * 0.98F, 0.0F, away * 0.02F, pal.dark(), vis, false);
        // 2. tunnel rings going away from the viewer, then the core at the far end
        VertexConsumer tunnel = buffers.getBuffer(TUNNEL);
        for (int i = 0; i < 6; i++) {
            float k = i / 5.0F;
            float depth = away * (0.18F + 0.42F * i);
            float size = r * (0.94F - 0.12F * i);
            float[] color = i < 3 ? pal.deep() : pal.mid();
            float rot = spin * (25.0F + 16.0F * i) * (i % 2 == 0 ? 1.0F : -1.0F);
            disc(poseStack, tunnel, size, rot, depth, color, vis * (0.62F - 0.36F * k) * (0.85F + 0.15F * pulse));
        }
        disc(poseStack, buffers.getBuffer(CORE), r * 0.52F, 0.0F, away * 2.4F, pal.hot(),
                vis * (0.85F + 0.15F * pulse));
        // 3. spiral light
        VertexConsumer swirl = buffers.getBuffer(SWIRL);
        disc(poseStack, swirl, r * 0.82F, spin * 52.0F, away * 0.05F, pal.mid(), vis * 0.85F);
        disc(poseStack, swirl, r * 0.64F, -spin * 80.0F + 57.0F, away * 0.10F, pal.hot(), vis * 0.45F);
        // 4. rim: solid colour first (stays visible against a bright sky), then additive light
        disc(poseStack, buffers.getBuffer(RIM_SOLID), r, spin * 20.0F, 0.0F, pal.mid(), vis * 0.95F, false);
        VertexConsumer rimGlow = buffers.getBuffer(RIM_GLOW);
        disc(poseStack, rimGlow, r, spin * 20.0F, -away * 0.01F, pal.mid(), vis * 0.45F);
        disc(poseStack, rimGlow, r * 0.975F, -spin * 31.0F + 115.0F, -away * 0.02F, pal.hot(),
                vis * (0.22F + 0.12F * pulse));
        disc(poseStack, buffers.getBuffer(HALO), r * 1.30F, 0.0F, away * 0.03F, pal.deep(),
                vis * (0.45F + 0.15F * pulse));
        // 5. embers spiralling in
        drawEmbers(poseStack, buffers.getBuffer(SPARK), portal.seed(), radius, t, vis, away, pal);
        // 6. lightning crackling around the rim
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer ribbons = buffers.getBuffer(FlashLightningRibbon.RENDER_TYPE);
        drawRimArcs(ribbons, matrix, camera, portal.seed(), radius, ticks, vis, away, pal);
        drawDischarges(ribbons, matrix, camera, portal.seed(), radius, ticks, vis, pal);

        poseStack.popPose();
        poseStack.popPose();

        if (buffers instanceof MultiBufferSource.BufferSource source) {
            // back to front: void, tunnel, core, spiral, rim, halo, embers, lightning
            source.endBatch(VOID);
            source.endBatch(TUNNEL);
            source.endBatch(CORE);
            source.endBatch(SWIRL);
            source.endBatch(RIM_SOLID);
            source.endBatch(RIM_GLOW);
            source.endBatch(HALO);
            source.endBatch(SPARK);
            source.endBatch(FlashLightningRibbon.RENDER_TYPE);
        }
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

    private static void drawGround(PoseStack poseStack, MultiBufferSource buffers,
                                   AccelerationPortalBlockEntity portal, float radius,
                                   float vis, float pulse, float t, Palette pal) {
        poseStack.pushPose();
        poseStack.translate(0.0D, -portal.groundOffset() + 0.03D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        disc(poseStack, buffers.getBuffer(HALO), radius * 1.5F, t * 12.0F, 0.0F, pal.mid(),
                vis * (0.55F + 0.15F * pulse));
        poseStack.popPose();
    }

    /** Glowing embers drifting along a spiral into the centre. */
    private static void drawEmbers(PoseStack poseStack, VertexConsumer consumer, int seed,
                                   float radius, float t, float vis, float away, Palette pal) {
        Matrix4f m = poseStack.last().pose();
        for (int i = 0; i < 28; i++) {
            RandomSource random = RandomSource.create(seed * 53L + i * 7907L);
            float speed = 0.22F + random.nextFloat() * 0.30F;
            float life = (t * speed + random.nextFloat()) % 1.0F;
            float angle = random.nextFloat() * Mth.TWO_PI + life * 3.4F + t * 0.6F;
            float dist = radius * (1.05F - 0.95F * life);
            float x = Mth.cos(angle) * dist;
            float y = Mth.sin(angle) * dist;
            float z = away * (life * 1.6F) - away * 0.05F;
            float size = radius * (0.035F + random.nextFloat() * 0.03F) * (1.0F - 0.5F * life);
            float alpha = vis * Mth.sin(life * Mth.PI) * 0.95F;
            float[] c = random.nextFloat() < 0.35F ? pal.hot() : pal.mid();
            quadBoth(consumer, m, x - size, y - size, x + size, y + size, z, c, alpha, true);
        }
    }

    /** Short bright arcs running round the rim, each one alive for a few ticks. */
    private static void drawRimArcs(VertexConsumer consumer, Matrix4f matrix, Vec3 camera, int seed,
                                    float radius, float ticks, float vis, float away, Palette pal) {
        for (int j = 0; j < 9; j++) {
            int period = 5 + (j * 3) % 5;
            long cycle = (long) Math.floor((ticks + j * 2) / period);
            float frac = ((ticks + j * 2) % period) / period;
            float alpha = vis * Mth.clamp(Mth.sin(frac * Mth.PI) * 1.8F, 0.0F, 1.0F);
            if (alpha < 0.03F) {
                continue;
            }
            RandomSource random = RandomSource.create(seed * 977L + j * 131L + cycle * 7919L);
            double start = random.nextDouble() * Math.PI * 2.0D;
            double span = 0.5D + random.nextDouble() * 0.9D;
            List<Vec3> points = new ArrayList<>(14);
            for (int k = 0; k <= 13; k++) {
                double a = start + span * k / 13.0D;
                double rr = radius * (1.0D + (random.nextDouble() - 0.5D) * 0.07D);
                points.add(new Vec3(Math.cos(a) * rr, Math.sin(a) * rr,
                        -away * 0.04D + (random.nextDouble() - 0.5D) * 0.06D));
            }
            FlashLightningRibbon.drawArc(consumer, matrix, camera, points,
                    0.07F, 0.028F, 0.010F, pal.boltGlow(), pal.boltMid(), pal.boltCore(), alpha);
        }
    }

    /** A few forked bolts that leap out of the rim now and then. */
    private static void drawDischarges(VertexConsumer consumer, Matrix4f matrix, Vec3 camera, int seed,
                                       float radius, float ticks, float vis, Palette pal) {
        for (int j = 0; j < 5; j++) {
            int period = 9 + (j * 5) % 8;
            long cycle = (long) Math.floor((ticks + j * 7) / period);
            float frac = ((ticks + j * 7) % period) / period;
            if (frac > 0.45F) {
                continue; // visible for a short flash only
            }
            float alpha = vis * Mth.sin(frac / 0.45F * Mth.PI);
            RandomSource random = RandomSource.create(seed * 389L + j * 2027L + cycle * 104729L);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 start = new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
            fork(consumer, matrix, camera, start, angle + (random.nextDouble() - 0.5D) * 0.8D,
                    radius * (0.45D + random.nextDouble() * 0.55D), 8, random, alpha, 1, pal);
        }
    }

    private static void fork(VertexConsumer consumer, Matrix4f matrix, Vec3 camera, Vec3 start,
                             double direction, double length, int segments, RandomSource random,
                             float alpha, int depth, Palette pal) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        points.add(start);
        double step = length / segments;
        Vec3 current = start;
        double dir = direction;
        for (int k = 1; k <= segments; k++) {
            dir += (random.nextDouble() - 0.5D) * 0.8D;
            current = current.add(Math.cos(dir) * step, Math.sin(dir) * step,
                    (random.nextDouble() - 0.5D) * 0.10D);
            points.add(current);
        }
        float w = depth > 0 ? 1.0F : 0.6F;
        FlashLightningRibbon.drawArc(consumer, matrix, camera, points,
                0.065F * w, 0.025F * w, 0.009F * w, pal.boltGlow(), pal.boltMid(), pal.boltCore(), alpha);
        if (depth > 0) {
            int at = 2 + random.nextInt(Math.max(1, segments - 3));
            fork(consumer, matrix, camera, points.get(at),
                    dir + (random.nextBoolean() ? 0.7D : -0.7D), length * 0.4D,
                    Math.max(3, segments / 2), random, alpha * 0.85F, depth - 1, pal);
        }
    }

    // ------------------------------------------------------------------
    // Geometry helpers
    // ------------------------------------------------------------------

    /** Square textured quad of half-size {@code half}, turned by {@code degrees}, at depth {@code z}. */
    private static void disc(PoseStack poseStack, VertexConsumer consumer, float half, float degrees,
                             float z, float[] color, float alpha) {
        disc(poseStack, consumer, half, degrees, z, color, alpha, true);
    }

    /**
     * @param additive additive layers ignore alpha, so the colour is scaled by it instead
     */
    private static void disc(PoseStack poseStack, VertexConsumer consumer, float half, float degrees,
                             float z, float[] color, float alpha, boolean additive) {
        if (alpha <= 0.005F) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(degrees));
        quadBoth(consumer, poseStack.last().pose(), -half, -half, half, half, z, color, alpha, additive);
        poseStack.popPose();
    }

    /** Both windings, so the quad shows from either side (some render types cull back faces). */
    private static void quadBoth(VertexConsumer c, Matrix4f m, float x0, float y0, float x1, float y1,
                                 float z, float[] color, float alpha, boolean additive) {
        float a = Mth.clamp(alpha, 0.0F, 1.0F);
        float k = additive ? a : 1.0F;
        float r = color[0] * k, g = color[1] * k, b = color[2] * k;
        vertex(c, m, x0, y0, z, r, g, b, a, 0.0F, 1.0F, 1.0F);
        vertex(c, m, x1, y0, z, r, g, b, a, 1.0F, 1.0F, 1.0F);
        vertex(c, m, x1, y1, z, r, g, b, a, 1.0F, 0.0F, 1.0F);
        vertex(c, m, x0, y1, z, r, g, b, a, 0.0F, 0.0F, 1.0F);
        vertex(c, m, x0, y1, z, r, g, b, a, 0.0F, 0.0F, -1.0F);
        vertex(c, m, x1, y1, z, r, g, b, a, 1.0F, 0.0F, -1.0F);
        vertex(c, m, x1, y0, z, r, g, b, a, 1.0F, 1.0F, -1.0F);
        vertex(c, m, x0, y0, z, r, g, b, a, 0.0F, 1.0F, -1.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float red, float green, float blue, float alpha,
                               float u, float v, float nz) {
        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, nz)
                .endVertex();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(AccelerationPortalBlockEntity portal) {
        return true;
    }
}

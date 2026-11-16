package com.flash.client.infinity;

import com.flash.client.VfxDraw;
import com.flash.infinity.entity.BlackHole;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The black hole: a sphere of nothing, its accretion disc of violet and green light turning round it. */
public class BlackHoleRenderer extends EntityRenderer<BlackHole> {

    private static final ResourceLocation BLACK = new ResourceLocation("minecraft",
            "textures/block/black_concrete.png");

    public BlackHoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BlackHole e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float r = e.size(pt) / 40.0F;
        if (r <= 0.02F) {
            return;
        }
        float t = e.tickCount + pt;
        // the sphere: black, unlit
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(BLACK));
        Matrix4f m = pose.last().pose();
        int lat = 10;
        int lon = 16;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                Vec3 a = pt(i, j, lat, lon, r);
                Vec3 b = pt(i, j + 1, lat, lon, r);
                Vec3 c = pt(i + 1, j + 1, lat, lon, r);
                Vec3 d = pt(i + 1, j, lat, lon, r);
                v(vc, m, pose, a);
                v(vc, m, pose, d);
                v(vc, m, pose, c);
                v(vc, m, pose, b);
            }
        }
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camRel = cam.getPosition().subtract(e.getPosition(pt));
        BufferBuilder buf = VfxDraw.begin();
        try {
            // the accretion disc: hot bands from white to orange to deep red, the photon ring, two jets
            Vec3 up = new Vec3(0.15D, 1.0D, 0.08D).normalize();
            float[][] bands = {{1.0F, 0.95F, 0.8F}, {1.0F, 0.7F, 0.25F}, {1.0F, 0.45F, 0.1F}, {0.8F, 0.2F, 0.08F},
                    {0.5F, 0.1F, 0.15F}};
            for (int k = 0; k < bands.length; k++) {
                VfxDraw.ring(buf, m, Vec3.ZERO, up, r * (1.35D + k * 0.38D), r * 0.22D, t * (0.22F - k * 0.035F),
                        bands[k][0], bands[k][1], bands[k][2], 0.85F - k * 0.12F);
            }
            VfxDraw.sphere(buf, m, Vec3.ZERO, r * 1.08D, camRel, 0.75F, 0.85F, 1.0F, 0.55F, t, 4.0D);
            Vec3 l = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector());
            Vec3 u = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector());
            VfxDraw.glow(buf, m, Vec3.ZERO, l, u, r * 4.0D, 1.0F, 0.55F, 0.2F, 0.18F);
            float jet = 0.6F + 0.2F * (float) Math.sin(t * 0.4F);
            VfxDraw.energy(buf, m, up.scale(r), up.scale(r * 6.0D), (float) (r * 0.18D), 0.6F, 0.75F, 1.0F, jet);
            VfxDraw.energy(buf, m, up.scale(-r), up.scale(-r * 6.0D), (float) (r * 0.18D), 0.6F, 0.75F, 1.0F, jet);
        } finally {
            VfxDraw.end(buf);
        }
    }

    private static Vec3 pt(int i, int j, int lat, int lon, double r) {
        double phi = Math.PI * i / lat;
        double th = Math.PI * 2 * j / lon;
        return new Vec3(Math.sin(phi) * Math.cos(th) * r, Math.cos(phi) * r, Math.sin(phi) * Math.sin(th) * r);
    }

    private static void v(VertexConsumer vc, Matrix4f m, PoseStack pose, Vec3 p) {
        Vec3 n = p.normalize();
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(0, 0, 0, 255).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0).normal(pose.last().normal(), (float) n.x,
                        (float) n.y, (float) n.z).endVertex();
    }

    @Override
    public boolean shouldRender(BlackHole e, net.minecraft.client.renderer.culling.Frustum f, double x, double y,
                                double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHole e) {
        return BLACK;
    }
}

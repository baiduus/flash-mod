package com.flash.client.infinity;

import com.flash.client.VfxDraw;
import com.flash.infinity.entity.SpacePortal;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A Space Stone portal: an oval of blue light, its rim of sparks turning, growing as it opens and shrinking as it
 * closes (the fire style: an orange spinning ring, Doctor Strange's).
 */
public class SpacePortalRenderer extends EntityRenderer<SpacePortal> {

    public SpacePortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SpacePortal e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = e.openness(pt);
        if (open <= 0.01F) {
            return;
        }
        int c = e.color();
        float r = ((c >> 16) & 255) / 255.0F;
        float g = ((c >> 8) & 255) / 255.0F;
        float b = (c & 255) / 255.0F;
        if (e.fire()) {
            r = 1.0F;
            g = 0.55F;
            b = 0.1F;
        }
        float t = e.tickCount + pt;
        Vec3 n = e.normal();
        Vec3 right = new Vec3(n.z, 0.0D, -n.x).scale(0.8D * open);
        Vec3 up = new Vec3(0.0D, 1.25D * open, 0.0D);
        Vec3 c0 = new Vec3(0.0D, 1.25D, 0.0D);
        Matrix4f m = pose.last().pose();
        BufferBuilder buf = VfxDraw.begin();
        try {
            // a vortex: a deep centre, arms of light winding into it, rims turning against each other
            VfxDraw.disc(buf, m, c0, right, up, 1.0D, r, g, b, r * 0.15F, g * 0.15F, b * 0.25F + 0.05F, 0.9F);
            for (int arm = 0; arm < 4; arm++) {
                Vec3[] sp = new Vec3[24];
                for (int i = 0; i < sp.length; i++) {
                    double f = i / (double) (sp.length - 1);
                    double a = t * 0.18D + arm * Math.PI / 2.0D + f * Math.PI * 2.2D;
                    double rad = 1.0D - f * 0.9D;
                    sp[i] = c0.add(right.scale(Math.cos(a) * rad)).add(up.scale(Math.sin(a) * rad));
                }
                VfxDraw.path(buf, m, sp, 0.06F, Math.min(1.0F, r + 0.3F), Math.min(1.0F, g + 0.3F),
                        Math.min(1.0F, b + 0.2F), 0.75F);
            }
            for (int ringI = 0; ringI < 3; ringI++) {
                int seg = 48;
                Vec3[] rim = new Vec3[seg + 1];
                double sc = 1.0D + ringI * 0.07D;
                double wob = Math.sin(t * 0.2D + ringI) * 0.02D;
                for (int i = 0; i <= seg; i++) {
                    double a = i / (double) seg * Math.PI * 2.0D;
                    rim[i] = c0.add(right.scale(Math.cos(a) * (sc + wob))).add(up.scale(Math.sin(a) * (sc + wob)));
                }
                VfxDraw.path(buf, m, rim, ringI == 0 ? 0.1F : 0.04F, r, g, b, ringI == 0 ? 1.0F : 0.6F);
            }
            for (int k = 0; k < 10; k++) {
                double a = t * (k % 2 == 0 ? 0.14D : -0.1D) + k * Math.PI / 5.0D;
                Vec3 s = c0.add(right.scale(Math.cos(a) * 1.12D)).add(up.scale(Math.sin(a) * 1.12D));
                VfxDraw.glow(buf, m, s, right.normalize(), new Vec3(0.0D, 1.0D, 0.0D), 0.14D, 1.0F, 1.0F, 1.0F, 0.85F);
            }
            VfxDraw.glow(buf, m, c0, right.normalize(), new Vec3(0.0D, 1.0D, 0.0D), 2.2D * open, r, g, b, 0.25F);
        } finally {
            VfxDraw.end(buf);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SpacePortal e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

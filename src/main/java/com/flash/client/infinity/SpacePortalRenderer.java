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
            VfxDraw.disc(buf, m, c0, right, up, 1.0D, r, g, b, r * 0.4F + 0.3F, g * 0.4F + 0.3F, b * 0.4F + 0.4F,
                    0.85F);
            int seg = 40;
            Vec3[] rim = new Vec3[seg + 1];
            for (int i = 0; i <= seg; i++) {
                double a = i / (double) seg * Math.PI * 2.0D;
                rim[i] = c0.add(right.scale(Math.cos(a))).add(up.scale(Math.sin(a)));
            }
            VfxDraw.path(buf, m, rim, 0.09F, r, g, b, 1.0F);
            for (int k = 0; k < 6; k++) {
                double a = t * 0.12D + k * Math.PI / 3.0D;
                Vec3 s = c0.add(right.scale(Math.cos(a) * 1.05D)).add(up.scale(Math.sin(a) * 1.05D));
                VfxDraw.glow(buf, m, s, right.normalize(), new Vec3(0.0D, 1.0D, 0.0D), 0.18D, 1.0F, 1.0F, 1.0F, 0.8F);
            }
        } finally {
            VfxDraw.end(buf);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SpacePortal e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

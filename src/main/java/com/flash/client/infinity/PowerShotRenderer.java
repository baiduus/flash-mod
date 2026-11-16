package com.flash.client.infinity;

import com.flash.client.VfxDraw;
import com.flash.infinity.entity.PowerShot;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** The Power Stone's blasts and rockets: a ball of its light, a trail behind. */
public class PowerShotRenderer extends EntityRenderer<PowerShot> {

    public PowerShotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PowerShot e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        int c = e.color();
        float r = ((c >> 16) & 255) / 255.0F;
        float g = ((c >> 8) & 255) / 255.0F;
        float b = (c & 255) / 255.0F;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 left = new Vec3(cam.getLeftVector());
        Vec3 up = new Vec3(cam.getUpVector());
        Vec3 back = e.getDeltaMovement().normalize().scale(-1.2D);
        BufferBuilder buf = VfxDraw.begin();
        try {
            if (!e.rocket()) {
                // the Energy Blast: a pulsing violet star, its rays turning, a twisting double trail
                var m = pose.last().pose();
                float t = e.tickCount + pt;
                float pulse = 0.85F + 0.15F * (float) Math.sin(t * 0.8F);
                VfxDraw.glow(buf, m, Vec3.ZERO, left, up, 1.1D * pulse, 0.55F, 0.1F, 0.9F, 0.45F);
                VfxDraw.glow(buf, m, Vec3.ZERO, left, up, 0.6D * pulse, 0.85F, 0.35F, 1.0F, 0.9F);
                VfxDraw.glow(buf, m, Vec3.ZERO, left, up, 0.25D, 1.0F, 1.0F, 1.0F, 1.0F);
                VfxDraw.burst(buf, m, Vec3.ZERO, left, up, 0.9F * pulse, 0.8F, 0.4F, 1.0F, 0.7F, t * 0.15F);
                Vec3 dir = e.getDeltaMovement().lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D)
                        : e.getDeltaMovement().normalize();
                Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 up2 = side.cross(dir).normalize();
                Vec3 prev1 = Vec3.ZERO;
                Vec3 prev2 = Vec3.ZERO;
                for (int i = 1; i <= 10; i++) {
                    double d = i * 0.28D;
                    double ang = t * 0.6D + i * 0.7D;
                    double rad = 0.22D * (1.0D - i / 12.0D);
                    Vec3 base = dir.scale(-d);
                    Vec3 p1 = base.add(side.scale(Math.cos(ang) * rad)).add(up2.scale(Math.sin(ang) * rad));
                    Vec3 p2 = base.add(side.scale(-Math.cos(ang) * rad)).add(up2.scale(-Math.sin(ang) * rad));
                    float fade = 1.0F - i / 11.0F;
                    VfxDraw.energy(buf, m, prev1, p1, 0.07F * fade, 0.8F, 0.35F, 1.0F, 0.9F * fade);
                    VfxDraw.energy(buf, m, prev2, p2, 0.07F * fade, 1.0F, 0.6F, 1.0F, 0.9F * fade);
                    prev1 = p1;
                    prev2 = p2;
                }
                return;
            }
            VfxDraw.glow(buf, pose.last().pose(), Vec3.ZERO, left, up, e.rocket() ? 0.3D : 0.5D, r, g, b, 1.0F);
            VfxDraw.glow(buf, pose.last().pose(), Vec3.ZERO, left, up, 0.15D, 1.0F, 1.0F, 1.0F, 1.0F);
            VfxDraw.energy(buf, pose.last().pose(), back, Vec3.ZERO, e.rocket() ? 0.08F : 0.15F, r, g, b, 0.8F);
        } finally {
            VfxDraw.end(buf);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PowerShot e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

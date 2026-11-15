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

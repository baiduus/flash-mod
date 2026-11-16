package com.flash.client.infinity;

import com.flash.infinity.TempleBeacon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** The Temple Beacon's beam: a beacon's, always on. */
public class TempleBeaconRenderer implements BlockEntityRenderer<TempleBeacon.Beam> {

    public TempleBeaconRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(TempleBeacon.Beam b, float partial, PoseStack pose, MultiBufferSource buf, int light,
                       int overlay) {
        long time = b.getLevel() == null ? 0L : b.getLevel().getGameTime();
        int y = 0;
        for (TempleBeacon.Section s : b.sections) {
            BeaconRenderer.renderBeaconBeam(pose, buf, BeaconRenderer.BEAM_LOCATION, partial, 1.0F, time, y,
                    s.height(), s.color(), 0.2F, 0.25F);
            y += s.height();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TempleBeacon.Beam b) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}

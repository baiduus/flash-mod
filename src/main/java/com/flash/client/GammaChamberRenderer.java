package com.flash.client;

import com.flash.FlashMod;
import com.flash.block.GammaChamberBlock;
import com.flash.block.GammaChamberBlockEntity;
import com.flash.client.geo.GeoAnimation;
import com.flash.client.geo.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gamma chamber drawn from its geo model (tools/gen_geo_machines.py): metal frame,
 * green glass tube, glowing rings that travel along the tube, the gamma beam and
 * the capsule on top. Animations: idle, loaded (capsule inside) and active.
 */
public class GammaChamberRenderer implements BlockEntityRenderer<GammaChamberBlockEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID, "textures/entity/gamma_chamber.png");
    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/gamma_chamber.geo.json");
    private static final ResourceLocation ANIMS = new ResourceLocation(FlashMod.MODID, "animations/gamma_chamber.animation.json");
    private static GeoModel model;
    private static Map<String, GeoAnimation> anims;

    public GammaChamberRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void reset() {
        model = null;
        anims = null;
    }

    @Override
    public void render(GammaChamberBlockEntity chamber, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (model == null) {
            model = GeoModel.load(GEO, null, true);
            anims = GeoAnimation.load(ANIMS);
        }
        BlockState state = chamber.getBlockState();
        boolean loaded = state.hasProperty(GammaChamberBlock.LOADED) && state.getValue(GammaChamberBlock.LOADED);
        boolean active = state.hasProperty(GammaChamberBlock.ACTIVE) && state.getValue(GammaChamberBlock.ACTIVE);
        float time = (chamber.getLevel() != null ? chamber.getLevel().getGameTime() : 0L) + partialTick;
        model.reset();
        GeoAnimation a = anims.get(active ? "active" : loaded ? "loaded" : "idle");
        if (a != null) {
            a.apply(model, time / 20.0F);
        }
        GeoModel.Bone capsule = model.bone("glow_capsule");
        if (capsule != null) {
            capsule.visible = loaded || active;
        }
        pose.pushPose();
        // model space: y down with the feet at 24 px
        pose.translate(0.5D, 1.5D, 0.5D);
        pose.scale(-1.0F, -1.0F, 1.0F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay, 0,
                1.0F, 1.0F, 1.0F, 1.0F);
        float glow = active ? 1.0F : loaded ? 0.85F : 0.6F;
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 0xF000F0, overlay, 1,
                glow, glow, glow, 1.0F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GammaChamberBlockEntity chamber) {
        return true;
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoAnimation;
import com.flash.client.geo.GeoModel;
import com.flash.ultron.SurgeryTableBlock;
import com.flash.ultron.SurgeryTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The surgery table (geo/surgery_table.geo.json, tools/gen_ultron.py), drawn from its head block over both blocks:
 * the claws folded at rest, working over the patient's chest while it operates (the laser on).
 */
public class SurgeryTableRenderer implements BlockEntityRenderer<SurgeryTableBlockEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID,
            "textures/entity/surgery_table.png");
    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID, "geo/surgery_table.geo.json");
    private static final ResourceLocation ANIMS = new ResourceLocation(FlashMod.MODID,
            "animations/surgery_table.animation.json");
    private static GeoModel model;
    private static Map<String, GeoAnimation> anims;

    public SurgeryTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void reset() {
        model = null;
        anims = null;
    }

    @Override
    public void render(SurgeryTableBlockEntity table, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (model == null) {
            model = GeoModel.load(GEO, null, true);
            anims = GeoAnimation.load(ANIMS);
        }
        BlockState state = table.getBlockState();
        if (!state.hasProperty(SurgeryTableBlock.FACING)) {
            return;
        }
        boolean operating = state.getValue(SurgeryTableBlock.OPERATING);
        float time = (table.getLevel() != null ? table.getLevel().getGameTime() : 0L) + partialTick;
        model.reset();
        GeoAnimation a = anims.get(operating ? "operate" : "idle");
        if (a != null) {
            a.apply(model, time / 20.0F);
        }
        float yaw = switch (state.getValue(SurgeryTableBlock.FACING)) {
            case WEST -> 270.0F;
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            default -> 0.0F;
        };
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(0.0D, 1.5D, 0.0D);
        pose.scale(-1.0F, -1.0F, 1.0F);
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, 0,
                1.0F, 1.0F, 1.0F, 1.0F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 0xF000F0, overlay, 1,
                1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SurgeryTableBlockEntity table) {
        return true;
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.block.TransformationChamberBlock;
import com.flash.block.TransformationChamberBlockEntity;
import com.flash.client.geo.GeoAnimation;
import com.flash.client.geo.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Transformation Chamber v10 drawn from its geo model (tools/gen_machines_v3.py): a Speed Force capsule on a
 * dodecagonal platform, red shell open at the front, two accelerator rings that sweep down around the occupant
 * while it runs, capacitor towers at the back and the side port of the potion container. Solid parts, glow and
 * glass in three passes. Animations: idle and active.
 */
public class TransformationChamberRenderer implements BlockEntityRenderer<TransformationChamberBlockEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(FlashMod.MODID,
            "textures/entity/transformation_chamber.png");
    private static final ResourceLocation GEO = new ResourceLocation(FlashMod.MODID,
            "geo/transformation_chamber.geo.json");
    private static final ResourceLocation ANIMS = new ResourceLocation(FlashMod.MODID,
            "animations/transformation_chamber.animation.json");
    private static GeoModel model;
    private static Map<String, GeoAnimation> anims;

    public TransformationChamberRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void reset() {
        model = null;
        anims = null;
    }

    @Override
    public void render(TransformationChamberBlockEntity chamber, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (model == null) {
            model = GeoModel.load(GEO, null, true);
            anims = GeoAnimation.load(ANIMS);
        }
        BlockState state = chamber.getBlockState();
        if (!state.hasProperty(TransformationChamberBlock.FACING)) {
            return;
        }
        boolean active = state.getValue(TransformationChamberBlock.ACTIVE);
        Direction facing = state.getValue(TransformationChamberBlock.FACING);
        float time = (chamber.getLevel() != null ? chamber.getLevel().getGameTime() : 0L) + partialTick;
        model.reset();
        GeoAnimation a = anims.get(active ? "active" : "idle");
        if (a != null) {
            a.apply(model, time / 20.0F);
        }
        pose.pushPose();
        // the machine's frame (facing south) turned like the blockstate turns it, about the controller cell
        float yaw = switch (facing) {
            case WEST -> 270.0F;
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            default -> 0.0F;
        };
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5D, 0.0D, -0.5D);
        // the model's origin: the cabin centre
        pose.translate(TransformationChamberBlock.CABIN_X, 0.0D, TransformationChamberBlock.CABIN_Z);
        // model space: y down with the feet at 24 px
        pose.translate(0.0D, 1.5D, 0.0D);
        pose.scale(-1.0F, -1.0F, 1.0F);
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, 0,
                1.0F, 1.0F, 1.0F, 1.0F);
        float glow = active ? 1.0F : 0.7F;
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 0xF000F0, overlay, 1,
                glow, glow, glow, 1.0F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay, 2,
                1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TransformationChamberBlockEntity chamber) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}

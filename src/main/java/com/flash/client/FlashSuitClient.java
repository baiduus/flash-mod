package com.flash.client;

import com.flash.FlashMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Suit models for the Flash suits (player-skin layout with the outer layer, HD textures
 * from the Flash asset pack recoloured per suit by tools/gen_suits_from_pack.py). Vanilla armor inflates the body by
 * 1.0 (0.5 for leggings), which looks bulky; the suits use a tiny inflation so
 * they hug the character like a skin-tight suit.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FlashSuitClient {

    /** Just thicker than the skin's own second layer (0.25) so nothing pokes through. */
    private static final float OUTER_GROW = 0.28F;
    private static final float INNER_GROW = 0.27F;

    public static final ModelLayerLocation OUTER =
            new ModelLayerLocation(new ResourceLocation(FlashMod.MODID, "suit_outer"), "main");
    public static final ModelLayerLocation INNER =
            new ModelLayerLocation(new ResourceLocation(FlashMod.MODID, "suit_inner"), "main");

    private static HumanoidModel<LivingEntity> outerModel;
    private static HumanoidModel<LivingEntity> innerModel;

    private FlashSuitClient() {
    }

    /**
     * Player-skin layout (64 x 64 UV on a 160-unit HD texture, like the Flash asset pack):
     * every part has its base box and the outer-layer box, so the suit has the same
     * two layers as a skin.
     */
    private static LayerDefinition suitLayer(float grow) {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh =
                new net.minecraft.client.model.geom.builders.MeshDefinition();
        net.minecraft.client.model.geom.builders.PartDefinition root = mesh.getRoot();
        CubeDeformation g = new CubeDeformation(grow);
        CubeDeformation o = new CubeDeformation(grow + 0.25F);
        root.addOrReplaceChild("head", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, g),
                net.minecraft.client.model.geom.PartPose.ZERO);
        root.addOrReplaceChild("hat", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(grow + 0.5F)),
                net.minecraft.client.model.geom.PartPose.ZERO);
        root.addOrReplaceChild("body", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, g)
                        .texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, o),
                net.minecraft.client.model.geom.PartPose.ZERO);
        root.addOrReplaceChild("right_arm", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, g)
                        .texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, o),
                net.minecraft.client.model.geom.PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, g)
                        .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, o),
                net.minecraft.client.model.geom.PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, g)
                        .texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, o),
                net.minecraft.client.model.geom.PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, g)
                        .texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, o),
                net.minecraft.client.model.geom.PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 160, 160);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(OUTER, () -> suitLayer(OUTER_GROW));
        event.registerLayerDefinition(FlashSuitStandModel.LAYER, FlashSuitStandModel::createLayer);
        event.registerLayerDefinition(INNER, () -> suitLayer(INNER_GROW));
    }

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            outerModel = null;
            innerModel = null;
            HulkClient.resetModel();
            HulkBoulderRenderer.reset();
            FlashSpeedsterPose.reset();
            GammaChamberRenderer.reset();
            AccelerationPortalRenderer.resetFrame();
        });
    }

    public static HumanoidModel<?> model(boolean inner) {
        if (inner) {
            if (innerModel == null) {
                innerModel = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(INNER));
            }
            return innerModel;
        }
        if (outerModel == null) {
            outerModel = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(OUTER));
        }
        return outerModel;
    }
}

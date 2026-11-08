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
     * The vanilla armor draw of the suits is empty: the suit is the geo model of the Flash
     * asset pack, drawn by {@link FlashGeoSuitLayer} on the posed body.
     */
    private static LayerDefinition suitLayer(float grow) {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh =
                new net.minecraft.client.model.geom.builders.MeshDefinition();
        net.minecraft.client.model.geom.builders.PartDefinition root = mesh.getRoot();
        for (String part : new String[] {"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            root.addOrReplaceChild(part, net.minecraft.client.model.geom.builders.CubeListBuilder.create(),
                    net.minecraft.client.model.geom.PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
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
            AccelerationPortalRenderer.resetFrame();
            FlashGeoSuitLayer.reset();
            SupermanSuitLayer.reset();
            MjolnirRenderer.reset();
            GammaChamberRenderer.reset();
            FlashRingLayer.reset();
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

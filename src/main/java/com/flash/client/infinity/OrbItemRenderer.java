package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.client.geo.GeoModel;
import com.flash.infinity.InfinityContainers;
import com.flash.infinity.InfinityStone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The Orb of Morag in hand: the original's render layer model (orb.geo.json) and its "hold" animation, driven like
 * the original's queries: the two halves turn and part while it opens (18 ticks) or closes (10 ticks), the teeth slide
 * out between them and the Power Stone inside grows and turns. Elsewhere (inventory, ground, frames) the item model.
 */
public class OrbItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation STATIC = new ResourceLocation(FlashMod.MODID, "item/infinity/orb_static");
    private static final ResourceLocation TEX = new ResourceLocation(FlashMod.MODID,
            "textures/infinity_models/orb_render_layer.png");
    private static final ResourceLocation STONE_TEX = new ResourceLocation(FlashMod.MODID,
            "textures/item/infinity_stone.png");
    private static OrbItemRenderer instance;
    private static GeoModel orb;
    private static GeoModel stone;
    /** How open the local player's Orb is (0 closed, 1 open) and when it was last worked out. */
    private static float open;
    private static long lastNanos;

    public OrbItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static OrbItemRenderer get() {
        if (instance == null) {
            instance = new OrbItemRenderer();
        }
        return instance;
    }

    private static GeoModel load(String path) {
        try {
            return GeoModel.load(new ResourceLocation(FlashMod.MODID, path), null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Eases the opening towards the local player's state (the original's open / close animation timers). */
    private static float openness() {
        long t = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.25F, (t - lastNanos) / 1.0E9F);
        lastNanos = t;
        boolean on = InfinityClient.toggled("orb/open_close_dummy");
        // 18 ticks to open, 10 to close
        open = on ? Math.min(1.0F, open + dt / 0.9F) : Math.max(0.0F, open - dt / 0.5F);
        return open;
    }

    private static float smooth(float x) {
        x = Math.max(0.0F, Math.min(1.0F, x));
        return x * x * (3.0F - 2.0F * x);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        boolean hand = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!hand) {
            BakedModel model = mc.getModelManager().getModel(STATIC);
            mc.getItemRenderer().renderModelLists(model, stack, light, overlay, pose,
                    buffers.getBuffer(Sheets.cutoutBlockSheet()));
            return;
        }
        if (orb == null) {
            orb = load("geo/infinity/orb.geo.json");
            stone = load("geo/infinity/power_stone_orb.geo.json");
            if (orb == null) {
                return;
            }
        }
        // only the local player's state is known here: the others' Orbs stay shut
        float p = context.firstPerson() ? openness() : (mc.player != null && mc.player.getMainHandItem() == stack
                || mc.player != null && mc.player.getOffhandItem() == stack ? openness() : 0.0F);
        float split = smooth(p * 1.6F) * 1.75F;
        float extent = smooth(p * 2.0F - 0.8F);
        float teeth = smooth(p) * 90.0F;
        float turn = smooth(p * 1.3F) * 90.0F;
        orb.reset();
        bone(orb, "New", 0.0F, split, turn, 1.0F);
        bone(orb, "group", 0.0F, -split, -turn, 1.0F);
        bone(orb, "group2", 0.0F, 0.0F, teeth, 1.0F);
        bone(orb, "bone2", 0.0F, -(split + (1.0F - extent) * 2.0F), 0.0F, extent);
        bone(orb, "group3", 0.0F, split / 3.0F + (1.0F - extent) * 2.0F, 0.0F, extent);

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(1.0F, -1.0F, -1.0F);
        // the orb's middle (bedrock 0, 19.1, -7.5) on the item's middle
        pose.translate(0.0F, -4.9F / 16.0F, 7.5F / 16.0F);
        orb.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY,
                0, 1.0F, 1.0F, 1.0F, 1.0F);
        if (stone != null && p > 0.05F && InfinityContainers.stoneIn(stack) == InfinityStone.POWER) {
            float age = mc.player != null ? mc.player.tickCount + mc.getFrameTime() : 0.0F;
            stone.reset();
            GeoModel.Bone b = stone.bone("bone");
            if (b != null) {
                float s = smooth(p * 1.5F - 0.3F);
                b.sx = b.sy = b.sz = Math.max(1.0E-3F, s);
                b.arx = age * 3.0F;
                b.ary = age * 5.0F;
                b.arz = age * 2.0F;
            }
            int c = InfinityStone.POWER.color;
            stone.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(STONE_TEX)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 0, ((c >> 16) & 0xFF) / 255.0F, ((c >> 8) & 0xFF) / 255.0F,
                    (c & 0xFF) / 255.0F, 1.0F);
            stone.render(pose, buffers.getBuffer(RenderType.entityGlintDirect()), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        pose.popPose();
    }

    /** One of the original's query-driven bones: y offset (java pixels), turn round y (degrees), scale. */
    private static void bone(GeoModel m, String name, float x, float y, float yaw, float scale) {
        GeoModel.Bone b = m.bone(name);
        if (b == null) {
            return;
        }
        b.ax += x;
        b.ay += y;
        b.ary += yaw;
        float s = Math.max(1.0E-3F, scale);
        b.sx *= s;
        b.sy *= s;
        b.sz *= s;
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.client.geo.GeoAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The speedster run of the Flash asset pack ({@code flash_player.animation.json}):
 * {@code speedster_run} while running at super speed, {@code speedster_run_jump}
 * when jumping while running and {@code speedster_run_end} when stopping.
 *
 * <p>Vanilla resets several parts at the end of {@code setupAnim}, so the pose is
 * applied afterwards: the normal body draw is hidden and a layer placed first in
 * the player renderer poses the model, draws the skin, and leaves the pose for
 * the next layers (suit, items, cape follow it).</p>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlashSpeedsterPose {

    private static final ResourceLocation ANIMS =
            new ResourceLocation(FlashMod.MODID, "animations/flash_player.animation.json");
    private static final String RUN = "animation.player.speedster_run";
    private static final String RUN_END = "animation.player.speedster_run_end";
    private static final String RUN_JUMP = "animation.player.speedster_run_jump";
    /** Blocks per tick above which the run animation plays while sprinting with super speed. */
    private static final double RUN_SPEED = 0.12D;

    private static Map<String, GeoAnimation> anims;

    private static final class State {
        long lastRun = Long.MIN_VALUE / 2;
        long airStart = -1L;
        boolean running;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** Visibility of the parts before they were hidden for this frame. */
    private static boolean[] savedVisible;
    private static UUID hiddenFor;

    private FlashSpeedsterPose() {
    }

    public static void reset() {
        anims = null;
        STATES.clear();
    }

    private static Map<String, GeoAnimation> anims() {
        if (anims == null) {
            anims = GeoAnimation.load(ANIMS);
        }
        return anims;
    }

    /** The animation and time to play now, or null for the normal Minecraft pose. */
    private record Choice(GeoAnimation animation, float seconds) {
    }

    private static Choice choose(AbstractClientPlayer player, float partialTick) {
        if (player.isInvisible() || player.isPassenger() || player.isSwimming() || player.isFallFlying()
                || com.flash.hulk.HulkState.isTransformed(player) || FlashClientVisualLevels.get(player) <= 0
                || FlashClientVisualLevels.activeMask(player) != 0) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        float time = now + partialTick;
        State st = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
        // sprinting with a speed level: the speed of the level and the speedster run
        boolean running = player.isSprinting() && speed > RUN_SPEED && !player.isCrouching();
        if (running) {
            st.lastRun = now;
        }
        if (!running && st.running && player.onGround()) {
            // just stopped
            st.lastRun = now;
        }
        st.running = running;
        if (running && !player.onGround()) {
            if (st.airStart < 0L) {
                st.airStart = now;
            }
        } else {
            st.airStart = -1L;
        }
        Map<String, GeoAnimation> a = anims();
        if (running) {
            if (st.airStart >= 0L && a.containsKey(RUN_JUMP)) {
                GeoAnimation jump = a.get(RUN_JUMP);
                float t = (time - st.airStart) / 20.0F;
                if (t < jump.length) {
                    return new Choice(jump, t);
                }
            }
            GeoAnimation run = a.get(RUN);
            if (run == null) {
                return null;
            }
            // the loop runs faster the faster the player goes
            float rate = (float) Math.max(1.0D, Math.min(2.2D, speed / 0.6D));
            return new Choice(run, (time * rate / 20.0F) % run.length);
        }
        GeoAnimation end = a.get(RUN_END);
        if (end != null) {
            float t = (time - st.lastRun) / 20.0F;
            if (t >= 0.0F && t < end.length && player.onGround()) {
                return new Choice(end, t);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Hiding the normal draw
    // ------------------------------------------------------------------

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve,
                m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants, m.leftPants};
    }

    /** Player being drawn by the speedster run this frame (its body, suit and ring). */
    private static UUID posingFor;

    public static boolean isPosing(AbstractClientPlayer player) {
        return posingFor != null && posingFor.equals(player.getUUID());
    }

    /** Last of the pre-render handlers (after the skin hiders): hides the body draw when posing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !(event.getRenderer().getModel() instanceof PlayerModel<?> model)) {
            return;
        }
        posingFor = null;
        if (event.isCanceled() || choose(player, event.getPartialTick()) == null) {
            return;
        }
        ModelPart[] ps = parts(model);
        savedVisible = new boolean[ps.length];
        for (int i = 0; i < ps.length; i++) {
            savedVisible[i] = ps[i].visible;
            ps[i].visible = false;
        }
        hiddenFor = player.getUUID();
        posingFor = player.getUUID();
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() instanceof AbstractClientPlayer player && isPosing(player)) {
            posingFor = null;
        }
    }

    // ------------------------------------------------------------------
    // The layer that draws the animated body
    // ------------------------------------------------------------------

    public static void install(PlayerRenderer renderer) {
        renderer.addLayer(new Layer(renderer));
    }

    private static final ResourceLocation SKIN_GEO = new ResourceLocation(FlashMod.MODID, "geo/player_skin.geo.json");
    private static final ResourceLocation SKIN_SLIM_GEO =
            new ResourceLocation(FlashMod.MODID, "geo/player_skin_slim.geo.json");
    private static com.flash.client.geo.GeoModel skin;
    private static com.flash.client.geo.GeoModel skinSlim;
    /** Root ("body" bone) pivot: 0.7 blocks above the feet, in model pixels (y down, feet at 24). */
    private static final float ROOT_PIVOT_Y = 24.0F - 11.2F;
    private static final String[] OUTER = {"torso_layer", "head_layer", "right_arm_layer", "left_arm_layer",
            "right_leg_layer", "left_leg_layer"};
    /** Suit bone (the pack names its +x limbs "right") and the animation bone that moves it. */
    private static final String[][] SUIT_BONES = {{"head", "head"}, {"body", "torso"}, {"right_arm", "left_arm"},
            {"left_arm", "right_arm"}, {"right_leg", "left_leg"}, {"left_leg", "right_leg"}};

    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (hiddenFor == null || !hiddenFor.equals(player.getUUID())) {
                return;
            }
            hiddenFor = null;
            PlayerModel<AbstractClientPlayer> parent = this.getParentModel();
            ModelPart[] ps = parts(parent);
            for (int i = 0; i < ps.length && savedVisible != null && i < savedVisible.length; i++) {
                ps[i].visible = savedVisible[i];
            }
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
            Choice c = choose(player, partialTick);
            if (c == null) {
                // stopped between the two checks: the normal body
                parent.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(
                        player.getSkinTextureLocation())), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                return;
            }
            GeoAnimation a = c.animation();
            float t = c.seconds();
            boolean slim = "slim".equals(player.getModelName());
            if (skin == null) {
                skin = com.flash.client.geo.GeoModel.load(SKIN_GEO, null);
                skinSlim = com.flash.client.geo.GeoModel.load(SKIN_SLIM_GEO, null);
            }
            com.flash.client.geo.GeoModel body = slim ? skinSlim : skin;
            body.reset();
            a.apply(body, t);
            com.flash.client.geo.GeoModel.Bone head = body.bone("head");
            if (head != null) {
                head.ary += netHeadYaw;
                head.arx += headPitch;
            }
            boolean suit = com.flash.item.FlashSuitItem.countWorn(player) > 0;
            for (String outer : OUTER) {
                com.flash.client.geo.GeoModel.Bone b = body.bone(outer);
                if (b != null) {
                    b.visible = !suit;
                }
            }
            body.render(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation())),
                    light, overlay, 0, 1.0F, 1.0F, 1.0F, 1.0F);
            if (suit) {
                float[] rot = new float[3];
                float[] pos = new float[3];
                pose.pushPose();
                if (a.sampleBone("body", t, rot, pos)) {
                    pose.translate(pos[0] / 16.0F, (ROOT_PIVOT_Y - pos[1]) / 16.0F, pos[2] / 16.0F);
                    pose.mulPose(new org.joml.Quaternionf().rotationZYX(rad(rot[2]), rad(rot[1]), rad(rot[0])));
                    pose.translate(0.0F, -ROOT_PIVOT_Y / 16.0F, 0.0F);
                }
                FlashGeoSuitLayer.drawAnimated(player, pose, buffers, light, suitModel -> {
                    for (String[] pair : SUIT_BONES) {
                        com.flash.client.geo.GeoModel.Bone b = suitModel.bone(pair[0]);
                        if (b == null || !a.sampleBone(pair[1], t, rot, pos)) {
                            continue;
                        }
                        b.arx += rot[0];
                        b.ary += rot[1];
                        b.arz += rot[2];
                        b.ax += pos[0];
                        b.ay -= pos[1];
                        b.az += pos[2];
                        if ("head".equals(pair[0])) {
                            b.ary += netHeadYaw;
                            b.arx += headPitch;
                        }
                    }
                });
                pose.popPose();
            }
            if (FlashClientRing.has(player)) {
                pose.pushPose();
                if (body.transformToBone("right_arm", pose)) {
                    FlashRingLayer.drawRing(pose, buffers, light, slim);
                }
                pose.popPose();
            }
        }
    }

    private static float rad(float deg) {
        return deg * ((float) Math.PI / 180.0F);
    }
}

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

    /** Last of the pre-render handlers (after the skin hiders): hides the body draw when posing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !(event.getRenderer().getModel() instanceof PlayerModel<?> model)
                || event.isCanceled() || choose(player, event.getPartialTick()) == null) {
            return;
        }
        ModelPart[] ps = parts(model);
        savedVisible = new boolean[ps.length];
        for (int i = 0; i < ps.length; i++) {
            savedVisible[i] = ps[i].visible;
            ps[i].visible = false;
        }
        hiddenFor = player.getUUID();
    }

    // ------------------------------------------------------------------
    // The posing layer (first layer of the player renderer)
    // ------------------------------------------------------------------

    /** Puts the posing layer before the armor layer (falls back to the end of the list). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void install(PlayerRenderer renderer) {
        Layer layer = new Layer(renderer);
        try {
            List layers = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                    LivingEntityRenderer.class, renderer, "f_115291_");
            layers.add(0, layer);
        } catch (Exception ex) {
            renderer.addLayer(layer);
        }
    }

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
            PlayerModel<AbstractClientPlayer> model = this.getParentModel();
            ModelPart[] ps = parts(model);
            for (int i = 0; i < ps.length && savedVisible != null && i < savedVisible.length; i++) {
                ps[i].visible = savedVisible[i];
            }
            Choice c = choose(player, partialTick);
            if (c != null) {
                applyPose(model, c.animation(), c.seconds());
            }
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation())),
                    light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // Pose
    // ------------------------------------------------------------------

    /** Root ("body" bone) pivot: 0.7 blocks above the feet, in model pixels (y down, feet at 24). */
    private static final float ROOT_PIVOT_Y = 24.0F - 11.2F;

    private static void applyPose(PlayerModel<?> m, GeoAnimation a, float t) {
        float[] rot = new float[3];
        float[] pos = new float[3];
        // root: rotation about its pivot plus a translation, applied to every part
        double[][] rootR = identity();
        double[] rootT = {0.0D, 0.0D, 0.0D};
        if (a.sampleBone("body", t, rot, pos)) {
            rootR = rotZYX(rad(rot[2]), rad(rot[1]), rad(rot[0]));
            rootT = new double[] {pos[0], -pos[1], pos[2]};
        }
        pose(m.body, a, "torso", t, 0.0F, 0.0F, 0.0F, rootR, rootT, rot, pos, false);
        pose(m.head, a, "head", t, 0.0F, 0.0F, 0.0F, rootR, rootT, rot, pos, true);
        pose(m.rightArm, a, "right_arm", t, -5.0F, 2.0F, 0.0F, rootR, rootT, rot, pos, false);
        pose(m.leftArm, a, "left_arm", t, 5.0F, 2.0F, 0.0F, rootR, rootT, rot, pos, false);
        pose(m.rightLeg, a, "right_leg", t, -1.9F, 12.0F, 0.0F, rootR, rootT, rot, pos, false);
        pose(m.leftLeg, a, "left_leg", t, 1.9F, 12.0F, 0.0F, rootR, rootT, rot, pos, false);
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /**
     * One part: the animated local transform (the head keeps looking around on top of it),
     * then the root transform.
     */
    private static void pose(ModelPart part, GeoAnimation a, String bone, float t, float dx, float dy, float dz,
                             double[][] rootR, double[] rootT, float[] rot, float[] pos, boolean keepLook) {
        double[] offset;
        double[][] r;
        if (a.sampleBone(bone, t, rot, pos)) {
            offset = new double[] {dx + pos[0], dy - pos[1], dz + pos[2]};
            float lx = keepLook ? part.xRot : 0.0F;
            float ly = keepLook ? part.yRot : 0.0F;
            r = rotZYX(rad(rot[2]), rad(rot[1]) + ly, rad(rot[0]) + lx);
        } else {
            // not animated by this clip: the normal pose (head look, ...) at the default place
            offset = new double[] {dx, dy, dz};
            r = rotZYX(part.zRot, part.yRot, part.xRot);
            if (!keepLook) {
                r = rotZYX(0.0F, 0.0F, 0.0F);
            }
        }
        // offset' = R_root (offset - pivot) + pivot + T_root ; rotation' = R_root * R
        double[] rel = {offset[0], offset[1] - ROOT_PIVOT_Y, offset[2]};
        double[] moved = mul(rootR, rel);
        part.x = (float) (moved[0] + rootT[0]);
        part.y = (float) (moved[1] + ROOT_PIVOT_Y + rootT[1]);
        part.z = (float) (moved[2] + rootT[2]);
        double[][] fin = mul(rootR, r);
        // decompose Rz * Ry * Rx
        double sy = -fin[2][0];
        sy = Math.max(-1.0D, Math.min(1.0D, sy));
        part.yRot = (float) Math.asin(sy);
        part.xRot = (float) Math.atan2(fin[2][1], fin[2][2]);
        part.zRot = (float) Math.atan2(fin[1][0], fin[0][0]);
    }

    private static float rad(float deg) {
        return deg * ((float) Math.PI / 180.0F);
    }

    private static double[][] identity() {
        return new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    }

    /** Rz(z) * Ry(y) * Rx(x), like {@code Quaternionf.rotationZYX(z, y, x)} of a ModelPart. */
    private static double[][] rotZYX(double z, double y, double x) {
        double cx = Math.cos(x);
        double sx = Math.sin(x);
        double cy = Math.cos(y);
        double sy = Math.sin(y);
        double cz = Math.cos(z);
        double sz = Math.sin(z);
        double[][] rx = {{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
        double[][] ry = {{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        double[][] rz = {{cz, -sz, 0}, {sz, cz, 0}, {0, 0, 1}};
        return mul(rz, mul(ry, rx));
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return out;
    }

    private static double[] mul(double[][] a, double[] v) {
        return new double[] {
                a[0][0] * v[0] + a[0][1] * v[1] + a[0][2] * v[2],
                a[1][0] * v[0] + a[1][1] * v[1] + a[1][2] * v[2],
                a[2][0] * v[0] + a[2][1] * v[1] + a[2][2] * v[2]};
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Plays the Superman player animations of the pack ({@code animations/superman/*.json},
 * Player Animator "emote" format, version 3) on the vanilla player model: a base
 * animation (flight states, solar charge), an overlay while a power is held (heat
 * vision, breaths, holding a creature) and one-shot animations sent by the
 * server (punch, clap, grab, throw, landing...).
 *
 * <p>Like the speedster run, the normal body draw is hidden and a layer poses the
 * model after {@code setupAnim} and draws it.</p>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanPose {

    private static final String[] PARTS = {"body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final String[] AXES = {"pitch", "yaw", "roll", "x", "y", "z", "bend"};
    private static final String[] LIMBS = {"rightArm", "leftArm", "rightLeg", "leftLeg"};
    /** Pivot of the whole-body rotation in model space (0.7 blocks above the feet). */
    private static final float BODY_PIVOT = (24.0F - 11.2F) / 16.0F;
    private static final float FADE_TICKS = 4.0F;

    // ------------------------------------------------------------------
    // Emote data
    // ------------------------------------------------------------------

    private static final class Track {
        final float[] ticks;
        final float[] values;
        final String[] easing;

        Track(float[] ticks, float[] values, String[] easing) {
            this.ticks = ticks;
            this.values = values;
            this.easing = easing;
        }

        float sample(float t) {
            int n = ticks.length;
            if (n == 1 || t <= ticks[0]) {
                return values[0];
            }
            if (t >= ticks[n - 1]) {
                return values[n - 1];
            }
            int i = 1;
            while (i < n - 1 && ticks[i] < t) {
                i++;
            }
            float t0 = ticks[i - 1];
            float t1 = ticks[i];
            float f = t1 - t0 <= 1.0E-6F ? 1.0F : (t - t0) / (t1 - t0);
            return values[i - 1] + (values[i] - values[i - 1]) * ease(easing[i], f);
        }
    }

    static final class Emote {
        final float begin;
        final float end;
        final float stop;
        final float returnTick;
        final boolean loop;
        /** "part.axis" -> keyframes. */
        final Map<String, Track> tracks;

        Emote(float begin, float end, float stop, float returnTick, boolean loop, Map<String, Track> tracks) {
            this.begin = begin;
            this.end = end;
            this.stop = Math.max(stop, end);
            this.returnTick = returnTick;
            this.loop = loop;
            this.tracks = tracks;
        }

        /** Animation time for ticks since the start (loops back to the return tick). */
        float time(float elapsed) {
            float t = begin + elapsed;
            if (loop && t > end) {
                float len = Math.max(1.0F, end - returnTick);
                t = returnTick + ((t - end) % len);
            }
            return t;
        }

        boolean finished(float elapsed) {
            return !loop && begin + elapsed >= stop;
        }

        /** Weight while ending (non-looping: fades back to the normal pose between end and stop). */
        float weight(float elapsed) {
            float in = Math.min(1.0F, elapsed / FADE_TICKS);
            if (loop) {
                return in;
            }
            float t = begin + elapsed;
            if (t <= end) {
                return in;
            }
            return Math.max(0.0F, Math.min(in, 1.0F - (t - end) / Math.max(1.0F, stop - end)));
        }

        Float value(String part, String axis, float t) {
            Track track = tracks.get(part + "." + axis);
            return track == null ? null : track.sample(Math.min(t, end));
        }
    }

    private static float ease(String name, float x) {
        if (name == null) {
            return x;
        }
        switch (name) {
            case "easeinoutsine":
                return (float) (-(Math.cos(Math.PI * x) - 1.0D) / 2.0D);
            case "easeinsine":
                return (float) (1.0D - Math.cos(x * Math.PI / 2.0D));
            case "easeoutsine":
                return (float) Math.sin(x * Math.PI / 2.0D);
            case "easeinquad":
                return x * x;
            case "easeoutquad":
                return 1.0F - (1.0F - x) * (1.0F - x);
            case "easeinoutquad":
                return x < 0.5F ? 2.0F * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 2) / 2.0F;
            case "easeincubic":
                return x * x * x;
            case "easeoutcubic":
                return 1.0F - (float) Math.pow(1.0F - x, 3);
            case "easeinoutcubic":
                return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3) / 2.0F;
            case "easeinquart":
                return x * x * x * x;
            case "easeoutquart":
                return 1.0F - (float) Math.pow(1.0F - x, 4);
            case "easeinoutquart":
                return x < 0.5F ? 8.0F * x * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 4) / 2.0F;
            case "easeinquint":
                return x * x * x * x * x;
            case "easeoutquint":
                return 1.0F - (float) Math.pow(1.0F - x, 5);
            case "easeinoutquint":
                return x < 0.5F ? 16.0F * x * x * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 5) / 2.0F;
            case "easeinexpo":
                return x == 0.0F ? 0.0F : (float) Math.pow(2.0D, 10.0D * x - 10.0D);
            case "easeoutexpo":
                return x == 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * x);
            case "easeinoutexpo":
                return x == 0.0F || x == 1.0F ? x : x < 0.5F ? (float) Math.pow(2.0D, 20.0D * x - 10.0D) / 2.0F
                        : (2.0F - (float) Math.pow(2.0D, -20.0D * x + 10.0D)) / 2.0F;
            case "easeincirc":
                return 1.0F - (float) Math.sqrt(1.0D - x * x);
            case "easeoutcirc":
                return (float) Math.sqrt(1.0D - (x - 1.0F) * (x - 1.0F));
            case "easeinoutcirc":
                return x < 0.5F ? (1.0F - (float) Math.sqrt(1.0D - 4.0D * x * x)) / 2.0F
                        : ((float) Math.sqrt(1.0D - Math.pow(-2.0D * x + 2.0D, 2)) + 1.0F) / 2.0F;
            case "easeinback":
                return 2.70158F * x * x * x - 1.70158F * x * x;
            case "easeoutback":
                return 1.0F + 2.70158F * (float) Math.pow(x - 1.0F, 3) + 1.70158F * (float) Math.pow(x - 1.0F, 2);
            case "easeinoutback": {
                float c = 1.70158F * 1.525F;
                return x < 0.5F ? (float) (Math.pow(2.0F * x, 2) * ((c + 1.0F) * 2.0F * x - c)) / 2.0F
                        : (float) (Math.pow(2.0F * x - 2.0F, 2) * ((c + 1.0F) * (x * 2.0F - 2.0F) + c) + 2.0F) / 2.0F;
            }
            case "easeinelastic":
                return x == 0.0F || x == 1.0F ? x
                        : (float) (-Math.pow(2.0D, 10.0D * x - 10.0D) * Math.sin((x * 10.0D - 10.75D) * (2.0D * Math.PI / 3.0D)));
            case "easeoutelastic":
                return x == 0.0F || x == 1.0F ? x
                        : (float) (Math.pow(2.0D, -10.0D * x) * Math.sin((x * 10.0D - 0.75D) * (2.0D * Math.PI / 3.0D)) + 1.0D);
            case "easeinoutelastic": {
                if (x == 0.0F || x == 1.0F) {
                    return x;
                }
                double c = 2.0D * Math.PI / 4.5D;
                return x < 0.5F ? (float) (-(Math.pow(2.0D, 20.0D * x - 10.0D) * Math.sin((20.0D * x - 11.125D) * c)) / 2.0D)
                        : (float) (Math.pow(2.0D, -20.0D * x + 10.0D) * Math.sin((20.0D * x - 11.125D) * c) / 2.0D + 1.0D);
            }
            case "easeoutbounce":
                return bounce(x);
            case "easeinbounce":
                return 1.0F - bounce(1.0F - x);
            case "easeinoutbounce":
                return x < 0.5F ? (1.0F - bounce(1.0F - 2.0F * x)) / 2.0F : (1.0F + bounce(2.0F * x - 1.0F)) / 2.0F;
            case "constant":
                return 0.0F;
            default:
                return x;
        }
    }

    private static float bounce(float x) {
        float n = 7.5625F;
        float d = 2.75F;
        if (x < 1.0F / d) {
            return n * x * x;
        } else if (x < 2.0F / d) {
            x -= 1.5F / d;
            return n * x * x + 0.75F;
        } else if (x < 2.5F / d) {
            x -= 2.25F / d;
            return n * x * x + 0.9375F;
        }
        x -= 2.625F / d;
        return n * x * x + 0.984375F;
    }

    private static final Map<String, Emote> EMOTES = new HashMap<>();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    static Emote emote(String name) {
        if (EMOTES.containsKey(name)) {
            return EMOTES.get(name);
        }
        Emote e = load(name);
        EMOTES.put(name, e);
        return e;
    }

    public static void reset() {
        EMOTES.clear();
        STATES.clear();
    }

    private static Emote load(String name) {
        ResourceLocation loc = new ResourceLocation(FlashMod.MODID, "animations/superman/" + name + ".json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(loc).openAsReader()) {
            JsonObject emote = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("emote");
            boolean degrees = !emote.has("degrees") || emote.get("degrees").getAsBoolean();
            Map<String, List<float[]>> raw = new HashMap<>();
            Map<String, List<String>> eas = new HashMap<>();
            for (JsonElement el : emote.getAsJsonArray("moves")) {
                JsonObject move = el.getAsJsonObject();
                float tick = move.get("tick").getAsFloat();
                String easing = move.has("easing") ? move.get("easing").getAsString().toLowerCase(java.util.Locale.ROOT)
                        : "linear";
                for (String part : PARTS) {
                    if (!move.has(part)) {
                        continue;
                    }
                    JsonObject p = move.getAsJsonObject(part);
                    for (String axis : AXES) {
                        if (!p.has(axis)) {
                            continue;
                        }
                        float v = p.get(axis).getAsFloat();
                        if (!degrees && (axis.equals("pitch") || axis.equals("yaw") || axis.equals("roll")
                                || axis.equals("bend"))) {
                            v = (float) Math.toDegrees(v);
                        }
                        String key = part + "." + axis;
                        raw.computeIfAbsent(key, k -> new ArrayList<>()).add(new float[] {tick, v});
                        eas.computeIfAbsent(key, k -> new ArrayList<>()).add(easing);
                    }
                }
            }
            Map<String, Track> tracks = new HashMap<>();
            for (Map.Entry<String, List<float[]>> en : raw.entrySet()) {
                List<float[]> frames = en.getValue();
                List<String> e = eas.get(en.getKey());
                Integer[] order = new Integer[frames.size()];
                for (int i = 0; i < order.length; i++) {
                    order[i] = i;
                }
                java.util.Arrays.sort(order, (a, b) -> Float.compare(frames.get(a)[0], frames.get(b)[0]));
                float[] t = new float[order.length];
                float[] v = new float[order.length];
                String[] es = new String[order.length];
                for (int i = 0; i < order.length; i++) {
                    t[i] = frames.get(order[i])[0];
                    v[i] = frames.get(order[i])[1];
                    es[i] = e.get(order[i]);
                }
                tracks.put(en.getKey(), new Track(t, v, es));
            }
            return new Emote(emote.get("beginTick").getAsFloat(), emote.get("endTick").getAsFloat(),
                    emote.has("stopTick") ? emote.get("stopTick").getAsFloat() : emote.get("endTick").getAsFloat(),
                    emote.has("returnTick") ? emote.get("returnTick").getAsFloat() : 0.0F,
                    emote.has("isLoop") && emote.get("isLoop").getAsBoolean(), tracks);
        } catch (Exception ex) {
            LOGGER.error("Could not load Superman animation {}", loc, ex);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Per-player state
    // ------------------------------------------------------------------

    private static final class State {
        String base;
        float baseStart;
        String hold;
        float holdStart;
        String shot;
        float shotStart;
        boolean wasFlying;
        boolean wasDiving;
        double lastHs;
        int lastTick = -1;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static float now(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level != null ? mc.level.getGameTime() : 0L) + partialTick;
    }

    /** One-shot animation from the server. */
    public static void play(UUID player, String animation) {
        if (emote(animation) == null) {
            return;
        }
        State st = STATES.computeIfAbsent(player, id -> new State());
        st.shot = animation;
        st.shotStart = now(0.0F);
    }

    private static boolean flying(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) {
            return player.getAbilities().flying;
        }
        return SupermanState.flag(player, SupermanPowers.F_FLIGHT) && !player.onGround();
    }

    /** Base animation from the flight state and the motion. */
    private static String chooseBase(AbstractClientPlayer player, State st, float time) {
        if (SupermanState.flag(player, SupermanPowers.F_ULTIMATE)) {
            return "solar_charge";
        }
        boolean flying = flying(player);
        double vx = player.getX() - player.xo;
        double vy = player.getY() - player.yo;
        double vz = player.getZ() - player.zo;
        double yaw = Math.toRadians(player.yBodyRot);
        double forward = -vx * Math.sin(yaw) + vz * Math.cos(yaw);
        double left = vx * Math.cos(yaw) + vz * Math.sin(yaw);
        double hs = Math.sqrt(vx * vx + vz * vz);
        boolean newTick = player.tickCount != st.lastTick;
        double lastHs = st.lastHs;
        if (newTick) {
            st.lastTick = player.tickCount;
            st.lastHs = hs;
        }
        if (flying && !st.wasFlying && st.shot == null) {
            st.shot = hs > 0.25D ? "takeoff8" : "flight_launch";
            st.shotStart = time;
        }
        if (!flying && st.wasFlying && player.onGround() && st.shot == null) {
            st.shot = hs > 0.25D ? "landing8" : "landing";
            st.shotStart = time;
        }
        st.wasFlying = flying;
        if (player.isPassenger() || player.isSwimming()) {
            st.wasDiving = false;
            return null;
        }
        if (!flying) {
            st.wasDiving = false;
            // three-block jump and the crouch leap
            if (!player.onGround() && !player.isInWater()) {
                if (vy > 0.3D) {
                    return "super_jump_rise9";
                }
                if (vy < -0.35D) {
                    return "super_jump_fall9";
                }
            }
            return null;
        }
        // braking out of a fast flight
        if (newTick && lastHs > 0.6D && hs < lastHs - 0.12D && hs < 0.45D && st.shot == null) {
            st.shot = lastHs > 0.9D ? "brake_boost" : "brake";
            st.shotStart = time;
        }
        boolean diving = vy < -0.7D;
        if (diving && !st.wasDiving && player == Minecraft.getInstance().player) {
            player.playSound(com.flash.registry.ModSounds.DIVE.get(), 0.8F, 1.0F);
        }
        st.wasDiving = diving;
        if (diving) {
            return "dive9";
        }
        if (vy < -0.2D && hs < 0.3D) {
            return "descend";
        }
        if (hs < 0.08D) {
            return vy > 0.15D ? "upright_flight9" : "hover";
        }
        if (forward < -0.05D && -forward > Math.abs(left)) {
            return "reverse_flight";
        }
        if (Math.abs(left) > Math.abs(forward)) {
            // slow sideways drift stays upright; faster is a real strafe
            if (hs < 0.35D) {
                return left > 0.0D ? "upright_left9" : "upright_right9";
            }
            return left > 0.0D ? "strafe_left" : "strafe_right";
        }
        if (hs > 1.6D) {
            return "prone_flight12";
        }
        if (hs > 0.9D) {
            return "boost";
        }
        if (Math.abs(left) > 0.35D * forward) {
            return left > 0.0D ? "bank_left" : "bank_right";
        }
        return "cruise";
    }

    private static String chooseHold(AbstractClientPlayer player) {
        if (SupermanState.flag(player, SupermanPowers.F_HEAT)) {
            return SupermanState.flag(player, SupermanPowers.F_LASER) ? "laser_blast" : "heat_vision";
        }
        if (SupermanState.flag(player, SupermanPowers.F_FREEZE)) {
            return "ice_breath";
        }
        if (SupermanState.flag(player, SupermanPowers.F_BREATH)) {
            return "super_breath";
        }
        if (SupermanState.flag(player, SupermanPowers.F_HOLDING)) {
            return "grab_hold";
        }
        return null;
    }

    private record Layers(Emote base, float baseTime, float baseWeight, Emote hold, float holdTime, float holdWeight,
                          Emote shot, float shotTime, float shotWeight) {
        boolean any() {
            return base != null || hold != null || shot != null;
        }
    }

    private static Layers choose(AbstractClientPlayer player, float partialTick) {
        if (player.isInvisible() || !SupermanState.isSuperman(player)) {
            STATES.remove(player.getUUID());
            return null;
        }
        float time = now(partialTick);
        State st = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        String base = chooseBase(player, st, time);
        if (base == null ? st.base != null : !base.equals(st.base)) {
            st.base = base;
            st.baseStart = time;
        }
        String hold = chooseHold(player);
        if (hold == null ? st.hold != null : !hold.equals(st.hold)) {
            st.hold = hold;
            st.holdStart = time;
        }
        Emote b = base != null ? emote(base) : null;
        Emote h = hold != null ? emote(hold) : null;
        Emote s = null;
        float sTime = 0.0F;
        float sWeight = 0.0F;
        if (st.shot != null) {
            s = emote(st.shot);
            float el = time - st.shotStart;
            if (s == null || s.finished(el)) {
                st.shot = null;
                s = null;
            } else {
                sTime = s.time(el);
                sWeight = s.weight(el);
            }
        }
        Layers l = new Layers(b, b != null ? b.time(time - st.baseStart) : 0.0F,
                b != null ? b.weight(time - st.baseStart) : 0.0F,
                h, h != null ? h.time(time - st.holdStart) : 0.0F, h != null ? h.weight(time - st.holdStart) : 0.0F,
                s, sTime, sWeight);
        return l.any() ? l : null;
    }

    // ------------------------------------------------------------------
    // Hiding the normal draw
    // ------------------------------------------------------------------

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve,
                m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants, m.leftPants};
    }

    private static boolean[] savedVisible;
    private static UUID hiddenFor;
    private static Layers pending;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !(event.getRenderer().getModel() instanceof PlayerModel<?> model)) {
            return;
        }
        hiddenFor = null;
        pending = null;
        if (event.isCanceled() || FlashSpeedsterPose.isPosing(player)) {
            return;
        }
        Layers l = choose(player, event.getPartialTick());
        if (l == null) {
            return;
        }
        ModelPart[] ps = parts(model);
        savedVisible = new boolean[ps.length];
        for (int i = 0; i < ps.length; i++) {
            savedVisible[i] = ps[i].visible;
            ps[i].visible = false;
        }
        hiddenFor = player.getUUID();
        pending = l;
    }

    public static void install(PlayerRenderer renderer) {
        renderer.addLayer(new Layer(renderer));
    }

    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (hiddenFor == null || !hiddenFor.equals(player.getUUID()) || pending == null) {
                return;
            }
            hiddenFor = null;
            Layers l = pending;
            pending = null;
            PlayerModel<AbstractClientPlayer> m = this.getParentModel();
            ModelPart[] ps = parts(m);
            for (int i = 0; i < ps.length && savedVisible != null && i < savedVisible.length; i++) {
                ps[i].visible = savedVisible[i];
            }
            float[] body = new float[6];
            float[] bend = new float[4];
            apply(m, l.base(), l.baseTime(), l.baseWeight(), body, bend);
            apply(m, l.hold(), l.holdTime(), l.holdWeight(), body, bend);
            apply(m, l.shot(), l.shotTime(), l.shotWeight(), body, bend);
            m.hat.copyFrom(m.head);
            m.jacket.copyFrom(m.body);
            m.rightSleeve.copyFrom(m.rightArm);
            m.leftSleeve.copyFrom(m.leftArm);
            m.rightPants.copyFrom(m.rightLeg);
            m.leftPants.copyFrom(m.leftLeg);
            pose.pushPose();
            // the whole body: entity space (y up) -> model space (x and y flipped)
            pose.translate(-body[3], BODY_PIVOT - body[4], body[5]);
            pose.mulPose(new org.joml.Quaternionf().rotationZYX(rad(body[2]), rad(-body[1]), rad(-body[0])));
            pose.translate(0.0F, -BODY_PIVOT, 0.0F);
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
            var consumer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation()));
            // bent limbs (elbows and knees) are drawn in two halves instead of the straight part
            Limbs limbs = "slim".equals(player.getModelName()) ? slimLimbs() : wideLimbs();
            ModelPart[] straight = {m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
            ModelPart[] overlays = {m.rightSleeve, m.leftSleeve, m.rightPants, m.leftPants};
            boolean[] bent = new boolean[4];
            for (int i = 0; i < 4; i++) {
                if (Math.abs(bend[i]) > 0.5F && straight[i].visible) {
                    bent[i] = true;
                    limbs.base[i].copyFrom(straight[i]);
                    limbs.base[i].getChild("lower").xRot = rad(bend[i]);
                    limbs.over[i].copyFrom(straight[i]);
                    limbs.over[i].getChild("lower").xRot = rad(bend[i]);
                    limbs.over[i].visible = overlays[i].visible;
                    straight[i].visible = false;
                    overlays[i].visible = false;
                }
            }
            m.renderToBuffer(pose, consumer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            for (int i = 0; i < 4; i++) {
                if (bent[i]) {
                    limbs.base[i].render(pose, consumer, light, overlay);
                    if (limbs.over[i].visible) {
                        limbs.over[i].render(pose, consumer, light, overlay);
                    }
                    straight[i].visible = true;
                    overlays[i].visible = limbs.over[i].visible;
                }
            }
            pose.popPose();
        }
    }

    /** Applies one emote with a weight; the whole-body transform and the limb bends are blended in. */
    private static void apply(PlayerModel<?> m, Emote e, float t, float w, float[] body, float[] bend) {
        if (e == null || w <= 0.0F) {
            return;
        }
        for (int a = 0; a < 6; a++) {
            Float v = e.value("body", AXES[a], t);
            if (v != null) {
                body[a] += (v - body[a]) * w;
            }
        }
        for (int i = 0; i < 4; i++) {
            Float v = e.value(LIMBS[i], "bend", t);
            if (v != null) {
                bend[i] += (v - bend[i]) * w;
            }
        }
        part(m.body, e, "torso", t, w, false);
        part(m.head, e, "head", t, w, true);
        part(m.rightArm, e, "rightArm", t, w, false);
        part(m.leftArm, e, "leftArm", t, w, false);
        part(m.rightLeg, e, "rightLeg", t, w, false);
        part(m.leftLeg, e, "leftLeg", t, w, false);
    }

    private static void part(ModelPart p, Emote e, String name, float t, float w, boolean additive) {
        Float pitch = e.value(name, "pitch", t);
        Float yaw = e.value(name, "yaw", t);
        Float roll = e.value(name, "roll", t);
        if (pitch != null) {
            p.xRot = additive ? p.xRot + rad(pitch) * w : p.xRot + (rad(pitch) - p.xRot) * w;
        }
        if (yaw != null) {
            p.yRot = additive ? p.yRot + rad(yaw) * w : p.yRot + (rad(yaw) - p.yRot) * w;
        }
        if (roll != null) {
            p.zRot = additive ? p.zRot + rad(roll) * w : p.zRot + (rad(roll) - p.zRot) * w;
        }
        Float x = e.value(name, "x", t);
        Float y = e.value(name, "y", t);
        Float z = e.value(name, "z", t);
        // limb positions are absolute pivots in the emote format (e.g. legs at y = 12)
        if (x != null) {
            p.x += (x - p.x) * w;
        }
        if (y != null) {
            p.y += (y - p.y) * w;
        }
        if (z != null) {
            p.z += (z - p.z) * w;
        }
    }

    // ------------------------------------------------------------------
    // Two-piece limbs for the bends
    // ------------------------------------------------------------------

    /** Upper half of each limb with the lower half as a child at the elbow / knee (skin + outer layer). */
    private static final class Limbs {
        final ModelPart[] base = new ModelPart[4];
        final ModelPart[] over = new ModelPart[4];
    }

    private static Limbs wide;
    private static Limbs slim;

    private static Limbs wideLimbs() {
        if (wide == null) {
            wide = buildLimbs(false);
        }
        return wide;
    }

    private static Limbs slimLimbs() {
        if (slim == null) {
            slim = buildLimbs(true);
        }
        return slim;
    }

    private static Limbs buildLimbs(boolean slimArms) {
        float aw = slimArms ? 3.0F : 4.0F;
        // {u, v, overlay u, overlay v, box x, box top, width}
        float[][] spec = {
                {40, 16, 40, 32, slimArms ? -2.0F : -3.0F, -2.0F, aw},
                {32, 48, 48, 48, -1.0F, -2.0F, aw},
                {0, 16, 0, 32, -2.0F, 0.0F, 4.0F},
                {16, 48, 0, 48, -2.0F, 0.0F, 4.0F}};
        Limbs out = new Limbs();
        for (int i = 0; i < 4; i++) {
            float[] s = spec[i];
            out.base[i] = segment((int) s[0], (int) s[1], s[4], s[5], s[6], 0.0F);
            out.over[i] = segment((int) s[2], (int) s[3], s[4], s[5], s[6], 0.25F);
        }
        return out;
    }

    private static ModelPart segment(int u, int v, float x, float top, float width, float grow) {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh =
                new net.minecraft.client.model.geom.builders.MeshDefinition();
        net.minecraft.client.model.geom.builders.CubeDeformation d =
                new net.minecraft.client.model.geom.builders.CubeDeformation(grow);
        net.minecraft.client.model.geom.builders.PartDefinition upper = mesh.getRoot().addOrReplaceChild("upper",
                net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(u, v)
                        .addBox(x, top, -2.0F, width, 6.0F, 4.0F, d),
                net.minecraft.client.model.geom.PartPose.ZERO);
        upper.addOrReplaceChild("lower",
                net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(u, v + 6)
                        .addBox(x, 0.0F, -2.0F, width, 6.0F, 4.0F, d),
                net.minecraft.client.model.geom.PartPose.offset(0.0F, top + 6.0F, 0.0F));
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot()
                .getChild("upper");
    }

    private static float rad(float deg) {
        return deg * ((float) Math.PI / 180.0F);
    }
}

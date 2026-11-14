package com.flash.client;

import com.flash.FlashMod;
import com.flash.superman.SupermanPowers;
import com.flash.superman.SupermanState;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Superman animations played by playerAnimator: every animation of the Man of Tomorrow pack (files in
 * {@code assets/flash/player_animation}); walking, running and jumping stay the Minecraft ones ({@link LegMask}). Three layers on every player: the base
 * (flight states, solar charge, jumps), a held layer while a power is kept
 * (heat vision, laser, breaths, holding a creature, the wound-up punch) and
 * one-shots (punch, clap, grab, throw, landings, takeoffs, brakes...).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SupermanPose {

    private static final ResourceLocation BASE = new ResourceLocation(FlashMod.MODID, "superman_base");
    private static final ResourceLocation HOLD = new ResourceLocation(FlashMod.MODID, "superman_hold");
    private static final ResourceLocation SHOT = new ResourceLocation(FlashMod.MODID, "superman_shot");
    private static final int SWITCH_DELAY = 3;

    private SupermanPose() {
    }

    /** Layers on every player (mod bus, client setup). */
    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(BASE, 1000, p -> new ModifierLayer<>());
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HOLD, 1100,
                    p -> new ModifierLayer<>(null, new LegMask(p, false)));
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(SHOT, 1200,
                    p -> new ModifierLayer<>(null, new LegMask(p, true)));
        }
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    private static KeyframeAnimation anim(String name) {
        return PlayerAnimationRegistry.getAnimation(new ResourceLocation(FlashMod.MODID, name));
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player, ResourceLocation id) {
        Object data = PlayerAnimationAccess.getPlayerAssociatedData(player).get(id);
        return data instanceof ModifierLayer<?> l ? (ModifierLayer<IAnimation>) l : null;
    }

    private static void set(AbstractClientPlayer player, ResourceLocation id, String name, int startTick, int fade,
                            boolean frozen) {
        ModifierLayer<IAnimation> layer = layer(player, id);
        if (layer == null) {
            return;
        }
        KeyframeAnimation a = name != null && !LANTERN.equals(name) ? anim(name) : null;
        IAnimation next = null;
        if (LANTERN.equals(name)) {
            // the Lanterns' flight and free fall (procedural, LanternFlight)
            next = new LanternFlight(player);
        } else if (a != null) {
            next = frozen ? new Frozen(a, startTick) : new KeyframeAnimationPlayer(a, startTick);
        }
        if (fade > 0) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), next);
        } else {
            layer.setAnimation(next);
        }
    }

    /** Keyframe player that stops on the last keyframe and holds it (Man of Tomorrow's ClipSampler clamps there). */
    private static final class Clamped extends KeyframeAnimationPlayer {
        private final int end;

        Clamped(KeyframeAnimation animation) {
            super(animation, 0);
            this.end = animation.endTick;
        }

        @Override
        public void tick() {
            if (this.getTick() < this.end) {
                super.tick();
            }
        }
    }

    /** Keyframe player that stays on its start tick (a pose held while a key is down). */
    private static final class Frozen extends KeyframeAnimationPlayer {
        Frozen(KeyframeAnimation animation, int tick) {
            super(animation, tick);
        }

        @Override
        public void tick() {
            // stays on the frame
        }
    }

    // ------------------------------------------------------------------
    // Per-player state
    // ------------------------------------------------------------------

    private static final class State {
        String base;
        String candidate;
        int candidateSince;
        String hold;
        String shot;
        int shotUntil;
        boolean wasFlying;
        boolean wasDiving;
        double lastSpeed;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public static void reset() {
        STATES.clear();
    }

    /** One-shot playing on a player (null when none). */
    public static String shot(UUID player) {
        State st = STATES.get(player);
        return st != null ? st.shot : null;
    }

    /** Base flight animation playing on a player (null when none). */
    public static String base(UUID player) {
        State st = STATES.get(player);
        return st != null ? st.base : null;
    }

    /** One-shot animation from the server; "name@tick" starts it at that tick (to meet the hit). */
    public static void play(UUID playerId, String animation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getPlayerByUUID(playerId) instanceof AbstractClientPlayer player)) {
            return;
        }
        int offset = 0;
        int at = animation.indexOf('@');
        if (at > 0) {
            try {
                offset = Integer.parseInt(animation.substring(at + 1));
            } catch (NumberFormatException ignored) {
                offset = 0;
            }
            animation = animation.substring(0, at);
        }
        State st = STATES.computeIfAbsent(playerId, id -> new State());
        if ("none".equals(animation)) {
            st.shot = null;
            // (the coil fades out over 7 ticks, as in Man of Tomorrow)
            set(player, SHOT, null, 0, 7, false);
            return;
        }
        shot(player, st, animation, offset);
    }

    /** Base "animation" name of the Lanterns' procedural flight and free fall (LanternFlight). */
    public static final String LANTERN = "lantern";

    /** Whole-body moves: they keep their own legs even on the move. */
    private static final java.util.Set<String> WHOLE_BODY = java.util.Set.of("takeoff", "takeoff8", "flight_launch",
            "landing", "landing8", "hero_landing9", "brake", "brake_boost", "charge_takeoff9", "charged_launch9",
            "super_combo");

    /**
     * Walking, running and jumping stay the Minecraft ones: while Superman is on foot and moving or in a normal jump,
     * the legs and the whole-body transform of the power animations are left to the game.
     */
    private static final class LegMask extends dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier {
        private final AbstractClientPlayer player;
        private final boolean shots;

        LegMask(AbstractClientPlayer player, boolean shots) {
            this.player = player;
            this.shots = shots;
        }

        private boolean masked() {
            if (flying(this.player) || this.player.isPassenger()) {
                return false;
            }
            if (this.shots) {
                State st = STATES.get(this.player.getUUID());
                // Thor's moves keep their own legs too: with the walking legs under a moved torso the body
                // came apart
                if (st != null && st.shot != null && (WHOLE_BODY.contains(st.shot) || st.shot.startsWith("thor_") || st.shot.equals("lex_land") || st.shot.equals("lex_unibeam"))) {
                    return false;
                }
            }
            double vx = this.player.getX() - this.player.xo;
            double vz = this.player.getZ() - this.player.zo;
            return vx * vx + vz * vz > 2.5E-4D || !this.player.onGround();
        }

        @Override
        public dev.kosmx.playerAnim.core.util.Vec3f get3DTransform(String modelName,
                                                                    dev.kosmx.playerAnim.api.TransformType type,
                                                                    float tickDelta,
                                                                    dev.kosmx.playerAnim.core.util.Vec3f value) {
            if (("rightLeg".equals(modelName) || "leftLeg".equals(modelName) || "body".equals(modelName))
                    && this.masked()) {
                return value;
            }
            return super.get3DTransform(modelName, type, tickDelta, value);
        }
    }

    private static void shot(AbstractClientPlayer player, State st, String name, int offset) {
        KeyframeAnimation a = anim(name);
        if (a == null) {
            return;
        }
        st.shot = name;
        if ("charge_takeoff9".equals(name)) {
            // the coil of the charged takeoff, as Man of Tomorrow plays it: 4 ticks of fade in, then the crouch runs
            // to its last keyframe and holds there until the launch (or the release)
            st.shotUntil = player.tickCount + 400;
            ModifierLayer<IAnimation> layer = layer(player, SHOT);
            if (layer != null) {
                layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), new Clamped(a));
            }
            return;
        }
        // looping files (e.g. the solar release) still play once as a one-shot
        st.shotUntil = player.tickCount + Math.max(1, a.stopTick - offset);
        set(player, SHOT, name, offset, offset > 0 ? 0 : 2, false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        for (Player p : mc.level.players()) {
            if (p instanceof AbstractClientPlayer player) {
                update(player);
            }
        }
    }

    private static void update(AbstractClientPlayer player) {
        State st = STATES.get(player.getUUID());
        // the flight poses are Thor's too
        if (!SupermanState.isSuperman(player) && !com.flash.thor.ThorState.isThor(player) && !lex(player)
                && !YasClient.isYas(player) && !ScarletClient.isScarlet(player) && !StormClient.isStorm(player)) {
            LanternFlight.forget(player);
            if (st != null) {
                STATES.remove(player.getUUID());
                set(player, BASE, null, 0, 5, false);
                set(player, HOLD, null, 0, 5, false);
                set(player, SHOT, null, 0, 5, false);
            }
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(player.getUUID(), st);
        }
        LanternFlight.tick(player, flying(player) && !player.isPassenger() && !player.isSwimming());
        // base, with a short delay so it does not flicker on the thresholds
        String want = chooseBase(player, st);
        if (same(want, st.base)) {
            st.candidate = null;
        } else {
            if (!same(want, st.candidate)) {
                st.candidate = want;
                st.candidateSince = player.tickCount;
            }
            boolean instant = st.base == null || want == null || "dive9".equals(want) || LANTERN.equals(want)
                    || "solar_charge".equals(want) || "solar_charge".equals(st.base);
            if (instant || player.tickCount - st.candidateSince >= SWITCH_DELAY) {
                st.base = want;
                st.candidate = null;
                set(player, BASE, want, 0, 6, false);
            }
        }
        // held powers
        String hold = chooseHold(player);
        if (!same(hold, st.hold)) {
            st.hold = hold;
            if (hold != null && hold.endsWith("#4")) {
                set(player, HOLD, hold.substring(0, hold.length() - 2), 4, 3, true);
            } else {
                set(player, HOLD, hold, 0, 4, false);
            }
        }
        // one-shot finished
        if (st.shot != null && player.tickCount >= st.shotUntil) {
            st.shot = null;
            set(player, SHOT, null, 0, 6, false);
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean flying(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) {
            return player.getAbilities().flying;
        }
        return SupermanState.flag(player, SupermanPowers.F_FLYING)
                || com.flash.thor.ThorState.flag(player, com.flash.thor.ThorPowers.F_FLYING)
                || com.flash.lex.LexState.flag(player, com.flash.lex.LexSuit.F_FLY)
                || YasClient.flying(player) || ScarletClient.flying(player) || StormClient.flying(player);
    }

    /** Lex's suit on (no powers of his own): his moves play here too, with Iron Man's flight poses. */
    private static boolean lex(AbstractClientPlayer player) {
        return com.flash.lex.LexState.flag(player, com.flash.lex.LexSuit.F_ON);
    }


    /** Base animation from the flight state and the motion. */
    private static String chooseBase(AbstractClientPlayer player, State st) {
        boolean flying = flying(player);
        double vx = player.getX() - player.xo;
        double vy = player.getY() - player.yo;
        double vz = player.getZ() - player.zo;
        double yaw = Math.toRadians(player.yBodyRot);
        double forward = -vx * Math.sin(yaw) + vz * Math.cos(yaw);
        double left = vx * Math.cos(yaw) + vz * Math.sin(yaw);
        double hs = Math.sqrt(vx * vx + vz * vz);
        double speed = Math.sqrt(hs * hs + vy * vy);
        double lastSpeed = st.lastSpeed;
        st.lastSpeed = speed;
        // (no takeoff one-shot: the pack's takeoff lies flat before the flight pose, it looked wrong)
        if (!flying && st.wasFlying && player.onGround() && st.shot == null) {
            shot(player, st, hs > 0.25D ? "landing8" : "landing", 0);
        }
        st.wasFlying = flying;
        if (player.isPassenger() || player.isSwimming()) {
            st.wasDiving = false;
            return null;
        }
        if (!flying) {
            // on foot: the normal Minecraft motion; dropping through the air: the Lanterns' free fall
            st.wasDiving = false;
            return LanternFlight.falling(player, false) || LanternFlight.active(player) && !player.onGround()
                    ? LANTERN : null;
        }
        // braking out of a fast flight
        if (lastSpeed > 0.7D && speed < lastSpeed - 0.15D && speed < 0.45D && st.shot == null) {
            shot(player, st, lastSpeed > 1.2D ? "brake_boost" : "brake", 0);
        }
        boolean diving = vy < -0.7D && -vy > hs;
        if (diving && !st.wasDiving && player == Minecraft.getInstance().player) {
            player.playSound(com.flash.registry.ModSounds.DIVE.get(), 0.8F, 1.0F);
        }
        st.wasDiving = diving;
        // high up in the sunlight and still: basking, arms open to the sun (the recharge spot)
        if (hs < 0.08D && Math.abs(vy) < 0.15D && basking(player) && !lex(player)) {
            return "solar_bask";
        }
        // every other flight: the Lanterns' (LanternFlight: upright and leaning slow, a dart fast)
        return LANTERN;
    }

    /** Hovering high in daylight (the solar recharge spot). */
    public static boolean basking(AbstractClientPlayer player) {
        if (player.getY() <= SupermanPowers.HIGH_ALTITUDE || player.level().isRaining()) {
            return false;
        }
        long day = player.level().getDayTime() % 24000L;
        return (day < 12300L || day > 23850L)
                && player.level().canSeeSky(net.minecraft.core.BlockPos.containing(player.getEyePosition()));
    }

    private static String chooseHold(AbstractClientPlayer player) {
        if (SupermanState.flag(player, SupermanPowers.F_ULTIMATE)) {
            return "solar_charge";
        }
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
        // (charging the punch keeps the normal arms, as in Man of Tomorrow: the Super Punch plays on release)
        return null;
    }
}

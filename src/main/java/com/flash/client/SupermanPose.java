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
 * Superman animations played by playerAnimator (the files of the pack in
 * {@code assets/flash/player_animation}). Three layers on every player: the base
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
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HOLD, 1100, p -> new ModifierLayer<>());
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(SHOT, 1200, p -> new ModifierLayer<>());
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
        KeyframeAnimation a = name != null ? anim(name) : null;
        IAnimation next = null;
        if (a != null) {
            next = frozen ? new Frozen(a, startTick) : new KeyframeAnimationPlayer(a, startTick);
        }
        if (fade > 0) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), next);
        } else {
            layer.setAnimation(next);
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
            set(player, SHOT, null, 0, 4, false);
            return;
        }
        shot(player, st, animation, offset);
    }

    /** Only the flight animations of the pack are played; everything else keeps the normal Minecraft motion. */
    private static final java.util.Set<String> FLIGHT_SHOTS = java.util.Set.of("takeoff", "takeoff8", "flight_launch",
            "landing", "landing8", "hero_landing9", "brake", "brake_boost", "charge_takeoff9", "charged_launch9",
            "super_combo", "grab_reach", "grab_throw");

    private static void shot(AbstractClientPlayer player, State st, String name, int offset) {
        if (!FLIGHT_SHOTS.contains(name)) {
            return;
        }
        KeyframeAnimation a = anim(name);
        if (a == null) {
            return;
        }
        st.shot = name;
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
        if (!SupermanState.isSuperman(player)) {
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
        // base, with a short delay so it does not flicker on the thresholds
        String want = chooseBase(player, st);
        if (same(want, st.base)) {
            st.candidate = null;
        } else {
            if (!same(want, st.candidate)) {
                st.candidate = want;
                st.candidateSince = player.tickCount;
            }
            boolean instant = st.base == null || want == null || "dive9".equals(want)
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
        return SupermanState.flag(player, SupermanPowers.F_FLYING);
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
        if (flying && !st.wasFlying && st.shot == null) {
            shot(player, st, hs > 0.25D ? "takeoff8" : "flight_launch", 0);
        }
        if (!flying && st.wasFlying && player.onGround() && st.shot == null) {
            shot(player, st, hs > 0.25D ? "landing8" : "landing", 0);
        }
        st.wasFlying = flying;
        if (player.isPassenger() || player.isSwimming()) {
            st.wasDiving = false;
            return null;
        }
        if (!flying) {
            // on foot: the normal Minecraft motion
            st.wasDiving = false;
            return null;
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
        if (diving) {
            return "dive9";
        }
        if (vy > 0.8D && vy > hs * 1.5D) {
            // straight up toward the sun, fists overhead
            return "solar_vertical";
        }
        if (vy > 0.45D && vy > hs) {
            return "upright_flight9";
        }
        if (vy < -0.15D && hs < 0.25D) {
            return "descend";
        }
        if (hs < 0.08D) {
            // high up in the sunlight: basking, arms open to the sun
            return basking(player) ? "solar_bask" : "hover";
        }
        if (forward < -0.05D && -forward > Math.abs(left)) {
            return "reverse_flight";
        }
        if (Math.abs(left) > Math.abs(forward)) {
            if (hs < 0.35D) {
                return left > 0.0D ? "upright_left9" : "upright_right9";
            }
            return left > 0.0D ? "strafe_left" : "strafe_right";
        }
        if (speed > 1.8D) {
            return "prone_flight12";
        }
        if (speed > 0.8D) {
            return "boost";
        }
        if (Math.abs(left) > 0.35D * forward) {
            return left > 0.0D ? "bank_left" : "bank_right";
        }
        return "cruise";
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
        if (SupermanState.flag(player, SupermanPowers.F_HOLDING)) {
            return "grab_hold";
        }
        if (player != null) {
            // the other powers keep the normal Minecraft arms
            return null;
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
        if (SupermanState.flag(player, SupermanPowers.F_CHARGE)) {
            // the punch wound up and held until the key is released
            return "super_punch#4";
        }
        return null;
    }
}

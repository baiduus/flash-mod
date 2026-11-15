package com.flash.client;

import com.flash.FlashMod;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spider-Man's body, ported from Powerborne Heroes' Spider-Man (its Palladium animations, used with permission) to a
 * playerAnimator layer of the mod's own, without Palladium: the same poses, numbers and timers (each one a 0..1
 * progress growing a tick at a time while its state lasts, Palladium's "animation timers"), applied as its
 * animation builder does (each part eased from where it is toward the pose by the progress, in priority order).
 * <ul>
 * <li>web swing: one arm (or both) up the line, the body pitched along the rope and banked into the turns, the legs
 * trailing the arc; hanging upside down on the line;</li>
 * <li>letting go: one of eight releases (front / back flips, spins, spread, a dive...), then the dive while
 * falling;</li>
 * <li>wall and ceiling crawling (the limbs climbing with the motion, blended by where he faces), the wall jump, the
 * double jump with its flip, perching crouched;</li>
 * <li>the web-zip (shot, burst), web shots, the spider-sense deflect, the flying kick.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpiderAnim implements IAnimation {

    private static final ResourceLocation LAYER = new ResourceLocation(FlashMod.MODID, "spider_anim");
    private static final float FLIP_PIVOT_Y = 20.0F;
    private static final float CLIMB_FLIP_PIVOT_Y = 14.4F;

    // ------------------------------------------------------------------
    // Timers (Palladium's animation_timer: +1 a tick while on, -1 while off, progress = value / max)
    // ------------------------------------------------------------------

    static final int SWING = 0;
    static final int HANG = 1;
    static final int RELEASE = 2;
    static final int RELEASE_LIMBS = 3;
    static final int DIVE = 4;
    static final int GROUND = 5;
    static final int PERCH_DELAY = 6;
    static final int PERCH = 7;
    static final int WALL = 8;
    static final int CEILING = 9;
    static final int WALL_JUMP = 10;
    static final int DOUBLE_JUMP = 11;
    static final int DOUBLE_LIMBS = 12;
    static final int ZIP_SHOOT = 13;
    static final int ZIP_BURST = 14;
    static final int SHOOT = 15;
    static final int DEFLECT = 16;
    static final int KICK = 17;
    private static final int[] MAX = {4, 10, 16, 7, 12, 5, 15, 12, 5, 8, 10, 12, 5, 6, 8, 4, 3, 4};
    /** Whole turns: they never run back (360 degrees is where they started). */
    private static final boolean[] ONE_SHOT = {false, false, true, false, false, false, false, false, false, false,
            false, true, false, false, false, false, false, false};

    // events (Powerborne's tags, held for a number of ticks)
    public static final int EV_RELEASE = 0;
    public static final int EV_WALL_JUMP = 1;
    public static final int EV_DOUBLE_JUMP = 2;
    public static final int EV_ZIP = 3;
    public static final int EV_SHOOT = 4;
    public static final int EV_DEFLECT = 5;
    public static final int EV_KICK = 6;

    /** Per player. */
    private static final class S {
        final int[] val = new int[MAX.length];
        final int[] prev = new int[MAX.length];
        int releaseTag;
        int wallJumpTag;
        int doubleTag;
        int zipShootTag;
        int zipBurstTag;
        int shootTag;
        int deflectTag;
        int kickTag;
        boolean diveTag;
        int armVariant = -1;
        int releaseVariant = -1;
        int diveVariant = -1;
        int jumpFlip;
        // swing physics (spiderman_anim.js updateSwingState)
        float bankPrev;
        float bankCurr;
        float diveTiltPrev;
        float diveTiltCurr;
        float swingPitchPrev;
        float swingPitchCurr;
        float ropePitch;
        float legTrailPrev;
        float legTrailCurr;
        float freeArmPrev;
        float freeArmCurr;
        float rollPrev;
        float rollCurr;
        // climbing (wall_crawling_anim.js)
        float phasePrev;
        float phase;
        float legPhasePrev;
        float legPhase;
        float facingPrev;
        float facing;
        float vertW = 1.0F;
        float strafeW;
        float swingSign = 1.0F;
        float nx;
        float nz;
        // perch
        float perchMovePrev;
        float perchMove;
        // the frame cache
        float cachePt = -1.0F;
        int cacheTick = -1;
        final Map<String, List<Op>> ops = new HashMap<>();
    }

    private static final Map<Integer, S> STATES = new HashMap<>();

    private final AbstractClientPlayer player;

    private SpiderAnim(AbstractClientPlayer player) {
        this.player = player;
    }

    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1250, SpiderAnim::new);
        }
    }

    public static void reset() {
        STATES.clear();
    }

    private static S state(Player p) {
        return STATES.computeIfAbsent(p.getId(), k -> new S());
    }

    /** Something he did (from his own client's motion or the server's effects): the poses that go with it. */
    public static void event(Player p, int kind, int variant) {
        S s = state(p);
        switch (kind) {
            case EV_RELEASE -> {
                s.releaseTag = 16;
                s.releaseVariant = variant;
                s.val[RELEASE] = 0;
                s.prev[RELEASE] = 0;
                s.diveTag = true;
            }
            case EV_WALL_JUMP -> {
                s.wallJumpTag = 16;
                s.diveTag = true;
            }
            case EV_DOUBLE_JUMP -> {
                s.doubleTag = 12;
                s.jumpFlip = variant;
                s.val[DOUBLE_JUMP] = 0;
                s.prev[DOUBLE_JUMP] = 0;
            }
            case EV_ZIP -> {
                s.zipShootTag = 6;
                s.zipBurstTag = 21;
                s.diveTag = true;
            }
            case EV_SHOOT -> s.shootTag = 5;
            case EV_DEFLECT -> s.deflectTag = 4;
            case EV_KICK -> s.kickTag = 12;
            default -> {
            }
        }
    }

    /** The swing's camera roll of the local player (degrees). */
    public static float roll(Player p, float pt) {
        S s = STATES.get(p.getId());
        return s == null ? 0.0F : Mth.lerp(pt, s.rollPrev, s.rollCurr);
    }

    // ------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        STATES.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player pl) || !SpiderClient.isSpider(pl));
        for (Player p : mc.level.players()) {
            if (SpiderClient.isSpider(p)) {
                tick(p, state(p));
            }
        }
    }

    private static void timer(S s, int t, boolean on) {
        s.prev[t] = s.val[t];
        if (on) {
            s.val[t] = Math.min(MAX[t], s.val[t] + 1);
        } else if (ONE_SHOT[t] && s.val[t] >= MAX[t]) {
            s.val[t] = 0;
            s.prev[t] = 0;
        } else {
            s.val[t] = Math.max(0, s.val[t] - 1);
        }
    }

    private static void tick(Player p, S s) {
        Vec3 anchor = SpiderClient.line(p);
        boolean swinging = anchor != null;
        Vec3 wall = SpiderClient.wallNormal(p);
        boolean ceiling = SpiderClient.onCeiling(p);
        boolean hanging = SpiderClient.hanging(p);
        boolean ground = p.onGround();
        if (ground || swinging || wall != null || ceiling) {
            s.diveTag = false;
        }
        s.releaseTag = Math.max(0, s.releaseTag - 1);
        s.wallJumpTag = Math.max(0, s.wallJumpTag - 1);
        s.doubleTag = Math.max(0, s.doubleTag - 1);
        s.zipShootTag = Math.max(0, s.zipShootTag - 1);
        s.zipBurstTag = Math.max(0, s.zipBurstTag - 1);
        s.shootTag = Math.max(0, s.shootTag - 1);
        s.deflectTag = Math.max(0, s.deflectTag - 1);
        s.kickTag = Math.max(0, s.kickTag - 1);
        boolean burst = s.zipBurstTag > 0 && s.zipShootTag <= 0;
        timer(s, SWING, swinging);
        timer(s, HANG, hanging);
        timer(s, RELEASE, s.releaseTag > 0);
        timer(s, RELEASE_LIMBS, s.releaseTag > 0 && s.val[RELEASE] <= 7);
        timer(s, DIVE, s.diveTag && s.releaseTag <= 0 && !burst && s.wallJumpTag <= 0 && s.doubleTag <= 0);
        timer(s, GROUND, ground);
        boolean crouchStill = p.isCrouching() && ground && !swinging;
        timer(s, PERCH_DELAY, crouchStill);
        timer(s, PERCH, crouchStill && s.val[PERCH_DELAY] >= MAX[PERCH_DELAY]);
        timer(s, WALL, wall != null);
        timer(s, CEILING, ceiling);
        timer(s, WALL_JUMP, s.wallJumpTag > 0);
        timer(s, DOUBLE_JUMP, s.doubleTag > 0);
        timer(s, DOUBLE_LIMBS, s.doubleTag > 0 && s.val[DOUBLE_JUMP] <= 5);
        timer(s, ZIP_SHOOT, s.zipShootTag > 0);
        timer(s, ZIP_BURST, burst);
        timer(s, SHOOT, s.shootTag > 0);
        timer(s, DEFLECT, s.deflectTag > 0);
        timer(s, KICK, s.kickTag > 0);
        if (s.val[SWING] <= 0 && !swinging && s.val[DIVE] <= 0) {
            s.armVariant = -1;
        }
        if (swinging && s.armVariant < 0) {
            s.armVariant = SpiderClient.armVariant(p);
        }
        if (s.val[DIVE] <= 0 && !s.diveTag) {
            s.diveVariant = -1;
        }
        if (s.diveTag && s.diveVariant < 0) {
            s.diveVariant = p.getRandom().nextInt(2);
        }
        swingPhysics(p, s, anchor);
        if (wall != null) {
            s.nx = (float) wall.x;
            s.nz = (float) wall.z;
            climbState(p, s);
        }
        boolean moving = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) > 0.01D;
        s.perchMovePrev = s.perchMove;
        s.perchMove = moving ? Math.min(1.0F, s.perchMove + 0.05F) : Math.max(0.0F, s.perchMove - 0.05F);
    }

    /** spiderman_anim.js updateSwingState, a tick at a time (and the camera roll of spiderman_client.js). */
    private static void swingPhysics(Player p, S s, Vec3 anchor) {
        s.bankPrev = s.bankCurr;
        s.diveTiltPrev = s.diveTiltCurr;
        s.swingPitchPrev = s.swingPitchCurr;
        s.legTrailPrev = s.legTrailCurr;
        s.freeArmPrev = s.freeArmCurr;
        s.rollPrev = s.rollCurr;
        Vec3 v = p.getDeltaMovement();
        double vx = v.x;
        double vy = v.y;
        double vz = v.z;
        double yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double side = vx * Math.sin(yaw) - vz * Math.cos(yaw);
        s.bankCurr += ((float) Mth.clamp(side * 22.0D, -28.0D, 28.0D) - s.bankCurr) * 0.12F;
        float down = (float) Mth.clamp(-vy, 0.0D, 2.5D);
        s.diveTiltCurr += (-down * 50.0F - s.diveTiltCurr) * 0.18F;
        float onGround = MAX[GROUND] > 0 ? s.val[GROUND] / (float) MAX[GROUND] : 0.0F;
        float target = 0.0F;
        if (anchor != null) {
            double rx = p.getX() - anchor.x;
            double ry = p.getY() + 1.0D - anchor.y;
            double rz = p.getZ() - anchor.z;
            double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (len > 1.0E-6D) {
                double fwd = rx / len * -Math.sin(yaw) + rz / len * Math.cos(yaw);
                target = (float) Math.toDegrees(Math.atan2(fwd, -ry / len));
            }
            if (onGround <= 0.0F) {
                s.ropePitch = s.ropePitch + Mth.wrapDegrees(target - s.ropePitch);
            } else {
                s.ropePitch += (0.0F - s.ropePitch) * 0.25F;
                s.swingPitchCurr += (0.0F - s.swingPitchCurr) * 0.25F;
            }
        } else {
            s.ropePitch = Mth.wrapDegrees(s.ropePitch);
            s.swingPitchCurr = Mth.wrapDegrees(s.swingPitchCurr);
            s.swingPitchPrev = Mth.wrapDegrees(s.swingPitchPrev);
        }
        double speed = v.length();
        float tension = (float) Mth.clamp(speed / 2.5D, 0.0D, 1.0D);
        float wrapped = Mth.wrapDegrees(s.ropePitch);
        float base = s.ropePitch - wrapped;
        float absW = Math.abs(wrapped);
        float follow = 0.4F + 0.6F * tension;
        boolean catching = Math.abs(s.swingPitchCurr - s.ropePitch) > 40.0F;
        if (!catching) {
            follow += (1.0F - follow) * Mth.clamp((absW - 70.0F) / 40.0F, 0.0F, 1.0F);
        }
        float targetPitch = anchor != null ? base + wrapped * follow : s.swingPitchCurr;
        if (onGround > 0.0F) {
            targetPitch *= 1.0F - onGround;
        }
        float rate = 0.08F + tension * 0.17F;
        if (!catching && absW > 90.0F) {
            rate = Math.max(rate, 0.4F);
        }
        s.swingPitchCurr += (targetPitch - s.swingPitchCurr) * rate;
        float fallTrail = (float) Mth.clamp(-vy, 0.0D, 2.0D) * 16.0F;
        float legArc = Mth.sin(s.ropePitch * Mth.DEG_TO_RAD);
        s.legTrailCurr += (Mth.clamp(-legArc * 110.0F - fallTrail, -110.0F, 90.0F) - s.legTrailCurr) * 0.2F;
        s.freeArmCurr += (Mth.clamp(-18.0F - legArc * 40.0F, -80.0F, 30.0F) - s.freeArmCurr) * 0.25F;
        // the camera roll (lateral speed around the anchor)
        float roll = 0.0F;
        if (anchor != null) {
            double rx = p.getX() - anchor.x;
            double rz = p.getZ() - anchor.z;
            double rh = Math.sqrt(rx * rx + rz * rz);
            if (rh > 1.0E-4D) {
                double lateral = (-vx * rz + vz * rx) / rh;
                roll = (float) Mth.clamp(lateral * 4.0D, -15.0D, 15.0D);
            }
        }
        s.rollCurr += (roll - s.rollCurr) * (Math.abs(roll) > Math.abs(s.rollCurr) ? 0.12F : 0.08F);
    }

    /** wall_crawling_anim.js updateClimbAnimState, a tick at a time. */
    private static void climbState(Player p, S s) {
        Vec3 v = p.getDeltaMovement();
        double vy = p.getY() - p.yo;
        double strafe = v.x * -s.nz + v.z * s.nx;
        s.phasePrev = s.phase;
        s.legPhasePrev = s.legPhase;
        s.facingPrev = s.facing;
        double speed = Math.abs(vy) * 1.5D + Math.abs(strafe) * 1.5D;
        if (speed > 0.001D) {
            s.phase += (float) speed;
            s.legPhase += (float) speed;
            double total = Math.abs(vy) + Math.abs(strafe);
            if (total > 0.001D) {
                s.vertW += ((float) (Math.abs(vy) / total) - s.vertW) * 0.2F;
                s.strafeW += ((float) (Math.abs(strafe) / total) - s.strafeW) * 0.2F;
                s.swingSign += ((strafe >= 0.0D ? 1.0F : -1.0F) - s.swingSign) * 0.2F;
            }
        }
        double yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double lx = -Math.sin(yaw);
        double lz = Math.cos(yaw);
        double dot = lx * s.nx + lz * s.nz;
        double cross = lx * s.nz - lz * s.nx;
        float facing = (float) Math.toDegrees(Math.atan2(cross, dot));
        s.facing = Mth.wrapDegrees(s.facing + Mth.wrapDegrees(facing - s.facing) * 0.15F);
    }

    // ------------------------------------------------------------------
    // The builder (Palladium's: each part eased toward its pose by the progress)
    // ------------------------------------------------------------------

    private enum Ease { LINEAR, IN_OUT_SINE, OUT_SINE, OUT_CUBIC, IN_OUT_CUBIC, OUT_QUAD }

    private static float ease(Ease e, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return switch (e) {
            case LINEAR -> t;
            case IN_OUT_SINE -> -(Mth.cos((float) Math.PI * t) - 1.0F) / 2.0F;
            case OUT_SINE -> Mth.sin(t * (float) Math.PI / 2.0F);
            case OUT_CUBIC -> 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            case IN_OUT_CUBIC -> t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
            case OUT_QUAD -> 1.0F - (1.0F - t) * (1.0F - t);
        };
    }

    /** One step on a part: actions in order, then eased by its progress. */
    private static final class Op {
        // actions: kind (0 set rot, 1 add rot, 2 set pos, 3 add pos, 4 negate rot, 5 add base rot), axis, value
        final List<float[]> acts = new ArrayList<>();
        Ease ease = Ease.LINEAR;
        float progress;
        float pivot = Float.NaN;

        Op setR(int axis, float deg) {
            this.acts.add(new float[] {0, axis, deg * Mth.DEG_TO_RAD});
            return this;
        }

        Op rotR(int axis, float deg) {
            this.acts.add(new float[] {1, axis, deg * Mth.DEG_TO_RAD});
            return this;
        }

        Op setP(int axis, float px) {
            this.acts.add(new float[] {2, axis, px});
            return this;
        }

        Op moveP(int axis, float px) {
            this.acts.add(new float[] {3, axis, px});
            return this;
        }

        Op neg(int axis) {
            this.acts.add(new float[] {4, axis, 0});
            return this;
        }

        Op baseR(int axis, float k) {
            this.acts.add(new float[] {5, axis, k});
            return this;
        }

        /** Palladium's body setY(a).setY2(-a): turning about a point a pixels up. */
        Op pivot(float px) {
            this.pivot = px;
            return this;
        }
    }

    private static final class Builder {
        final Map<String, List<Op>> ops;

        Builder(Map<String, List<Op>> ops) {
            this.ops = ops;
        }

        Op get(String part) {
            return new Op();
        }

        void animate(String part, Op op, Ease e, float progress) {
            if (progress <= 0.0F) {
                return;
            }
            op.ease = e;
            op.progress = progress;
            this.ops.computeIfAbsent(part, k -> new ArrayList<>()).add(op);
        }
    }

    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static float prog(S s, int t, float pt) {
        return Mth.lerp(pt, s.prev[t], s.val[t]) / MAX[t];
    }

    /** The poses of this frame (once per frame), in Palladium's priority order. */
    private static void build(Player p, S s, float pt) {
        s.ops.clear();
        Builder b = new Builder(s.ops);
        float onGroundP = prog(s, GROUND, pt);
        float swingP = prog(s, SWING, pt);
        float bank = Mth.lerp(pt, s.bankPrev, s.bankCurr);
        float diveTilt = Mth.lerp(pt, s.diveTiltPrev, s.diveTiltCurr);
        float swingPitch = Mth.lerp(pt, s.swingPitchPrev, s.swingPitchCurr);
        float legTrail = Mth.lerp(pt, s.legTrailPrev, s.legTrailCurr);
        float freeArm = Mth.lerp(pt, s.freeArmPrev, s.freeArmCurr);
        boolean reeling = SpiderClient.reeling(p);
        // 100: the legs on the line
        if (swingP > 0.0F) {
            float legP = swingP * (1.0F - onGroundP);
            if (reeling) {
                b.animate("rightLeg", new Op().setR(X, 28).setR(Y, 6).setP(Z, 0), Ease.IN_OUT_CUBIC, swingP);
                b.animate("leftLeg", new Op().setR(X, 22).setR(Y, -4).setP(Z, 0), Ease.IN_OUT_CUBIC, swingP);
            } else {
                b.animate("rightLeg", new Op().setR(X, legTrail + 4).setR(Y, 1).setR(Z, 3).setP(Z, 0),
                        Ease.IN_OUT_CUBIC, legP);
                b.animate("leftLeg", new Op().setR(X, legTrail - 4).setR(Y, -1).setR(Z, -2).moveP(Y, -1),
                        Ease.IN_OUT_CUBIC, legP);
            }
            b.animate("torso", new Op().setR(X, 0).setR(Y, 0).setR(Z, 0), Ease.OUT_CUBIC, swingP);
        }
        // 103: climbing the wall
        float wallP = prog(s, WALL, pt);
        if (wallP > 0.0F) {
            climbPose(b, s, pt, wallP);
        }
        // 104: on the ceiling
        float ceilP = prog(s, CEILING, pt);
        if (ceilP > 0.0F) {
            b.animate("head", new Op().setR(X, -55), Ease.OUT_CUBIC, ceilP);
            b.animate("body", new Op().setR(X, -90).setR(Z, 180).moveP(Y, 15), Ease.OUT_CUBIC, ceilP);
            b.animate("rightArm", new Op().setR(X, -114.6F).baseR(X, -0.45F), Ease.OUT_CUBIC, ceilP);
            b.animate("leftArm", new Op().setR(X, -114.6F).baseR(X, -0.45F), Ease.OUT_CUBIC, ceilP);
            b.animate("rightLeg", new Op().setR(X, -22.9F).baseR(X, -0.3F), Ease.OUT_CUBIC, ceilP);
            b.animate("leftLeg", new Op().setR(X, -22.9F).baseR(X, -0.3F), Ease.OUT_CUBIC, ceilP);
        }
        // 105: the wall jump
        float wjP = prog(s, WALL_JUMP, pt);
        if (wjP > 0.0F) {
            divePose(b, s, pt, wjP, 0, -10, onGroundP);
        }
        // 120 / 121: the release's limbs and pose, the double jump's
        float relP = prog(s, RELEASE, pt);
        float relLimbP = prog(s, RELEASE_LIMBS, pt);
        if (relLimbP > 0.0F && s.releaseVariant >= 0) {
            releaseLimbs(b, relLimbP, s.releaseVariant);
        }
        float djLimbP = prog(s, DOUBLE_LIMBS, pt);
        if (djLimbP > 0.0F) {
            releaseLimbs(b, djLimbP, s.jumpFlip);
        }
        if (relP > 0.0F && s.releaseVariant >= 0) {
            int v = s.releaseVariant;
            if (v == 4 || v == 6) {
                divePose(b, s, pt, relP, 0, -10, onGroundP);
            } else if (v == 0 || v == 1 || v == 5 || v == 7) {
                b.animate("body", new Op().pivot(FLIP_PIVOT_Y), Ease.OUT_SINE, relP);
            } else if (v == 2) {
                b.animate("rightArm", new Op().setR(X, 1).setR(Y, 24).setR(Z, 73).setP(Y, 3), Ease.OUT_SINE, relP);
                b.animate("leftArm", new Op().setR(X, 1).setR(Y, -24).setR(Z, -73).setP(Y, 3), Ease.OUT_SINE, relP);
                b.animate("rightLeg", new Op().setR(X, 47).setR(Y, 1).setR(Z, 12), Ease.OUT_SINE, relP);
                b.animate("leftLeg", new Op().setR(X, 47).setR(Y, -1).setR(Z, -12), Ease.OUT_SINE, relP);
                b.animate("body", new Op().rotR(X, -10).pivot(FLIP_PIVOT_Y), Ease.OUT_SINE, relP);
            }
        }
        float djP = prog(s, DOUBLE_JUMP, pt);
        if (djP > 0.0F) {
            b.animate("body", new Op().pivot(FLIP_PIVOT_Y), Ease.OUT_SINE, djP);
        }
        // 198: the dive
        float diveP = prog(s, DIVE, pt);
        if (diveP > 0.0F && s.diveVariant >= 0) {
            divePose(b, s, pt, diveP, s.diveVariant, diveTilt, onGroundP);
        }
        // 199 / 201: the web-zip
        float zsP = prog(s, ZIP_SHOOT, pt);
        if (zsP > 0.0F) {
            float legP = zsP * (1.0F - onGroundP);
            float pitch = Mth.clamp(p.getViewXRot(pt), -90.0F, 90.0F);
            float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot)
                    - Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot)), -75.0F, 75.0F);
            b.animate("body", new Op().setR(Z, 0).setR(X, 0), Ease.OUT_CUBIC, zsP);
            b.animate("rightArm", new Op().setR(X, pitch - 90).setR(Y, yaw).setR(Z, 6).setP(Z, -3).setP(X, -3)
                    .setP(Y, 3), Ease.OUT_CUBIC, zsP);
            b.animate("leftArm", new Op().setR(X, 65).setR(Y, -8).setR(Z, -16).setP(Z, 5).setP(X, 3), Ease.OUT_CUBIC,
                    zsP);
            b.animate("torso", new Op().setR(X, 28 * (1 - onGroundP)).setR(Z, -16 * (1 - onGroundP)).setR(Y, -42),
                    Ease.OUT_CUBIC, zsP);
            b.animate("rightLeg", new Op().setR(X, 60).setR(Z, 4).setR(Y, -15).setP(Z, -3).setP(Y, 10).setP(X, -2),
                    Ease.OUT_CUBIC, legP);
            b.animate("leftLeg", new Op().setR(X, 75).setR(Z, -30).setR(Y, -65).setP(X, 7).setP(Y, 8).setP(Z, 2),
                    Ease.OUT_CUBIC, legP);
        }
        float zbP = prog(s, ZIP_BURST, pt) * (1.0F - onGroundP) * (1.0F - wallP);
        if (zbP > 0.0F) {
            b.animate("rightArm", new Op().setR(X, 80).setR(Z, 90).setR(Y, 30).setP(Z, 4).setP(X, -4), Ease.OUT_CUBIC,
                    zbP);
            b.animate("leftArm", new Op().setR(X, 35).setR(Z, -16).setR(Y, 20).setP(Z, -1).setP(X, 4.5F),
                    Ease.OUT_CUBIC, zbP);
            b.animate("rightLeg", new Op().setR(X, 27).setR(Z, 5).setR(Y, 40).setP(Z, 1).setP(Y, 6).setP(X, -5),
                    Ease.OUT_CUBIC, zbP);
            b.animate("leftLeg", new Op().setR(X, 62).setR(Y, -20).setP(Z, -1).setP(Y, 10).setP(X, 5), Ease.OUT_CUBIC,
                    zbP);
            b.animate("torso", new Op().setR(X, 25).setR(Y, 22).setR(Z, 5), Ease.OUT_CUBIC, zbP);
            b.animate("body", new Op().setR(X, -10).setR(Z, 0), Ease.OUT_CUBIC, zbP);
            b.animate("head", new Op().rotR(X, -10), Ease.OUT_CUBIC, zbP);
        }
        // the flying kick (the zip slam's impact pose, a leg out)
        float kickP = prog(s, KICK, pt);
        if (kickP > 0.0F) {
            b.animate("rightArm", new Op().setP(Z, -3).setP(X, -5).setR(X, -60).setR(Y, -20).setR(Z, 30),
                    Ease.LINEAR, kickP);
            b.animate("leftArm", new Op().setR(X, 30).setR(Y, 0).setR(Z, -65).setP(Y, 3), Ease.LINEAR, kickP);
            b.animate("body", new Op().setR(X, -40), Ease.LINEAR, kickP);
            b.animate("leftLeg", new Op().setP(Z, -1).setP(Y, 5).setR(Y, -10).setR(X, -50), Ease.LINEAR, kickP);
            b.animate("rightLeg", new Op().setP(Z, -4).setP(Y, 8).setP(X, -4).setR(X, 20).setR(Z, -15), Ease.LINEAR,
                    kickP);
            b.animate("head", new Op().rotR(X, -40), Ease.LINEAR, kickP);
        }
        // 300: the arm on the line, the body along the rope
        if (swingP > 0.0F) {
            int arm = s.armVariant < 0 ? 0 : s.armVariant;
            if (arm == 0) {
                b.animate("rightArm", new Op().setR(X, -160).setR(Z, -5), Ease.OUT_CUBIC, swingP);
                b.animate("leftArm", new Op().setR(X, freeArm).setR(Y, -15).setR(Z, -18), Ease.OUT_CUBIC, swingP);
            } else if (arm == 1) {
                b.animate("leftArm", new Op().setR(X, -160).setR(Z, 5), Ease.OUT_CUBIC, swingP);
                b.animate("rightArm", new Op().setR(X, freeArm).setR(Y, 15).setR(Z, 18), Ease.OUT_CUBIC, swingP);
            } else {
                b.animate("rightArm", new Op().setR(X, -140).setR(Y, -30).setR(Z, 14), Ease.OUT_CUBIC, swingP);
                b.animate("leftArm", new Op().setR(X, -140).setR(Y, 30).setR(Z, -14), Ease.OUT_CUBIC, swingP);
            }
            float bodyPitch = (swingP < 1.0F ? Mth.wrapDegrees(swingPitch) : swingPitch) * (1.0F - onGroundP);
            float roll = Mth.lerp(pt, s.rollPrev, s.rollCurr);
            b.animate("body", new Op().setR(X, bodyPitch).setR(Z, roll * -0.9F)
                    .pivot(FLIP_PIVOT_Y), Ease.IN_OUT_SINE, swingP);
        }
        // 302: the release's / double jump's turn
        if (relP > 0.0F && s.releaseVariant >= 0) {
            releaseBody(b, relP, s.releaseVariant);
        }
        if (djP > 0.0F) {
            releaseBody(b, djP, s.jumpFlip);
        }
        // 320: hanging upside down
        float hangP = prog(s, HANG, pt);
        if (hangP > 0.0F) {
            b.animate("body", new Op().setR(Z, 180).pivot(FLIP_PIVOT_Y), Ease.IN_OUT_CUBIC, hangP);
            b.animate("head", new Op().neg(X).neg(Y), Ease.IN_OUT_CUBIC, hangP);
            b.animate("rightArm", new Op().setR(X, -42).setR(Y, -57).setR(Z, 12).setP(X, -4.5F).setP(Y, 1.5F)
                    .setP(Z, -1.5F), Ease.OUT_CUBIC, hangP);
            b.animate("leftArm", new Op().setR(X, -42).setR(Y, 58).setR(Z, -12).setP(X, 4.5F).setP(Y, 1.5F)
                    .setP(Z, -1.5F), Ease.OUT_CUBIC, hangP);
            b.animate("rightLeg", new Op().setR(X, 18).setR(Y, 44).setR(Z, -9).setP(X, -4.9F).setP(Y, 7.5F)
                    .setP(Z, -3), Ease.OUT_CUBIC, hangP);
            b.animate("leftLeg", new Op().setR(X, 8).setR(Y, -48).setR(Z, 15).setP(X, 5.4F).setP(Y, 6.5F)
                    .setP(Z, -2.5F), Ease.OUT_CUBIC, hangP);
        }
        // 900: perched, crouched on the edge
        float perchP = prog(s, PERCH, pt) * (1.0F - Mth.lerp(pt, s.perchMovePrev, s.perchMove));
        if (perchP > 0.0F) {
            b.animate("body", new Op().setP(Y, -5).setP(Z, 5), Ease.IN_OUT_CUBIC, perchP);
            b.animate("head", new Op().rotR(X, 8).setP(Y, 3).setP(Z, -7), Ease.IN_OUT_CUBIC, perchP);
            b.animate("torso", new Op().setR(X, 40).setP(Y, 2.5F).setP(Z, -7), Ease.IN_OUT_CUBIC, perchP);
            b.animate("rightArm", new Op().setR(X, -15).setR(Y, -40).setR(Z, 2).setP(X, -3).setP(Y, 6.25F)
                    .setP(Z, -7), Ease.IN_OUT_CUBIC, perchP);
            b.animate("leftArm", new Op().setR(X, -15).setR(Y, 40).setR(Z, -2).setP(X, 3).setP(Y, 6.25F)
                    .setP(Z, -7), Ease.IN_OUT_CUBIC, perchP);
            b.animate("rightLeg", new Op().setR(X, 51).setR(Y, 47).setP(X, -7.9F).setP(Y, 9).setP(Z, -5.5F),
                    Ease.IN_OUT_CUBIC, perchP);
            b.animate("leftLeg", new Op().setR(X, 51).setR(Y, -47).setP(X, 7.9F).setP(Y, 9).setP(Z, -5.5F),
                    Ease.IN_OUT_CUBIC, perchP);
        }
        // 1000: a web shot from the wrist (the arm along the aim)
        float shootP = prog(s, SHOOT, pt);
        if (shootP > 0.0F) {
            float pitch = Mth.clamp(p.getViewXRot(pt), -90.0F, 90.0F);
            float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot)
                    - Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot)), -75.0F, 75.0F);
            b.animate("rightArm", new Op().setR(X, pitch - 90).setR(Y, yaw).setR(Z, 0).moveP(Y, -1).moveP(X, -1),
                    Ease.OUT_CUBIC, shootP);
        }
        // 1001: the spider-sense deflect
        float defP = prog(s, DEFLECT, pt);
        if (defP > 0.0F) {
            b.animate("rightArm", new Op().setR(X, -100).setR(Y, -55).setR(Z, -20).setP(X, -5).setP(Y, 4)
                    .setP(Z, -1), Ease.OUT_QUAD, defP);
        }
    }

    private static void climbPose(Builder b, S s, float pt, float progress) {
        float phase = Mth.lerp(pt, s.phasePrev, s.phase);
        float limbSwing = Mth.sin(phase);
        float legPhase = Mth.lerp(pt, s.legPhasePrev, s.legPhase);
        float facing = s.facingPrev + Mth.wrapDegrees(s.facing - s.facingPrev) * pt;
        float front = Math.max(0.0F, 1.0F - Math.abs(facing) / 90.0F);
        float sideB = Math.max(0.0F, 1.0F - Math.abs(Math.abs(facing) - 90.0F) / 90.0F);
        float back = Math.max(0.0F, 1.0F - Math.abs(Math.abs(facing) - 180.0F) / 90.0F);
        boolean rightSide = facing >= 0.0F;
        float vRA = -20.0F * limbSwing;
        float sRA = -20.0F * limbSwing * s.swingSign;
        float swRA = vRA * s.vertW + sRA * s.strafeW;
        float swLA = -swRA;
        b.animate("body", new Op().pivot(CLIMB_FLIP_PIVOT_Y), Ease.OUT_CUBIC, progress);
        // [front, sideRight, sideLeft, back] x [x, y, z]
        float[][] ra = {{-140, 0, 0}, {-80, -5, 25}, {-5, 0, 15}, {35, 0, 20}};
        float[][] la = {{-140, 0, 0}, {5, 0, -15}, {-80, 5, -25}, {35, 0, -20}};
        float[][] rl = {{-3, 0, 0}, {10, 0, 10}, {0, 0, -5}, {40, 15, 0}};
        float[][] ll = {{-3, 0, 0}, {0, 0, 5}, {10, 0, -10}, {30, -20, 0}};
        float[] r = blend(ra, front, sideB, back, rightSide);
        float[] l = blend(la, front, sideB, back, rightSide);
        b.animate("rightArm", new Op().setR(X, r[0] + swRA * front).setR(Y, r[1]).setR(Z, r[2]), Ease.OUT_CUBIC,
                progress);
        b.animate("leftArm", new Op().setR(X, l[0] + swLA * front).setR(Y, l[1]).setR(Z, l[2]), Ease.OUT_CUBIC,
                progress);
        float[] rlp = blend(rl, front, sideB, back, rightSide);
        float[] llp = blend(ll, front, sideB, back, rightSide);
        float f1 = (Mth.cos(legPhase) + 1.0F) / 2.0F * front;
        float f2 = (Mth.cos(legPhase + (float) Math.PI) + 1.0F) / 2.0F * front;
        b.animate("torso", new Op().setR(X, 20 * back), Ease.OUT_CUBIC, progress);
        b.animate("rightLeg", new Op().setR(X, rlp[0]).setR(Y, rlp[1]).setR(Z, rlp[2] + f1 * -8)
                .moveP(X, -2 * back - f1).moveP(Y, -3 * back - f1 * 6).setP(Z, -1 * back + f1 * -3), Ease.OUT_CUBIC,
                progress);
        b.animate("leftLeg", new Op().setR(X, llp[0]).setR(Y, llp[1]).setR(Z, llp[2] + f2 * 8)
                .moveP(X, 2 * back + f2).moveP(Y, -2 * back - f2 * 6).setP(Z, -1 * back + f2 * -3), Ease.OUT_CUBIC,
                progress);
    }

    private static float[] blend(float[][] poses, float front, float side, float back, boolean rightSide) {
        float[] sd = rightSide ? poses[1] : poses[2];
        return new float[] {poses[0][0] * front + sd[0] * side + poses[3][0] * back,
                poses[0][1] * front + sd[1] * side + poses[3][1] * back,
                poses[0][2] * front + sd[2] * side + poses[3][2] * back};
    }

    private static void releaseBody(Builder b, float progress, int variant) {
        switch (variant) {
            case 0 -> b.animate("body", new Op().rotR(X, -360).pivot(FLIP_PIVOT_Y), Ease.IN_OUT_SINE, progress);
            case 1, 5 -> b.animate("body", new Op().rotR(X, 360).pivot(FLIP_PIVOT_Y), Ease.IN_OUT_SINE, progress);
            case 3 -> b.animate("body", new Op().rotR(Y, 360), Ease.OUT_SINE, progress);
            case 7 -> b.animate("body", new Op().rotR(Y, -360), Ease.OUT_SINE, progress);
            default -> {
            }
        }
    }

    private static void releaseLimbs(Builder b, float progress, int variant) {
        switch (variant) {
            case 0, 1 -> {
                b.animate("head", new Op().rotR(X, 30), Ease.OUT_CUBIC, progress);
                b.animate("torso", new Op().setR(X, 5), Ease.OUT_CUBIC, progress);
                b.animate("rightArm", new Op().setR(X, -20).setR(Z, -5), Ease.OUT_CUBIC, progress);
                b.animate("leftArm", new Op().setR(X, -5).setR(Z, 5), Ease.OUT_CUBIC, progress);
                b.animate("rightLeg", new Op().setR(X, -5).setP(Y, 2).setP(Z, -3.5F), Ease.OUT_CUBIC, progress);
                b.animate("leftLeg", new Op().setR(X, 0).setP(Y, 2).setP(Z, -3.5F), Ease.OUT_CUBIC, progress);
            }
            case 3 -> {
                b.animate("head", new Op().setR(Y, -30), Ease.OUT_CUBIC, progress);
                b.animate("rightArm", new Op().setR(X, 6).setR(Z, -15).setR(Y, 30).setP(X, -5).setP(Z, 2).setP(Y, 2),
                        Ease.OUT_CUBIC, progress);
                b.animate("leftArm", new Op().setR(X, -1).setR(Z, 15).setR(Y, 30).setP(X, 5).setP(Z, -2).setP(Y, 2),
                        Ease.OUT_CUBIC, progress);
                b.animate("rightLeg", new Op().setR(X, -15).setR(Z, -5), Ease.OUT_CUBIC, progress);
                b.animate("leftLeg", new Op().setR(X, 15).setR(Z, 5).setR(Y, -15), Ease.OUT_CUBIC, progress);
            }
            case 5 -> {
                b.animate("head", new Op().setR(X, -10), Ease.OUT_CUBIC, progress);
                b.animate("rightArm", new Op().setR(X, 3).setR(Y, 90).setR(Z, 85), Ease.OUT_CUBIC, progress);
                b.animate("leftArm", new Op().setR(X, 3).setR(Y, -90).setR(Z, -85), Ease.OUT_CUBIC, progress);
                b.animate("rightLeg", new Op().setR(X, 34), Ease.OUT_CUBIC, progress);
                b.animate("leftLeg", new Op().setR(X, 25), Ease.OUT_CUBIC, progress);
            }
            case 7 -> {
                b.animate("head", new Op().setR(Z, -10).setP(X, 1), Ease.OUT_CUBIC, progress);
                b.animate("torso", new Op().setR(Y, -10).setR(Z, -12).setP(X, 1), Ease.OUT_CUBIC, progress);
                b.animate("rightArm", new Op().setR(X, -22).setR(Y, -19).setR(Z, 88).setP(X, -3.5F).setP(Y, 4)
                        .setP(Z, -0.5F), Ease.OUT_CUBIC, progress);
                b.animate("leftArm", new Op().setR(X, -11).setR(Y, -7).setR(Z, -64).setP(X, 6.5F).setP(Y, 3),
                        Ease.OUT_CUBIC, progress);
                b.animate("rightLeg", new Op().setR(X, -16).setP(X, 0).setP(Y, 8).setP(Z, -1), Ease.OUT_CUBIC,
                        progress);
                b.animate("leftLeg", new Op().setR(X, -27).setR(Z, -13).setP(Y, 9.5F).setP(X, 5).setP(Z, -0.5F),
                        Ease.OUT_CUBIC, progress);
            }
            default -> {
            }
        }
    }

    private static void divePose(Builder b, S s, float pt, float progress, int variant, float diveTilt,
                                 float onGroundP) {
        float swingP = prog(s, SWING, pt);
        float wallP = prog(s, WALL, pt);
        float landFade = Math.min(1.0F, onGroundP * 2.5F);
        progress = progress * (1.0F - wallP) * (1.0F - landFade);
        if (progress <= 0.0F) {
            return;
        }
        if (variant == 0) {
            float lp = progress * (1.0F - swingP);
            b.animate("rightArm", new Op().setR(X, 60 - diveTilt * 0.4F).setR(Z, 15).setR(Y, -15).setP(Z, 1.5F),
                    Ease.OUT_CUBIC, lp);
            b.animate("leftArm", new Op().setR(X, 60 - diveTilt * 0.4F).setR(Z, -15).setR(Y, 15).setP(Z, 1.5F),
                    Ease.OUT_CUBIC, lp);
            b.animate("rightLeg", new Op().setR(X, 50).setR(Y, 15).setP(Z, -1).setP(Y, 9).setP(X, -4), Ease.OUT_CUBIC,
                    lp);
            b.animate("leftLeg", new Op().setR(X, 30).setR(Y, -20).setP(Z, -1).setP(Y, 9).setP(X, 4), Ease.OUT_CUBIC,
                    lp);
            b.animate("torso", new Op().setR(X, 20).setR(Y, 0).setR(Z, 0), Ease.OUT_CUBIC, lp);
            b.animate("body", new Op().setR(X, -10).setR(Z, 0), Ease.OUT_CUBIC, progress);
        } else {
            float start = Math.min(1.0F, prog(s, DIVE, pt) * MAX[DIVE] / 3.0F);
            float headP = swingP > 0.0F ? progress * (1.0F - swingP) : progress;
            b.animate("body", new Op().rotR(X, -10 + diveTilt).pivot(FLIP_PIVOT_Y), Ease.OUT_CUBIC, progress);
            b.animate("torso", new Op().setR(X, 0).setR(Y, 0).setR(Z, 0), Ease.OUT_CUBIC, progress);
            b.animate("head", new Op().rotR(X, diveTilt * 0.5F), Ease.OUT_CUBIC, headP);
            b.animate("rightArm", new Op().setR(X, 10 - diveTilt * 0.1F).setR(Z, 20).setR(Y, 25)
                    .setP(Z, 1.5F - 1.5F * start), Ease.OUT_CUBIC, progress);
            b.animate("leftArm", new Op().setR(X, 10 - diveTilt * 0.1F).setR(Z, -20).setR(Y, -25)
                    .setP(Z, 1.5F - 1.5F * start), Ease.OUT_CUBIC, progress);
            b.animate("rightLeg", new Op().setR(X, diveTilt * -0.15F - 3).setR(Z, 4).setP(Y, 9 + 3 * start)
                    .setP(X, -4 + 2.1F * start).setP(Z, -1 + start), Ease.OUT_CUBIC, progress);
            b.animate("leftLeg", new Op().setR(X, diveTilt * -0.2F).setR(Z, -4).setP(Y, 9 + 3 * start)
                    .setP(X, 4 - 2.1F * start).setP(Z, -1 + start), Ease.OUT_CUBIC, progress);
        }
    }

    // ------------------------------------------------------------------
    // The layer
    // ------------------------------------------------------------------

    private S frame(float pt) {
        S s = STATES.get(this.player.getId());
        if (s == null) {
            return null;
        }
        if (s.cachePt != pt || s.cacheTick != this.player.tickCount) {
            s.cachePt = pt;
            s.cacheTick = this.player.tickCount;
            build(this.player, s, pt);
        }
        return s;
    }

    @Override
    public boolean isActive() {
        S s = STATES.get(this.player.getId());
        if (s == null || !SpiderClient.isSpider(this.player)) {
            return false;
        }
        for (int i = 0; i < MAX.length; i++) {
            if (i != GROUND && i != PERCH_DELAY && (s.val[i] > 0 || s.prev[i] > 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value) {
        if (type != TransformType.ROTATION && type != TransformType.POSITION) {
            return value;
        }
        S s = frame(tickDelta);
        if (s == null) {
            return value;
        }
        List<Op> list = s.ops.get(modelName);
        if ("body".equals(modelName)) {
            return body(s, list, type, value);
        }
        if (list == null) {
            return value;
        }
        float[] cur = {value.getX(), value.getY(), value.getZ()};
        float[] base = cur.clone();
        boolean rot = type == TransformType.ROTATION;
        for (Op op : list) {
            float[] t = cur.clone();
            boolean touched = false;
            for (float[] a : op.acts) {
                int kind = (int) a[0];
                int axis = (int) a[1];
                if (rot && kind == 0) {
                    t[axis] = a[2];
                } else if (rot && kind == 1) {
                    t[axis] += a[2];
                } else if (rot && kind == 4) {
                    t[axis] = -base[axis];
                } else if (rot && kind == 5) {
                    t[axis] += base[axis] * a[2];
                } else if (!rot && kind == 2) {
                    t[axis] = a[2];
                } else if (!rot && kind == 3) {
                    t[axis] += a[2];
                } else {
                    continue;
                }
                touched = true;
            }
            if (!touched) {
                continue;
            }
            float e = ease(op.ease, op.progress);
            for (int i = 0; i < 3; i++) {
                cur[i] += (t[i] - cur[i]) * e;
            }
        }
        return new Vec3f(cur[0], cur[1], cur[2]);
    }

    /** The whole body: its turn (about the pivot of the pose) and its offset. */
    private static Vec3f body(S s, List<Op> list, TransformType type, Vec3f value) {
        float[] rot = {0.0F, 0.0F, 0.0F};
        float[] pos = {0.0F, 0.0F, 0.0F};
        float pivot = 0.7F * 16.0F;
        if (list != null) {
            for (Op op : list) {
                float e = ease(op.ease, op.progress);
                float[] tr = rot.clone();
                float[] tp = pos.clone();
                for (float[] a : op.acts) {
                    int kind = (int) a[0];
                    int axis = (int) a[1];
                    switch (kind) {
                        case 0 -> tr[axis] = a[2];
                        case 1 -> tr[axis] += a[2];
                        case 2 -> tp[axis] = a[2];
                        case 3 -> tp[axis] += a[2];
                        default -> {
                        }
                    }
                }
                for (int i = 0; i < 3; i++) {
                    rot[i] += (tr[i] - rot[i]) * e;
                    pos[i] += (tp[i] - pos[i]) * e;
                }
                if (!Float.isNaN(op.pivot) && e > 0.0F) {
                    pivot = op.pivot;
                }
            }
        }
        if (type == TransformType.ROTATION) {
            return new Vec3f(value.getX() + rot[0], value.getY() + rot[1], value.getZ() + rot[2]);
        }
        // turning about a point "pivot" pixels up instead of playerAnimator's own (0.7 blocks)
        float h = pivot / 16.0F - 0.7F;
        Vector3f up = new Vector3f(0.0F, h, 0.0F);
        new Quaternionf().rotationZYX(rot[2], rot[1], rot[0]).transform(up);
        return new Vec3f(value.getX() + pos[0] / 16.0F - up.x, value.getY() + pos[1] / 16.0F + h - up.y,
                value.getZ() + pos[2] / 16.0F - up.z);
    }
}

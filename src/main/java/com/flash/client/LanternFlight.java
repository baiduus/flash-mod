package com.flash.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * The flight and the free fall of the player from the Create: Lanterns mod (createlanterns by vidarrcrib, its
 * FlightPose and BearerPose; ported to 1.20.1 with permission): a body that follows the motion, upright and
 * leaning into it slow, lying along it like a dart fast (arms ahead), banking into the turns, breathing and
 * standing in the hero pose when still; and, falling without flying, spread out like a skydiver (diving head down
 * when looking down). Drawn for the heroes who fly (SupermanPose puts it on their base layer: the limbs and the
 * head here, the whole body turned in SupermanClient's render hook).
 *
 * <p>The channels are the original's: 2 lean, 3 bank, 4 the bank's axis, 5 drop, 8 nod; then x/y/z rotation of the
 * right arm (10), left arm (13), right leg (16) and left leg (19).
 */
public final class LanternFlight implements IAnimation {

    public static final float CHEST = 1.05F;
    private static final int CHANNELS = 23;
    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private final AbstractClientPlayer player;
    private float delta;

    public LanternFlight(AbstractClientPlayer player) {
        this.player = player;
    }

    // ------------------------------------------------------------------
    // Per player state, ticked by SupermanPose
    // ------------------------------------------------------------------

    private static final class State {
        final float[] target = new float[CHANNELS];
        final float[] fallPose = new float[CHANNELS];
        final float[] cur = new float[CHANNELS];
        final float[] prev = new float[CHANNELS];
        float weight;
        float previousWeight;
        float speed;
        float previousSpeed;
        float fall;
        float previousFall;
        float soar;
        float previousSoar;
        float pitch;
        float bodyYaw;
        float headYaw;
        float lastForward;
        float surge;
        int still;
        float heroic;
        int sinceFlying = 1000;
        boolean fresh = true;
    }

    /** Is the player drawn by it now (flying, falling, or fading out)? */
    public static boolean active(Player p) {
        State s = STATES.get(p.getId());
        return s != null && (s.weight > 0.0F || s.previousWeight > 0.0F);
    }

    public static void forget(Player p) {
        STATES.remove(p.getId());
    }

    /** Free fall: a hero in the air, not flying, dropping fast (or let go of a flight in the air). */
    public static boolean falling(Player p, boolean flying) {
        if (flying || p.onGround() || p.isInWater() || p.isInLava() || p.isPassenger() || p.isFallFlying()
                || p.getAbilities().flying) {
            return false;
        }
        State s = STATES.get(p.getId());
        double vy = p.getY() - p.yo;
        boolean letGo = s != null && s.sinceFlying <= 3;
        boolean already = s != null && s.fall > 0.0F && vy < -0.1D;
        return letGo || vy < -0.9D || already;
    }

    /** One tick of the pose (every flying hero, each client tick). */
    public static void tick(Player player, boolean flying) {
        boolean falling = falling(player, flying);
        State s = STATES.get(player.getId());
        if (s == null) {
            if (!flying && !falling) {
                return;
            }
            s = new State();
            s.bodyYaw = player.yBodyRot;
            s.headYaw = player.getYHeadRot();
            STATES.put(player.getId(), s);
        }
        s.previousWeight = s.weight;
        s.previousSpeed = s.speed;
        s.previousFall = s.fall;
        s.previousSoar = s.soar;
        System.arraycopy(s.cur, 0, s.prev, 0, CHANNELS);
        s.sinceFlying = flying ? 0 : s.sinceFlying + 1;
        boolean carried = flying && !player.onGround();
        s.weight = !carried && !falling ? Math.max(0.0F, s.weight - 0.15F) : Math.min(1.0F, s.weight + 0.2F);
        if (s.fresh && player.tickCount <= 5 && (carried || falling)) {
            s.weight = s.previousWeight = 1.0F;
        }
        s.fresh = false;
        if (falling) {
            s.fall = Math.min(1.0F, s.fall + 0.09F);
        } else if (flying) {
            s.fall = Math.max(0.0F, s.fall - 0.12F);
        }
        float lastHead = s.headYaw;
        s.headYaw = player.getYHeadRot();
        s.bodyYaw = Mth.rotLerp(s.speed < 0.25F ? 0.25F : 0.45F, s.bodyYaw, s.headYaw);
        if (flying || falling) {
            player.yBodyRot = s.bodyYaw;
        }
        double dx = player.getX() - player.xo;
        double dy = player.getY() - player.yo;
        double dz = player.getZ() - player.zo;
        float yaw = s.bodyYaw * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        float forward = (float) (-dx * sin + dz * cos);
        float right = (float) (-(dx * cos + dz * sin));
        float up = (float) dy;
        float turn = Mth.wrapDegrees(s.headYaw - lastHead) * Mth.DEG_TO_RAD
                + 0.35F * Mth.wrapDegrees(s.headYaw - s.bodyYaw) * Mth.DEG_TO_RAD * 0.25F;
        s.speed = Mth.lerp(0.35F, s.speed, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        s.surge = Mth.lerp(0.4F, s.surge, forward - s.lastForward);
        s.lastForward = forward;
        float[] t = s.target;
        float flight = (float) Math.sqrt(Math.max(forward, 0.0F) * Math.max(forward, 0.0F) + dy * dy);
        float soarTarget = !flying ? 0.0F : Mth.clamp((flight - 0.85F) / 0.75F, 0.0F, 1.0F);
        s.soar += (soarTarget - s.soar) * (soarTarget > s.soar ? 0.12F : 0.18F);
        float soarAmount = s.soar * s.soar * (3.0F - 2.0F * s.soar);
        double flat = Math.sqrt(dx * dx + dz * dz);
        float pitchTarget = !(s.speed > 0.3F) ? 0.0F
                : Mth.clamp((float) Math.atan2(-dy, Math.max(flat, 1.0E-4D)), -1.4F, 1.4F);
        s.pitch += (pitchTarget - s.pitch) * 0.3F;
        t[4] = s.pitch * soarAmount;
        float lean = Mth.clamp(0.95F * forward, -0.45F, 1.1F);
        float soarLean = Mth.clamp((float) (Math.PI / 2) + s.pitch, 0.15F, 2.9F);
        t[2] = Mth.lerp(soarAmount, lean, soarLean);
        float turning = Mth.clamp(turn * 6.0F, -0.55F, 0.55F) * Mth.clamp(Math.abs(forward) / 0.4F, 0.0F, 1.0F);
        float carving = Mth.clamp(turn * 10.0F, -1.0F, 1.0F) * Mth.clamp(forward, 0.0F, 1.0F);
        t[3] = Mth.lerp(soarAmount, Mth.clamp(0.8F * right + turning, -0.7F, 0.7F),
                Mth.clamp(0.5F * right + carving, -1.05F, 1.05F));
        float lift = Mth.clamp(up * 0.5F, 0.0F, 0.1F);
        float sink = Mth.clamp(-up * 2.0F, 0.0F, 1.6F);
        float armSwing = Mth.clamp(0.7F * forward + Mth.clamp(s.surge * 5.0F, -0.4F, 0.3F), -0.55F, 0.95F);
        float legSwing = Mth.clamp(0.45F * forward + Mth.clamp(s.surge * 3.0F, -0.25F, 0.2F), -0.35F, 0.55F);
        float armSide = -Mth.clamp(0.55F * right, -0.3F, 0.3F);
        float legSide = -Mth.clamp(0.4F * right, -0.22F, 0.22F);
        float armSpread = 0.12F + sink - lift;
        float legSpread = 0.05F + Mth.clamp(-up * 0.6F, 0.0F, 0.3F) - lift * 0.5F;
        t[10] = armSwing + 0.06F - sink * 0.45F;
        t[13] = armSwing - 0.04F - sink * 0.45F;
        t[11] = 0.0F;
        t[14] = 0.0F;
        t[12] = Math.max(-0.2F, armSpread + armSide);
        t[15] = Math.min(0.2F, -armSpread + armSide);
        t[16] = legSwing + 0.12F - sink * 0.12F;
        t[19] = legSwing - 0.05F - sink * 0.2F;
        t[17] = 0.0F;
        t[20] = 0.0F;
        t[18] = Math.max(-0.12F, legSpread + legSide);
        t[21] = Math.min(0.12F, -legSpread + legSide);
        if (soarAmount > 0.0F) {
            // lying along the flight like a dart, arms ahead
            t[10] = Mth.lerp(soarAmount, t[10], -2.98F);
            t[13] = Mth.lerp(soarAmount, t[13], -2.9F);
            t[12] = Mth.lerp(soarAmount, t[12], 0.16F);
            t[15] = Mth.lerp(soarAmount, t[15], -0.16F);
            t[16] = Mth.lerp(soarAmount, t[16], 0.06F);
            t[19] = Mth.lerp(soarAmount, t[19], -0.02F);
            t[18] = Mth.lerp(soarAmount, t[18], 0.03F);
            t[21] = Mth.lerp(soarAmount, t[21], -0.03F);
        }
        // still in the air for three seconds: the hero pose (a knee bent, fists out)
        s.still = carried && s.speed < 0.05F ? s.still + 1 : 0;
        s.heroic = s.still > 60 ? Math.min(1.0F, s.heroic + 0.05F) : Math.max(0.0F, s.heroic - 0.1F);
        if (s.heroic > 0.0F) {
            float h = s.heroic * s.heroic * (3.0F - 2.0F * s.heroic);
            boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
            int knee = rightHanded ? 16 : 19;
            int straight = rightHanded ? 19 : 16;
            t[12] += 0.18F * h;
            t[15] -= 0.18F * h;
            t[knee] = Mth.lerp(h, t[knee], -0.45F);
            t[straight] = Mth.lerp(h, t[straight], 0.05F);
        }
        if (s.fall > 0.0F) {
            // free fall: spread out like a skydiver, or diving head down when looking down
            float dive = Mth.clamp((player.getXRot() - 15.0F) / 60.0F, 0.0F, 1.0F);
            float[] f = s.fallPose;
            f[2] = Mth.lerp(dive, 1.42F, 2.95F);
            f[3] = Mth.clamp(0.7F * right, -0.45F, 0.45F);
            f[4] = 0.0F;
            f[10] = Mth.lerp(dive, 0.28F, 0.3F);
            f[13] = Mth.lerp(dive, 0.24F, 0.32F);
            f[11] = 0.0F;
            f[14] = 0.0F;
            f[12] = Mth.lerp(dive, 2.0F, 0.16F);
            f[15] = -Mth.lerp(dive, 1.95F, 0.14F);
            f[16] = Mth.lerp(dive, 0.5F, 0.06F);
            f[19] = Mth.lerp(dive, 0.42F, 0.02F);
            f[17] = 0.0F;
            f[20] = 0.0F;
            f[18] = Mth.lerp(dive, 0.36F, 0.04F);
            f[21] = -Mth.lerp(dive, 0.34F, 0.03F);
            float mix = s.fall * s.fall * (3.0F - 2.0F * s.fall);
            for (int i = 0; i < CHANNELS; i++) {
                t[i] = Mth.lerp(mix, t[i], f[i]);
            }
        }
        // the body follows its targets as on springs (the original's stiffness, a tick at a time)
        for (int i = 0; i < CHANNELS; i++) {
            s.cur[i] += (t[i] - s.cur[i]) * 0.38F;
        }
        if (s.weight <= 0.0F && s.previousWeight <= 0.0F) {
            STATES.remove(player.getId());
        }
    }

    private static float blend(State s, float pt) {
        float w = Mth.lerp(pt, s.previousWeight, s.weight);
        return w * w * (3.0F - 2.0F * w);
    }

    private static float drawn(State s, int c, float pt) {
        return Mth.lerp(pt, s.prev[c], s.cur[c]);
    }

    private static float hover(State s, float pt) {
        return 1.0F - Mth.clamp(Mth.lerp(pt, s.previousSpeed, s.speed) / 0.25F, 0.0F, 1.0F);
    }

    /** The original's flourish: a slow breath and drift while hovering, a buffet of the wind on the limbs fast. */
    private static float flourish(Player p, State s, int c, float pt) {
        float w = blend(s, pt);
        if (w <= 0.0F) {
            return 0.0F;
        }
        float t = p.tickCount + pt;
        float hover = hover(s, pt) * w;
        float speed = Mth.lerp(pt, s.previousSpeed, s.speed);
        float f = Mth.lerp(pt, s.previousFall, s.fall);
        float flutter = (Math.min(speed, 1.6F) * 0.03F * (1.0F - f) + Math.min(speed, 4.0F) * 0.035F * f) * w;
        float breath = Mth.sin((((p.level().getGameTime() + Math.floorMod(p.getUUID().hashCode(), 64)) % 64L) + pt)
                * (float) (Math.PI * 2) / 64.0F) * hover;
        return switch (c) {
            case 5 -> -0.015F * breath - Mth.sin(t * 0.12F) * 0.035F * hover;
            case 8 -> -0.02F * breath;
            case 10 -> hover * 0.07F * Mth.sin(t * 0.083F) + flutter * wave(t, 0.93F, 0.0F);
            case 13 -> hover * 0.07F * Mth.sin(t * 0.083F + 2.1F) + flutter * wave(t, 0.97F, 1.9F);
            case 12 -> 0.03F * breath + hover * 0.05F * Mth.sin(t * 0.061F) + flutter * 0.6F * wave(t, 1.17F, 0.5F);
            case 15 -> -0.03F * breath - hover * 0.05F * Mth.sin(t * 0.061F + 1.3F)
                    - flutter * 0.6F * wave(t, 1.11F, 2.4F);
            case 16 -> hover * 0.11F * Mth.sin(t * 0.1F) + flutter * 0.7F * wave(t, 0.81F, 0.9F);
            case 19 -> hover * 0.11F * Mth.sin(t * 0.1F + (float) Math.PI) + flutter * 0.7F * wave(t, 0.86F, 3.6F);
            default -> 0.0F;
        };
    }

    private static float wave(float t, float rate, float phase) {
        return 0.75F * Mth.sin(t * rate + phase) + 0.25F * Mth.sin(t * rate * 2.37F + phase * 1.7F + 0.6F);
    }

    private static float within(float value, float min, float max) {
        if (value > max - 0.3F) {
            return max - 0.3F + 0.3F * (float) Math.tanh((value - (max - 0.3F)) / 0.3F);
        }
        return value < min + 0.3F ? min + 0.3F - 0.3F * (float) Math.tanh((min + 0.3F - value) / 0.3F) : value;
    }

    private static final Quaternionf TURN = new Quaternionf();

    /**
     * The whole body turned as the original's (lean forward, bank on the axis of the climb, around the chest),
     * before the renderer draws it; true when it pushed a pose (to pop after).
     */
    public static boolean rotateBody(Player p, PoseStack ps, float pt) {
        State s = STATES.get(p.getId());
        if (s == null) {
            return false;
        }
        float w = blend(s, pt);
        if (w <= 0.0F) {
            return false;
        }
        float lean = drawn(s, 2, pt) * w;
        float bank = drawn(s, 3, pt) * w;
        float axis = drawn(s, 4, pt) * w;
        float drop = (drawn(s, 5, pt) + flourish(p, s, 5, pt)) * w;
        if (Math.abs(lean) + Math.abs(bank) + Math.abs(drop) < 1.0E-5F) {
            return false;
        }
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float scale = p.getScale();
        ps.pushPose();
        ps.translate(0.0F, (CHEST - drop) * scale, 0.0F);
        // into the frame the renderer turns the body into, turned there, and back
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        TURN.identity();
        if (bank != 0.0F) {
            TURN.rotateAxis(bank, 0.0F, -Mth.sin(axis), -Mth.cos(axis));
        }
        if (lean != 0.0F) {
            TURN.rotateX(-lean);
        }
        ps.mulPose(TURN);
        ps.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
        ps.translate(0.0F, -CHEST * scale, 0.0F);
        return true;
    }

    // ------------------------------------------------------------------
    // The animation on the player's base layer: the limbs and the head
    // ------------------------------------------------------------------

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void setupAnim(float tickDelta) {
        this.delta = tickDelta;
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value) {
        if (type != TransformType.ROTATION) {
            return value;
        }
        State s = STATES.get(this.player.getId());
        if (s == null) {
            return value;
        }
        float pt = tickDelta;
        float w = blend(s, pt);
        if (w <= 0.0F) {
            return value;
        }
        int limb = switch (modelName) {
            case "rightArm" -> 10;
            case "leftArm" -> 13;
            case "rightLeg" -> 16;
            case "leftLeg" -> 19;
            case "head" -> -1;
            default -> -2;
        };
        if (limb == -2) {
            return value;
        }
        if (limb == -1) {
            float lean = drawn(s, 2, pt) * w;
            float nod = (drawn(s, 8, pt) + flourish(this.player, s, 8, pt)) * w;
            float soar = Mth.lerp(pt, s.previousSoar, s.soar);
            soar = soar * soar * (3.0F - 2.0F * soar);
            return new Vec3f(within(value.getX() - lean + nod, -1.15F - 0.3F * soar, 0.9F), value.getY(),
                    value.getZ());
        }
        float x = Mth.lerp(w, value.getX(), drawn(s, limb, pt) + flourish(this.player, s, limb, pt));
        float y = Mth.lerp(w, value.getY(), drawn(s, limb + 1, pt));
        float z = Mth.lerp(w, value.getZ(), drawn(s, limb + 2, pt) + flourish(this.player, s, limb + 2, pt));
        return new Vec3f(x, y, z);
    }
}

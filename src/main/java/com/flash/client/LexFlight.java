package com.flash.client;

import com.flash.FlashMod;
import com.flash.lex.LexState;
import com.flash.lex.LexSuit;
import com.flash.registry.ModSounds;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Iron Man's flight of Legends: Superheroes for Lex's suit, on the client.
 * <ul>
 * <li>The fast flight: sprinting in the air the suit flies where the pilot looks, with momentum (the vanilla
 * creative flight stays for hovering and slow moves).</li>
 * <li>The body: upright while hovering, leaning a little into the motion when cruising and banking into turns; in
 * the fast flight it lies along the look (Legends: 90 degrees plus the pitch), the head kept looking ahead. The arm
 * and leg poses are the {@code lex_fly_*} animations (SupermanPose).</li>
 * <li>The thrusters: their start, loop and stop sounds (Legends' jetpack thrust), smoke under the boots and the
 * trail of the fast flight; the flames themselves are drawn by {@link LexSuitLayer}.</li>
 * <li>The moving parts of the Mark 6 (faceplate, back flaps, wrist lasers, wrist launcher, overdrive), eased in and
 * out for {@link LexSuitLayer}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class LexFlight {

    private static final ResourceLocation HEAD_LAYER = new ResourceLocation(FlashMod.MODID, "lex_flight_head");

    /** Fast flight speed (blocks per tick), and in overdrive. */
    private static final double FAST = 1.15D;
    private static final double FAST_OVERDRIVE = 1.6D;

    // the parts of the suit that move (progress 0..1)
    public static final int P_FACEPLATE = 0;
    public static final int P_THRUSTERS = 1;
    public static final int P_OVERDRIVE = 2;
    public static final int P_LASERS = 3;
    public static final int P_PODS = 4;
    public static final int P_FLY = 5;
    private static final int PARTS = 6;
    private static final int[] PART_FLAG = {LexSuit.F_OPEN, LexSuit.F_FAST, LexSuit.F_OVERDRIVE, LexSuit.F_LASER,
            LexSuit.F_PODS, LexSuit.F_FLY};
    /** Ticks to move each part all the way (the faceplate takes 0.25 s, as in Legends). */
    private static final float[] PART_TICKS = {5.0F, 3.0F, 5.0F, 3.0F, 3.0F, 4.0F};

    private static final class State {
        final float[] part = new float[PARTS];
        final float[] partO = new float[PARTS];
        float pitch;
        float roll;
        long lastNanos;
        boolean flying;
        boolean pushed;
        ThrustLoop loop;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    /** Body pitch drawn this frame per player (for the head layer). */
    private static final Map<UUID, Float> PITCH = new ConcurrentHashMap<>();
    private static Vec3 fastVelocity;

    private LexFlight() {
    }

    public static void reset() {
        STATES.clear();
        PITCH.clear();
        fastVelocity = null;
    }

    private static State state(Entity e) {
        return STATES.computeIfAbsent(e.getId(), id -> new State());
    }

    /** How far a moving part of the suit on this entity is (0 closed .. 1 open). */
    public static float part(Entity e, int which, float partial) {
        State s = STATES.get(e.getId());
        if (s == null) {
            return 0.0F;
        }
        return Mth.lerp(partial, s.partO[which], s.part[which]);
    }

    private static boolean flying(Player p) {
        if (p instanceof LocalPlayer local) {
            return local.getAbilities().flying && LexState.flag(p, LexSuit.F_ON);
        }
        return LexState.flag(p, LexSuit.F_FLY);
    }

    private static boolean fast(Player p) {
        if (p instanceof LocalPlayer local) {
            return flying(p) && local.isSprinting();
        }
        return LexState.flag(p, LexSuit.F_FAST);
    }

    // ------------------------------------------------------------------
    // Ticks: the fast flight, the parts, the sounds and the smoke
    // ------------------------------------------------------------------

    /** After the move of the tick: the velocity the next tick will fly with. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof LocalPlayer p)) {
            return;
        }
        boolean steer = LexClient.pilot(p) && fast(p) && p.input != null && p.input.forwardImpulse > 0.0F;
        if (!steer) {
            fastVelocity = null;
            return;
        }
        Vec3 now = p.getDeltaMovement();
        if (fastVelocity == null) {
            fastVelocity = now;
        }
        double speed = LexState.flag(p, LexSuit.F_OVERDRIVE) ? FAST_OVERDRIVE : FAST;
        Vec3 target = p.getLookAngle().scale(speed);
        fastVelocity = fastVelocity.add(target.subtract(fastVelocity).scale(0.14D));
        if (p.horizontalCollision) {
            fastVelocity = new Vec3(fastVelocity.x * 0.4D, fastVelocity.y, fastVelocity.z * 0.4D);
        }
        if (p.verticalCollision) {
            fastVelocity = new Vec3(fastVelocity.x, fastVelocity.y * 0.4D, fastVelocity.z);
        }
        p.setDeltaMovement(fastVelocity);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Player p : mc.level.players()) {
            boolean suit = LexState.flag(p, LexSuit.F_ON) || LexState.flag(p, LexSuit.F_OPEN);
            State s = suit ? state(p) : STATES.get(p.getId());
            if (s == null) {
                continue;
            }
            for (int i = 0; i < PARTS; i++) {
                s.partO[i] = s.part[i];
                boolean on = i == P_FLY ? flying(p) : i == P_THRUSTERS ? fast(p) : LexState.flag(p, PART_FLAG[i]);
                float step = 1.0F / PART_TICKS[i];
                s.part[i] = Mth.clamp(s.part[i] + (on ? step : -step), 0.0F, 1.0F);
            }
            boolean fly = flying(p);
            if (fly != s.flying) {
                s.flying = fly;
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), fly ? ModSounds.LEX_THRUST_START.get()
                        : ModSounds.LEX_THRUST_END.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
            }
            if (fly) {
                if (s.loop == null || s.loop.isStopped()) {
                    s.loop = new ThrustLoop(p);
                    mc.getSoundManager().play(s.loop);
                }
                if (fast(p)) {
                    // the body lies along the flight: the body turns with the look
                    p.yBodyRot = p.yHeadRot;
                }
                smoke(p, s);
            }
        }
    }

    /** Smoke under the boots, a white trail in the fast flight, dust on the ground under a low hover. */
    private static void smoke(Player p, State s) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson() && !fast(p) && p.tickCount % 2 == 0) {
            return;
        }
        Quaternionf q = bodyRotation(p, s.pitch, s.roll, 1.0F);
        double yaw = Math.toRadians(p.yBodyRot);
        Vector3f left = new Vector3f((float) Math.cos(yaw), 0.0F, (float) Math.sin(yaw));
        boolean fast = fast(p);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f foot = new Vector3f(left).mul(0.12F * side).add(0.0F, -0.95F, 0.0F);
            q.transform(foot);
            double x = p.getX() + foot.x;
            double y = p.getY() + 0.9D + foot.y;
            double z = p.getZ() + foot.z;
            Vec3 v = p.getDeltaMovement();
            if (fast) {
                mc.level.addParticle(ParticleTypes.CLOUD, x - v.x * 0.5D, y - v.y * 0.5D, z - v.z * 0.5D, 0.0D, 0.0D,
                        0.0D);
            } else if (p.tickCount % 2 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, x, y, z, v.x * 0.5D, -0.12D, v.z * 0.5D);
            }
        }
        if (!fast && p.tickCount % 3 == 0) {
            // the downdraft raising dust on the ground under a low hover
            var ground = p.level().clip(new net.minecraft.world.level.ClipContext(p.position(),
                    p.position().add(0.0D, -3.0D, 0.0D), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, p));
            if (ground.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                Vec3 at = ground.getLocation();
                double a = p.getRandom().nextDouble() * Math.PI * 2.0D;
                mc.level.addParticle(ParticleTypes.POOF, at.x, at.y + 0.05D, at.z, Math.cos(a) * 0.12D, 0.01D,
                        Math.sin(a) * 0.12D);
            }
        }
    }

    /** The thrusters' loop: louder and higher the faster the suit flies. */
    private static final class ThrustLoop extends AbstractTickableSoundInstance {
        private final Player player;

        ThrustLoop(Player player) {
            super(ModSounds.LEX_THRUST_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.volume = 0.35F;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !flying(this.player)) {
                this.stop();
                return;
            }
            double speed = this.player.position().subtract(this.player.xo, this.player.yo, this.player.zo).length();
            this.volume = 0.35F + (float) Math.min(0.5D, speed * 0.45D);
            this.pitch = 0.92F + (float) Math.min(0.3D, speed * 0.22D);
            this.x = this.player.getX();
            this.y = this.player.getY();
            this.z = this.player.getZ();
        }
    }

    // ------------------------------------------------------------------
    // The body in flight
    // ------------------------------------------------------------------

    /** Pitch forward (degrees) and bank (degrees, + to the left) of the body, around its middle. */
    private static Quaternionf bodyRotation(Player p, float pitch, float roll, float partial) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot));
        float fx = (float) -Math.sin(yaw);
        float fz = (float) Math.cos(yaw);
        // up turned toward the front around up x front, and toward the left around up x left
        Quaternionf q = new Quaternionf().rotateAxis((float) Math.toRadians(pitch), fz, 0.0F, -fx);
        q.mul(new Quaternionf().rotateAxis((float) Math.toRadians(roll), (float) Math.sin(yaw), 0.0F,
                (float) -Math.cos(yaw)));
        return q;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        State s = STATES.get(p.getId());
        if (s == null) {
            return;
        }
        float partial = event.getPartialTick();
        long nanos = System.nanoTime();
        float dt = s.lastNanos == 0L ? 0.05F : Math.min(0.1F, (nanos - s.lastNanos) / 1.0E9F);
        s.lastNanos = nanos;
        float targetPitch = 0.0F;
        float targetRoll = 0.0F;
        if (flying(p) && !p.isPassenger()) {
            double vx = p.getX() - p.xo;
            double vy = p.getY() - p.yo;
            double vz = p.getZ() - p.zo;
            double yaw = Math.toRadians(p.yBodyRot);
            double forward = -vx * Math.sin(yaw) + vz * Math.cos(yaw);
            double left = vx * Math.cos(yaw) + vz * Math.sin(yaw);
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            float turn = Mth.wrapDegrees(p.yHeadRot - p.yHeadRotO);
            if (fast(p) && speed > 0.3D) {
                targetPitch = Mth.clamp(90.0F + p.getViewXRot(partial), 0.0F, 180.0F);
                targetRoll = Mth.clamp(-turn * 2.2F, -45.0F, 45.0F);
            } else {
                targetPitch = (float) Mth.clamp(forward * 45.0D, -12.0D, 22.0D);
                targetRoll = (float) Mth.clamp(left * 55.0D, -22.0D, 22.0D);
            }
        }
        float k = 1.0F - (float) Math.exp(-dt * 7.0F);
        s.pitch += (targetPitch - s.pitch) * k;
        s.roll += (targetRoll - s.roll) * k;
        if (Math.abs(s.pitch) < 0.3F && Math.abs(s.roll) < 0.3F) {
            s.pitch = 0.0F;
            s.roll = 0.0F;
            PITCH.remove(p.getUUID());
            return;
        }
        PITCH.put(p.getUUID(), s.pitch);
        var pose = event.getPoseStack();
        pose.pushPose();
        s.pushed = true;
        pose.translate(0.0D, 0.9D, 0.0D);
        pose.mulPose(bodyRotation(p, s.pitch, s.roll, partial));
        pose.translate(0.0D, -0.9D, 0.0D);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        State s = STATES.get(event.getEntity().getId());
        if (s != null && s.pushed) {
            s.pushed = false;
            event.getPoseStack().popPose();
        }
    }

    /** Keeps the head looking ahead while the body is tilted (Legends' keepHeadLooking). */
    private static final class HeadLayer implements IAnimation {
        private final UUID player;

        HeadLayer(AbstractClientPlayer player) {
            this.player = player.getUUID();
        }

        @Override
        public boolean isActive() {
            return PITCH.containsKey(this.player);
        }

        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
            Float pitch = PITCH.get(this.player);
            if (pitch == null || type != TransformType.ROTATION || !"head".equals(modelName)) {
                return value0;
            }
            return new Vec3f(value0.getX() - (float) Math.toRadians(pitch), value0.getY(), value0.getZ());
        }

        @Override
        public void setupAnim(float tickDelta) {
        }
    }

    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HEAD_LAYER, 1300, HeadLayer::new);
        }
    }
}

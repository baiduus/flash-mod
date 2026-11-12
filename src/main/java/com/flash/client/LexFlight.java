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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/**
 * Lex's suit in flight, on the client. The flight itself is the mod's (Superman's and Thor's aimed flight,
 * SupermanClient, with their poses and lean), with Iron Man's arms held back ({@link ArmsBack}) and the suit's own
 * green thrusters:
 * <ul>
 * <li>their start, loop and stop sounds (Legends' jetpack thrust), smoke under the boots and the trail of the fast
 * flight; the flames themselves are drawn by {@link LexSuitLayer};</li>
 * <li>the moving parts of the Mark 6 (faceplate, back flaps, wrist lasers, wrist launcher, overdrive), eased in and
 * out for {@link LexSuitLayer}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class LexFlight {

    private static final ResourceLocation ARMS_LAYER = new ResourceLocation(FlashMod.MODID, "lex_flight_arms");


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
        boolean flying;
        ThrustLoop loop;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private LexFlight() {
    }

    public static void reset() {
        STATES.clear();

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
            return flying(p) && Minecraft.getInstance().options.keySprint.isDown() && local.input != null
                    && local.input.forwardImpulse > 0.0F;
        }
        return LexState.flag(p, LexSuit.F_FAST);
    }

    // ------------------------------------------------------------------
    // Ticks: the fast flight, the parts, the sounds and the smoke
    // ------------------------------------------------------------------

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
        double yaw = Math.toRadians(p.yBodyRot);
        Vector3f left = new Vector3f((float) Math.cos(yaw), 0.0F, (float) Math.sin(yaw));
        boolean fast = fast(p);
        Vec3 v = p.getDeltaMovement();
        // the feet: under the body hovering, behind it in a fast (lying) flight
        Vec3 back = v.lengthSqr() > 1.0E-4D ? v.normalize().scale(-0.9D) : new Vec3(0.0D, -0.9D, 0.0D);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f foot = new Vector3f(left).mul(0.12F * side);
            Vec3 at = fast ? p.position().add(0.0D, 0.9D, 0.0D).add(back) : p.position();
            double x = at.x + foot.x;
            double y = at.y + foot.y;
            double z = at.z + foot.z;
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

    /**
     * The flight is the mod's own (Superman's and Thor's: SupermanClient moves and leans the body, SupermanPose
     * picks the poses), with Iron Man's arms: held back along the body, palms behind, while no move of the suit is
     * playing.
     */
    private static final class ArmsBack implements IAnimation {
        private final AbstractClientPlayer player;

        ArmsBack(AbstractClientPlayer player) {
            this.player = player;
        }

        @Override
        public boolean isActive() {
            return flying(this.player) && !this.player.isPassenger() && SupermanPose.base(this.player.getUUID()) != null
                    && SupermanPose.shot(this.player.getUUID()) == null;
        }

        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
            if (type != TransformType.ROTATION) {
                return value0;
            }
            float fast = part(this.player, P_THRUSTERS, tickDelta);
            float sway = (float) Math.sin((this.player.tickCount + tickDelta) * 0.08F) * 0.03F;
            if ("rightArm".equals(modelName)) {
                return new Vec3f(0.42F + 0.25F * fast + sway, 0.0F, 0.14F - 0.06F * fast);
            }
            if ("leftArm".equals(modelName)) {
                return new Vec3f(0.42F + 0.25F * fast - sway, 0.0F, -0.14F + 0.06F * fast);
            }
            return value0;
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
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ARMS_LAYER, 1300, ArmsBack::new);
        }
    }
}

package com.flash.client;

import com.flash.FlashMod;
import com.flash.registry.ModSounds;
import com.flash.ultron.UltronSentry;
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
import net.minecraft.core.particles.DustParticleOptions;
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
 * Ultron in flight, Iron Man's way: the mod's aimed flight (SupermanClient, with Iron Man's poses, SupermanPose) on
 * thrusters in the boots and palms. Here: how far the thrusters burn (UltronLayer draws the flames), the arms held
 * back along the body, the thrust's sound, sparks and smoke under the boots and a red-hot trail in the fast flight.
 * His drones fly the same way.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltronFlight {

    private static final ResourceLocation ARMS_LAYER = new ResourceLocation(FlashMod.MODID, "ultron_flight_arms");
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.12F), 0.7F);

    private static final class State {
        float fly;
        float flyO;
        float fast;
        float fastO;
        boolean flying;
        ThrustLoop loop;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private UltronFlight() {
    }

    public static void reset() {
        STATES.clear();
    }

    /** 0 (thrusters off) .. 1 (burning). */
    public static float fly(Entity e, float partial) {
        State s = STATES.get(e.getId());
        return s == null ? 0.0F : Mth.lerp(partial, s.flyO, s.fly);
    }

    /** 0 hovering .. 1 the fast flight. */
    public static float fast(Entity e, float partial) {
        State s = STATES.get(e.getId());
        return s == null ? 0.0F : Mth.lerp(partial, s.fastO, s.fast);
    }

    static boolean flying(Entity e) {
        if (e instanceof UltronSentry) {
            return true;
        }
        if (e instanceof LocalPlayer local) {
            return UltronClient.isUltron(local) && local.getAbilities().flying && !local.isSleeping();
        }
        return e instanceof Player p && UltronClient.flying(p) && !p.isSleeping();
    }

    private static boolean goingFast(Entity e) {
        if (e instanceof LocalPlayer local) {
            return flying(e) && Minecraft.getInstance().options.keySprint.isDown() && local.input != null
                    && local.input.forwardImpulse > 0.0F;
        }
        return flying(e) && e.position().subtract(e.xo, e.yo, e.zo).length() > 0.75D;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Entity e : mc.level.entitiesForRendering()) {
            boolean ultron = e instanceof UltronSentry || (e instanceof Player p && UltronClient.isUltron(p));
            State s = ultron ? STATES.computeIfAbsent(e.getId(), id -> new State()) : STATES.get(e.getId());
            if (s == null) {
                continue;
            }
            boolean fly = ultron && flying(e);
            boolean fast = ultron && goingFast(e);
            s.flyO = s.fly;
            s.fastO = s.fast;
            s.fly = Mth.clamp(s.fly + (fly ? 0.25F : -0.25F), 0.0F, 1.0F);
            s.fast = Mth.clamp(s.fast + (fast ? 0.34F : -0.34F), 0.0F, 1.0F);
            if (e instanceof Player p) {
                if (fly != s.flying) {
                    s.flying = fly;
                    mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), fly ? ModSounds.LEX_THRUST_START.get()
                            : ModSounds.LEX_THRUST_END.get(), SoundSource.PLAYERS, 0.8F, 0.85F, false);
                }
                if (fly && (s.loop == null || s.loop.isStopped())) {
                    s.loop = new ThrustLoop(p);
                    mc.getSoundManager().play(s.loop);
                }
            }
            if (fly) {
                exhaust(mc, e, fast);
            }
        }
    }

    /** Embers and smoke under the boots; a red-hot trail behind them in the fast flight. */
    private static void exhaust(Minecraft mc, Entity e, boolean fast) {
        if (e == mc.player && mc.options.getCameraType().isFirstPerson() && !fast && e.tickCount % 2 == 0) {
            return;
        }
        double yaw = Math.toRadians(e instanceof Player p ? p.yBodyRot : e.getYRot());
        Vector3f left = new Vector3f((float) Math.cos(yaw), 0.0F, (float) Math.sin(yaw));
        Vec3 v = e.getDeltaMovement();
        Vec3 back = fast && v.lengthSqr() > 1.0E-4D ? v.normalize().scale(-0.9D) : new Vec3(0.0D, -0.05D, 0.0D);
        float scale = e instanceof UltronSentry ? 0.85F : 1.0F;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f foot = new Vector3f(left).mul(0.12F * side * scale);
            Vec3 at = fast ? e.position().add(0.0D, 0.9D * scale, 0.0D).add(back) : e.position();
            double x = at.x + foot.x;
            double y = at.y + foot.y;
            double z = at.z + foot.z;
            mc.level.addParticle(EMBER, x, y, z, 0.0D, -0.05D, 0.0D);
            if (fast) {
                mc.level.addParticle(ParticleTypes.SMALL_FLAME, x - v.x * 0.4D, y - v.y * 0.4D, z - v.z * 0.4D,
                        0.0D, 0.0D, 0.0D);
            } else if (e.tickCount % 3 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, x, y - 0.1D, z, v.x * 0.5D, -0.1D, v.z * 0.5D);
            }
        }
    }

    private static final class ThrustLoop extends AbstractTickableSoundInstance {
        private final Player player;

        ThrustLoop(Player player) {
            super(ModSounds.LEX_THRUST_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.volume = 0.35F;
            this.pitch = 0.85F;
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
            this.pitch = 0.8F + (float) Math.min(0.3D, speed * 0.22D);
            this.x = this.player.getX();
            this.y = this.player.getY();
            this.z = this.player.getZ();
        }
    }

    /** Iron Man's arms in flight: along the body, palms back (the thrusters in them). */
    private static final class ArmsBack implements IAnimation {
        private final AbstractClientPlayer player;
        private float bodyPitch;

        ArmsBack(AbstractClientPlayer player) {
            this.player = player;
        }

        @Override
        public boolean isActive() {
            return UltronClient.isUltron(this.player) && flying(this.player) && !this.player.isPassenger()
                    && !this.player.isSwimming() && SupermanPose.shot(this.player.getUUID()) == null;
        }

        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
            if (type != TransformType.ROTATION) {
                return value0;
            }
            if ("body".equals(modelName)) {
                this.bodyPitch = value0.getX();
                return value0;
            }
            if (!"rightArm".equals(modelName) && !"leftArm".equals(modelName)) {
                return value0;
            }
            String base = SupermanPose.base(this.player.getUUID());
            boolean lyingPose = base != null && (base.startsWith("prone") || base.startsWith("boost")
                    || base.startsWith("cruise") || base.startsWith("dive") || base.startsWith("strafe"));
            float lying = Math.max(lyingPose ? 1.0F : 0.0F, Mth.clamp(-this.bodyPitch / 1.4F, 0.0F, 1.0F));
            float sway = (float) Math.sin((this.player.tickCount + tickDelta) * 0.08F) * 0.03F;
            float back = Mth.lerp(lying, 0.7F, 0.12F);
            float out = Mth.lerp(lying, 0.22F, 0.1F);
            if ("rightArm".equals(modelName)) {
                return new Vec3f(back + sway, 0.3F, out);
            }
            return new Vec3f(back - sway, -0.3F, -out);
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

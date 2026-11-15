package com.flash.client;

import com.flash.FlashMod;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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

/**
 * Spider-Man's body (procedural, on a playerAnimator layer of its own): on a web line the right arm reaches up to
 * where it is fastened and the legs tuck and swing; on a wall he crawls, arm over arm; the flying kick, one leg
 * out. Blended in and out, so the walk and the jump stay Minecraft's.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpiderPose implements IAnimation {

    private static final ResourceLocation LAYER = new ResourceLocation(FlashMod.MODID, "spider_pose");
    private static final int CH = 12;
    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private static final class State {
        final float[] cur = new float[CH];
        final float[] prev = new float[CH];
        final float[] target = new float[CH];
        float weight;
        float prevWeight;
    }

    private final AbstractClientPlayer player;

    private SpiderPose(AbstractClientPlayer player) {
        this.player = player;
    }

    @Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1250, SpiderPose::new);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        STATES.int2ObjectEntrySet().removeIf(e -> !(mc.level.getEntity(e.getIntKey()) instanceof Player));
        for (Player p : mc.level.players()) {
            tick(p);
        }
    }

    private static void tick(Player p) {
        boolean spider = SpiderClient.isSpider(p);
        State s = STATES.get(p.getId());
        if (!spider && s == null) {
            return;
        }
        if (s == null) {
            s = new State();
            STATES.put(p.getId(), s);
        }
        System.arraycopy(s.cur, 0, s.prev, 0, CH);
        s.prevWeight = s.weight;
        Vec3 anchor = spider ? SpiderClient.line(p) : null;
        boolean kick = spider && SpiderClient.kicking(p);
        boolean cling = spider && !kick && anchor == null && SpiderClient.clinging(p);
        float want = anchor != null || kick || cling ? 1.0F : 0.0F;
        s.weight += (want - s.weight) * 0.3F;
        if (want <= 0.0F && s.weight < 0.01F) {
            s.weight = 0.0F;
            if (!spider) {
                STATES.remove(p.getId());
            }
            return;
        }
        float t = p.tickCount;
        float[] g = s.target;
        if (anchor != null) {
            // the right arm reaches for where the line is fastened
            Vec3 shoulder = p.position().add(0.0D, 1.4D, 0.0D);
            Vec3 rel = anchor.subtract(shoulder);
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            double fwd = -rel.x * Math.sin(yaw) + rel.z * Math.cos(yaw);
            double side = rel.x * Math.cos(yaw) + rel.z * Math.sin(yaw);
            double flat = Math.sqrt(fwd * fwd + side * side);
            float elev = (float) Math.atan2(rel.y, Math.max(0.01D, fwd));
            float x = Mth.clamp(-(Mth.HALF_PI + elev), -3.5F, -1.4F);
            float out = Mth.clamp((float) Math.atan2(-side, Math.max(0.5D, flat)) * 0.6F + 0.12F, -0.6F, 0.7F);
            double speed = p.getDeltaMovement().length();
            float sw = (float) Math.sin(t * 0.25F) * (float) Math.min(1.0D, speed) * 0.25F;
            set(g, 0, x, 0.0F, out);
            // the other arm out for balance
            set(g, 3, -0.45F + sw, 0.0F, -0.45F);
            // legs tucked, swinging with the arc
            set(g, 6, -1.05F + sw, 0.0F, 0.12F);
            set(g, 9, -0.25F - sw, 0.0F, -0.12F);
        } else if (kick) {
            set(g, 0, 0.65F, 0.0F, 0.35F);
            set(g, 3, 0.55F, 0.0F, -0.35F);
            set(g, 6, -1.6F, 0.0F, 0.05F);
            set(g, 9, 0.55F, 0.0F, -0.05F);
        } else {
            // crawling on the wall: arm over arm, the legs bent and pushing
            double vy = p.getY() - p.yo;
            float move = (float) Math.min(1.0D, Math.abs(vy) * 5.0D);
            float c = (float) Math.sin(t * 0.55F) * move;
            set(g, 0, -2.55F + 0.45F * c, 0.0F, 0.32F);
            set(g, 3, -2.55F - 0.45F * c, 0.0F, -0.32F);
            set(g, 6, -0.85F - 0.4F * c, 0.0F, 0.3F);
            set(g, 9, -0.85F + 0.4F * c, 0.0F, -0.3F);
        }
        for (int i = 0; i < CH; i++) {
            s.cur[i] += (g[i] - s.cur[i]) * 0.45F;
        }
    }

    private static void set(float[] g, int at, float x, float y, float z) {
        g[at] = x;
        g[at + 1] = y;
        g[at + 2] = z;
    }

    @Override
    public boolean isActive() {
        State s = STATES.get(this.player.getId());
        return s != null && (s.weight > 0.0F || s.prevWeight > 0.0F);
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value) {
        if (type != TransformType.ROTATION) {
            return value;
        }
        int limb = switch (modelName) {
            case "rightArm" -> 0;
            case "leftArm" -> 3;
            case "rightLeg" -> 6;
            case "leftLeg" -> 9;
            default -> -1;
        };
        State s = STATES.get(this.player.getId());
        if (limb < 0 || s == null) {
            return value;
        }
        float w = Mth.lerp(tickDelta, s.prevWeight, s.weight);
        if (w <= 0.0F) {
            return value;
        }
        float x = Mth.lerp(tickDelta, s.prev[limb], s.cur[limb]);
        float y = Mth.lerp(tickDelta, s.prev[limb + 1], s.cur[limb + 1]);
        float z = Mth.lerp(tickDelta, s.prev[limb + 2], s.cur[limb + 2]);
        return new Vec3f(Mth.lerp(w, value.getX(), x), Mth.lerp(w, value.getY(), y), Mth.lerp(w, value.getZ(), z));
    }
}

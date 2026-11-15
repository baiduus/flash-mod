package com.flash.client;

import com.flash.FlashMod;
import com.flash.doom.DoomAbilities;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Doctor Doom's arms when he uses his powers (a playerAnimator layer of its own, eased in and out): the gauntlet
 * raised at what he aims (bolts, the beam; the other hand on its wrist for the beam), both gauntlets forward for the
 * discharge, the arms raised and opened for the force field, thrust out sideways for the wave, the hand out for the
 * mystic bolt, both arms up to the sky for the ritual.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class DoomPose {

    private static final ResourceLocation LAYER = new ResourceLocation(FlashMod.MODID, "doom_pose");

    private static final int AIM = 0;
    private static final int BEAM = 1;
    private static final int BOTH = 2;
    private static final int FIELD = 3;
    private static final int PUSH = 4;
    private static final int MYSTIC = 5;
    private static final int RITUAL = 6;

    private static final class S {
        int pose = -1;
        int left;
        float weight;
        float prevWeight;
    }

    private static final Map<Integer, S> STATES = new HashMap<>();

    private DoomPose() {
    }

    /** One of his powers seen (DoomVfx): the arms that go with it. */
    static void cast(Player p, int fx, float power) {
        int pose;
        int ticks;
        switch (fx) {
            case DoomAbilities.FX_BOLT -> {
                pose = AIM;
                ticks = 10;
            }
            case DoomAbilities.FX_BEAM -> {
                pose = BEAM;
                ticks = 6;
            }
            case DoomAbilities.FX_DISCHARGE -> {
                pose = BOTH;
                ticks = 14;
            }
            case DoomAbilities.FX_SHIELD -> {
                pose = FIELD;
                ticks = 16;
            }
            case DoomAbilities.FX_WAVE -> {
                pose = PUSH;
                ticks = 14;
            }
            case DoomAbilities.FX_CAST -> {
                pose = power >= 2.0F ? RITUAL : MYSTIC;
                ticks = power >= 2.0F ? 30 : 12;
            }
            default -> {
                return;
            }
        }
        S s = STATES.computeIfAbsent(p.getId(), k -> new S());
        // the discharge's side bolts are its own (keep the both-arms pose)
        if (pose == AIM && s.pose == BOTH && s.left > 0) {
            return;
        }
        s.pose = pose;
        s.left = ticks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || STATES.isEmpty()) {
            return;
        }
        STATES.entrySet().removeIf(en -> {
            S s = en.getValue();
            s.prevWeight = s.weight;
            if (s.left > 0) {
                s.left--;
                s.weight = Math.min(1.0F, s.weight + 0.34F);
            } else {
                s.weight = Math.max(0.0F, s.weight - 0.2F);
            }
            return s.weight <= 0.0F && s.prevWeight <= 0.0F && s.left <= 0
                    || !(mc.level.getEntity(en.getKey()) instanceof Player);
        });
    }

    /** His arms posed (SpiderPoseMixin, right after the game posed the model). */
    public static void apply(net.minecraft.client.model.PlayerModel<?> m, net.minecraft.world.entity.LivingEntity e,
                             float ageInTicks) {
        if (!(e instanceof Player p) || ageInTicks == 0.0F || !DoomClient.isDoom(p)) {
            return;
        }
        S s = STATES.get(p.getId());
        if (s == null || s.weight <= 0.0F && s.prevWeight <= 0.0F) {
            return;
        }
        float pt = Minecraft.getInstance().getFrameTime();
        pose(p, s, "rightArm", m.rightArm, pt);
        pose(p, s, "leftArm", m.leftArm, pt);
        pose(p, s, "torso", m.body, pt);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.jacket.copyFrom(m.body);
    }

    private static void pose(Player p, S s, String name, net.minecraft.client.model.geom.ModelPart part, float pt) {
        Vec3f v = transform(p, s, name, pt, new Vec3f(part.xRot, part.yRot, part.zRot));
        part.setRotation(v.getX(), v.getY(), v.getZ());
    }

    private static Vec3f transform(Player player, S s, String modelName, float tickDelta, Vec3f value) {
        if (s.pose < 0) {
            return value;
        }
        float w = Mth.lerp(tickDelta, s.prevWeight, s.weight);
        w = w * w * (3.0F - 2.0F * w);
        if (w <= 0.0F) {
            return value;
        }
        boolean right = "rightArm".equals(modelName);
        boolean left = "leftArm".equals(modelName);
        boolean torso = "torso".equals(modelName);
        if (!right && !left && !torso) {
            return value;
        }
        // where he aims: the look's pitch, and its yaw from the body
        float pitch = Mth.clamp(player.getViewXRot(tickDelta), -80.0F, 80.0F) * Mth.DEG_TO_RAD;
        float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot)
                - Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot)), -70.0F, 70.0F)
                * Mth.DEG_TO_RAD;
        float aimX = -Mth.HALF_PI + pitch;
        float x;
        float y;
        float z;
        if (torso) {
            float turn = s.pose == AIM || s.pose == BEAM || s.pose == MYSTIC ? yaw * 0.3F : 0.0F;
            return new Vec3f(value.getX(), Mth.lerp(w, value.getY(), turn), value.getZ());
        }
        switch (s.pose) {
            case AIM -> {
                if (right) {
                    x = aimX;
                    y = yaw;
                    z = 0.0F;
                } else {
                    x = -0.35F;
                    y = 0.2F;
                    z = -0.12F;
                }
            }
            case BEAM -> {
                // the right gauntlet out, the left hand bracing it at the wrist
                if (right) {
                    x = aimX;
                    y = yaw;
                    z = 0.0F;
                } else {
                    x = aimX + 0.25F;
                    y = yaw + 0.55F;
                    z = 0.0F;
                }
            }
            case BOTH -> {
                x = aimX;
                y = yaw + (right ? -0.22F : 0.22F);
                z = 0.0F;
            }
            case FIELD -> {
                // raised and opened
                x = -2.35F;
                y = 0.0F;
                z = right ? 0.75F : -0.75F;
            }
            case PUSH -> {
                // thrust out sideways
                x = -0.2F;
                y = 0.0F;
                z = right ? 1.45F : -1.45F;
            }
            case MYSTIC -> {
                if (right) {
                    x = aimX;
                    y = yaw;
                    z = 0.0F;
                } else {
                    x = -1.15F;
                    y = 0.75F;
                    z = 0.0F;
                }
            }
            default -> {
                // the ritual: both arms to the sky
                float sway = Mth.sin((player.tickCount + tickDelta) * 0.3F) * 0.08F;
                x = -2.95F + sway;
                y = 0.0F;
                z = right ? 0.3F : -0.3F;
            }
        }
        return new Vec3f(Mth.lerp(w, value.getX(), x), Mth.lerp(w, value.getY(), y), Mth.lerp(w, value.getZ(), z));
    }
}

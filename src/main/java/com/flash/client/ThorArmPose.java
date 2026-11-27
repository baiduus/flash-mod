package com.flash.client;

import com.flash.thor.ThorPowers;
import com.flash.thor.ThorState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thor's arm, put straight on the model (as the Spider-Man and Doom poses, SpiderPoseMixin): during the Lightning
 * Blast the hand straight out at what he aims (with Mjolnir held so its head points forward), and while he charges
 * the hand raised over the head with the hammer, for the lightning to strike it. Eased in and out.
 */
@Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID, value = Dist.CLIENT)
public final class ThorArmPose {

    private static final int NONE = 0;
    private static final int FORWARD = 1;
    private static final int UP = 2;

    private static final class S {
        int pose;
        float w;
        float wO;
    }

    private static final Map<Integer, S> STATES = new HashMap<>();

    private ThorArmPose() {
    }

    private static int want(Player p) {
        if (!ThorState.isThor(p)) {
            return NONE;
        }
        if (ThorState.flag(p, ThorPowers.F_BLAST)) {
            return FORWARD;
        }
        if (ThorState.flag(p, ThorPowers.F_CHARGING)) {
            return UP;
        }
        return NONE;
    }

    /** Raised to charge: where the hammer is (for the lightning that strikes it), or null. */
    public static boolean raised(Player p) {
        S s = STATES.get(p.getId());
        return s != null && s.pose == UP && s.w > 0.5F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Player p : mc.level.players()) {
            int want = want(p);
            S s = STATES.get(p.getId());
            if (s == null) {
                if (want == NONE) {
                    continue;
                }
                s = new S();
                STATES.put(p.getId(), s);
            }
            s.wO = s.w;
            if (want != NONE) {
                if (s.pose != want && s.w > 0.0F) {
                    // a quick change of arm: straight over
                    s.w = Math.max(0.0F, s.w - 0.5F);
                    if (s.w <= 0.0F) {
                        s.pose = want;
                    }
                } else {
                    s.pose = want;
                    s.w = Math.min(1.0F, s.w + 0.34F);
                }
            } else {
                s.w = Math.max(0.0F, s.w - 0.25F);
            }
        }
    }

    public static void apply(PlayerModel<?> m, LivingEntity e, float ageInTicks) {
        if (!(e instanceof Player p) || ageInTicks == 0.0F) {
            return;
        }
        S s = STATES.get(p.getId());
        if (s == null || s.pose == NONE || (s.w <= 0.0F && s.wO <= 0.0F)) {
            return;
        }
        float pt = Minecraft.getInstance().getFrameTime();
        float w = Mth.lerp(pt, s.wO, s.w);
        w = w * w * (3.0F - 2.0F * w);
        boolean rightMain = p.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = rightMain ? m.rightArm : m.leftArm;
        float side = rightMain ? 1.0F : -1.0F;
        float x;
        float y;
        float z;
        if (s.pose == FORWARD) {
            // straight out at the aim (the head's pitch and yaw): the hammer's head forward
            float pitch = Mth.clamp(p.getViewXRot(pt), -80.0F, 80.0F) * Mth.DEG_TO_RAD;
            float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot)
                    - Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot)), -60.0F, 60.0F) * Mth.DEG_TO_RAD;
            x = -Mth.HALF_PI + pitch;
            y = yaw - 0.1F * side;
            z = 0.0F;
        } else {
            // raised high over the head, a little out to the side, the hammer to the sky
            float sway = Mth.sin((p.tickCount + pt) * 0.25F) * 0.04F;
            x = -Mth.PI + 0.12F + sway;
            y = 0.0F;
            z = 0.12F * side;
        }
        arm.xRot = Mth.lerp(w, arm.xRot, x);
        arm.yRot = Mth.lerp(w, arm.yRot, y);
        arm.zRot = Mth.lerp(w, arm.zRot, z);
        (rightMain ? m.rightSleeve : m.leftSleeve).copyFrom(arm);
    }
}

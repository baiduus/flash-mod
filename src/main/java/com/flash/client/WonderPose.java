package com.flash.client;

import com.flash.FlashMod;
import com.flash.wonder.WonderAbilities;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wonder Woman's arms when she uses her powers (posed by SpiderPoseMixin right after the game poses the model, eased
 * in and out): the lasso thrown, whirled over her head; the bracelets crossed before her face (the guard, held while
 * it lasts) and struck together; the shield thrown backhand; both fists raised for the leap of the Cataclysm and
 * driven down into the ground.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class WonderPose {

    private static final int THROW = 0;
    private static final int WHIRL = 1;
    private static final int GUARD = 2;
    private static final int CLASH = 3;
    private static final int SHIELD = 4;
    private static final int LEAP = 5;
    private static final int SLAM = 6;

    private static final class S {
        int pose = -1;
        int left;
        int total = 1;
        float weight;
        float prevWeight;
    }

    private static final Map<Integer, S> STATES = new HashMap<>();

    private WonderPose() {
    }

    static void cast(Player p, int fx) {
        int pose;
        int ticks;
        switch (fx) {
            case WonderAbilities.FX_LASSO -> {
                pose = THROW;
                ticks = 10;
            }
            case WonderAbilities.FX_SPIN -> {
                pose = WHIRL;
                ticks = 14;
            }
            case WonderAbilities.FX_GUARD -> {
                pose = GUARD;
                ticks = 8;
            }
            case WonderAbilities.FX_CLASH -> {
                pose = CLASH;
                ticks = 12;
            }
            case WonderAbilities.FX_SHIELD -> {
                pose = SHIELD;
                ticks = 9;
            }
            case WonderAbilities.FX_LEAP -> {
                pose = LEAP;
                ticks = 16;
            }
            case WonderAbilities.FX_CATACLYSM -> {
                pose = SLAM;
                ticks = 14;
            }
            default -> {
                return;
            }
        }
        S s = STATES.computeIfAbsent(p.getId(), k -> new S());
        s.pose = pose;
        s.left = ticks;
        s.total = ticks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        // the guard is held as long as it lasts
        for (Player p : mc.level.players()) {
            if (WonderClient.guarding(p)) {
                S s = STATES.computeIfAbsent(p.getId(), k -> new S());
                if (s.pose == GUARD || s.left <= 0) {
                    s.pose = GUARD;
                    s.left = Math.max(s.left, 2);
                    s.total = Math.max(s.total, 2);
                }
            }
        }
        if (STATES.isEmpty()) {
            return;
        }
        STATES.entrySet().removeIf(en -> {
            S s = en.getValue();
            s.prevWeight = s.weight;
            if (s.left > 0) {
                s.left--;
                s.weight = Math.min(1.0F, s.weight + 0.4F);
            } else {
                s.weight = Math.max(0.0F, s.weight - 0.2F);
            }
            return s.weight <= 0.0F && s.prevWeight <= 0.0F && s.left <= 0
                    || !(mc.level.getEntity(en.getKey()) instanceof Player);
        });
    }

    public static void apply(PlayerModel<?> m, LivingEntity e, float ageInTicks) {
        if (!(e instanceof Player p) || ageInTicks == 0.0F || !WonderClient.isWonder(p)) {
            return;
        }
        S s = STATES.get(p.getId());
        if (s == null || s.pose < 0 || s.weight <= 0.0F && s.prevWeight <= 0.0F) {
            return;
        }
        float pt = Minecraft.getInstance().getFrameTime();
        float w = Mth.lerp(pt, s.prevWeight, s.weight);
        w = w * w * (3.0F - 2.0F * w);
        if (w <= 0.0F) {
            return;
        }
        // how far through the move (0 start, 1 end)
        float prog = 1.0F - Mth.clamp((s.left - pt) / Math.max(1, s.total), 0.0F, 1.0F);
        float pitch = Mth.clamp(p.getViewXRot(pt), -80.0F, 80.0F) * Mth.DEG_TO_RAD;
        float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot)
                - Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot)), -70.0F, 70.0F) * Mth.DEG_TO_RAD;
        float aimX = -Mth.HALF_PI + pitch;
        float time = p.tickCount + pt;
        float[] r;
        float[] l;
        float bodyY = 0.0F;
        switch (s.pose) {
            case THROW -> {
                // the right arm swung out at what she lassoes
                float swing = Mth.sin(Math.min(1.0F, prog * 1.6F) * Mth.PI) * 0.6F;
                r = new float[] {aimX - swing, yaw, 0.0F};
                l = new float[] {-0.3F, 0.1F, -0.2F};
                bodyY = yaw * 0.3F;
            }
            case WHIRL -> {
                // the lasso whirled over her head
                r = new float[] {-2.9F, 0.0F, 0.25F + Mth.sin(time * 1.2F) * 0.25F};
                l = new float[] {-0.4F, 0.0F, -0.35F};
                bodyY = Mth.sin(time * 1.2F) * 0.15F;
            }
            case GUARD -> {
                // the forearms crossed before her face
                r = new float[] {-1.75F, -0.6F, 0.0F};
                l = new float[] {-1.75F, 0.6F, 0.0F};
            }
            case CLASH -> {
                // crossed, then struck together and flung out
                if (prog < 0.35F) {
                    r = new float[] {-1.9F, -0.7F, 0.0F};
                    l = new float[] {-1.9F, 0.7F, 0.0F};
                } else {
                    r = new float[] {aimX, yaw - 0.15F, 0.0F};
                    l = new float[] {aimX, yaw + 0.15F, 0.0F};
                }
            }
            case SHIELD -> {
                // a backhand throw: from across the chest out at the target
                float k = Mth.clamp(prog * 1.5F, 0.0F, 1.0F);
                r = new float[] {Mth.lerp(k, -1.3F, aimX), Mth.lerp(k, 0.9F, yaw - 0.3F), 0.0F};
                l = new float[] {-0.25F, 0.0F, -0.15F};
                bodyY = Mth.lerp(k, 0.4F, -0.2F);
            }
            case LEAP -> {
                // both fists raised to the sky
                r = new float[] {-2.95F, 0.0F, 0.2F};
                l = new float[] {-2.95F, 0.0F, -0.2F};
            }
            default -> {
                // both fists driven down into the ground
                r = new float[] {-0.9F, 0.0F, 0.15F};
                l = new float[] {-0.9F, 0.0F, -0.15F};
            }
        }
        pose(m.rightArm, r, w);
        pose(m.leftArm, l, w);
        m.body.yRot = Mth.lerp(w, m.body.yRot, bodyY);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.jacket.copyFrom(m.body);
    }

    private static void pose(ModelPart part, float[] to, float w) {
        part.setRotation(Mth.lerp(w, part.xRot, to[0]), Mth.lerp(w, part.yRot, to[1]), Mth.lerp(w, part.zRot, to[2]));
    }
}

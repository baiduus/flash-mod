package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.client.VfxDraw;
import com.flash.infinity.InfPowers;
import com.flash.infinity.StoneFx;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The stones' effects, drawn in light (VfxDraw): each stone in its colour. One-shots (a jump through space, a punch
 * of power, a soul healed or torn out, reality rewritten, illusions, a mind read, time bent, rockets) and what goes
 * on while it lasts (time frozen: clock rings; the power cyclone: a violet vortex; intangibility: a golden ghost).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class StoneVfx {

    private static final float[] SPACE = {0.3F, 0.65F, 1.0F};
    private static final float[] MIND = {1.0F, 0.85F, 0.2F};
    private static final float[] REALITY = {1.0F, 0.15F, 0.2F};
    private static final float[] POWER = {0.7F, 0.2F, 1.0F};
    private static final float[] TIME = {0.2F, 1.0F, 0.45F};
    private static final float[] SOUL = {1.0F, 0.55F, 0.1F};
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private record Fx(byte kind, int entity, Vec3 a, Vec3 b, int life, int[] age) {
    }

    private static final List<Fx> FX = new ArrayList<>();

    private StoneVfx() {
    }

    public static void add(byte kind, int entity, Vec3 a, Vec3 b) {
        int life = switch (kind) {
            case StoneFx.JUMP -> 18;
            case StoneFx.PUNCH -> 12;
            case StoneFx.HEAL -> 30;
            case StoneFx.DRAIN -> 60;
            case StoneFx.REALITY -> 20;
            case StoneFx.CLONES -> 16;
            case StoneFx.MIND -> 14;
            case StoneFx.TIME -> 26;
            case StoneFx.ROCKETS -> 10;
            default -> 10;
        };
        if (FX.size() < 64) {
            FX.add(new Fx(kind, entity, a, b, life, new int[1]));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) {
            return;
        }
        for (Iterator<Fx> it = FX.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (++f.age()[0] >= f.life()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean any = !FX.isEmpty();
        if (!any) {
            for (Player p : mc.level.players()) {
                if (lasting(p) != 0) {
                    any = true;
                    break;
                }
            }
        }
        if (!any) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        BufferBuilder buf = VfxDraw.begin();
        try {
            for (Fx f : FX) {
                draw(buf, m, mc, f, pt, cam, left, up);
            }
            for (Player p : mc.level.players()) {
                int l = lasting(p);
                if (l != 0) {
                    lastingFx(buf, m, p, l, pt, cam, left, up);
                }
            }
        } finally {
            VfxDraw.end(buf);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------
    // While it lasts
    // ------------------------------------------------------------------

    private static final int L_TIME = 1;
    private static final int L_CYCLONE = 2;
    private static final int L_GHOST = 4;

    private static int lasting(Player p) {
        Set<String> keys = InfinityClient.visualKeys(p);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        int out = 0;
        for (String k : keys) {
            InfPowers.Ability a = InfPowers.ability(k);
            if (a == null) {
                continue;
            }
            switch (a.type) {
                case "flash:time_freeze" -> out |= L_TIME;
                case "flash:power_cyclone" -> out |= L_CYCLONE;
                case "palladium:intangibility" -> out |= L_GHOST;
                default -> {
                }
            }
        }
        return out;
    }

    private static void lastingFx(BufferBuilder buf, Matrix4f m, Player p, int l, float pt, Vec3 cam, Vec3 left,
                                  Vec3 up) {
        float t = p.tickCount + pt;
        Vec3 feet = p.getPosition(pt);
        Vec3 mid = feet.add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
        if ((l & L_TIME) != 0) {
            // the Time Stone: green clock rings turning around the body, a dial on the ground
            for (int i = 0; i < 3; i++) {
                Vec3 n = new Vec3(Math.sin(t * 0.03D + i * 2.1D), 1.2D, Math.cos(t * 0.03D + i * 2.1D)).normalize();
                VfxDraw.ring(buf, m, mid, n, 1.1D + i * 0.18D, 0.04D, t * (0.06F + i * 0.02F) * (i % 2 == 0 ? 1 : -1),
                        TIME[0], TIME[1], TIME[2], 0.7F);
            }
            Vec3 dial = feet.add(0.0D, 0.04D, 0.0D);
            VfxDraw.ring(buf, m, dial, UP, 1.6D, 0.05D, 0.0F, TIME[0], TIME[1], TIME[2], 0.6F);
            for (int h = 0; h < 12; h++) {
                double a = h * Math.PI / 6.0D;
                Vec3 o = dial.add(Math.cos(a) * 1.45D, 0.0D, Math.sin(a) * 1.45D);
                VfxDraw.glow(buf, m, o, left, up, 0.08D, TIME[0], TIME[1], TIME[2], 0.8F);
            }
            double hand = t * 0.05D;
            VfxDraw.energy(buf, m, dial, dial.add(Math.cos(hand) * 1.3D, 0.0D, Math.sin(hand) * 1.3D), 0.05F,
                    TIME[0], TIME[1], TIME[2], 0.9F);
            VfxDraw.energy(buf, m, dial, dial.add(Math.cos(hand / 12.0D) * 0.8D, 0.0D, Math.sin(hand / 12.0D)
                    * 0.8D), 0.07F, 0.8F, 1.0F, 0.85F, 0.9F);
        }
        if ((l & L_CYCLONE) != 0) {
            // the power cyclone: violet bands winding up around him
            for (int k = 0; k < 3; k++) {
                Vec3[] pts = new Vec3[20];
                for (int i = 0; i < pts.length; i++) {
                    double h = i / (double) (pts.length - 1);
                    double a = t * 0.35D + k * Math.PI * 2.0D / 3.0D + h * Math.PI * 3.0D;
                    double r = 0.7D + h * 1.6D;
                    pts[i] = feet.add(Math.cos(a) * r, h * 3.2D, Math.sin(a) * r);
                }
                VfxDraw.path(buf, m, pts, 0.09F, POWER[0], POWER[1], POWER[2], 0.75F);
            }
            VfxDraw.glow(buf, m, mid, left, up, 1.6D, POWER[0], POWER[1], POWER[2], 0.18F);
        }
        if ((l & L_GHOST) != 0) {
            // intangible: a faint golden ghost of him
            float pulse = 0.5F + 0.2F * (float) Math.sin(t * 0.3F);
            VfxDraw.sphere(buf, m, mid, 1.0D, cam, MIND[0], MIND[1], MIND[2], 0.15F * pulse, t, 1.5D);
            VfxDraw.glow(buf, m, mid, left, up, 1.2D, MIND[0], MIND[1], MIND[2], 0.12F * pulse);
        }
    }

    // ------------------------------------------------------------------
    // One-shots
    // ------------------------------------------------------------------

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Fx f, float pt, Vec3 cam, Vec3 left,
                             Vec3 up) {
        float age = f.age()[0] + pt;
        float k = Math.min(1.0F, age / f.life());
        float fade = 1.0F - k;
        Entity e = f.entity() < 0 ? null : mc.level.getEntity(f.entity());
        switch (f.kind()) {
            case StoneFx.JUMP -> {
                // space folds: an implosion where he was, a burst where he lands, a thread of light between
                float in = (float) Math.pow(fade, 2.0D);
                Vec3 a = f.a().add(0.0D, 1.0D, 0.0D);
                Vec3 b = f.b().add(0.0D, 1.0D, 0.0D);
                VfxDraw.sphere(buf, m, a, 1.4D * in + 0.05D, cam, SPACE[0], SPACE[1], SPACE[2], 0.7F * in, age, 2.0D);
                VfxDraw.ring(buf, m, a, UP, 1.8D * in + 0.1D, 0.12D, age * 0.3F, SPACE[0], SPACE[1], SPACE[2], in);
                VfxDraw.burst(buf, m, b, left, up, 2.2F * k + 0.2F, SPACE[0], SPACE[1], SPACE[2], fade, age * 0.2F);
                VfxDraw.ring(buf, m, f.b().add(0.0D, 0.1D, 0.0D), UP, 0.3D + 3.0D * k, 0.2D * fade, 0.0F, SPACE[0],
                        SPACE[1], SPACE[2], fade);
                VfxDraw.energy(buf, m, a, b, 0.06F * fade, 0.7F, 0.9F, 1.0F, 0.6F * fade);
            }
            case StoneFx.PUNCH -> {
                // a cone of shock rings driven forward
                Vec3 dir = f.b().normalize();
                for (int i = 0; i < 4; i++) {
                    double d = 0.6D + i * 1.4D + k * 3.0D;
                    VfxDraw.ring(buf, m, f.a().add(dir.scale(d)), dir, 0.4D + d * 0.35D, 0.12D, age * 0.2F, POWER[0],
                            POWER[1], POWER[2], fade * (1.0F - i * 0.2F));
                }
                VfxDraw.burst(buf, m, f.a().add(dir.scale(0.8D)), left, up, 1.4F * fade + 0.3F, 1.0F, 0.8F, 1.0F,
                        fade, age * 0.4F);
            }
            case StoneFx.ROCKETS -> {
                Vec3 dir = f.b().normalize();
                VfxDraw.burst(buf, m, f.a().add(dir.scale(0.5D)), left, up, 1.2F * fade + 0.2F, POWER[0], POWER[1],
                        POWER[2], fade, age * 0.3F);
                VfxDraw.glow(buf, m, f.a(), left, up, 0.9D * fade, 1.0F, 0.7F, 1.0F, fade);
            }
            case StoneFx.HEAL -> {
                // the Soul Stone mends: orange souls spiralling up the body
                Vec3 base = e != null ? e.getPosition(pt) : f.a();
                for (int s = 0; s < 3; s++) {
                    Vec3[] pts = VfxDraw.helix(base, base.add(0.0D, 2.2D, 0.0D), 0.7D * fade + 0.2D, 1.5D,
                            age * 0.25D + s * 2.1D, 18);
                    VfxDraw.path(buf, m, pts, 0.06F, SOUL[0], SOUL[1], SOUL[2], 0.8F * fade);
                }
                VfxDraw.glow(buf, m, base.add(0.0D, 1.0D, 0.0D), left, up, 1.3D, SOUL[0], SOUL[1], SOUL[2],
                        0.35F * fade);
            }
            case StoneFx.DRAIN -> {
                // a soul torn out: an orange tendril winding from the victim to the stone
                Vec3 to = e != null ? e.getPosition(pt).add(0.0D, 1.0D, 0.0D) : f.b();
                Vec3 from = f.a();
                float al = Math.min(1.0F, age / 6.0F) * fade;
                Vec3[] coil = VfxDraw.helix(from, to, 0.25D, 4.0D, -age * 0.5D, 40);
                VfxDraw.path(buf, m, coil, 0.07F, SOUL[0], SOUL[1], SOUL[2], al);
                VfxDraw.energy(buf, m, from, to, 0.05F, 1.0F, 0.8F, 0.5F, 0.6F * al);
                double run = (age * 0.05D) % 1.0D;
                VfxDraw.glow(buf, m, from.lerp(to, run), left, up, 0.35D, 1.0F, 0.85F, 0.55F, al);
                VfxDraw.glow(buf, m, from, left, up, 0.8D, SOUL[0], SOUL[1], SOUL[2], 0.5F * al);
            }
            case StoneFx.REALITY -> {
                // reality rewritten at a block: red shards spinning in, a flicker of its edges
                Vec3 c = f.a();
                double s = 0.55D + 0.25D * fade;
                for (int i = 0; i < 3; i++) {
                    Vec3 n = new Vec3(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0);
                    VfxDraw.ring(buf, m, c, n, s, 0.05D, age * 0.3F, REALITY[0], REALITY[1], REALITY[2], fade);
                }
                VfxDraw.burst(buf, m, c, left, up, 1.0F * fade + 0.2F, REALITY[0], REALITY[1], REALITY[2], fade,
                        age * 0.5F);
                VfxDraw.glow(buf, m, c, left, up, 0.9D, 1.0F, 0.4F, 0.4F, 0.5F * fade);
            }
            case StoneFx.CLONES -> {
                Vec3 c = f.a().add(0.0D, 1.0D, 0.0D);
                VfxDraw.ring(buf, m, f.a().add(0.0D, 0.1D, 0.0D), UP, 0.5D + 4.0D * k, 0.15D * fade, age * 0.2F,
                        MIND[0], MIND[1], MIND[2], fade);
                VfxDraw.burst(buf, m, c, left, up, 2.5F * k + 0.3F, MIND[0], MIND[1], MIND[2], fade, age * 0.3F);
            }
            case StoneFx.MIND -> {
                VfxDraw.energy(buf, m, f.a().add(0.0D, -0.2D, 0.0D), f.b(), 0.05F * fade, MIND[0], MIND[1], MIND[2],
                        0.7F * fade);
                Vec3[] coil = VfxDraw.helix(f.a().add(0.0D, -0.2D, 0.0D), f.b(), 0.15D, 6.0D, age * 0.6D, 40);
                VfxDraw.path(buf, m, coil, 0.03F, 1.0F, 1.0F, 0.7F, 0.6F * fade);
            }
            case StoneFx.TIME -> {
                Vec3 c = f.a().add(0.0D, 1.0D, 0.0D);
                for (int i = 0; i < 3; i++) {
                    double r = 0.3D + (k * 4.0D) * (1.0D - i * 0.25D);
                    VfxDraw.ring(buf, m, f.a().add(0.0D, 0.05D + i * 0.6D, 0.0D), UP, r, 0.08D, age * 0.1F, TIME[0],
                            TIME[1], TIME[2], fade);
                }
                VfxDraw.glow(buf, m, c, left, up, 1.5D * fade, TIME[0], TIME[1], TIME[2], 0.4F * fade);
            }
            default -> {
            }
        }
    }
}

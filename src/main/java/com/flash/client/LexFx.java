package com.flash.client;

import com.flash.FlashMod;
import com.flash.lex.LexState;
import com.flash.lex.LexSuit;
import com.flash.network.LexFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The beams of Lex's suit drawn as Legends: Superheroes draws Iron Man's: solid light (a white core in coloured
 * glow, additive): the repulsor beam and blasts and the unibeam in Kryptonite green, the wrist lasers in red,
 * the shockwave of the power dive.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class LexFx {

    private record Shot(int kind, int entity, Vec3 a, Vec3 b, long start, int life) {
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final float[] GREEN = {0.35F, 1.0F, 0.35F};
    private static final float[] RED = {1.0F, 0.12F, 0.1F};

    private LexFx() {
    }

    public static void reset() {
        SHOTS.clear();
    }

    public static void handle(LexFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 a = new Vec3(p.ax(), p.ay(), p.az());
        Vec3 b = new Vec3(p.bx(), p.by(), p.bz());
        int life = p.kind() == LexFxPacket.LASER ? 10 : p.kind() == LexFxPacket.DIVE ? 12 : 5;
        SHOTS.add(new Shot(p.kind(), p.entity(), a, b, mc.level.getGameTime(), life));
        if (p.kind() == LexFxPacket.BLAST) {
            for (int i = 0; i < 6; i++) {
                mc.level.addParticle(ParticleTypes.END_ROD, b.x, b.y, b.z, (Math.random() - 0.5D) * 0.2D,
                        Math.random() * 0.2D, (Math.random() - 0.5D) * 0.2D);
            }
        }
        if (SHOTS.size() > 64) {
            SHOTS.remove(0);
        }
    }

    /** About where the right palm is (aiming forward). */
    static Vec3 palm(Player p, float partial, boolean right) {
        Vec3 look = p.getViewVector(partial);
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        boolean first = p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        return p.getEyePosition(partial).add(look.scale(first ? 0.9D : 0.8D)).add(side.scale(right ? 0.32D : -0.32D))
                .add(0.0D, first ? -0.3D : -0.4D, 0.0D);
    }

    static Vec3 chest(Player p, float partial) {
        Vec3 pos = p.getPosition(partial);
        return pos.add(0.0D, p.getBbHeight() * 0.68D, 0.0D).add(p.getViewVector(partial).scale(0.45D));
    }

    private static Vec3 aim(ClientLevel level, Player p, Vec3 from, float partial, double range) {
        Vec3 eye = p.getEyePosition(partial);
        Vec3 look = p.getViewVector(partial);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, stop, box,
                e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(stop));
        return hit != null ? hit.getLocation() : stop;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick();
        float time = level.getGameTime() + partial;
        SHOTS.removeIf(s -> time - s.start() > s.life());
        List<Player> beaming = new ArrayList<>();
        for (Player p : level.players()) {
            if (LexState.flag(p, LexSuit.F_BEAM) || LexState.flag(p, LexSuit.F_UNIBEAM)
                    || LexState.flag(p, LexSuit.F_UNI_CHARGING)) {
                beaming.add(p);
            }
        }
        if (SHOTS.isEmpty() && beaming.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer c = buffers.getBuffer(RenderType.lightning());
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        float flicker = 0.9F + 0.1F * (float) Math.sin(time * 2.3F);
        for (Player p : beaming) {
            if (LexState.flag(p, LexSuit.F_BEAM)) {
                for (boolean right : new boolean[] {true, false}) {
                    Vec3 from = palm(p, partial, right);
                    Vec3 to = aim(level, p, from, partial, 32.0D);
                    beam(c, m, cam, from, to, 0.07F * flicker, GREEN, 1.0F);
                }
            }
            if (LexState.flag(p, LexSuit.F_UNIBEAM)) {
                Vec3 from = chest(p, partial);
                Vec3 to = aim(level, p, from, partial, 40.0D);
                beam(c, m, cam, from, to, 0.22F * flicker, GREEN, 1.0F);
            } else if (LexState.flag(p, LexSuit.F_UNI_CHARGING)) {
                // the arc reactor gathering power
                Vec3 at = chest(p, partial);
                float r = 0.12F + 0.05F * (float) Math.sin(time * 1.7F);
                Vec3 d = new Vec3(0.0D, r, 0.0D);
                beam(c, m, cam, at.subtract(d), at.add(d), r * 1.5F, GREEN, 0.9F);
            }
        }
        for (Shot s : SHOTS) {
            float age = time - s.start();
            float k = age / s.life();
            switch (s.kind()) {
                case LexFxPacket.BLAST -> beam(c, m, cam, s.a(), s.b(), 0.12F * (1.0F - k * 0.6F), GREEN, 1.0F - k);
                case LexFxPacket.LASER -> {
                    // the two wrist lasers sweep from side to side in front
                    Entity e = level.getEntity(s.entity());
                    Vec3 dir = s.b();
                    double base = Math.atan2(dir.z, dir.x);
                    // (a direction twice as long: the sweep goes back the other way)
                    double sweep = 75.0D - 150.0D * Math.min(1.0D, k);
                    if (dir.length() > 1.5D) {
                        sweep = -sweep;
                    }
                    for (int w = 0; w < 2; w++) {
                        double ang = base + Math.toRadians((w == 0 ? 1 : -1) * sweep);
                        Vec3 from = e instanceof Player pl ? palm(pl, partial, w == 0) : s.a();
                        Vec3 to = from.add(Math.cos(ang) * 9.0D, 0.0D, Math.sin(ang) * 9.0D);
                        beam(c, m, cam, from, to, 0.035F, RED, 1.0F - k * 0.5F);
                    }
                }
                case LexFxPacket.DIVE -> {
                    double r = 0.5D + 5.5D * Math.sqrt(k);
                    int n = 32;
                    for (int i = 0; i < n; i++) {
                        double a0 = i * Math.PI * 2.0D / n;
                        double a1 = (i + 1) * Math.PI * 2.0D / n;
                        Vec3 p0 = s.a().add(Math.cos(a0) * r, 0.2D, Math.sin(a0) * r);
                        Vec3 p1 = s.a().add(Math.cos(a1) * r, 0.2D, Math.sin(a1) * r);
                        beam(c, m, cam, p0, p1, 0.18F, GREEN, 1.0F - k);
                    }
                }
                default -> {
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lightning());
    }

    /** A beam of solid light: glow, middle and white core, each a strip facing the camera. */
    static void beam(VertexConsumer c, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float[] color, float alpha) {
        strip(c, m, cam, a, b, width * 3.0F, color[0], color[1], color[2], 0.22F * alpha);
        strip(c, m, cam, a, b, width * 1.6F, color[0], color[1], color[2], 0.55F * alpha);
        strip(c, m, cam, a, b, width * 0.6F, 1.0F, 1.0F, 1.0F, 0.95F * alpha);
    }

    private static void strip(VertexConsumer c, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g,
                              float bl, float alpha) {
        Vec3 d = b.subtract(a);
        Vec3 toCam = cam.subtract(a.add(d.scale(0.5D)));
        Vec3 side = d.cross(toCam);
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize().scale(width * 0.5D);
        vertex(c, m, a.add(side), r, g, bl, alpha);
        vertex(c, m, b.add(side), r, g, bl, alpha);
        vertex(c, m, b.subtract(side), r, g, bl, alpha);
        vertex(c, m, a.subtract(side), r, g, bl, alpha);
        // the other face too (the strip is seen from both sides)
        vertex(c, m, a.subtract(side), r, g, bl, alpha);
        vertex(c, m, b.subtract(side), r, g, bl, alpha);
        vertex(c, m, b.add(side), r, g, bl, alpha);
        vertex(c, m, a.add(side), r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        c.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }
}

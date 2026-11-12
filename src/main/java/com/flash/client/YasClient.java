package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.YasFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Yas on the client: who is Yas (for YasLayer), the Guardian's question (YasDialogScreen) and the effects of the
 * transcendence:
 * <ul>
 * <li>rising: very pale yellow lightning wrapping the body, more and more of it; halfway, a white beam of light
 * from the sky through the middle of the body into the ground; at the end a white flash;</li>
 * <li>the punishment: a beam of black energy from the Guardian's eyes into the player and black lightning around
 * him, the sight going dark for 3 s, then a burst of black dust.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class YasClient {

    static final ResourceLocation WHITE = new ResourceLocation(FlashMod.MODID, "textures/misc/white.png");
    static final float[] PALE_YELLOW = {1.0F, 0.97F, 0.68F};
    private static final float[] WHITE_C = {1.0F, 1.0F, 1.0F};

    private static final Set<Integer> YAS = new HashSet<>();

    private record Fx(int kind, int entity, int source, long start, int ticks) {
    }

    private static final List<Fx> FX = new ArrayList<>();
    private static long flashUntil;
    private static long flashStart;

    private YasClient() {
    }

    public static void reset() {
        YAS.clear();
        FLYING.clear();
        NO_LIGHTNING.clear();
        NO_EYES.clear();
        FX.clear();
        flashUntil = 0L;
    }

    private static final Set<Integer> FLYING = new HashSet<>();
    private static final Set<Integer> NO_LIGHTNING = new HashSet<>();
    private static final Set<Integer> NO_EYES = new HashSet<>();

    public static void setYas(int entity, boolean yas, boolean flying, boolean lightning, boolean eyes) {
        if (lightning) {
            NO_LIGHTNING.remove(entity);
        } else {
            NO_LIGHTNING.add(entity);
        }
        if (eyes) {
            NO_EYES.remove(entity);
        } else {
            NO_EYES.add(entity);
        }
        if (yas) {
            YAS.add(entity);
        } else {
            YAS.remove(entity);
        }
        if (yas && flying) {
            FLYING.add(entity);
        } else {
            FLYING.remove(entity);
        }
    }

    /** Whether her body shows the lightning / the eyes glow (her choice in the K menu). */
    public static boolean showLightning(Entity e) {
        return !NO_LIGHTNING.contains(e.getId());
    }

    public static boolean showEyes(Entity e) {
        return !NO_EYES.contains(e.getId());
    }

    public static boolean localLightning() {
        var p = Minecraft.getInstance().player;
        return p == null || showLightning(p);
    }

    public static boolean localEyes() {
        var p = Minecraft.getInstance().player;
        return p == null || showEyes(p);
    }

    /** Shown at once in the menu (the server confirms with the sync). */
    public static void setLocalLook(boolean lightning, boolean eyes) {
        var p = Minecraft.getInstance().player;
        if (p != null) {
            setYas(p.getId(), isYas(p), FLYING.contains(p.getId()), lightning, eyes);
        }
    }

    /** Yas in flight (the local player: her own abilities). */
    public static boolean flying(net.minecraft.world.entity.player.Player p) {
        if (!isYas(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying : FLYING.contains(p.getId());
    }

    public static boolean isYas(Entity e) {
        return YAS.contains(e.getId());
    }

    public static void openDialog(int guardian) {
        Minecraft.getInstance().setScreen(new YasDialogScreen(guardian));
    }

    public static void fx(YasFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        FX.removeIf(f -> f.entity() == p.entity());
        Entity e = mc.level.getEntity(p.entity());
        switch (p.kind()) {
            case YasFxPacket.ASCEND, YasFxPacket.DOOM -> FX.add(new Fx(p.kind(), p.entity(), p.source(), now,
                    p.ticks()));
            case YasFxPacket.ASCEND_END -> {
                if (e != null) {
                    burst(mc.level, e, true);
                }
                if (mc.player != null && p.entity() == mc.player.getId()) {
                    flashStart = now;
                    flashUntil = now + 30;
                }
            }
            case YasFxPacket.DOOM_END -> {
                if (e != null) {
                    burst(mc.level, e, false);
                }
            }
            default -> {
            }
        }
    }

    private static void burst(ClientLevel level, Entity e, boolean light) {
        Random r = new Random();
        for (int i = 0; i < 80; i++) {
            double a = r.nextDouble() * Math.PI * 2.0D;
            double v = 0.1D + r.nextDouble() * 0.4D;
            double vy = (r.nextDouble() - 0.3D) * 0.4D;
            if (light) {
                level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, e.getX(),
                        e.getY() + 1.0D, e.getZ(), Math.cos(a) * v, vy, Math.sin(a) * v);
            } else {
                level.addParticle(new DustParticleOptions(new Vector3f(0.05F, 0.0F, 0.08F), 2.0F), e.getX(),
                        e.getY() + 1.0D, e.getZ(), Math.cos(a) * v, vy, Math.sin(a) * v);
                level.addParticle(ParticleTypes.SQUID_INK, e.getX(), e.getY() + 1.0D, e.getZ(), Math.cos(a) * v * 0.6D,
                        vy, Math.sin(a) * v * 0.6D);
            }
        }
    }

    // ------------------------------------------------------------------
    // Particles of the sequences and of Yas
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        FX.removeIf(f -> now - f.start() > f.ticks() + 2 || mc.level.getEntity(f.entity()) == null);
        Random r = new Random();
        for (Fx f : FX) {
            Entity e = mc.level.getEntity(f.entity());
            if (e == null) {
                continue;
            }
            boolean ascend = f.kind() == YasFxPacket.ASCEND;
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2.0D;
                double rad = 0.6D + r.nextDouble() * 0.6D;
                if (ascend) {
                    mc.level.addParticle(ParticleTypes.END_ROD, e.getX() + Math.cos(a) * rad,
                            e.getY() + r.nextDouble() * 2.0D, e.getZ() + Math.sin(a) * rad, 0.0D, 0.04D, 0.0D);
                } else {
                    mc.level.addParticle(new DustParticleOptions(new Vector3f(0.03F, 0.0F, 0.06F), 1.4F),
                            e.getX() + Math.cos(a) * rad, e.getY() + r.nextDouble() * 2.0D,
                            e.getZ() + Math.sin(a) * rad, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        // Yas: a spark now and then on her body
        for (var p : mc.level.players()) {
            if (isYas(p) && !p.isInvisible() && r.nextInt(5) == 0) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (r.nextDouble() - 0.5D) * 0.7D,
                        p.getY() + r.nextDouble() * 1.8D, p.getZ() + (r.nextDouble() - 0.5D) * 0.7D, 0.0D, 0.02D,
                        0.0D);
            }
        }
    }

    // ------------------------------------------------------------------
    // The lightning and the beams
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick();
        float time = level.getGameTime() + partial;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (Fx f : FX) {
            Entity e = level.getEntity(f.entity());
            if (e == null) {
                continue;
            }
            float age = time - f.start();
            Vec3 at = e.getPosition(partial);
            if (f.kind() == YasFxPacket.ASCEND) {
                ascend(glow, m, cam, at, age, f.ticks());
            } else {
                doomGlow(glow, m, cam, level, f, e, at, age, partial);
            }
        }
        buffers.endBatch(RenderType.lightning());
        // the black parts: drawn dark over the world (not added as light)
        VertexConsumer dark = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (Fx f : FX) {
            Entity e = level.getEntity(f.entity());
            if (e == null || f.kind() != YasFxPacket.DOOM) {
                continue;
            }
            doomDark(dark, m, cam, level, f, e, e.getPosition(partial), time - f.start(), partial);
        }
        buffers.endBatch(RenderType.entityTranslucent(WHITE));
        pose.popPose();
    }

    private static void ascend(VertexConsumer c, Matrix4f m, Vec3 cam, Vec3 at, float age, int ticks) {
        float k = Math.min(1.0F, age / ticks);
        Random r = new Random((long) (age / 2.0F) * 7919L + (long) (at.x * 13.0D));
        int bolts = 3 + (int) (k * 9.0F);
        for (int i = 0; i < bolts; i++) {
            double a0 = r.nextDouble() * Math.PI * 2.0D;
            double a1 = a0 + (r.nextDouble() - 0.5D) * 2.2D;
            double rad = 0.45D + r.nextDouble() * 0.5D + k * 0.4D;
            Vec3 from = at.add(Math.cos(a0) * rad, r.nextDouble() * 2.0D, Math.sin(a0) * rad);
            Vec3 to = at.add(Math.cos(a1) * rad * 0.8D, r.nextDouble() * 2.0D, Math.sin(a1) * rad * 0.8D);
            bolt(c, m, cam, r, from, to, 0.035F + k * 0.02F, PALE_YELLOW, 0.9F);
        }
        if (age >= ticks * 0.5F) {
            // the white beam through the middle of the body
            float in = Math.min(1.0F, (age - ticks * 0.5F) / 10.0F);
            float pulse = 0.85F + 0.15F * (float) Math.sin(age * 0.9F);
            Vec3 top = at.add(0.0D, 90.0D, 0.0D);
            Vec3 bottom = at.add(0.0D, -8.0D, 0.0D);
            LexFx.beam(c, m, cam, top, bottom, 0.55F * in * pulse, WHITE_C, in);
            LexFx.beam(c, m, cam, top, bottom, 1.6F * in, PALE_YELLOW, 0.25F * in);
        }
    }

    private static Vec3 eyeOf(ClientLevel level, Fx f, Vec3 fallback, float partial) {
        Entity g = f.source() >= 0 ? level.getEntity(f.source()) : null;
        return g != null ? g.getEyePosition(partial) : fallback.add(0.0D, 12.0D, 0.0D);
    }

    /** The violet edge of the black beam and of the black lightning (light). */
    private static void doomGlow(VertexConsumer c, Matrix4f m, Vec3 cam, ClientLevel level, Fx f, Entity e, Vec3 at,
                                 float age, float partial) {
        Vec3 chest = at.add(0.0D, e.getBbHeight() * 0.6D, 0.0D);
        Vec3 eye = eyeOf(level, f, at, partial);
        float in = Math.min(1.0F, age / 6.0F);
        LexFx.beam(c, m, cam, eye, chest, 0.5F * in, new float[] {0.45F, 0.0F, 0.7F}, 0.35F);
    }

    /** The black beam and the black lightning (drawn dark). */
    private static void doomDark(VertexConsumer c, Matrix4f m, Vec3 cam, ClientLevel level, Fx f, Entity e, Vec3 at,
                                 float age, float partial) {
        Vec3 chest = at.add(0.0D, e.getBbHeight() * 0.6D, 0.0D);
        Vec3 eye = eyeOf(level, f, at, partial);
        float in = Math.min(1.0F, age / 6.0F);
        float k = Math.min(1.0F, age / f.ticks());
        darkStrip(c, m, cam, eye, chest, 0.28F * in, 0.95F);
        Random r = new Random((long) (age / 2.0F) * 104729L + e.getId());
        int bolts = 3 + (int) (k * 7.0F);
        for (int i = 0; i < bolts; i++) {
            double a0 = r.nextDouble() * Math.PI * 2.0D;
            double rad = 0.4D + r.nextDouble() * 0.7D;
            Vec3 from = at.add(Math.cos(a0) * rad, r.nextDouble() * 2.2D, Math.sin(a0) * rad);
            Vec3 to = i % 3 == 0 ? chest : at.add(Math.cos(a0 + 1.0D) * rad, r.nextDouble() * 2.2D,
                    Math.sin(a0 + 1.0D) * rad);
            Vec3 prev = from;
            for (int s = 1; s <= 6; s++) {
                double t = s / 6.0D;
                Vec3 next = from.lerp(to, t);
                if (s < 6) {
                    next = next.add((r.nextDouble() - 0.5D) * 0.25D, (r.nextDouble() - 0.5D) * 0.25D,
                            (r.nextDouble() - 0.5D) * 0.25D);
                }
                darkStrip(c, m, cam, prev, next, 0.06F, 0.9F);
                prev = next;
            }
        }
    }

    /** A jagged bolt of light (glow and white core). */
    static void bolt(VertexConsumer c, Matrix4f m, Vec3 cam, Random r, Vec3 from, Vec3 to, float width, float[] color,
                     float alpha) {
        Vec3 prev = from;
        int segs = 7;
        for (int s = 1; s <= segs; s++) {
            double t = s / (double) segs;
            Vec3 next = from.lerp(to, t);
            if (s < segs) {
                next = next.add((r.nextDouble() - 0.5D) * 0.3D, (r.nextDouble() - 0.5D) * 0.3D,
                        (r.nextDouble() - 0.5D) * 0.3D);
            }
            LexFx.beam(c, m, cam, prev, next, width, color, alpha);
            prev = next;
        }
    }

    private static void darkStrip(VertexConsumer c, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float alpha) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a.add(d.scale(0.5D))));
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize().scale(width * 0.5D);
        int al = (int) (alpha * 255.0F);
        dark(c, m, a.add(side), al);
        dark(c, m, b.add(side), al);
        dark(c, m, b.subtract(side), al);
        dark(c, m, a.subtract(side), al);
    }

    private static void dark(VertexConsumer c, Matrix4f m, Vec3 p, int alpha) {
        c.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(6, 0, 12, alpha).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    // ------------------------------------------------------------------
    // The sight: a white flash when she transcends, darkness while doomed
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        float time = mc.level.getGameTime() + event.getPartialTick();
        var g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (time < flashUntil) {
            float a = 1.0F - (time - flashStart) / (flashUntil - flashStart);
            g.fill(0, 0, w, h, ((int) (Math.max(0.0F, a) * 230.0F) << 24) | 0xFFFFFF);
        }
        for (Fx f : FX) {
            if (f.entity() == mc.player.getId() && f.kind() == YasFxPacket.DOOM) {
                float k = Math.min(1.0F, (time - f.start()) / f.ticks());
                g.fill(0, 0, w, h, ((int) (k * k * 235.0F) << 24) | 0x05000A);
            }
        }
    }

    /** Whether a player is rising or doomed right now (YasLayer shows the body lit while rising). */
    public static boolean ascending(LivingEntity e) {
        for (Fx f : FX) {
            if (f.entity() == e.getId() && f.kind() == YasFxPacket.ASCEND) {
                return true;
            }
        }
        return false;
    }
}

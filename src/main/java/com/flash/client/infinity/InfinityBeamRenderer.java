package com.flash.client.infinity;

import com.flash.FlashMod;
import com.flash.client.VfxDraw;
import com.flash.infinity.InfPowers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The stones' energy beams as the original's energy_beams files draw them: from the head or the hand (body_part),
 * a glow and a core colour, lightning ones jagged, the rainbow one cycling its colours.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class InfinityBeamRenderer {

    private static final Map<String, JsonObject> BEAMS = new HashMap<>();

    private InfinityBeamRenderer() {
    }

    private static JsonObject beam(String id) {
        return BEAMS.computeIfAbsent(id, k -> {
            String path = k.substring(k.indexOf(':') + 1);
            try (var in = Minecraft.getInstance().getResourceManager().open(new ResourceLocation(FlashMod.MODID,
                    "infinity_beams/" + path + ".json"))) {
                JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonArray();
                return a.get(0).getAsJsonObject();
            } catch (Exception e) {
                return new JsonObject();
            }
        });
    }

    private static int color(JsonObject b, String key, int def) {
        try {
            return Integer.parseInt(b.get(key).getAsString().substring(1), 16);
        } catch (RuntimeException e) {
            return def;
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
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        BufferBuilder buf = null;
        try {
            for (Player p : mc.level.players()) {
                for (String type : new String[] {"palladium:energy_beam", "flash:power_blast"}) {
                    InfPowers.Ability a = beamOf(p, type);
                    if (a == null) {
                        continue;
                    }
                    if (buf == null) {
                        ps.pushPose();
                        ps.translate(-cam.x, -cam.y, -cam.z);
                        buf = VfxDraw.begin();
                    }
                    draw(buf, ps.last().pose(), mc, p, a, pt, camera);
                }
            }
        } finally {
            if (buf != null) {
                VfxDraw.end(buf);
                ps.popPose();
            }
        }
    }

    private static InfPowers.Ability beamOf(Player p, String type) {
        if (p == Minecraft.getInstance().player) {
            for (String pid : InfinityClient.powers()) {
                InfPowers.Power pw = InfPowers.get(pid);
                if (pw == null) {
                    continue;
                }
                for (InfPowers.Ability a : pw.abilities.values()) {
                    if (type.equals(a.type) && InfinityClient.on(a.key())) {
                        return a;
                    }
                }
            }
            return null;
        }
        for (InfPowers.Power pw : InfPowers.all().values()) {
            for (InfPowers.Ability a : pw.abilities.values()) {
                if (type.equals(a.type) && InfinityClient.visibleKey(p, a.key())) {
                    return a;
                }
            }
        }
        return null;
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Minecraft mc, Player p, InfPowers.Ability a, float pt,
                             Camera camera) {
        String id = a.json.has("energy_beam") ? a.json.get("energy_beam").getAsString() : "flash:power_stone_beam";
        boolean maxed = "flash:power_blast".equals(a.type) && p == mc.player
                && InfinityClient.VIEW.score("DamageCycle") >= 11;
        if (maxed && a.json.has("max_energy_beam")) {
            id = a.json.get("max_energy_beam").getAsString();
        }
        JsonObject b = beam(id);
        double max = a.json.has("max_distance") ? a.json.get("max_distance").getAsDouble() : 30.0D;
        Vec3 eye = p.getEyePosition(pt);
        Vec3 look = p.getViewVector(pt);
        boolean blast = "flash:power_blast".equals(a.type);
        net.minecraft.world.entity.LivingEntity locked = blast
                ? com.flash.infinity.InfinityBeams.lock(mc.level, p, eye, look, max) : null;
        if (locked != null) {
            look = locked.getPosition(pt).add(0.0D, locked.getBbHeight() * 0.5D, 0.0D).subtract(eye).normalize();
        }
        HitResult hit = mc.level.clip(new ClipContext(eye, eye.add(look.scale(max)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(max)) : hit.getLocation();
        String part = b.has("body_part") ? b.get("body_part").getAsString() : "right_arm";
        Vec3 from;
        boolean me = p == mc.player && mc.options.getCameraType().isFirstPerson();
        if ("head".equals(part)) {
            from = eye.add(look.scale(0.4D)).add(0.0D, 0.05D, 0.0D);
        } else {
            boolean right = "right_arm".equals(part);
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
            double side = (right ? -1 : 1) * (me ? 0.3D : 0.36D);
            Vec3 sideV = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).scale(side);
            from = me ? camera.getPosition().add(new Vec3(camera.getLookVector()).scale(0.6D))
                    .add(new Vec3(camera.getLeftVector()).scale(right ? -0.3D : 0.3D)).add(0.0D, -0.25D, 0.0D)
                    : eye.add(sideV).add(look.scale(0.6D)).add(0.0D, -0.4D, 0.0D);
        }
        int glow = color(b, "glow_color", 0xBB00FF);
        int core = color(b, "core_color", 0xFFFFFF);
        float t = p.tickCount + pt;
        if (b.has("rainbow") && b.get("rainbow").getAsBoolean()) {
            glow = java.awt.Color.HSBtoRGB((t * 0.02F) % 1.0F, 1.0F, 1.0F) & 0xFFFFFF;
        }
        float w = 0.12F;
        if (b.has("size")) {
            w = 0.06F * b.getAsJsonArray("size").get(0).getAsFloat();
        }
        float gr = ((glow >> 16) & 255) / 255.0F;
        float gg = ((glow >> 8) & 255) / 255.0F;
        float gb = (glow & 255) / 255.0F;
        float cr = ((core >> 16) & 255) / 255.0F;
        float cg = ((core >> 8) & 255) / 255.0F;
        float cb = (core & 255) / 255.0F;
        if (blast) {
            // the Power Blast: a torrent of violet power, a white-hot heart, lightning coiling around it
            float pulse = 0.85F + 0.15F * (float) Math.sin(t * 0.9F);
            VfxDraw.energy(buf, m, from, end, w * 3.2F * pulse, 0.45F, 0.05F, 0.8F, 0.35F);
            VfxDraw.energy(buf, m, from, end, w * 1.6F, 0.75F, 0.25F, 1.0F, 0.9F);
            VfxDraw.energy(buf, m, from, end, w * 0.55F, 1.0F, 0.95F, 1.0F, 1.0F);
            long seed = (long) (t * 3.0F) + p.getId() * 31L;
            VfxDraw.path(buf, m, VfxDraw.jagged(from, end, seed, 0.35D, 5), w * 0.3F, 0.9F, 0.55F, 1.0F, 0.9F);
            VfxDraw.path(buf, m, VfxDraw.jagged(from, end, seed + 13, 0.35D, 5), w * 0.25F, 1.0F, 0.8F, 1.0F, 0.8F);
            Vec3 lv = end.subtract(from).normalize();
            VfxDraw.ring(buf, m, from.add(lv.scale(0.3D)), lv, w * 4.0D, w * 0.8D, t * 0.3F, 0.8F, 0.4F, 1.0F, 0.8F);
            Vec3 l2 = new Vec3(camera.getLeftVector());
            Vec3 u2 = new Vec3(camera.getUpVector());
            VfxDraw.burst(buf, m, end, l2, u2, (float) (w * 9.0D) * pulse, 0.85F, 0.4F, 1.0F, 0.8F, t * 0.2F);
        } else if (b.has("type") && b.get("type").getAsString().endsWith("lightning")) {
            long seed = (long) (t * 2.0F) + p.getId() * 31L;
            VfxDraw.path(buf, m, VfxDraw.jagged(from, end, seed, 0.25D, 4), w, gr, gg, gb, 0.9F);
            VfxDraw.path(buf, m, VfxDraw.jagged(from, end, seed + 7, 0.15D, 4), w * 0.35F, cr, cg, cb, 1.0F);
        } else {
            // the stones' beams (the Soul Stone's): a core of their light, two coils of it winding round
            VfxDraw.energy(buf, m, from, end, w * 1.8F, gr, gg, gb, 0.45F);
            VfxDraw.energy(buf, m, from, end, w, gr, gg, gb, 0.9F);
            VfxDraw.energy(buf, m, from, end, w * 0.4F, cr, cg, cb, 1.0F);
            double turns = from.distanceTo(end) / 2.5D;
            VfxDraw.path(buf, m, VfxDraw.helix(from, end, w * 2.2D, turns, t * 0.5D, 60), w * 0.35F,
                    Math.min(1.0F, gr + 0.3F), Math.min(1.0F, gg + 0.3F), Math.min(1.0F, gb + 0.3F), 0.8F);
            VfxDraw.path(buf, m, VfxDraw.helix(from, end, w * 2.2D, turns, t * 0.5D + Math.PI, 60), w * 0.35F,
                    Math.min(1.0F, gr + 0.3F), Math.min(1.0F, gg + 0.3F), Math.min(1.0F, gb + 0.3F), 0.8F);
            VfxDraw.burst(buf, m, end, new Vec3(camera.getLeftVector()), new Vec3(camera.getUpVector()),
                    (float) (w * 6.0D), gr, gg, gb, 0.7F, t * 0.25F);
        }
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        VfxDraw.glow(buf, m, from, left, up, w * 3.0D, gr, gg, gb, 0.8F);
        if (hit.getType() != HitResult.Type.MISS) {
            VfxDraw.glow(buf, m, end, left, up, w * 5.0D, gr, gg, gb, 0.9F);
        }
        if (p.tickCount % 2 == 0 && hit.getType() != HitResult.Type.MISS) {
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                    0.0D, 0.05D, 0.0D);
        }
    }
}

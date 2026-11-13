package com.flash.client;

import com.flash.FlashMod;
import com.flash.mot.Bolt42;
import com.flash.network.FlashNetwork;
import com.flash.network.StormActionPacket;
import com.flash.network.StormFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Storm on the client: who she is, her keys (Lightning, Winds, Weather, Ice: R and Z), and her bolts, drawn with
 * Man of Tomorrow's Shazam lightning (Bolt42, relocated into the mod), each strike on the ground leaving the pack's
 * burn mark (SupermanScorch, its scorch ported).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class StormClient {

    private static final Set<Integer> STORM = new HashSet<>();
    private static final Set<Integer> FLYING = new HashSet<>();
    private static final Map<Integer, Long> CASTING = new HashMap<>();

    /** A bolt being drawn. */
    private static final class Bolt {
        final Bolt42.Shape shape;
        final long seed;
        final long born;
        final int life;
        final float width;

        Bolt(Bolt42.Shape shape, long seed, long born, int life, float width) {
            this.shape = shape;
            this.seed = seed;
            this.born = born;
            this.life = life;
            this.width = width;
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();

    private StormClient() {
    }

    public static void reset() {
        STORM.clear();
        FLYING.clear();
        CASTING.clear();
        BOLTS.clear();
    }

    public static void set(int entity, boolean storm, boolean flying) {
        if (storm) {
            STORM.add(entity);
        } else {
            STORM.remove(entity);
        }
        if (storm && flying) {
            FLYING.add(entity);
        } else {
            FLYING.remove(entity);
        }
    }

    public static boolean isStorm(Entity e) {
        return STORM.contains(e.getId());
    }

    public static boolean flying(Player p) {
        if (!isStorm(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying : FLYING.contains(p.getId());
    }

    /** Her eyes white and lightning on her: flying, or a moment after calling the storm. */
    public static boolean charged(Entity e) {
        if (!isStorm(e)) {
            return false;
        }
        Long until = CASTING.get(e.getId());
        return (e instanceof Player p && flying(p)) || (until != null && e.level().getGameTime() < until);
    }

    // ------------------------------------------------------------------
    // The keys of her modes
    // ------------------------------------------------------------------

    private static boolean useWas;
    private static int useHeld = -1;
    private static int beamTicks;
    private static final int BEAM_AFTER = 6;
    private static final int BEAM_MAX = 100;

    /** Every tick from YasAbilities with her mode now (null: not her). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            useHeld = -1;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "lightning" -> {
                if (pressed) {
                    useHeld = 0;
                    beamTicks = 0;
                } else if (down && useHeld >= 0) {
                    useHeld++;
                    if (useHeld >= BEAM_AFTER) {
                        if (ScarletClient.coolingDown("storm_beam")) {
                            if (useHeld == BEAM_AFTER) {
                                ScarletClient.denied("storm_beam");
                            }
                        } else if (++beamTicks > BEAM_MAX) {
                            ScarletClient.startLocal("storm_beam", ScarletClient.cd("storm_beam"));
                        } else if (useHeld % 3 == 0) {
                            send(StormActionPacket.BEAM);
                        }
                    }
                } else if (!down && useHeld >= 0) {
                    if (useHeld < BEAM_AFTER) {
                        use("storm_strike", StormActionPacket.STRIKE);
                    } else if (beamTicks > 20 && !ScarletClient.coolingDown("storm_beam")) {
                        ScarletClient.startLocal("storm_beam",
                                ScarletClient.cd("storm_beam") * Math.min(beamTicks, BEAM_MAX) / BEAM_MAX);
                    }
                    useHeld = -1;
                }
                if (variant) {
                    use("storm_chain", StormActionPacket.CHAIN);
                }
            }
            case "wind" -> {
                if (pressed) {
                    use("gust", StormActionPacket.GUST);
                }
                if (variant) {
                    use("tornado", StormActionPacket.TORNADO);
                }
            }
            case "weather" -> {
                if (pressed) {
                    use("weather", StormActionPacket.WEATHER);
                }
                if (variant) {
                    use("fury_storm", StormActionPacket.FURY);
                }
            }
            case "ice" -> {
                if (pressed) {
                    use("hail", StormActionPacket.HAIL);
                }
                if (variant) {
                    use("frost", StormActionPacket.FROST);
                }
            }
            default -> {
            }
        }
    }

    private static void use(String cd, int action) {
        if (ScarletClient.coolingDown(cd)) {
            ScarletClient.denied(cd);
        } else {
            send(action);
        }
    }

    private static void send(int action) {
        FlashNetwork.CHANNEL.sendToServer(new StormActionPacket(action, 0));
    }

    // ------------------------------------------------------------------
    // The bolts
    // ------------------------------------------------------------------

    public static void fx(StormFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        CASTING.put(p.caster(), now + 40L);
        long seed = p.seed();
        switch (p.type()) {
            case StormFxPacket.SKY -> {
                // the Shazam bolt: wide, forked, from the sky to the ground
                BOLTS.add(new Bolt(Bolt42.build(seed, seed * 31L + 7L, p.from(), p.to(), 0.14F * Math.max(0.5F,
                        p.power()), 6, 0.3D, 1, 1.0F), seed, now, 9, 1.0F));
                SupermanScorch.paint(UUID.randomUUID(), p.to().add(0.0D, 0.01D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D),
                        false);
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FLASH, p.to().x, p.to().y + 0.5D,
                        p.to().z, 0.0D, 0.0D, 0.0D);
            }
            case StormFxPacket.ARC -> BOLTS.add(new Bolt(Bolt42.build(seed, seed * 17L + 3L, p.from(), p.to(),
                    0.05F * Math.max(0.5F, p.power()), 3, 0.32D, 1, 0.4F), seed, now, 6, 1.0F));
            case StormFxPacket.BEAM -> {
                BOLTS.add(new Bolt(Bolt42.build(seed, seed * 13L + 5L, p.from(), p.to(), 0.06F, 3, 0.28D, 1, 0.4F),
                        seed, now, 4, 1.0F));
                Entity caster = mc.level.getEntity(p.caster());
                if (p.power() > 0.5F && caster != null) {
                    // the stream drags a burn trail over the ground
                    SupermanScorch.paint(caster.getUUID(), p.to(), new Vec3(0.0D, 1.0D, 0.0D), true);
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        BOLTS.removeIf(b -> now - b.born > b.life);
        CASTING.values().removeIf(t -> t < now);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOLTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        Bolt42.View view = new Bolt42.View();
        view.cx = c.x;
        view.cy = c.y;
        view.cz = c.z;
        var left = cam.getLeftVector();
        var up = cam.getUpVector();
        view.rx = -left.x();
        view.ry = -left.y();
        view.rz = -left.z();
        view.ux = up.x();
        view.uy = up.y();
        view.uz = up.z();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-c.x, -c.y, -c.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(Bolt42.Types.GLOW);
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick();
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            float age = now - b.born + partial;
            float reveal = Math.min(1.0F, age / 1.5F);
            float fade = Math.max(0.0F, 1.0F - age / (b.life + 1.0F));
            // a flicker, as lightning does
            float flicker = (((int) age) % 3 == 1) ? 0.55F : 1.0F;
            Bolt42.draw(vc, pose.last().pose(), view, b.shape, reveal, 1.25F * fade * flicker, b.width, b.seed,
                    (int) age);
        }
        buffers.endBatch(Bolt42.Types.GLOW);
        pose.popPose();
    }
}

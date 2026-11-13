package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.ScarletActionPacket;
import com.flash.network.ScarletFxPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/**
 * The Scarlet Witch on the client: who she is (ScarletLayer draws her red aura while she flies or casts), the keys of
 * her own modes (Mind, Energy, Necromancy: R and Z, sent to ScarletAbilities) and the effects of her magic.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ScarletClient {

    private static final Set<Integer> SCARLET = new HashSet<>();
    private static final Set<Integer> FLYING = new HashSet<>();
    private static final Set<Integer> NO_AURA = new HashSet<>();
    /** Her cooldowns: id -> game time it ends, and its length. */
    private static final Map<String, long[]> COOLDOWNS = new HashMap<>();
    /** Entity id -> game time until which her aura burns (she cast something). */
    private static final Map<Integer, Long> CASTING = new HashMap<>();
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.92F, 0.06F, 0.16F), 1.4F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.5F, 0.0F, 0.1F), 2.0F);
    private static final Random RND = new Random();

    private ScarletClient() {
    }

    public static void reset() {
        SCARLET.clear();
        FLYING.clear();
        NO_AURA.clear();
        COOLDOWNS.clear();
        CASTING.clear();
    }

    public static void set(int entity, boolean scarlet, boolean flying, boolean aura) {
        if (aura) {
            NO_AURA.remove(entity);
        } else {
            NO_AURA.add(entity);
        }
        if (scarlet) {
            SCARLET.add(entity);
        } else {
            SCARLET.remove(entity);
        }
        if (scarlet && flying) {
            FLYING.add(entity);
        } else {
            FLYING.remove(entity);
        }
    }

    public static boolean isScarlet(Entity e) {
        return SCARLET.contains(e.getId());
    }

    public static boolean flying(Player p) {
        if (!isScarlet(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying : FLYING.contains(p.getId());
    }

    /** The red aura shows: flying, or a moment after casting. */
    public static boolean aura(Player p) {
        if (!isScarlet(p) || NO_AURA.contains(p.getId())) {
            return false;
        }
        Long until = CASTING.get(p.getId());
        return flying(p) || (until != null && p.level().getGameTime() < until);
    }

    /** The local player's own setting of the flight aura. */
    public static boolean localAura() {
        Player p = Minecraft.getInstance().player;
        return p == null || !NO_AURA.contains(p.getId());
    }

    // ------------------------------------------------------------------
    // Her cooldowns
    // ------------------------------------------------------------------

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static int cd(String id) {
        return com.flash.scarlet.ScarletCooldowns.ticks(id);
    }

    public static void startLocal(String id, int ticks) {
        if (ticks <= 0) {
            COOLDOWNS.remove(id);
        } else {
            COOLDOWNS.put(id, new long[] {now() + ticks, ticks});
        }
    }

    /** Ticks left of a cooldown (0: ready). */
    public static int remaining(String id) {
        long[] c = COOLDOWNS.get(id);
        return c == null ? 0 : (int) Math.max(0L, c[0] - now());
    }

    public static int length(String id) {
        long[] c = COOLDOWNS.get(id);
        return c == null ? cd(id) : (int) c[1];
    }

    public static boolean coolingDown(String id) {
        return remaining(id) > 0;
    }

    private static long deniedAt;

    /** Pressed while cooling down: a short note (not every tick). */
    public static void denied(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && now() - deniedAt > 10) {
            deniedAt = now();
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.flash.scarlet.wait",
                    net.minecraft.network.chat.Component.translatable("hud.flash.scarlet.row." + id),
                    String.format(java.util.Locale.ROOT, "%.1f", remaining(id) / 20.0F)), true);
        }
    }

    // ------------------------------------------------------------------
    // Her own modes: the keys
    // ------------------------------------------------------------------

    private static boolean useWas;
    private static int useHeld = -1;
    private static int blastHeld = -1;
    private static final int BEAM_AFTER = 6;
    private static final int BLAST_FULL = 40;
    /** The beam overheats after 4 s. */
    private static final int BEAM_MAX = 80;
    private static int beamTicks;

    /** 0..1 of the chaos blast being charged (Z held in Energy), or -1. */
    public static float blastCharge() {
        return blastHeld < 0 ? -1.0F : Math.min(1.0F, blastHeld / (float) BLAST_FULL);
    }

    /** Called every tick by YasAbilities with her mode now ({@code null}: one of Hand of God's, or not her). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            useHeld = -1;
            blastHeld = -1;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "mind" -> {
                if (pressed || variant) {
                    Entity t = aimedEntity(mc, false);
                    String id = pressed ? (t instanceof Player ? "read" : t != null ? "ally" : "wave")
                            : (t instanceof Player ? "puppet" : t != null ? "fury" : "hysteria");
                    if (coolingDown(id)) {
                        denied(id);
                    } else {
                        send(pressed ? ScarletActionPacket.MIND : ScarletActionPacket.MIND_VARIANT,
                                t == null ? -1 : t.getId(), 0);
                    }
                }
            }
            case "energy" -> {
                if (pressed) {
                    useHeld = 0;
                    beamTicks = 0;
                } else if (down && useHeld >= 0) {
                    useHeld++;
                    if (useHeld >= BEAM_AFTER) {
                        if (coolingDown("beam")) {
                            if (useHeld == BEAM_AFTER) {
                                denied("beam");
                            }
                        } else if (++beamTicks > BEAM_MAX) {
                            // overheated: the beam stops and cools down
                            startLocal("beam", cd("beam"));
                        } else if (useHeld % 3 == 0) {
                            send(ScarletActionPacket.BEAM, -1, 0);
                        }
                    }
                } else if (!down && useHeld >= 0) {
                    if (useHeld < BEAM_AFTER) {
                        if (coolingDown("bolt")) {
                            denied("bolt");
                        } else {
                            send(ScarletActionPacket.BOLT, -1, 0);
                        }
                    } else if (beamTicks > 20 && !coolingDown("beam")) {
                        startLocal("beam", cd("beam") * Math.min(beamTicks, BEAM_MAX) / BEAM_MAX);
                    }
                    useHeld = -1;
                }
                boolean z = FlashKeys.YAS_VARIANT.isDown() && mc.screen == null;
                if (z && blastHeld < 0 && coolingDown("blast")) {
                    if (variant) {
                        denied("blast");
                    }
                } else if (z) {
                    blastHeld = blastHeld < 0 ? 0 : blastHeld + 1;
                    if (blastHeld % 4 == 0) {
                        Player p = mc.player;
                        mc.level.addParticle(RED, p.getX() + (RND.nextDouble() - 0.5D), p.getY() + 1.2D,
                                p.getZ() + (RND.nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
                    }
                } else if (blastHeld >= 0) {
                    send(ScarletActionPacket.BLAST, -1, Math.round(blastCharge() * 100.0F));
                    blastHeld = -1;
                }
            }
            case "necro" -> {
                if (pressed) {
                    if (coolingDown("heal")) {
                        denied("heal");
                    } else {
                        send(ScarletActionPacket.HEAL, aimed(mc, true), 0);
                    }
                }
                if (variant) {
                    if (coolingDown("raise")) {
                        denied("raise");
                    } else {
                        send(ScarletActionPacket.RAISE, -1, 0);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void send(int action, int entity, int value) {
        FlashNetwork.CHANNEL.sendToServer(new ScarletActionPacket(action, entity, value));
    }

    private static int aimed(Minecraft mc, boolean anyLiving) {
        Entity e = aimedEntity(mc, anyLiving);
        return e == null ? -1 : e.getId();
    }

    /** The living thing under the crosshair (up to 64 blocks), or null. */
    private static Entity aimedEntity(Minecraft mc, boolean anyLiving) {
        Player p = mc.player;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(64.0D));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                p));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                e -> e instanceof LivingEntity && e.isAlive() && e != p
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand), eye.distanceToSqr(end));
        return hit == null ? null : hit.getEntity();
    }

    // ------------------------------------------------------------------
    // The effects
    // ------------------------------------------------------------------

    private static ParticleOptions energy() {
        var t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("hand_of_god", "telecinese"));
        return t instanceof ParticleOptions o ? o : RED;
    }

    public static void fx(ScarletFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        CASTING.put(p.caster(), level.getGameTime() + 30L);
        Vec3 a = p.from();
        Vec3 b = p.to();
        switch (p.type()) {
            case ScarletFxPacket.BOLT, ScarletFxPacket.BEAM, ScarletFxPacket.LINK, ScarletFxPacket.BLAST -> {
                line(level, a, b, p.type());
                if (p.type() == ScarletFxPacket.BLAST) {
                    sphere(level, b, p.power());
                } else if (p.type() == ScarletFxPacket.BOLT) {
                    sphere(level, b, 0.8F);
                }
            }
            case ScarletFxPacket.WAVE -> wave(level, a, p.power());
            case ScarletFxPacket.HEAL -> {
                for (int i = 0; i < 50; i++) {
                    double ang = i * 0.5D;
                    double h = i / 50.0D * Math.max(1.0D, p.power());
                    level.addParticle(RED, b.x + Math.cos(ang) * 0.7D, b.y + h, b.z + Math.sin(ang) * 0.7D, 0.0D,
                            0.02D, 0.0D);
                    level.addParticle(ParticleTypes.HEART, b.x + (RND.nextDouble() - 0.5D), b.y + h,
                            b.z + (RND.nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
                }
                line(level, a, b.add(0.0D, 1.0D, 0.0D), ScarletFxPacket.LINK);
            }
            case ScarletFxPacket.RAISE -> {
                for (int i = 0; i < 80; i++) {
                    double ang = RND.nextDouble() * Math.PI * 2.0D;
                    double r = RND.nextDouble() * 1.2D;
                    level.addParticle(i % 2 == 0 ? DARK : ParticleTypes.SOUL, b.x + Math.cos(ang) * r,
                            b.y + RND.nextDouble() * 0.3D, b.z + Math.sin(ang) * r, 0.0D, 0.08D + RND.nextDouble() * 0.15D,
                            0.0D);
                    level.addParticle(energy(), b.x, b.y + RND.nextDouble() * Math.max(1.0D, p.power()), b.z,
                            (RND.nextDouble() - 0.5D) * 0.2D, 0.05D, (RND.nextDouble() - 0.5D) * 0.2D);
                }
            }
            default -> {
            }
        }
    }

    private static void line(ClientLevel level, Vec3 a, Vec3 b, int type) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = (int) Math.min(240, len * (type == ScarletFxPacket.BEAM ? 3.0D : 4.0D));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0.0D : (double) i / n;
            // a little hex twist around the line
            double wob = type == ScarletFxPacket.LINK ? 0.0D : 0.15D;
            Vec3 at = a.add(d.scale(t)).add((RND.nextDouble() - 0.5D) * wob, (RND.nextDouble() - 0.5D) * wob,
                    (RND.nextDouble() - 0.5D) * wob);
            level.addParticle(type == ScarletFxPacket.LINK ? DARK : RED, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
            if (i % 3 == 0 && type != ScarletFxPacket.LINK) {
                level.addParticle(energy(), at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void sphere(ClientLevel level, Vec3 c, float radius) {
        int n = (int) (40 + radius * 30);
        for (int i = 0; i < n; i++) {
            double u = RND.nextDouble() * 2.0D - 1.0D;
            double ang = RND.nextDouble() * Math.PI * 2.0D;
            double s = Math.sqrt(1.0D - u * u);
            Vec3 dir = new Vec3(s * Math.cos(ang), u, s * Math.sin(ang));
            double v = 0.1D + radius * 0.08D;
            level.addParticle(i % 3 == 0 ? energy() : RED, c.x, c.y, c.z, dir.x * v, dir.y * v, dir.z * v);
        }
        level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0.0D, 0.0D, 0.0D);
    }

    private static void wave(ClientLevel level, Vec3 c, float radius) {
        for (int ring = 1; ring <= 4; ring++) {
            double r = radius * ring / 4.0D;
            int n = (int) (r * 10);
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2.0D / n;
                level.addParticle(ring % 2 == 0 ? RED : energy(), c.x + Math.cos(ang) * r * 0.25D, c.y + 0.3D,
                        c.z + Math.sin(ang) * r * 0.25D, Math.cos(ang) * r * 0.06D, 0.01D, Math.sin(ang) * r * 0.06D);
            }
        }
        level.addParticle(ParticleTypes.FLASH, c.x, c.y + 1.0D, c.z, 0.0D, 0.0D, 0.0D);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        CASTING.values().removeIf(t -> t < now);
        // the red mist around her hands while she flies or casts
        for (Player p : mc.level.players()) {
            if (aura(p) && now % 2 == 0 && !p.isInvisible()) {
                double yaw = Math.toRadians(p.yBodyRot);
                for (int side = -1; side <= 1; side += 2) {
                    double hx = p.getX() - Math.cos(yaw) * 0.38D * side;
                    double hz = p.getZ() - Math.sin(yaw) * 0.38D * side;
                    mc.level.addParticle(RED, hx, p.getY() + (flying(p) ? 1.3D : 0.8D), hz, 0.0D, 0.01D, 0.0D);
                }
            }
        }
    }
}

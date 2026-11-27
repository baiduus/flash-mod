package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroActionPacket;
import com.flash.network.HeroFxPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.network.SpiderMovePacket;
import com.flash.network.SpiderMoveSyncPacket;
import com.flash.spider.SpiderAbilities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Spider-Man on the client: who he is and his suit, the keys of his modes (R / Z), his locomotion and what is drawn
 * of him. The locomotion is Powerborne Heroes' Spider-Man (its scripts, used with permission) ported to the mod,
 * moved here as every player's own motion is:
 * <ul>
 * <li><b>Web swing</b> (the flight key held): a web shot ahead (5.5 blocks a tick) fastens where you look (up to 80
 * blocks; a little higher if nothing is there) and you swing on a rope of that length: W / A / D pump the swing
 * (more sprinting), space reels the line in, sneak while slow under it hangs upside down; let go and you are flung on
 * (a release flip, then the dive). Too far past the rope's length it snaps.</li>
 * <li><b>Wall crawling</b>: jumping (or walking) into a wall you stick to it: W up, S down, A / D sideways, round the
 * corners; looking away from the wall, space is a wall jump; looking up near a ceiling you crawl on it.</li>
 * <li><b>Double jump</b> with a flip.</li>
 * <li>The web lines (a thin white strand that falls when let go), the web splats where they stick (the cobweb), the
 * cocoons of the webbed, the spider-sense over his head and its direction indicator, the swing's wind and the
 * camera roll.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpiderClient {

    private static final ResourceLocation COCOON = new ResourceLocation(FlashMod.MODID,
            "textures/spider/web_cocoon.png");
    private static final ResourceLocation SENSE = new ResourceLocation(FlashMod.MODID,
            "textures/spider/spider_sense.png");
    private static final ResourceLocation INDICATOR = new ResourceLocation(FlashMod.MODID,
            "textures/spider/directional_indicator.png");
    private static final ResourceLocation COBWEB = new ResourceLocation("minecraft", "textures/block/cobweb.png");
    private static final float WEB_WIDTH = 0.036F;
    private static final float WEB_SHADE = 0.9F;

    /** Spider-Men around: entity -> suit (and SpiderPowers.UNSUITED_BIT). */
    private static final Map<Integer, Integer> SPIDERS = new HashMap<>();

    /** What the others are doing (from SpiderMoveSyncPacket). */
    private static final class Remote {
        Vec3 anchor;
        long lineStart;
        double lineDist;
        int arm;
        Vec3 wall;
        boolean ceiling;
        boolean hanging;
    }

    private static final Map<Integer, Remote> REMOTE = new HashMap<>();
    /** The webbed: entity -> game time it breaks free. */
    private static final Map<Integer, Long> WEBBED = new HashMap<>();
    /** The spider-sense showing: entity -> [game time it started, strength]. */
    private static final Map<Integer, long[]> SENSES = new HashMap<>();

    /** A web strand shot (a moment on screen). */
    private record Strand(int caster, Vec3 from, Vec3 to, int target, long born, int life, boolean fromHand) {
    }

    /** A line let go: it falls and fades (Powerborne's web fades). */
    private static final class Fade {
        final Vec3 s;
        final Vec3 e;
        int age;

        Fade(Vec3 s, Vec3 e) {
            this.s = s;
            this.e = e;
        }
    }

    /** A web stuck on a block face (the cobweb). */
    private static final class Splat {
        final Vec3 at;
        final Direction face;
        final long born;
        final float spin;

        Splat(Vec3 at, Direction face, long born) {
            this.at = at;
            this.face = face;
            this.born = born;
            this.spin = (float) (Math.random() * Math.PI * 2.0D);
        }
    }

    private static final List<Strand> STRANDS = new ArrayList<>();
    private static final List<Fade> FADES = new ArrayList<>();
    private static final List<Splat> SPLATS = new ArrayList<>();

    private SpiderClient() {
    }

    public static void reset() {
        SPIDERS.clear();
        REMOTE.clear();
        WEBBED.clear();
        SENSES.clear();
        STRANDS.clear();
        FADES.clear();
        SPLATS.clear();
        anchor = null;
        crawl = Crawl.NONE;
        SpiderAnim.reset();
    }

    public static void set(int entity, boolean has, int data) {
        if (has) {
            SPIDERS.put(entity, data);
        } else {
            SPIDERS.remove(entity);
            REMOTE.remove(entity);
        }
    }

    public static boolean isSpider(Entity e) {
        return SPIDERS.containsKey(e.getId());
    }

    public static int suit(Entity e) {
        return SPIDERS.getOrDefault(e.getId(), 0) & 0xFF;
    }

    /** His suit on (the suit key takes it off; his powers stay). */
    public static boolean suited(Entity e) {
        Integer d = SPIDERS.get(e.getId());
        return d != null && (d & com.flash.spider.SpiderPowers.UNSUITED_BIT) == 0;
    }

    /** The local player on a web line now. */
    public static boolean swinging() {
        return anchor != null;
    }

    private static Remote remote(Player p) {
        return REMOTE.get(p.getId());
    }

    private static boolean local(Player p) {
        return p == Minecraft.getInstance().player;
    }

    /** Where this player's swing line is fastened (null: none). */
    public static Vec3 line(Player p) {
        if (local(p)) {
            return anchor;
        }
        Remote r = remote(p);
        return r == null ? null : r.anchor;
    }

    /** The wall he crawls on (the unit vector into it), or null. */
    public static Vec3 wallNormal(Player p) {
        if (local(p)) {
            return crawl == Crawl.WALL ? new Vec3(wallX, 0.0D, wallZ) : null;
        }
        Remote r = remote(p);
        return r == null ? null : r.wall;
    }

    public static boolean onCeiling(Player p) {
        if (local(p)) {
            return crawl == Crawl.CEILING;
        }
        Remote r = remote(p);
        return r != null && r.ceiling;
    }

    public static boolean hanging(Player p) {
        if (local(p)) {
            return hanging;
        }
        Remote r = remote(p);
        return r != null && r.hanging;
    }

    public static boolean reeling(Player p) {
        return local(p) && anchor != null && attached && p instanceof LocalPlayer lp && lp.input.jumping && !hanging;
    }

    /** Which arm holds the line: 0 right, 1 left, 2 both. */
    public static int armVariant(Player p) {
        if (local(p)) {
            return armVariant;
        }
        Remote r = remote(p);
        return r == null ? 0 : r.arm;
    }

    // ------------------------------------------------------------------
    // The keys of his modes
    // ------------------------------------------------------------------

    private static boolean useWas;

    /** Every tick from YasAbilities with his mode now (null: not him). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "webs" -> {
                if (pressed) {
                    use("web_shot", SpiderAbilities.WEB_SHOT);
                }
                if (variant) {
                    use("web_net", SpiderAbilities.WEB_NET);
                }
            }
            case "pull" -> {
                if (pressed) {
                    use("yank", SpiderAbilities.YANK);
                }
                if (variant) {
                    use("zip", SpiderAbilities.ZIP);
                }
            }
            case "combat" -> {
                if (pressed) {
                    use("spider_kick", SpiderAbilities.KICK);
                }
                if (variant) {
                    use("sense", SpiderAbilities.SENSE);
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
            send(action, 0);
        }
    }

    public static void send(int action, int value) {
        FlashNetwork.CHANNEL.sendToServer(new HeroActionPacket(HeroSyncPacket.SPIDER, action, value));
    }

    /** The suit picked in the K menu (shown at once, kept by the server). */
    public static void pickSuit(int index) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && isSpider(p)) {
            SPIDERS.put(p.getId(), index | (SPIDERS.get(p.getId()) & com.flash.spider.SpiderPowers.UNSUITED_BIT));
            send(SpiderAbilities.SUIT, index);
        }
    }

    private static void moved(int kind, int variant, Vec3 at, double nx, double nz) {
        FlashNetwork.CHANNEL.sendToServer(new SpiderMovePacket(kind, variant, at, (float) nx, (float) nz));
    }

    /** Another Spider-Man's locomotion changed. */
    public static void remoteMove(SpiderMoveSyncPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(m.entity()) instanceof Player p)) {
            return;
        }
        Remote r = REMOTE.computeIfAbsent(m.entity(), k -> new Remote());
        long now = mc.level.getGameTime();
        switch (m.kind()) {
            case SpiderMovePacket.LINE_ON -> {
                r.anchor = m.at();
                r.lineStart = now;
                r.lineDist = p.getEyePosition().distanceTo(m.at());
                r.arm = m.variant() & 3;
                r.hanging = false;
                SPLATS.add(new Splat(m.at(), Direction.from3DDataValue((m.variant() >> 2) & 7), now
                        + (long) Math.ceil(r.lineDist / 5.5D)));
            }
            case SpiderMovePacket.LINE_OFF, SpiderMovePacket.LINE_BREAK -> {
                if (r.anchor != null) {
                    FADES.add(new Fade(wrist(mc, p, 1.0F, r.arm), r.anchor));
                }
                r.anchor = null;
                r.hanging = false;
            }
            case SpiderMovePacket.RELEASE -> {
                if (r.anchor != null) {
                    FADES.add(new Fade(wrist(mc, p, 1.0F, r.arm), r.anchor));
                }
                r.anchor = null;
                r.hanging = false;
                SpiderAnim.event(p, SpiderAnim.EV_RELEASE, m.variant());
            }
            case SpiderMovePacket.WALL -> {
                r.wall = new Vec3(m.nx(), 0.0D, m.nz());
                r.ceiling = false;
            }
            case SpiderMovePacket.CEILING -> {
                r.ceiling = true;
                r.wall = null;
            }
            case SpiderMovePacket.CRAWL_OFF -> {
                r.ceiling = false;
                r.wall = null;
            }
            case SpiderMovePacket.WALL_JUMP -> {
                r.ceiling = false;
                r.wall = null;
                SpiderAnim.event(p, SpiderAnim.EV_WALL_JUMP, 0);
            }
            case SpiderMovePacket.DOUBLE_JUMP -> SpiderAnim.event(p, SpiderAnim.EV_DOUBLE_JUMP, m.variant());
            case SpiderMovePacket.HANG_ON -> r.hanging = true;
            case SpiderMovePacket.HANG_OFF -> r.hanging = false;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // The web swing (Powerborne's web_swing.js, its numbers)
    // ------------------------------------------------------------------

    private static final double MAX_ATTACH = 80.0D;
    private static final double MIN_ATTACH = 1.5D;
    private static final double WEB_SPEED = 5.5D;
    private static final double SOFT_CAP = 3.0D;
    private static final double HARD_CAP = 4.5D;
    private static final double ROPE_MIN = 1.0D;
    private static final double REEL_SPEED = 0.38D;
    private static final double AUTO_REEL = 0.06D;
    private static final double PUMP = 0.06D;
    private static final double STRAFE_PUMP = 0.03D;
    private static final double SPRINT_PUMP = 1.5D;
    private static final double DRAG_COMP = 0.05D;
    private static final double TANGENTIAL_PUMP = 0.004D;
    private static final double CONSTRAINT = 0.15D;
    private static final double RELEASE_FORWARD = 0.5D;
    private static final double MAX_EXIST = 128.0D;

    private static Vec3 anchor;
    private static Direction anchorFace = Direction.UP;
    private static BlockPos anchorBlock;
    private static boolean attached;
    private static int shootTicks;
    private static double rope;
    private static int airTicks;
    private static int armVariant;
    private static boolean hanging;
    private static boolean swingWas;
    private static boolean jumpWas;
    private static boolean doubleUsed;
    private static int sinceGround;

    private static KeyMapping swingKey() {
        return FlashAbilityKeys.mapping(com.flash.ability.FlashAbility.WIND_HANDS);
    }

    private static void locomotion(Minecraft mc, LocalPlayer p) {
        boolean able = isSpider(p) && !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && p.isAlive()
                && !p.getAbilities().flying;
        if (p.getAbilities().flying && isSpider(p) && !p.isCreative() && !p.isSpectator()) {
            // he does not fly: the "may fly" is only for the long swings
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        if (p.onGround()) {
            doubleUsed = false;
            sinceGround = 0;
        } else {
            sinceGround++;
        }
        KeyMapping key = swingKey();
        boolean down = able && key != null && key.isDown() && mc.screen == null;
        if (down && !swingWas && anchor == null) {
            if (crawl != Crawl.NONE) {
                stopCrawl(p, false);
            }
            shoot(p);
        } else if (!down && anchor != null) {
            release(p, attached);
        }
        swingWas = down;
        boolean jump = p.input.jumping;
        boolean jumpPressed = jump && !jumpWas;
        if (!able) {
            if (anchor != null) {
                release(p, false);
            }
            if (crawl != Crawl.NONE) {
                stopCrawl(p, false);
            }
            jumpWas = jump;
            return;
        }
        if (anchor != null) {
            swing(mc, p);
        } else {
            crawlTick(p, jumpPressed);
            if (crawl == Crawl.NONE && jumpPressed && !p.onGround() && !doubleUsed && sinceGround > 3
                    && !p.isInWater() && !p.onClimbable()) {
                doubleJump(p);
            }
        }
        jumpWas = jump;
    }

    /** Where the line goes: where he looks; if nothing is there, a little higher. */
    private static BlockHitResult aimAnchor(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        float[] raise = {0.0F, 15.0F, 30.0F, 45.0F};
        for (float r : raise) {
            Vec3 dir = Vec3.directionFromRotation(Math.max(-85.0F, pitch - r), yaw);
            HitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(MAX_ATTACH)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                double d = eye.distanceTo(hit.getLocation());
                if (d >= MIN_ATTACH && (r == 0.0F || hit.getLocation().y > p.getY() + 2.0D)) {
                    return bh;
                }
            }
        }
        return null;
    }

    private static void shoot(LocalPlayer p) {
        BlockHitResult hit = aimAnchor(p);
        if (hit == null) {
            p.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.4F, 0.6F);
            return;
        }
        anchor = hit.getLocation();
        anchorFace = hit.getDirection();
        anchorBlock = hit.getBlockPos();
        attached = false;
        shootTicks = Math.max(1, (int) Math.ceil(p.getEyePosition().distanceTo(anchor) / WEB_SPEED));
        double roll = Math.random();
        armVariant = roll < 0.333D ? 1 : roll < 0.666D ? 0 : 2;
        hanging = false;
        airTicks = 0;
        moved(SpiderMovePacket.LINE_ON, armVariant | (anchorFace.get3DDataValue() << 2), anchor, 0.0D, 0.0D);
        SPLATS.add(new Splat(anchor, anchorFace, p.level().getGameTime() + shootTicks));
    }

    /** Let go: with momentum (speed over 0.2) flung on with a release flip and the dive; else just dropped. */
    private static void release(LocalPlayer p, boolean launch) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = anchor;
        Vec3 from = wrist(mc, p, 1.0F, armVariant);
        anchor = null;
        boolean wasHanging = hanging;
        hanging = false;
        if (wasHanging) {
            moved(SpiderMovePacket.HANG_OFF, 0, Vec3.ZERO, 0.0D, 0.0D);
        }
        if (attached && at != null) {
            FADES.add(new Fade(from, at));
        }
        Vec3 v = p.getDeltaMovement();
        if (launch && attached && v.length() > 0.2D && !p.onGround()) {
            Vec3 look = p.getLookAngle();
            double lh = Math.sqrt(look.x * look.x + look.z * look.z);
            double vx = v.x;
            double vy = v.y;
            double vz = v.z;
            if (lh > 1.0E-6D) {
                vx += look.x / lh * RELEASE_FORWARD;
                vz += look.z / lh * RELEASE_FORWARD;
            }
            vy += Mth.clamp((at.y - p.getY()) * 0.1D, -1.0D, 1.0D);
            Vec3 nv = new Vec3(vx, vy, vz);
            if (nv.length() > HARD_CAP) {
                nv = nv.normalize().scale(HARD_CAP);
            }
            p.setDeltaMovement(nv);
            p.fallDistance = 0.0F;
            int variant = p.getRandom().nextInt(8);
            SpiderAnim.event(p, SpiderAnim.EV_RELEASE, variant);
            moved(SpiderMovePacket.RELEASE, variant, Vec3.ZERO, 0.0D, 0.0D);
            releasing = 10;
        } else {
            moved(SpiderMovePacket.LINE_OFF, 0, Vec3.ZERO, 0.0D, 0.0D);
        }
        attached = false;
    }

    private static int releasing;

    private static void swing(Minecraft mc, LocalPlayer p) {
        if (!attached) {
            if (--shootTicks > 0) {
                return;
            }
            attached = true;
            rope = Math.max(MIN_ATTACH, p.position().add(0.0D, 1.0D, 0.0D).distanceTo(anchor));
            if (p.onGround()) {
                p.setDeltaMovement(p.getDeltaMovement().add(0.0D, 0.45D, 0.0D));
            }
            for (int i = 0; i < 6; i++) {
                p.level().addParticle(ParticleTypes.WHITE_ASH, anchor.x, anchor.y, anchor.z, 0.0D, 0.0D, 0.0D);
            }
        }
        Level level = p.level();
        double sx = p.getX();
        double sy = p.getY() + 1.0D;
        double sz = p.getZ();
        if (anchorBlock != null && level.getBlockState(anchorBlock).isAir()
                || anchor.distanceToSqr(sx, sy, sz) > MAX_EXIST * MAX_EXIST) {
            release(p, false);
            return;
        }
        Vec3 dm = p.getDeltaMovement();
        double vx = dm.x;
        double vy = dm.y;
        double vz = dm.z;
        boolean onGround = p.onGround();
        airTicks = onGround ? 0 : airTicks + 1;
        // hanging: sneaking, slow, straight under the line
        double ax = anchor.x;
        double ay = anchor.y;
        double az = anchor.z;
        boolean hang = false;
        if (!onGround && p.input.shiftKeyDown && !p.isSprinting()) {
            double dx = ax - sx;
            double dy = ay - sy;
            double dz = az - sz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1.0E-4D && dy > 0.0D) {
                double align = dy / len;
                double speed = dm.length();
                hang = hanging ? align >= 0.82D && speed <= 0.85D : align >= 0.99D && speed <= 0.5D;
            }
        }
        if (hang != hanging) {
            hanging = hang;
            moved(hang ? SpiderMovePacket.HANG_ON : SpiderMovePacket.HANG_OFF, 0, Vec3.ZERO, 0.0D, 0.0D);
        }
        // reeling in (space; sprinting forward, a little)
        double reelRatio = 1.0D;
        boolean fwd = p.input.up;
        boolean back = p.input.down;
        boolean left = p.input.left;
        boolean right = p.input.right;
        if ((onGround || airTicks > 4) && rope > ROPE_MIN) {
            double reel = 0.0D;
            if (p.input.jumping && !hanging) {
                reel += REEL_SPEED;
            }
            if (fwd && p.isSprinting() && !hanging) {
                reel += AUTO_REEL;
            }
            if (reel > 0.0D) {
                double nl = Math.max(ROPE_MIN, rope - reel);
                reelRatio = Math.min(1.03D, rope / nl);
                rope = nl;
            }
        }
        // pumping the swing
        int dirMult = fwd ? 1 : back ? -1 : 0;
        int strafeMult = right ? 1 : left ? -1 : 0;
        Vec3 look = p.getLookAngle();
        double lh = Math.sqrt(look.x * look.x + look.z * look.z);
        if (lh > 1.0E-6D && (dirMult != 0 || strafeMult != 0)) {
            double fx = look.x / lh;
            double fz = look.z / lh;
            double mx = fx * dirMult + -fz * strafeMult;
            double mz = fz * dirMult + fx * strafeMult;
            double ml = Math.sqrt(mx * mx + mz * mz);
            if (ml > 1.0E-6D) {
                double strength = (dirMult != 0 ? PUMP : STRAFE_PUMP) * (p.isSprinting() ? SPRINT_PUMP : 1.0D);
                if (hanging) {
                    strength *= 0.06D;
                }
                vx += mx / ml * strength;
                vz += mz / ml * strength;
            }
        }
        if (dirMult != 0) {
            double d = Math.sqrt((sx - ax) * (sx - ax) + (sy - ay) * (sy - ay) + (sz - az) * (sz - az));
            double vn = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (d > 1.0E-6D && vn > 1.0E-6D) {
                double ux = (sx - ax) / d;
                double uy = (sy - ay) / d;
                double uz = (sz - az) / d;
                double vr = vx * ux + vy * uy + vz * uz;
                double tx = vx - vr * ux;
                double ty = vy - vr * uy;
                double tz = vz - vr * uz;
                double tn = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tn > 1.0E-6D) {
                    double scale = TANGENTIAL_PUMP / tn * (hanging ? 0.06D : 1.0D);
                    vx += tx * scale;
                    vy += ty * scale;
                    vz += tz * scale;
                }
            }
        }
        vx *= 1.0D + DRAG_COMP;
        vz *= 1.0D + DRAG_COMP;
        vy *= 1.015D;
        // the rope
        double px = sx + vx;
        double py = sy + vy;
        double pz = sz + vz;
        double rx = px - ax;
        double ry = py - ay;
        double rz = pz - az;
        double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (d > rope && d > 1.0E-6D) {
            double ux = rx / d;
            double uy = ry / d;
            double uz = rz / d;
            double vr = vx * ux + vy * uy + vz * uz;
            if (vr > 0.0D) {
                vx -= vr * ux;
                vy -= vr * uy;
                vz -= vr * uz;
            }
            double excess = d - rope;
            if (excess > 10.0D) {
                moved(SpiderMovePacket.LINE_BREAK, 0, Vec3.ZERO, 0.0D, 0.0D);
                release(p, false);
                return;
            }
            vx -= ux * excess * CONSTRAINT;
            vy -= uy * excess * CONSTRAINT;
            vz -= uz * excess * CONSTRAINT;
        }
        if (reelRatio > 1.0D) {
            vx *= reelRatio;
            vy *= reelRatio;
            vz *= reelRatio;
        }
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > SOFT_CAP) {
            double drag = 1.0D - Math.min(0.1D * (speed - SOFT_CAP), 0.12D);
            vx *= drag;
            vy *= drag;
            vz *= drag;
        }
        speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > HARD_CAP) {
            double s = HARD_CAP / speed;
            vx *= s;
            vy *= s;
            vz *= s;
        }
        p.setDeltaMovement(vx, vy, vz);
        p.fallDistance = 0.0F;
    }

    private static void doubleJump(LocalPlayer p) {
        doubleUsed = true;
        Vec3 v = p.getDeltaMovement();
        Vec3 look = p.getLookAngle();
        double lh = Math.sqrt(look.x * look.x + look.z * look.z);
        double fx = lh > 1.0E-6D ? look.x / lh * 0.25D : 0.0D;
        double fz = lh > 1.0E-6D ? look.z / lh * 0.25D : 0.0D;
        p.setDeltaMovement(v.x + fx, Math.max(v.y, 0.72D), v.z + fz);
        p.fallDistance = 0.0F;
        int[] flips = {0, 1, 3, 5, 7};
        int variant = flips[p.getRandom().nextInt(flips.length)];
        SpiderAnim.event(p, SpiderAnim.EV_DOUBLE_JUMP, variant);
        moved(SpiderMovePacket.DOUBLE_JUMP, variant, Vec3.ZERO, 0.0D, 0.0D);
    }

    // ------------------------------------------------------------------
    // Wall and ceiling crawling (Powerborne's wall_crawling.js, its numbers)
    // ------------------------------------------------------------------

    private enum Crawl { NONE, WALL, CEILING }

    private static final double CLIMB_SPEED = 0.3D;
    private static final double STRAFE_SPEED = 0.25D;
    private static final double CEILING_SPEED = 0.25D;
    private static final double JUMP_LAUNCH = 1.3D;
    private static final double JUMP_UP = 1.05D;

    private static Crawl crawl = Crawl.NONE;
    /** Into the wall (a cardinal direction). */
    private static int wallX;
    private static int wallZ;
    private static int ceilingY;
    private static int suppress;

    private static final int[][] CARDINALS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** A wall in that direction, beside his body (at his waist or his chest). */
    private static boolean wallAt(LocalPlayer p, int dx, int dz, double yOffset) {
        AABB box = p.getBoundingBox();
        double reach = p.getBbWidth() * 0.5D + 0.35D;
        double x = p.getX() + dx * reach;
        double z = p.getZ() + dz * reach;
        Level level = p.level();
        return solid(level, BlockPos.containing(x, box.minY + 0.5D + yOffset, z))
                || solid(level, BlockPos.containing(x, box.minY + 1.3D + yOffset, z));
    }

    private static int[] findWall(LocalPlayer p, boolean preferLook) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double lx = -Math.sin(yaw);
        double lz = Math.cos(yaw);
        int[] best = null;
        double bestDot = -2.0D;
        for (int[] c : CARDINALS) {
            if (!wallAt(p, c[0], c[1], 0.0D)) {
                continue;
            }
            double dot = preferLook ? c[0] * lx + c[1] * lz : 0.0D;
            if (best == null || dot > bestDot) {
                best = c;
                bestDot = dot;
            }
        }
        return best;
    }

    private static boolean groundClearance(LocalPlayer p) {
        return p.level().noCollision(p, p.getBoundingBox().move(0.0D, -1.0D, 0.0D));
    }

    private static Integer ceilingAbove(LocalPlayer p, double range) {
        Vec3 from = p.position();
        Vec3 to = from.add(0.0D, p.getBbHeight() + range, 0.0D);
        HitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK ? bh.getBlockPos().getY()
                : null;
    }

    private static void startWall(LocalPlayer p, int[] c) {
        crawl = Crawl.WALL;
        wallX = c[0];
        wallZ = c[1];
        moved(SpiderMovePacket.WALL, 0, Vec3.ZERO, wallX, wallZ);
    }

    private static void startCeiling(LocalPlayer p, int y) {
        crawl = Crawl.CEILING;
        ceilingY = y;
        moved(SpiderMovePacket.CEILING, 0, Vec3.ZERO, 0.0D, 0.0D);
    }

    private static void stopCrawl(LocalPlayer p, boolean quiet) {
        crawl = Crawl.NONE;
        if (!quiet) {
            moved(SpiderMovePacket.CRAWL_OFF, 0, Vec3.ZERO, 0.0D, 0.0D);
        }
    }

    private static void crawlTick(LocalPlayer p, boolean jumpPressed) {
        if (suppress > 0) {
            suppress--;
        }
        if (p.isInWater() || p.isInLava() || p.onClimbable()) {
            if (crawl != Crawl.NONE) {
                stopCrawl(p, false);
            }
            return;
        }
        boolean fwd = p.input.up;
        boolean back = p.input.down;
        boolean left = p.input.left;
        boolean right = p.input.right;
        if (crawl == Crawl.NONE) {
            if (suppress > 0) {
                return;
            }
            if (!p.onGround() && p.getXRot() <= -40.0F) {
                Integer y = ceilingAbove(p, 0.6D);
                if (y != null) {
                    startCeiling(p, y);
                    return;
                }
            }
            boolean into = p.horizontalCollision;
            if (!p.onGround() && into && groundClearance(p) || p.onGround() && into && fwd) {
                int[] c = findWall(p, true);
                if (c != null) {
                    startWall(p, c);
                    if (p.onGround()) {
                        p.setDeltaMovement(p.getDeltaMovement().x, 0.32D, p.getDeltaMovement().z);
                    }
                }
            }
            return;
        }
        if (crawl == Crawl.CEILING) {
            ceilingTick(p, fwd, back, left, right, jumpPressed);
            return;
        }
        // on the wall
        if (p.input.shiftKeyDown) {
            stopCrawl(p, false);
            return;
        }
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double lx = -Math.sin(yaw);
        double lz = Math.cos(yaw);
        boolean facingWall = lx * wallX + lz * wallZ > 0.3D;
        if (jumpPressed && !facingWall) {
            // the wall jump: off it where he looks, high
            double lh = Math.sqrt(lx * lx + lz * lz);
            p.setDeltaMovement(lx / lh * JUMP_LAUNCH, JUMP_UP, lz / lh * JUMP_LAUNCH);
            p.fallDistance = 0.0F;
            stopCrawl(p, true);
            suppress = 6;
            SpiderAnim.event(p, SpiderAnim.EV_WALL_JUMP, 0);
            moved(SpiderMovePacket.WALL_JUMP, 0, Vec3.ZERO, 0.0D, 0.0D);
            return;
        }
        if (fwd && !back && p.getXRot() <= -50.0F) {
            Integer y = ceilingAbove(p, 0.6D);
            if (y != null) {
                startCeiling(p, y);
                return;
            }
        }
        boolean topped = !wallAt(p, wallX, wallZ, 0.6D);
        if (topped && fwd && !back) {
            // over the edge
            stopCrawl(p, false);
            suppress = 8;
            p.setDeltaMovement(wallX * 0.15D, 0.42D, wallZ * 0.15D);
            p.fallDistance = 0.0F;
            return;
        }
        if (!wallAt(p, wallX, wallZ, 0.0D)) {
            int[] c = findWall(p, true);
            if (c == null) {
                stopCrawl(p, false);
                return;
            }
            startWall(p, c);
        } else if (left != right) {
            // round a corner: a wall ahead where he strafes
            int s = right ? 1 : -1;
            int tx = -wallZ * s;
            int tz = wallX * s;
            if (wallAt(p, tx, tz, 0.0D)) {
                startWall(p, new int[] {tx, tz});
            }
        }
        if (p.onGround() && back) {
            stopCrawl(p, false);
            return;
        }
        double vy = topped ? (back ? -CLIMB_SPEED : 0.0D) : ((fwd || p.input.jumping ? 1 : 0) - (back ? 1 : 0))
                * CLIMB_SPEED;
        double strafe = ((right ? 1 : 0) - (left ? 1 : 0)) * STRAFE_SPEED;
        double tx = -wallZ;
        double tz = wallX;
        double vx = tx * strafe + wallX * 0.06D;
        double vz = tz * strafe + wallZ * 0.06D;
        if (back) {
            vx += wallX * 0.18D;
            vz += wallZ * 0.18D;
        }
        p.setDeltaMovement(vx, vy, vz);
        p.fallDistance = 0.0F;
    }

    private static void ceilingTick(LocalPlayer p, boolean fwd, boolean back, boolean left, boolean right,
                                    boolean jumpPressed) {
        Level level = p.level();
        if (jumpPressed || p.input.shiftKeyDown || !solid(level, new BlockPos(p.getBlockX(), ceilingY, p.getBlockZ()))) {
            stopCrawl(p, false);
            suppress = 10;
            return;
        }
        double vx = 0.0D;
        double vz = 0.0D;
        int forward = (fwd ? 1 : 0) - (back ? 1 : 0);
        int strafe = (left ? 1 : 0) - (right ? 1 : 0);
        if (forward != 0 || strafe != 0) {
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);
            vx = (strafe * cos - forward * sin) * CEILING_SPEED;
            vz = (strafe * sin + forward * cos) * CEILING_SPEED;
            if (!solid(level, BlockPos.containing(p.getX() + vx * 4.0D, ceilingY, p.getZ() + vz * 4.0D))) {
                vx = 0.0D;
                vz = 0.0D;
            }
        }
        BlockPos above = new BlockPos(p.getBlockX(), ceilingY, p.getBlockZ());
        var shape = level.getBlockState(above).getCollisionShape(level, above);
        double underside = ceilingY + (shape.isEmpty() ? 0.0D : shape.bounds().minY);
        double target = underside - p.getBbHeight();
        p.setDeltaMovement(vx, (target - p.getY()) * 0.5D + 0.08D, vz);
        p.fallDistance = 0.0F;
    }

    // ------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------

    private static WindSound wind;

    /** The wind of the swing (as Powerborne's: the elytra's rush, louder the faster). */
    private static final class WindSound extends AbstractTickableSoundInstance {
        WindSound() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void level(float v) {
            this.volume = Math.max(0.001F, v);
        }

        void end() {
            this.stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
        }
    }

    private static float windVol;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            return;
        }
        if (mc.player != null && !mc.isPaused()) {
            locomotion(mc, mc.player);
            tickWind(mc, mc.player);
        }
        long now = mc.level.getGameTime();
        WEBBED.values().removeIf(t -> t < now);
        SENSES.values().removeIf(s -> now - s[0] > 30L);
        STRANDS.removeIf(s -> now - s.born > s.life);
        SPLATS.removeIf(s -> now - s.born > 140L);
        for (Iterator<Fade> it = FADES.iterator(); it.hasNext(); ) {
            if (++it.next().age >= 20) {
                it.remove();
            }
        }
        REMOTE.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player pl) || !isSpider(pl));
        if (indicatorTicks > 0) {
            indicatorTicks--;
        }
        if (now % 6 == 0) {
            for (int id : WEBBED.keySet()) {
                Entity e = mc.level.getEntity(id);
                if (e != null) {
                    mc.level.addParticle(ParticleTypes.WHITE_ASH, e.getX(), e.getY() + e.getBbHeight() * 0.5D,
                            e.getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private static void tickWind(Minecraft mc, LocalPlayer p) {
        if (releasing > 0) {
            releasing--;
        }
        boolean on = isSpider(p) && (anchor != null && attached || releasing > 0);
        float target = 0.0F;
        if (on) {
            double sq = p.getDeltaMovement().lengthSqr();
            target = (float) Math.min(sq / 4.0D, 1.0D) * 0.35F;
        }
        windVol += (target - windVol) * 0.15F;
        if (on || windVol > 0.001F) {
            if (wind == null || !mc.getSoundManager().isActive(wind)) {
                wind = new WindSound();
                mc.getSoundManager().play(wind);
            }
            wind.level(windVol);
        } else if (wind != null) {
            wind.end();
            wind = null;
            windVol = 0.0F;
        }
    }

    /** The swing tilts the camera into the arc. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isSpider(mc.player) || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float roll = SpiderAnim.roll(mc.player, (float) event.getPartialTick());
        if (Math.abs(roll) > 0.01F) {
            event.setRoll(event.getRoll() + roll);
        }
    }

    // ------------------------------------------------------------------
    // What the server sends
    // ------------------------------------------------------------------

    private static int indicatorTicks;
    private static int indicatorEntity = -1;

    public static void fx(HeroFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Entity caster = mc.level.getEntity(p.caster());
        Player cp = caster instanceof Player pl ? pl : null;
        SpiderVfx.fx(p);
        switch (p.type()) {
            case SpiderAbilities.FX_SHOT -> {
                STRANDS.add(new Strand(p.caster(), p.from(), p.to(), -1, now, 5, true));
                if (cp != null) {
                    SpiderAnim.event(cp, SpiderAnim.EV_SHOOT, 0);
                }
                if (p.power() > 0.0F) {
                    splat(mc, p.to(), 8);
                    HitResult hit = mc.level.clip(new ClipContext(p.from(), p.to().add(p.to().subtract(p.from())
                            .normalize().scale(0.2D)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                    if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                        SPLATS.add(new Splat(bh.getLocation(), bh.getDirection(), now));
                    }
                }
            }
            case SpiderAbilities.FX_NET -> {
                Vec3 dir = p.to();
                Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 up = side.cross(dir).normalize();
                for (int k = 0; k < 9; k++) {
                    double a = (k - 4) / 4.0D * 0.65D;
                    double b = ((k * 37) % 5 - 2) / 2.0D * 0.25D;
                    Vec3 d = dir.add(side.scale(Math.tan(a))).add(up.scale(b)).normalize();
                    STRANDS.add(new Strand(p.caster(), p.from(), p.from().add(d.scale(12.0D)), -1, now, 8, true));
                }
                if (cp != null) {
                    SpiderAnim.event(cp, SpiderAnim.EV_SHOOT, 0);
                }
            }
            case SpiderAbilities.FX_YANK -> {
                STRANDS.add(new Strand(p.caster(), p.from(), p.to(), p.target(), now, 9, true));
                if (cp != null) {
                    SpiderAnim.event(cp, SpiderAnim.EV_SHOOT, 0);
                }
            }
            case SpiderAbilities.FX_ZIP -> {
                STRANDS.add(new Strand(p.caster(), p.from(), p.to(), -1, now, 10, true));
                if (cp != null) {
                    SpiderAnim.event(cp, SpiderAnim.EV_ZIP, 0);
                }
                HitResult hit = mc.level.clip(new ClipContext(p.from(), p.to().add(p.to().subtract(p.from())
                        .normalize().scale(0.2D)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                    SPLATS.add(new Splat(bh.getLocation(), bh.getDirection(), now + 2));
                }
            }
            case SpiderAbilities.FX_WEBBED -> {
                WEBBED.put(p.target(), now + (long) p.power());
                splat(mc, p.from().add(0.0D, 0.8D, 0.0D), 14);
            }
            case SpiderAbilities.FX_SENSE -> {
                SENSES.put(p.caster(), new long[] {now, (long) p.power()});
                if (mc.player != null && p.caster() == mc.player.getId() && p.target() >= 0) {
                    indicatorTicks = 30;
                    indicatorEntity = p.target();
                }
                if (p.power() >= 2.0F) {
                    for (int i = 0; i < 24; i++) {
                        double a = i / 24.0D * Math.PI * 2.0D;
                        mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.from().x + Math.cos(a) * 0.6D,
                                p.from().y + 2.1D, p.from().z + Math.sin(a) * 0.6D, Math.cos(a) * 0.12D, 0.05D,
                                Math.sin(a) * 0.12D);
                    }
                }
            }
            case SpiderAbilities.FX_KICK -> {
                if (cp != null) {
                    SpiderAnim.event(cp, SpiderAnim.EV_KICK, 0);
                }
                for (int i = 0; i < 10; i++) {
                    mc.level.addParticle(ParticleTypes.CLOUD, p.from().x, p.from().y + 0.4D, p.from().z,
                            -p.to().x * 0.2D + (Math.random() - 0.5D) * 0.1D, 0.02D,
                            -p.to().z * 0.2D + (Math.random() - 0.5D) * 0.1D);
                }
            }
            case SpiderAbilities.FX_BITE -> {
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0D * Math.PI * 2.0D;
                    double h = (i % 8) * 0.25D;
                    mc.level.addParticle(ParticleTypes.WITCH, p.from().x + Math.cos(a) * 0.7D, p.from().y + h,
                            p.from().z + Math.sin(a) * 0.7D, 0.0D, 0.04D, 0.0D);
                }
            }
            case SpiderAbilities.FX_DODGE -> {
                SENSES.put(p.caster(), new long[] {now, 1L});
                if (cp != null) {
                    SpiderAnim.event(cp, SpiderAnim.EV_DEFLECT, 0);
                }
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(ParticleTypes.POOF, p.from().x, p.from().y + 1.0D, p.from().z,
                            -p.to().x * 0.15D, 0.02D, -p.to().z * 0.15D);
                }
            }
            default -> {
            }
        }
    }

    private static void splat(Minecraft mc, Vec3 at, int n) {
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, (Math.random() - 0.5D) * 0.3D,
                    Math.random() * 0.2D, (Math.random() - 0.5D) * 0.3D);
            mc.level.addParticle(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Where his web leaves his hand: the raised hand on the line (right, left, or between both), the right wrist
     * otherwise; in first person, the vanilla rope hold.
     */
    private static Vec3 wrist(Minecraft mc, Player p, float pt, int arm) {
        boolean me = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (me) {
            return p.getRopeHoldPosition(pt).add(0.0D, 0.2D, 0.0D);
        }
        Vec3 base = p.getPosition(pt);
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        boolean raised = line(p) != null;
        double side = raised ? (arm == 1 ? 0.36D : arm == 2 ? 0.0D : -0.36D) : -0.36D;
        double h = raised ? 2.05D : 1.35D;
        double fwd = raised ? 0.05D : 0.35D;
        return base.add(-Math.sin(yaw) * fwd + Math.cos(yaw) * side, h, Math.cos(yaw) * fwd + Math.sin(yaw) * side);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                renderSense(event);
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SPIDERS.isEmpty() && WEBBED.isEmpty() && STRANDS.isEmpty() && FADES.isEmpty()
                && SPLATS.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        long now = mc.level.getGameTime();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // the lines (Powerborne's: a thin light-grey strand, lit by the world)
        VertexConsumer web = buffers.getBuffer(RenderType.textBackground());
        for (Player p : mc.level.players()) {
            Vec3 at = line(p);
            if (at == null || !isSpider(p)) {
                continue;
            }
            Vec3 from = wrist(mc, p, pt, armVariant(p));
            Vec3 to = at;
            double reach = lineReach(p, now, pt, from, at);
            if (reach < 1.0D) {
                to = from.add(at.subtract(from).scale(reach));
            }
            strand(web, m, from, to, 1.0F, light(mc, from.add(to).scale(0.5D)));
        }
        for (Strand s : STRANDS) {
            float age = now - s.born + pt;
            Entity caster = mc.level.getEntity(s.caster);
            Vec3 from = s.fromHand && caster instanceof Player cp ? wrist(mc, cp, pt, 0) : s.from;
            Vec3 to = s.to;
            Entity t = s.target >= 0 ? mc.level.getEntity(s.target) : null;
            if (t != null) {
                to = t.getPosition(pt).add(0.0D, t.getBbHeight() * 0.6D, 0.0D);
            }
            float reach = Math.min(1.0F, age / 2.0F);
            strand(web, m, from, from.add(to.subtract(from).scale(reach)), 1.0F, light(mc, from));
        }
        for (Fade f : FADES) {
            float t = Mth.clamp((f.age + pt) / 20.0F, 0.0F, 1.0F);
            Vec3 s = new Vec3(f.s.x + (f.e.x - f.s.x) * t, f.s.y - 5.0D * t * t, f.s.z + (f.e.z - f.s.z) * t);
            strand(web, m, s, f.e, 1.0F - t, light(mc, f.e));
        }
        buffers.endBatch(RenderType.textBackground());
        // the web splats (the cobweb on the block)
        if (!SPLATS.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(COBWEB));
            for (Splat s : SPLATS) {
                if (now < s.born) {
                    continue;
                }
                float age = now - s.born + pt;
                float a = Math.min(1.0F, age / 3.0F) * Math.min(1.0F, (140.0F - age) / 20.0F);
                if (a > 0.0F) {
                    splatQuad(vc, m, n, s, a, light(mc, s.at.add(Vec3.atLowerCornerOf(s.face.getNormal()).scale(0.3D))));
                }
            }
            buffers.endBatch(RenderType.entityTranslucent(COBWEB));
        }
        // the cocoons
        if (!WEBBED.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(COCOON));
            for (Map.Entry<Integer, Long> en : WEBBED.entrySet()) {
                Entity e = mc.level.getEntity(en.getKey());
                if (e == null || e.isRemoved()) {
                    continue;
                }
                Vec3 pos = e.getPosition(pt);
                AABB box = e.getBoundingBox().move(pos.subtract(e.position())).inflate(0.12D);
                cocoon(vc, m, n, box, light(mc, pos.add(0.0D, 0.5D, 0.0D)));
            }
            buffers.endBatch(RenderType.entityCutoutNoCull(COCOON));
        }
        ps.popPose();
    }

    /** The line shot out at 5.5 blocks a tick until it reaches where it sticks (0..1). */
    private static double lineReach(Player p, long now, float pt, Vec3 from, Vec3 at) {
        if (local(p)) {
            if (attached) {
                return 1.0D;
            }
            double total = Math.max(1.0D, Math.ceil(p.getEyePosition().distanceTo(at) / WEB_SPEED));
            return Mth.clamp((total - shootTicks + pt) / total, 0.0D, 1.0D);
        }
        Remote r = remote(p);
        if (r == null) {
            return 1.0D;
        }
        double travelled = (now - r.lineStart + pt) * WEB_SPEED;
        return Mth.clamp(travelled / Math.max(0.1D, from.distanceTo(at)), 0.0D, 1.0D);
    }

    private static void renderSense(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SENSES.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        long now = mc.level.getGameTime();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SENSE));
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        for (Map.Entry<Integer, long[]> en : SENSES.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            float age = now - en.getValue()[0] + pt;
            float big = en.getValue()[1] >= 2 ? 1.35F : 1.0F;
            float a = Math.min(1.0F, age / 3.0F) * Math.max(0.0F, 1.0F - (age - 18.0F) / 12.0F);
            float jitter = (((int) age) % 2 == 0) ? 0.03F : -0.03F;
            Vec3 c = e.getPosition(pt).add(jitter, e.getBbHeight() + 0.42D * big, 0.0D);
            billboard(vc, m, n, c, left, up, 0.5F * big, 0.42F * big, a);
        }
        buffers.endBatch(RenderType.entityTranslucent(SENSE));
        ps.popPose();
    }

    private static int light(Minecraft mc, Vec3 at) {
        return LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
    }

    /** Powerborne's web strand: a thin box, light grey, with the world's light. */
    private static void strand(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float alpha, int lm) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double inv = 1.0D / Math.sqrt(dx * dx + dz * dz + 1.0E-6D);
        float px = (float) (dz * inv * WEB_WIDTH / 2.0D);
        float pz = (float) (dx * inv * WEB_WIDTH / 2.0D);
        float ox = (float) a.x;
        float oy = (float) a.y;
        float oz = (float) a.z;
        float ex = (float) b.x;
        float ey = (float) b.y;
        float ez = (float) b.z;
        float w = WEB_WIDTH;
        float[][] q = {
                {ox - px, oy, oz + pz}, {ox + px, oy, oz - pz}, {ex + px, ey, ez - pz}, {ex - px, ey, ez + pz},
                {ox - px, oy + w, oz + pz}, {ex - px, ey + w, ez + pz}, {ex + px, ey + w, ez - pz},
                {ox + px, oy + w, oz - pz},
                {ox + px, oy, oz - pz}, {ox + px, oy + w, oz - pz}, {ex + px, ey + w, ez - pz}, {ex + px, ey, ez - pz},
                {ex - px, ey, ez + pz}, {ex - px, ey + w, ez + pz}, {ox - px, oy + w, oz + pz},
                {ox - px, oy, oz + pz}};
        for (float[] v : q) {
            vc.vertex(m, v[0], v[1], v[2]).color(WEB_SHADE, WEB_SHADE, WEB_SHADE, alpha).uv2(lm).endVertex();
        }
    }

    private static void splatQuad(VertexConsumer vc, Matrix4f m, Matrix3f n, Splat s, float alpha, int light) {
        Vec3 normal = Vec3.atLowerCornerOf(s.face.getNormal());
        Vec3 c = s.at.add(normal.scale(0.01D));
        Vec3 ref = Math.abs(normal.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = normal.cross(ref).normalize();
        Vec3 v = normal.cross(u).normalize();
        double cs = Math.cos(s.spin) * 0.45D;
        double sn = Math.sin(s.spin) * 0.45D;
        Vec3 e1 = u.scale(cs).add(v.scale(sn));
        Vec3 e2 = u.scale(-sn).add(v.scale(cs));
        quad(vc, m, n, c.subtract(e1).subtract(e2), c.add(e1).subtract(e2), c.add(e1).add(e2), c.subtract(e1).add(e2),
                0.0F, 0.0F, 1.0F, 1.0F, light, alpha);
        quad(vc, m, n, c.subtract(e1).add(e2), c.add(e1).add(e2), c.add(e1).subtract(e2), c.subtract(e1).subtract(e2),
                0.0F, 1.0F, 1.0F, 0.0F, light, alpha);
    }

    /** The web cocoon wrapped around a box. */
    private static void cocoon(VertexConsumer vc, Matrix4f m, Matrix3f n, AABB b, int light) {
        float u1 = 54.0F / 64.0F;
        float v1 = 63.0F / 64.0F;
        double x0 = b.minX;
        double y0 = b.minY;
        double z0 = b.minZ;
        double x1 = b.maxX;
        double y1 = b.maxY;
        double z1 = b.maxZ;
        quad(vc, m, n, new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x0, y1, z0), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x0, y0, z1), new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), 0, 0,
                u1, v1, light, 1.0F);
        quad(vc, m, n, new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), 0, 0,
                u1, v1, light, 1.0F);
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, Vec3 left, Vec3 up, float hw,
                                  float hh, float a) {
        Vec3 l = left.scale(hw);
        Vec3 u = up.scale(hh);
        quad(vc, m, n, c.add(l).subtract(u), c.subtract(l).subtract(u), c.subtract(l).add(u), c.add(l).add(u), 0.0F,
                0.0F, 1.0F, 1.0F, 0xF000F0, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0,
                             float v0, float u1, float v1, int light, float alpha) {
        vert(vc, m, n, a, u0, v1, light, alpha);
        vert(vc, m, n, b, u1, v1, light, alpha);
        vert(vc, m, n, c, u1, v0, light, alpha);
        vert(vc, m, n, d, u0, v0, light, alpha);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int light,
                             float alpha) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** First person: the spider-sense at the top of the screen, and Powerborne's indicator toward the threat. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || !event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id())
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        var g = event.getGuiGraphics();
        int sw = event.getWindow().getGuiScaledWidth();
        int sh = event.getWindow().getGuiScaledHeight();
        long[] s = SENSES.get(mc.player.getId());
        if (s != null) {
            float age = mc.level.getGameTime() - s[0] + event.getPartialTick();
            float a = Math.min(1.0F, age / 3.0F) * Math.max(0.0F, 1.0F - (age - 18.0F) / 12.0F);
            if (a > 0.01F) {
                int w = s[1] >= 2 ? 96 : 72;
                int h = w * 216 / 256;
                int x = (sw - w) / 2 + ((((int) age) % 2 == 0) ? 1 : -1);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.setColor(1.0F, 1.0F, 1.0F, a);
                g.blit(SENSE, x, 6, 0, 0, w, h, w, h);
                g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
        }
        if (indicatorTicks > 0) {
            Entity threat = indicatorEntity >= 0 ? mc.level.getEntity(indicatorEntity) : null;
            if (threat == null || !threat.isAlive()) {
                indicatorTicks = 0;
                return;
            }
            double dx = threat.getX() - mc.player.getX();
            double dz = threat.getZ() - mc.player.getZ();
            float toThreat = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float angle = Mth.wrapDegrees(toThreat - mc.player.getViewYRot(event.getPartialTick()));
            float a = Math.min(1.0F, indicatorTicks / 10.0F);
            g.pose().pushPose();
            g.pose().translate(sw / 2.0F, sh / 2.0F, 0.0F);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1.0F, 1.0F, 1.0F, a);
            g.blit(INDICATOR, -50, -50, 0, 0, 100, 100, 100, 100);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            g.pose().popPose();
        }
    }
}

package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.infinity.entity.BlackHole;
import com.flash.infinity.entity.SpacePortal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The Space Stone (and what other stones share of its kinds): the force field, the movement lock, the
 * invulnerability, the portals (the menu, the jump), telekinesis and the spatial lock, the black hole.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SpaceStone {

    public static final int PORTAL_COLOR = 0x5BA9FF;

    private SpaceStone() {
    }

    private static boolean has(ServerPlayer p, String type) {
        for (String key : InfinityServer.state(p).enabled) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a != null && type.equals(a.type)) {
                return true;
            }
        }
        return false;
    }

    private static InfPowers.Ability first(ServerPlayer p, String type) {
        for (String key : InfinityServer.state(p).enabled) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a != null && type.equals(a.type)) {
                return a;
            }
        }
        return null;
    }

    static void sound(Level level, Vec3 at, String name, float vol, float pitch) {
        SoundEvent s = InfinitySounds.get(name);
        if (s != null) {
            level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
        }
    }

    // ------------------------------------------------------------------
    // The force field, the invulnerability, the movement lock
    // ------------------------------------------------------------------

    private static final java.util.List<String> ALWAYS_BLOCKED = java.util.List.of("minecraft:iron_golem",
            "minecraft:warden", "minecraft:vindicator", "minecraft:piglin_brute", "minecraft:zombified_piglin");

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        // the force field, as Doctor Doom's: what hits him barely reaches him (15%, explosions half that)
        if (fieldOn(p) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float f = com.flash.doom.DoomPowers.SHIELD_FACTOR;
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
                f *= 0.5F;
            }
            event.setAmount(event.getAmount() * f);
            com.flash.doom.DoomAbilities.shieldHit(p);
            sound(p.level(), p.position(), "space_stone_forcefield", 0.6F, 1.4F);
        }
        InfPowers.Ability inv = first(p, "flash:invulnerability");
        if (inv != null) {
            event.setCanceled(true);
            p.invulnerableTime = 20;
        }
    }

    /** The Space Stone's force field is up. */
    public static boolean fieldOn(net.minecraft.world.entity.player.Player p) {
        return p instanceof ServerPlayer sp && first(sp, "flash:force_field") != null;
    }

    /** Doom's barrier around the field: whoever comes inside is pushed back to its edge. */
    @SubscribeEvent
    public static void onFieldTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.player instanceof ServerPlayer p
                && fieldOn(p) && !com.flash.doom.DoomAbilities.shielded(p)) {
            com.flash.doom.DoomAbilities.barrier(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        InfPowers.Ability inv = first(p, "flash:invulnerability");
        if (inv != null && InfPowers.bool(inv.json, "negate_hits", false)) {
            event.setCanceled(true);
            if (inv.json.has("particle")) {
                var pt = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(
                        inv.json.get("particle").getAsString()));
                if (pt instanceof net.minecraft.core.particles.SimpleParticleType sp) {
                    p.serverLevel().sendParticles(sp, p.getX(), p.getY() + 1.0D, p.getZ(), 7, 0.3D, 0.5D, 0.3D, 0.0D);
                }
            }
        }
    }

    /** The movement lock (force field, the Power Stone's cyclone): the player can't walk, strafe or jump. */
    static void movementLock(ServerPlayer p, boolean on) {
        if (on) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(0.0D, Math.min(v.y, 0.0D), 0.0D);
            p.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------
    // Portals
    // ------------------------------------------------------------------

    /** The portal menu's answer: a pair, one before the player, one at the destination (open 7 s). */
    public static void portalTo(ServerPlayer p, String dim, double x, double y, double z) {
        InfinityServer.State s = InfinityServer.state(p);
        boolean ok = false;
        for (String pid : s.powers) {
            InfPowers.Power pw = InfPowers.get(pid);
            if (pw == null) {
                continue;
            }
            for (InfPowers.Ability a : pw.abilities.values()) {
                ok |= "flash:portal".equals(a.type) && InfinityPowers.unlocked(s, a);
            }
        }
        if (!ok) {
            return;
        }
        ServerLevel to = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
        if (to == null) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.no_dimension", dim), true);
            return;
        }
        Vec3 dest = new Vec3(x, Math.max(to.getMinBuildHeight() + 1, Math.min(to.getMaxBuildHeight() - 3, y)), z);
        pair(p, to, dest, 140L, false);
    }

    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> PORTAL_TICKET =
            net.minecraft.server.level.TicketType.create("flash_space_portal",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 600);

    private static void pair(ServerPlayer p, ServerLevel to, Vec3 dest, long life, boolean jump) {
        ServerLevel here = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 nearAt = p.position().add(look.scale(2.5D));
        float yaw = p.getYRot() + 180.0F;
        SpacePortal near = SpacePortal.create(here, nearAt, yaw, PORTAL_COLOR, false);
        SpacePortal far = SpacePortal.create(to, dest, p.getYRot(), PORTAL_COLOR, false);
        to.getChunk(BlockPos.containing(dest));
        // both ends kept loaded and ticking while open: in another dimension the far end's chunk is not loaded by
        // anybody, so the far portal could not be found and nothing crossed
        net.minecraft.world.level.ChunkPos farChunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(dest));
        net.minecraft.world.level.ChunkPos nearChunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(nearAt));
        to.getChunkSource().addRegionTicket(PORTAL_TICKET, farChunk, 2, farChunk);
        here.getChunkSource().addRegionTicket(PORTAL_TICKET, nearChunk, 2, nearChunk);
        near.link(far);
        far.link(near);
        long end = here.getGameTime() + life + SpacePortal.OPEN_TICKS;
        near.expireAt(end);
        far.expireAt(to.getGameTime() + life + SpacePortal.OPEN_TICKS);
        here.addFreshEntity(near);
        to.addFreshEntity(far);
        sound(here, nearAt, "space_stone_portal", 1.0F, 1.0F);
        sound(to, dest, "space_stone_portal", 1.0F, 1.0F);
    }

    /** The jump: a short-lived pair, here and on safe ground where the player looks, and the player jumps there. */
    static void jump(ServerPlayer p, double range) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.no_block"), true);
            return;
        }
        Vec3 land = landing(level, p, hit.getBlockPos());
        if (land == null) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.no_block"), true);
            return;
        }
        pair(p, level, land, 60L, true);
        p.getPersistentData().putLong("flash_portal_until", level.getGameTime() + 20L);
        p.teleportTo(land.x, land.y, land.z);
        p.fallDistance = 0.0F;
    }

    private static Vec3 landing(ServerLevel level, Entity e, BlockPos hit) {
        for (int dy = 0; dy < 32; dy++) {
            BlockPos p = hit.above(dy + 1);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(p);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Telekinesis and the spatial lock
    // ------------------------------------------------------------------

    private static final class Grip {
        final UUID entity;
        double distance;

        Grip(UUID entity, double distance) {
            this.entity = entity;
            this.distance = distance;
        }
    }

    private static final Map<UUID, Grip> GRIPS = new HashMap<>();
    private static final Map<UUID, Vec3> THROWN = new HashMap<>();

    public static Entity held(ServerPlayer p) {
        Grip g = GRIPS.get(p.getUUID());
        return g == null ? null : p.serverLevel().getEntity(g.entity);
    }

    static void telekinesis(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a, boolean on, boolean was) {
        if (on && !was) {
            if (!grab(p, 24.0D)) {
                s.untoggle(a.key());
            }
            return;
        }
        if (!on && was) {
            release(p);
            return;
        }
        if (on) {
            Grip g = GRIPS.get(p.getUUID());
            Entity e = g == null ? null : p.serverLevel().getEntity(g.entity);
            if (e == null || !e.isAlive() || InfinityImmunity.immune(e, "telekinesis")) {
                release(p);
                s.untoggle(a.key());
                return;
            }
            Vec3 want = p.getEyePosition().add(p.getLookAngle().scale(g.distance)).subtract(0.0D, e.getBbHeight()
                    * 0.5D, 0.0D);
            Vec3 v = want.subtract(e.position()).scale(0.5D);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (e instanceof Mob m) {
                m.getNavigation().stop();
            }
            if (p.tickCount % 3 == 0) {
                beam(p, e);
            }
        }
    }

    private static boolean grab(ServerPlayer p, double range) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, stop, p.getBoundingBox().expandTowards(
                stop.subtract(eye)).inflate(1.0D), e -> !e.isSpectator() && e.isPickable() && !(e instanceof
                SpacePortal) && !InfinityImmunity.immune(e, "telekinesis"), eye.distanceToSqr(stop));
        Entity target = null;
        if (eh != null) {
            target = eh.getEntity();
            unlock(target);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            BlockState st = level.getBlockState(pos);
            if (st.getDestroySpeed(level, pos) < 0.0F || level.getBlockEntity(pos) != null || st.isAir()) {
                return false;
            }
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, st);
            fb.setNoGravity(true);
            fb.time = -100000;
            fb.dropItem = false;
            target = fb;
        }
        if (target == null) {
            return false;
        }
        target.setNoGravity(true);
        double d = Math.max(2.0D, Math.min(24.0D, eye.distanceTo(target.position())));
        GRIPS.put(p.getUUID(), new Grip(target.getUUID(), d));
        sound(level, target.position(), "space_stone_grab", 1.0F, 1.0F);
        return true;
    }

    static void release(ServerPlayer p) {
        Grip g = GRIPS.remove(p.getUUID());
        if (g == null) {
            return;
        }
        Entity e = p.serverLevel().getEntity(g.entity);
        if (e == null) {
            return;
        }
        if (!e.getPersistentData().contains("flash_spatial_lock")) {
            e.setNoGravity(false);
        }
        if (e instanceof FallingBlockEntity fb) {
            fb.time = 1;
            fb.dropItem = true;
        }
    }

    /** Scrolling while holding: closer or farther (1.25 a notch). */
    public static void scroll(ServerPlayer p, int dir) {
        Grip g = GRIPS.get(p.getUUID());
        if (g != null) {
            g.distance = Math.max(2.0D, Math.min(24.0D, g.distance + dir * 1.25D));
        }
    }

    /** Attacking: the held thing is thrown (telekinesis), or the black hole is. */
    public static void attack(ServerPlayer p) {
        BlackHole bh = blackHole(p);
        if (bh != null && !bh.thrown()) {
            bh.launch(p.getLookAngle());
            InfinityServer.State s = InfinityServer.state(p);
            for (String k : new java.util.ArrayList<>(s.toggled)) {
                InfPowers.Ability a = InfPowers.ability(k);
                if (a != null && "flash:black_hole".equals(a.type)) {
                    s.untoggle(k);
                }
            }
            BLACK_HOLES.remove(p.getUUID());
            return;
        }
        Grip g = GRIPS.remove(p.getUUID());
        if (g == null) {
            return;
        }
        Entity e = p.serverLevel().getEntity(g.entity);
        InfinityServer.State s = InfinityServer.state(p);
        for (String k : new java.util.ArrayList<>(s.toggled)) {
            InfPowers.Ability a = InfPowers.ability(k);
            if (a != null && "flash:telekinesis".equals(a.type)) {
                s.untoggle(k);
            }
        }
        if (e == null) {
            return;
        }
        e.setNoGravity(false);
        if (e instanceof FallingBlockEntity fb) {
            fb.time = 1;
            fb.dropItem = true;
        }
        e.setDeltaMovement(p.getLookAngle().scale(2.5D));
        e.hurtMarked = true;
        THROWN.put(e.getUUID(), p.position());
        e.getPersistentData().putUUID("flash_thrown_by", p.getUUID());
        e.getPersistentData().putInt("flash_thrown_ticks", 100);
    }

    /** The spatial lock: what telekinesis holds is locked where it is (using it, the use key). */
    public static void useWhileHolding(ServerPlayer p) {
        InfinityServer.State s = InfinityServer.state(p);
        boolean can = false;
        for (String pid : s.powers) {
            InfPowers.Power pw = InfPowers.get(pid);
            if (pw != null) {
                for (InfPowers.Ability a : pw.abilities.values()) {
                    can |= "flash:spatial_lock".equals(a.type) && InfinityPowers.unlocked(s, a);
                }
            }
        }
        Grip g = GRIPS.get(p.getUUID());
        if (!can || g == null) {
            return;
        }
        Entity e = p.serverLevel().getEntity(g.entity);
        GRIPS.remove(p.getUUID());
        for (String k : new java.util.ArrayList<>(s.toggled)) {
            InfPowers.Ability a = InfPowers.ability(k);
            if (a != null && "flash:telekinesis".equals(a.type)) {
                s.untoggle(k);
            }
        }
        if (e != null) {
            lock(e);
        }
    }

    static void lock(Entity e) {
        CompoundTag t = new CompoundTag();
        t.putDouble("X", e.getX());
        t.putDouble("Y", e.getY());
        t.putDouble("Z", e.getZ());
        e.getPersistentData().put("flash_spatial_lock", t);
        e.setNoGravity(true);
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof Mob m) {
            m.setNoAi(true);
        }
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    static void unlock(Entity e) {
        if (e.getPersistentData().contains("flash_spatial_lock")) {
            e.getPersistentData().remove("flash_spatial_lock");
            e.setNoGravity(false);
            if (e instanceof Mob m) {
                m.setNoAi(false);
            }
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8F, 0.8F);
        }
    }

    private static void beam(ServerPlayer p, Entity e) {
        Vec3 a = p.getEyePosition().add(p.getLookAngle().scale(0.6D)).subtract(0.0D, 0.3D, 0.0D);
        Vec3 b = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
        for (int i = 0; i <= 8; i++) {
            Vec3 at = a.lerp(b, i / 8.0D);
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.36F, 0.66F, 1.0F), 0.8F), at.x,
                    at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                CompoundTag d = e.getPersistentData();
                if (d.contains("flash_spatial_lock")) {
                    CompoundTag t = d.getCompound("flash_spatial_lock");
                    e.setDeltaMovement(Vec3.ZERO);
                    if (e.distanceToSqr(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")) > 1.0E-4D) {
                        e.teleportTo(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
                    }
                    if (e instanceof FallingBlockEntity fb) {
                        fb.time = -100000;
                    }
                    if (e.tickCount % 10 == 0) {
                        level.sendParticles(new DustParticleOptions(new Vector3f(0.3F, 0.7F, 1.0F), 0.8F), e.getX(),
                                e.getY() + e.getBbHeight() * 0.5D, e.getZ(), 3, 0.4D, 0.4D, 0.4D, 0.0D);
                    }
                }
                if (d.contains("flash_thrown_ticks")) {
                    int left = d.getInt("flash_thrown_ticks") - 1;
                    if (left <= 0 || e.onGround()) {
                        d.remove("flash_thrown_ticks");
                        d.remove("flash_thrown_by");
                        continue;
                    }
                    d.putInt("flash_thrown_ticks", left);
                    double speed = e.getDeltaMovement().length();
                    if (speed < 0.35D) {
                        continue;
                    }
                    Player by = d.hasUUID("flash_thrown_by") ? level.getPlayerByUUID(d.getUUID("flash_thrown_by"))
                            : null;
                    for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox()
                            .inflate(0.3D), o -> o != e && o != by)) {
                        hit.hurt(by != null ? p(level, by) : level.damageSources().fallingBlock(e),
                                (float) Math.max(4.0D, speed * 3.0D));
                        d.putInt("flash_thrown_ticks", 1);
                    }
                }
            }
        }
        BLACK_HOLES.entrySet().removeIf(en -> {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            BlackHole b = p == null ? null : (BlackHole) p.serverLevel().getEntity(en.getValue());
            return b == null || b.isRemoved();
        });
    }

    private static net.minecraft.world.damagesource.DamageSource p(ServerLevel level, Player by) {
        return level.damageSources().playerAttack(by);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity().getPersistentData().contains("flash_spatial_lock")) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // The black hole
    // ------------------------------------------------------------------

    private static final Map<UUID, UUID> BLACK_HOLES = new HashMap<>();

    static BlackHole blackHole(ServerPlayer p) {
        UUID id = BLACK_HOLES.get(p.getUUID());
        Entity e = id == null ? null : p.serverLevel().getEntity(id);
        return e instanceof BlackHole b ? b : null;
    }

    static void blackHoleToggle(ServerPlayer p, boolean on, boolean was) {
        if (on && !was) {
            BlackHole b = BlackHole.create(p.serverLevel(), p, p.getEyePosition().add(p.getLookAngle().scale(4.0D)));
            p.serverLevel().addFreshEntity(b);
            BLACK_HOLES.put(p.getUUID(), b.getUUID());
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F,
                    0.4F);
        } else if (!on && was) {
            BlackHole b = blackHole(p);
            if (b != null && !b.thrown()) {
                b.collapse();
            }
            BLACK_HOLES.remove(p.getUUID());
        }
    }
}

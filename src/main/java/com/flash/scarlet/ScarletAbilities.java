package com.flash.scarlet;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.ScarletActionPacket;
import com.flash.network.ScarletFxPacket;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Scarlet Witch's own powers (R / Z in her modes, ScarletClient):
 * <ul>
 * <li><b>Telepathy and Mind Control</b>: R on a player reads his mind (his inventory, to look at only); R on a
 * creature makes it her ally; R at nothing: a red wave that breaks nothing and turns the creatures around into her
 * allies (they attack whoever hits her). Z on a player: she walks him where she looks, for 8 s; Z on a creature: it
 * attacks everything near it; Z at nothing: the hostile creatures around turn on each other.</li>
 * <li><b>Energy Projection</b>: R a hex bolt, R held a red beam, Z held and let go a blast of chaos (it breaks
 * nothing).</li>
 * <li><b>Necromancy and Resurrection</b>: R heals whom she looks at (or herself) of everything; Z brings back what
 * died in the last five minutes where she looks (creatures come back as her servants; a dead player not yet
 * respawned stands up again where he fell).</li>
 * <li>The dismemberment of her telekinesis: torn apart (Hand of God's pieces) and whole again five seconds
 * later.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ScarletAbilities {

    private static final double RANGE = 64.0D;
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    /** The beam held on: when it started and the last hit. */
    private static final Map<UUID, long[]> BEAM = new HashMap<>();

    private ScarletAbilities() {
    }

    /** Off cooldown: true, and the cooldown starts (and is shown to her). */
    public static boolean ready(ServerPlayer p, String id) {
        return ready(p, id, ScarletCooldowns.ticks(id));
    }

    public static boolean ready(ServerPlayer p, String id, int ticks) {
        if (!cooling(p, id)) {
            start(p, id, ticks);
            return true;
        }
        return false;
    }

    public static boolean cooling(ServerPlayer p, String id) {
        Long until = COOLDOWNS.get(p.getUUID() + id);
        return until != null && p.level().getGameTime() < until;
    }

    public static void start(ServerPlayer p, String id, int ticks) {
        COOLDOWNS.put(p.getUUID() + id, p.level().getGameTime() + ticks);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new com.flash.network.ScarletCooldownPacket(id, ticks));
    }

    public static void act(ServerPlayer p, int action, int entity, int value) {
        if (!ScarletPowers.has(p) || p.isSpectator()) {
            return;
        }
        Entity aimed = entity >= 0 ? p.level().getEntity(entity) : null;
        if (aimed != null && (!aimed.isAlive() || aimed.distanceToSqr(p) > RANGE * RANGE || aimed == p)) {
            aimed = null;
        }
        switch (action) {
            case ScarletActionPacket.MIND -> mind(p, aimed);
            case ScarletActionPacket.MIND_VARIANT -> mindVariant(p, aimed);
            case ScarletActionPacket.BOLT -> bolt(p, false);
            case ScarletActionPacket.BEAM -> bolt(p, true);
            case ScarletActionPacket.BLAST -> blast(p, Math.max(0, Math.min(100, value)) / 100.0F);
            case ScarletActionPacket.HEAL -> heal(p, aimed);
            case ScarletActionPacket.RAISE -> raise(p);
            case ScarletActionPacket.DISMEMBER -> dismember(p);
            default -> {
            }
        }
    }

    private static void fx(ServerPlayer p, int type, Vec3 a, Vec3 b, float power) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ScarletFxPacket(p.getId(), type, a, b, power));
    }

    private static boolean immune(Entity e) {
        return e instanceof EnderDragon || e instanceof WitherBoss
                || e.getType() == EntityType.WARDEN || e instanceof com.flash.yas.YasGuardian;
    }

    // ------------------------------------------------------------------
    // Telepathy and Mind Control
    // ------------------------------------------------------------------

    private static void mind(ServerPlayer p, Entity aimed) {
        if (aimed instanceof ServerPlayer target) {
            if (!ready(p, "read")) {
                return;
            }
            readMind(p, target);
            fx(p, ScarletFxPacket.LINK, p.getEyePosition(), target.getEyePosition(), 1.0F);
            ScarletPowers.play(p.serverLevel(), target.position(), "telecinese_tensao", SoundEvents.EVOKER_PREPARE_WOLOLO,
                    1.4F);
            return;
        }
        if (aimed instanceof Mob mob && !immune(mob)) {
            if (!ready(p, "ally")) {
                return;
            }
            ScarletThralls.ally(p, mob, 90 * 20, false);
            fx(p, ScarletFxPacket.LINK, p.getEyePosition(), mob.getEyePosition(), 1.0F);
            ScarletPowers.play(p.serverLevel(), mob.position(), "telecinese_agarrar", SoundEvents.EVOKER_PREPARE_WOLOLO,
                    1.0F);
            return;
        }
        if (!ready(p, "wave")) {
            return;
        }
        // the red wave: nothing broken, every creature around hers
        ServerLevel level = p.serverLevel();
        double r = 16.0D;
        fx(p, ScarletFxPacket.WAVE, p.position(), p.position(), (float) r);
        ScarletPowers.play(level, p.position(), "realidade_onda", SoundEvents.GENERIC_EXPLODE, 0.6F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= r * r)) {
            Vec3 away = e.position().subtract(p.position());
            double d = Math.max(1.0D, away.length());
            e.setDeltaMovement(e.getDeltaMovement().add(away.normalize().scale(1.2D / Math.sqrt(d)))
                    .add(0.0D, 0.25D, 0.0D));
            e.hurtMarked = true;
            if (e instanceof Mob mob && !immune(mob)) {
                ScarletThralls.ally(p, mob, 60 * 20, false);
            } else if (e instanceof ServerPlayer other) {
                other.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
                other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            }
        }
    }

    private static void mindVariant(ServerPlayer p, Entity aimed) {
        if (aimed instanceof ServerPlayer target) {
            if (!ready(p, "puppet")) {
                return;
            }
            ScarletThralls.puppet(p, target, 8 * 20);
            fx(p, ScarletFxPacket.LINK, p.getEyePosition(), target.getEyePosition(), 1.0F);
            ScarletPowers.play(p.serverLevel(), target.position(), "telecinese_segurar", SoundEvents.EVOKER_CAST_SPELL,
                    0.8F);
            return;
        }
        if (aimed instanceof Mob mob && !immune(mob)) {
            if (!ready(p, "fury")) {
                return;
            }
            ScarletThralls.ally(p, mob, 20 * 20, true);
            fx(p, ScarletFxPacket.LINK, p.getEyePosition(), mob.getEyePosition(), 1.0F);
            ScarletPowers.play(p.serverLevel(), mob.position(), "telecinese_tensao", SoundEvents.EVOKER_CAST_SPELL,
                    0.7F);
            return;
        }
        if (!ready(p, "hysteria")) {
            return;
        }
        ServerLevel level = p.serverLevel();
        fx(p, ScarletFxPacket.WAVE, p.position(), p.position(), 20.0F);
        ScarletPowers.play(level, p.position(), "realidade_onda", SoundEvents.EVOKER_CAST_SPELL, 0.5F);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20.0D),
                m -> m instanceof net.minecraft.world.entity.monster.Enemy && !immune(m))) {
            ScarletThralls.ally(p, mob, 20 * 20, true);
        }
    }

    /** His inventory, to look at only (nothing can be taken). */
    private static void readMind(ServerPlayer p, ServerPlayer target) {
        SimpleContainer copy = new SimpleContainer(45);
        var inv = target.getInventory();
        for (int i = 0; i < 36; i++) {
            copy.setItem(i < 9 ? 36 + i : i - 9, inv.getItem(i).copy());
        }
        for (int i = 0; i < 4; i++) {
            copy.setItem(27 + i, inv.armor.get(3 - i).copy());
        }
        copy.setItem(31, inv.offhand.get(0).copy());
        p.openMenu(new SimpleMenuProvider((id, own, who) -> new ChestMenu(MenuType.GENERIC_9x5, id, own, copy, 5) {
            @Override
            public void clicked(int slot, int button, ClickType type, Player player) {
                // only to look at
            }

            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, Component.translatable("gui.flash.scarlet.mind_of", target.getDisplayName())));
    }

    // ------------------------------------------------------------------
    // Energy Projection
    // ------------------------------------------------------------------

    /** What the line from her eyes hits (a creature, or the block). */
    private static HitResult aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                p));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                e -> e instanceof LivingEntity && e.isAlive() && e != p && !ScarletThralls.isAllyOf(e, p.getUUID()),
                eye.distanceToSqr(end));
        return ent != null ? ent : block;
    }

    private static void bolt(ServerPlayer p, boolean beam) {
        if (beam) {
            // the beam: a hit every 3 ticks at most, 4 s on end, then it cools down
            if (cooling(p, "beam") || cooling(p, "beam_tick")) {
                return;
            }
            COOLDOWNS.put(p.getUUID() + "beam_tick", p.level().getGameTime() + 2L);
            long now = p.level().getGameTime();
            long[] streak = BEAM.computeIfAbsent(p.getUUID(), k -> new long[] {now, now});
            if (now - streak[1] > 10L) {
                streak[0] = now;
            }
            streak[1] = now;
            if (now - streak[0] > 80L) {
                start(p, "beam", ScarletCooldowns.ticks("beam"));
                BEAM.remove(p.getUUID());
                return;
            }
        } else if (!ready(p, "bolt")) {
            return;
        }
        HitResult hit = aim(p, RANGE);
        Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.6D)).add(0.0D, -0.25D, 0.0D);
        Vec3 to = hit.getLocation();
        fx(p, beam ? ScarletFxPacket.BEAM : ScarletFxPacket.BOLT, from, to, 1.0F);
        if (!beam || p.tickCount % 8 == 0) {
            ScarletPowers.play(p.serverLevel(), p.position(), beam ? "telecinese_cauda" : "energia_pulso",
                    SoundEvents.BLAZE_SHOOT, beam ? 1.6F : 1.2F);
        }
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().indirectMagic(p, p), (beam ? 1.2F : 5.0F) * ScarletPowers.POWER);
            Vec3 push = p.getLookAngle().scale(beam ? 0.25D : 1.1D);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, beam ? 0.02D : 0.25D, push.z));
            e.hurtMarked = true;
        }
    }

    private static void blast(ServerPlayer p, float charge) {
        if (!ready(p, "blast")) {
            return;
        }
        HitResult hit = aim(p, RANGE);
        Vec3 at = hit.getLocation();
        float radius = 2.0F + 4.0F * charge;
        float damage = (10.0F + 20.0F * charge) * ScarletPowers.POWER;
        ServerLevel level = p.serverLevel();
        fx(p, ScarletFxPacket.BLAST, p.getEyePosition().add(0.0D, -0.25D, 0.0D), at, radius);
        ScarletPowers.play(level, at, "energia_impacto", SoundEvents.GENERIC_EXPLODE, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e != p && e.isAlive() && !ScarletThralls.isAllyOf(e, p.getUUID()))) {
            double d = Math.max(0.5D, e.position().add(0.0D, e.getBbHeight() / 2.0D, 0.0D).distanceTo(at));
            if (d > radius + 1.0D) {
                continue;
            }
            float f = (float) (1.0D - Math.min(1.0D, d / (radius + 1.0D)) * 0.6D);
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().indirectMagic(p, p), damage * f);
            Vec3 away = e.position().subtract(at).normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(away.scale(1.5D * f + charge)).add(0.0D, 0.5D * f, 0.0D));
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------
    // Necromancy and Resurrection
    // ------------------------------------------------------------------

    private static void heal(ServerPlayer p, Entity aimed) {
        LivingEntity t = aimed instanceof LivingEntity l ? l : p;
        if (!ready(p, "heal")) {
            p.displayClientMessage(Component.translatable("message.flash.scarlet_heal_wait"), true);
            return;
        }
        t.setHealth(t.getMaxHealth());
        t.clearFire();
        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance e : t.getActiveEffects()) {
            if (!e.getEffect().isBeneficial()) {
                bad.add(e);
            }
        }
        for (MobEffectInstance e : bad) {
            t.removeEffect(e.getEffect());
        }
        t.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        t.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1));
        if (t instanceof ServerPlayer sp) {
            sp.getFoodData().eat(20, 1.0F);
        }
        fx(p, ScarletFxPacket.HEAL, p.getEyePosition(), t.position(), t.getBbHeight());
        ScarletPowers.play(p.serverLevel(), t.position(), "lua_volta", SoundEvents.ZOMBIE_VILLAGER_CURE, 1.4F);
    }

    /** What died lately, to be brought back. */
    private record Death(ResourceKey<Level> dim, Vec3 at, long time, CompoundTag mob, UUID player) {
    }

    private static final Deque<Death> DEATHS = new ArrayDeque<>();
    private static final long MEMORY = 5L * 60L * 20L;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || immune(e) || SPARED.contains(e.getUUID())) {
            return;
        }
        long now = e.level().getGameTime();
        if (e instanceof ServerPlayer sp) {
            DEATHS.addFirst(new Death(e.level().dimension(), e.position(), now, null, sp.getUUID()));
        } else if (e instanceof Mob) {
            CompoundTag tag = new CompoundTag();
            if (e.saveAsPassenger(tag)) {
                tag.remove("Passengers");
                DEATHS.addFirst(new Death(e.level().dimension(), e.position(), now, tag, null));
            }
        }
        while (DEATHS.size() > 96) {
            DEATHS.removeLast();
        }
    }

    private static void raise(ServerPlayer p) {
        if (!ready(p, "raise")) {
            p.displayClientMessage(Component.translatable("message.flash.scarlet_raise_wait"), true);
            return;
        }
        ServerLevel level = p.serverLevel();
        HitResult hit = aim(p, 32.0D);
        Vec3 center = hit.getType() == HitResult.Type.MISS ? p.position() : hit.getLocation();
        long now = level.getGameTime();
        int count = 0;
        for (Iterator<Death> it = DEATHS.iterator(); it.hasNext(); ) {
            Death d = it.next();
            if (now - d.time() > MEMORY) {
                it.remove();
                continue;
            }
            if (d.dim() != level.dimension() || d.at().distanceTo(center) > 14.0D) {
                continue;
            }
            if (d.mob() != null) {
                Entity made = EntityType.loadEntityRecursive(d.mob(), level, e -> {
                    e.moveTo(d.at().x, d.at().y, d.at().z, e.getYRot(), e.getXRot());
                    return e;
                });
                if (made instanceof Mob mob && level.getEntity(mob.getUUID()) == null) {
                    mob.setHealth(mob.getMaxHealth());
                    mob.deathTime = 0;
                    mob.clearFire();
                    level.addFreshEntity(mob);
                    ScarletThralls.ally(p, mob, 5 * 60 * 20, false);
                    fx(p, ScarletFxPacket.RAISE, d.at(), d.at(), mob.getBbHeight());
                    it.remove();
                    count++;
                }
            } else {
                ServerPlayer dead = p.server.getPlayerList().getPlayer(d.player());
                // dead and still on the death screen (his body leaves the world a second after he falls)
                if (dead != null && dead != p && dead.getHealth() <= 0.0F) {
                    // his own game respawns him (as the button would: no body left apart from him), and the
                    // respawn puts him back where he fell (onRespawn)
                    RAISING.put(dead.getUUID(), new Raising(level.dimension(), d.at(), p.getDisplayName(),
                            level.getGameTime() + 600L));
                    com.flash.network.FlashNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> dead),
                            new com.flash.network.ScarletRaisedPacket(0));
                    fx(p, ScarletFxPacket.RAISE, d.at(), d.at(), 1.8F);
                    it.remove();
                    count++;
                }
            }
        }
        ScarletPowers.play(level, center, "lua_volta", SoundEvents.ZOMBIE_VILLAGER_CURE, 0.6F);
        fx(p, ScarletFxPacket.WAVE, center, center, 6.0F);
        p.displayClientMessage(Component.translatable(count > 0 ? "message.flash.scarlet_raise"
                : "message.flash.scarlet_raise_none", count), true);
    }

    private record Raising(ResourceKey<Level> dim, Vec3 at, Component by, long until) {
    }

    /** Players the Scarlet Witch is raising: on their respawn, back where they fell. */
    private static final Map<UUID, Raising> RAISING = new HashMap<>();
    /** Raised players to put in place a couple of ticks after the respawn (game time due). */
    private static final Map<UUID, Long> PLACE = new HashMap<>();

    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer back
                && RAISING.containsKey(back.getUUID())) {
            PLACE.put(back.getUUID(), back.serverLevel().getGameTime() + 2L);
        }
    }

    @SubscribeEvent
    public static void onRaiseTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RAISING.isEmpty() && PLACE.isEmpty()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        PLACE.entrySet().removeIf(en -> {
            if (now < en.getValue()) {
                return false;
            }
            Raising r = RAISING.remove(en.getKey());
            ServerPlayer back = server.getPlayerList().getPlayer(en.getKey());
            ServerLevel to = r != null ? server.getLevel(r.dim()) : null;
            if (back != null && to != null && back.isAlive()) {
                back.teleportTo(to, r.at().x, r.at().y, r.at().z, back.getYRot(), back.getXRot());
                back.setHealth(back.getMaxHealth());
                back.fallDistance = 0.0F;
                back.displayClientMessage(Component.translatable("message.flash.scarlet_raised", r.by()), false);
            }
            return true;
        });
        RAISING.entrySet().removeIf(en -> now > en.getValue().until() && !PLACE.containsKey(en.getKey()));
    }

    // ------------------------------------------------------------------
    // The dismemberment of five seconds (and no implosion)
    // ------------------------------------------------------------------

    private record Torn(ResourceKey<Level> dim, CompoundTag tag, Vec3 at, long back, UUID player) {
    }

    private static final List<Torn> TORN = new ArrayList<>();
    /** Players torn apart now: they do not die of it. */
    private static final Set<UUID> SPARED = new HashSet<>();
    /** What the Scarlet Witch holds by telekinesis: target UUID -> game time last seen held. */
    private static final Map<UUID, Long> HELD = new HashMap<>();
    private static Method open;
    private static java.lang.reflect.Field holds;
    private static java.lang.reflect.Field owner;
    private static java.lang.reflect.Field target;
    private static java.lang.reflect.Field phase;
    private static java.lang.reflect.Field phaseTicks;
    private static Object holding;
    private static Object imploding;
    private static boolean looked;

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                Class<?> t = Class.forName("handofgod.telecinese.Telecinese");
                holds = t.getDeclaredField("PEGADAS");
                holds.setAccessible(true);
                Class<?> pegada = Class.forName("handofgod.telecinese.Telecinese$Pegada");
                owner = pegada.getDeclaredField("dono");
                owner.setAccessible(true);
                target = pegada.getDeclaredField("alvo");
                target.setAccessible(true);
                phase = pegada.getDeclaredField("fase");
                phase.setAccessible(true);
                phaseTicks = pegada.getDeclaredField("ticksFase");
                phaseTicks.setAccessible(true);
                Class<?> fase = Class.forName("handofgod.telecinese.Telecinese$Fase");
                for (Object o : fase.getEnumConstants()) {
                    if ("SEGURANDO".equals(o.toString())) {
                        holding = o;
                    } else if ("IMPLODINDO".equals(o.toString())) {
                        imploding = o;
                    }
                }
                open = t.getDeclaredMethod("abrir", ServerPlayer.class);
                open.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError e) {
                holds = null;
            }
        }
        return holds != null;
    }

    /** Her R-click on what she holds: Hand of God's tearing apart (its pieces), and whole again in 5 s. */
    private static void dismember(ServerPlayer p) {
        LivingEntity e = com.flash.yas.YasTelekinesis.held(p);
        if (e == null || !ready() || !ready(p, "dismember")) {
            return;
        }
        HELD.put(e.getUUID(), p.level().getGameTime());
        if (e instanceof ServerPlayer victim) {
            SPARED.add(victim.getUUID());
        }
        Object drops = null;
        boolean before = false;
        try {
            drops = Class.forName("handofgod.Config").getField("TK_DROPS_DESMEMBRAR").get(null);
            before = drops instanceof net.minecraftforge.common.ForgeConfigSpec.BooleanValue b && b.get();
            if (before) {
                ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue) drops).set(false);
            }
            open.invoke(null, p);
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException ex) {
            // Hand of God changed: nothing torn
        } finally {
            if (before) {
                ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue) drops).set(true);
            }
        }
    }

    /**
     * Every tick, what she holds: an implosion is turned back into a plain hold (she cannot crush), and the targets
     * are remembered, so that whatever Hand of God does to them (torn apart by its own click too) is undone.
     */
    @SubscribeEvent
    public static void onGuardTick(TickEvent.ServerTickEvent event) {
        if (!ready()) {
            return;
        }
        Map<?, ?> all;
        try {
            all = (Map<?, ?>) holds.get(null);
        } catch (ReflectiveOperationException e) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        for (Object hold : all.values()) {
            try {
                if (!(owner.get(hold) instanceof ServerPlayer p) || !ScarletPowers.has(p)) {
                    continue;
                }
                if (target.get(hold) instanceof LivingEntity e) {
                    HELD.put(e.getUUID(), now);
                }
                if (phase.get(hold) == imploding && holding != null) {
                    phase.set(hold, holding);
                    phaseTicks.setInt(hold, 0);
                }
            } catch (ReflectiveOperationException ignored) {
                // next
            }
        }
        HELD.values().removeIf(t -> now - t > 40L);
    }

    private static boolean heldByHer(Entity e) {
        return HELD.containsKey(e.getUUID());
    }

    /** A creature she held was taken away (torn apart): it comes back whole in five seconds, its drops gone. */
    @SubscribeEvent
    public static void onLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof LivingEntity) || e instanceof Player || !(event.getLevel() instanceof ServerLevel level)
                || !heldByHer(e)) {
            return;
        }
        Entity.RemovalReason why = e.getRemovalReason();
        if (why != Entity.RemovalReason.DISCARDED && why != Entity.RemovalReason.KILLED) {
            return;
        }
        HELD.remove(e.getUUID());
        CompoundTag tag = new CompoundTag();
        e.saveWithoutId(tag);
        String type = EntityType.getKey(e.getType()).toString();
        tag.putString("id", type);
        tag.remove("Passengers");
        tag.remove("DeathTime");
        tag.putShort("HurtTime", (short) 0);
        TORN.add(new Torn(level.dimension(), tag, e.position(), level.getGameTime() + 100L, null));
        // what it dropped right now goes back with it
        for (Entity drop : level.getEntities(e, e.getBoundingBox().inflate(2.5D),
                d -> (d instanceof net.minecraft.world.entity.item.ItemEntity
                        || d instanceof net.minecraft.world.entity.ExperienceOrb) && d.tickCount <= 1)) {
            drop.discard();
        }
    }

    /** A player she holds does not die of it: torn apart for five seconds instead (Hand of God kills what it tears). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof ServerPlayer victim) || !event.getSource().is(
                net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (SPARED.contains(victim.getUUID()) || heldByHer(victim)) {
            event.setCanceled(true);
            tearPlayer(victim);
        }
    }

    private static void tearPlayer(ServerPlayer victim) {
        if (TORN.stream().anyMatch(t -> victim.getUUID().equals(t.player()))) {
            return;
        }
        SPARED.add(victim.getUUID());
        HELD.remove(victim.getUUID());
        victim.setHealth(Math.max(1.0F, victim.getHealth()));
        victim.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 255, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 255, false, false));
        TORN.add(new Torn(victim.level().dimension(), null, victim.position(), victim.level().getGameTime() + 100L,
                victim.getUUID()));
        torn(victim, 100);
    }

    /** Tells the players around (and him) that he is torn apart (ticks) or whole again (0). */
    private static void torn(ServerPlayer victim, int ticks) {
        com.flash.network.FlashNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim),
                new com.flash.network.ScarletTornPacket(victim.getId(), ticks));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSparedDeath(LivingDeathEvent event) {
        if (SPARED.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
            event.getEntity().setHealth(1.0F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TORN.isEmpty()) {
            return;
        }
        var server = event.getServer();
        for (Iterator<Torn> it = new ArrayList<>(TORN).iterator(); it.hasNext(); ) {
            Torn t = it.next();
            ServerLevel level = server.getLevel(t.dim());
            if (level == null) {
                TORN.remove(t);
                continue;
            }
            if (level.getGameTime() < t.back()) {
                ServerPlayer v = t.player() != null ? server.getPlayerList().getPlayer(t.player()) : null;
                if (v != null && v.level() == level && v.position().distanceToSqr(t.at()) > 0.04D) {
                    // torn apart, he stays where his pieces are
                    v.teleportTo(level, t.at().x, t.at().y, t.at().z, v.getYRot(), v.getXRot());
                    v.setDeltaMovement(Vec3.ZERO);
                }
                if (level.getGameTime() % 4 == 0) {
                    level.sendParticles(ScarletPowers.RED, t.at().x, t.at().y + 0.8D, t.at().z, 3, 0.4D, 0.5D, 0.4D,
                            0.0D);
                }
                continue;
            }
            TORN.remove(t);
            // whole again
            if (t.player() != null) {
                SPARED.remove(t.player());
                ServerPlayer v = server.getPlayerList().getPlayer(t.player());
                if (v != null) {
                    v.removeEffect(MobEffects.INVISIBILITY);
                    v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    v.removeEffect(MobEffects.BLINDNESS);
                    v.removeEffect(MobEffects.WEAKNESS);
                    torn(v, 0);
                }
            } else {
                Entity made = EntityType.loadEntityRecursive(t.tag(), level, e -> {
                    e.moveTo(t.at().x, t.at().y, t.at().z, e.getYRot(), e.getXRot());
                    return e;
                });
                if (made instanceof LivingEntity le && level.getEntity(le.getUUID()) == null) {
                    le.setHealth(le.getMaxHealth());
                    le.deathTime = 0;
                    le.setDeltaMovement(Vec3.ZERO);
                    level.addFreshEntity(le);
                }
            }
            level.sendParticles(ScarletPowers.energy(), t.at().x, t.at().y + 0.9D, t.at().z, 40, 0.4D, 0.6D, 0.4D,
                    0.05D);
            level.sendParticles(ScarletPowers.RED, t.at().x, t.at().y + 0.9D, t.at().z, 30, 0.4D, 0.6D, 0.4D, 0.0D);
            ScarletPowers.play(level, t.at(), "poeira_volta", SoundEvents.ZOMBIE_VILLAGER_CURE, 1.6F);
        }
    }
}

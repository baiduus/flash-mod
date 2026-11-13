package com.flash.yas;

import com.flash.FlashMod;
import com.flash.entity.HumanNpc;
import com.flash.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Manipulation of Reality on a creature (R looking at it, in the mode of Reality): it is held still for a moment
 * in Hand of God's energy, with its sound of reality, and becomes another. A skeleton gets its flesh back and a
 * zombie its life: a plain human (HumanNpc, Steve or Alex); a pig turns zombified piglin and back, and so on for
 * all the creatures of the game (TABLE; what is not in it becomes an animal of the farm). The players, the bosses
 * and what is not a mob are not changed.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasReality {

    private static final int CHARGE = 30;
    private static final double RANGE = 64.0D;
    private static final Map<EntityType<?>, EntityType<?>> TABLE = new HashMap<>();
    private static final List<Change> CHANGES = new ArrayList<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private static final class Change {
        final Mob e;
        final UUID owner;
        int ticks;

        Change(Mob e, UUID owner) {
            this.e = e;
            this.owner = owner;
        }
    }

    private YasReality() {
    }

    private static void pair(EntityType<?> a, EntityType<?> b) {
        TABLE.put(a, b);
        TABLE.put(b, a);
    }

    private static void one(EntityType<?> a, EntityType<?> b) {
        TABLE.put(a, b);
    }

    private static Map<EntityType<?>, EntityType<?>> table() {
        if (TABLE.isEmpty()) {
            pair(EntityType.PIG, EntityType.ZOMBIFIED_PIGLIN);
            one(EntityType.PIGLIN, EntityType.PIG);
            one(EntityType.PIGLIN_BRUTE, EntityType.PIGLIN);
            pair(EntityType.HOGLIN, EntityType.ZOGLIN);
            pair(EntityType.COW, EntityType.MOOSHROOM);
            pair(EntityType.SHEEP, EntityType.GOAT);
            pair(EntityType.CHICKEN, EntityType.PARROT);
            pair(EntityType.HORSE, EntityType.ZOMBIE_HORSE);
            one(EntityType.SKELETON_HORSE, EntityType.HORSE);
            pair(EntityType.DONKEY, EntityType.MULE);
            pair(EntityType.LLAMA, EntityType.TRADER_LLAMA);
            one(EntityType.CAMEL, EntityType.HORSE);
            pair(EntityType.CAT, EntityType.OCELOT);
            pair(EntityType.WOLF, EntityType.FOX);
            pair(EntityType.POLAR_BEAR, EntityType.PANDA);
            pair(EntityType.RABBIT, EntityType.FROG);
            one(EntityType.TADPOLE, EntityType.FROG);
            pair(EntityType.SQUID, EntityType.GLOW_SQUID);
            pair(EntityType.COD, EntityType.SALMON);
            pair(EntityType.TROPICAL_FISH, EntityType.PUFFERFISH);
            pair(EntityType.AXOLOTL, EntityType.TURTLE);
            one(EntityType.DOLPHIN, EntityType.SQUID);
            pair(EntityType.BAT, EntityType.PHANTOM);
            pair(EntityType.ALLAY, EntityType.VEX);
            pair(EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM);
            pair(EntityType.SLIME, EntityType.MAGMA_CUBE);
            pair(EntityType.SPIDER, EntityType.CAVE_SPIDER);
            pair(EntityType.SILVERFISH, EntityType.ENDERMITE);
            pair(EntityType.BLAZE, EntityType.STRIDER);
            one(EntityType.GHAST, EntityType.BAT);
            one(EntityType.ENDERMAN, EntityType.ENDERMITE);
            one(EntityType.CREEPER, EntityType.CAT);
            one(EntityType.GUARDIAN, EntityType.PUFFERFISH);
            one(EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN);
            one(EntityType.SHULKER, EntityType.ENDERMITE);
            one(EntityType.RAVAGER, EntityType.COW);
            one(EntityType.WITCH, EntityType.VILLAGER);
            one(EntityType.VILLAGER, EntityType.WITCH);
            one(EntityType.WANDERING_TRADER, EntityType.VILLAGER);
            one(EntityType.ZOMBIE_VILLAGER, EntityType.VILLAGER);
            one(EntityType.PILLAGER, EntityType.VILLAGER);
            one(EntityType.VINDICATOR, EntityType.VILLAGER);
            one(EntityType.EVOKER, EntityType.VILLAGER);
            one(EntityType.ILLUSIONER, EntityType.VILLAGER);
            one(EntityType.BEE, EntityType.ALLAY);
            one(EntityType.SNIFFER, EntityType.COW);
            one(EntityType.WARDEN, EntityType.IRON_GOLEM);
        }
        return TABLE;
    }

    /** What it becomes ({@code null}: Reality does not touch it). */
    private static EntityType<?> into(Mob e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.ENDER_DRAGON || t == EntityType.WITHER || e instanceof com.flash.yas.YasGuardian) {
            return null;
        }
        EntityType<?> to = table().get(t);
        if (to != null) {
            return to;
        }
        if (bones(e) || rot(e)) {
            return ModEntities.HUMAN.get();
        }
        if (e instanceof HumanNpc) {
            return e.getRandom().nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON;
        }
        // the rest: an animal of the farm
        EntityType<?>[] farm = {EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN};
        return farm[e.getRandom().nextInt(farm.length)];
    }

    private static boolean bones(Entity e) {
        EntityType<?> t = e.getType();
        return t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.WITHER_SKELETON;
    }

    private static boolean rot(Entity e) {
        EntityType<?> t = e.getType();
        return t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED;
    }

    /** R on a creature: the change starts (a second of charge). */
    public static void start(ServerPlayer p, int id) {
        if (!(YasPowers.has(p) || com.flash.scarlet.ScarletPowers.has(p))) {
            return;
        }
        Entity target = p.level().getEntity(id);
        if (!(target instanceof Mob mob) || !mob.isAlive() || mob.distanceToSqr(p) > RANGE * RANGE
                || into(mob) == null) {
            return;
        }
        long now = p.level().getGameTime();
        Long until = COOLDOWN.get(p.getUUID());
        if (until != null && now < until) {
            return;
        }
        if (com.flash.scarlet.ScarletPowers.has(p) && !com.flash.scarlet.ScarletAbilities.ready(p, "reality_mob")) {
            return;
        }
        for (Change c : CHANGES) {
            if (c.e == mob) {
                return;
            }
        }
        COOLDOWN.put(p.getUUID(), now + 20L);
        CHANGES.add(new Change(mob, p.getUUID()));
        play(p.serverLevel(), mob.position(), "realidade_carga", SoundEvents.BEACON_POWER_SELECT, 1.2F);
        com.flash.superman.SupermanAbilities.anim(p, "none");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHANGES.isEmpty()) {
            return;
        }
        for (Iterator<Change> it = CHANGES.iterator(); it.hasNext(); ) {
            Change c = it.next();
            Mob e = c.e;
            if (!e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            c.ticks++;
            // held in the air, wrapped in energy
            e.setDeltaMovement(0.0D, c.ticks < 10 ? 0.04D : 0.0D, 0.0D);
            e.hurtMarked = true;
            e.getNavigation().stop();
            double r = e.getBbWidth() * 0.8D + 0.4D;
            for (int i = 0; i < 6; i++) {
                double a = (c.ticks * 0.35D) + i * Math.PI / 3.0D;
                double h = e.getBbHeight() * ((c.ticks + i * 5) % CHARGE) / CHARGE;
                level.sendParticles(energy(), e.getX() + Math.cos(a) * r, e.getY() + h, e.getZ() + Math.sin(a) * r,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            if (c.ticks % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() / 2.0D, e.getZ(), 3,
                        r * 0.5D, e.getBbHeight() * 0.4D, r * 0.5D, 0.01D);
            }
            if (c.ticks < CHARGE) {
                continue;
            }
            it.remove();
            change(level, e);
        }
    }

    private static void change(ServerLevel level, Mob e) {
        EntityType<?> to = into(e);
        if (to == null) {
            return;
        }
        Vec3 at = e.position();
        boolean bones = bones(e);
        boolean rot = rot(e);
        @SuppressWarnings("unchecked")
        Mob made = e.convertTo((EntityType<? extends Mob>) to, false);
        if (made == null) {
            return;
        }
        made.setHealth(made.getMaxHealth());
        if (made instanceof HumanNpc human) {
            human.setAlex(level.random.nextBoolean());
            if (bones || rot) {
                human.grow(bones ? 1 : 2);
            }
        }
        // the wave of reality
        play(level, at, "realidade_onda", SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F);
        level.sendParticles(energy(), at.x, at.y + made.getBbHeight() / 2.0D, at.z, 60, made.getBbWidth(),
                made.getBbHeight() / 2.0D, made.getBbWidth(), 0.15D);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + made.getBbHeight() / 2.0D, at.z, 1, 0.0D, 0.0D, 0.0D,
                0.0D);
        if (bones) {
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y + 1.0D, at.z, 50, 0.4D, 0.8D, 0.4D, 0.02D);
        }
    }

    private static ParticleOptions energy() {
        var t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("hand_of_god", "energia"));
        return t instanceof ParticleOptions o ? o : ParticleTypes.END_ROD;
    }

    static void play(ServerLevel level, Vec3 at, String hog, SoundEvent fallback, float pitch) {
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("hand_of_god", hog));
        level.playSound(null, at.x, at.y, at.z, s != null ? s : fallback, SoundSource.PLAYERS, 1.5F,
                s != null ? 1.0F : pitch);
    }

    /** Players are not changed. */
    public static boolean canChange(Entity e) {
        return e instanceof Mob && !(e instanceof Player);
    }

    static boolean isLiving(Entity e) {
        return e instanceof LivingEntity;
    }
}

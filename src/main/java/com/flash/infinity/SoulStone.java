package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.infinity.entity.StoneClone;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Soul Stone: souls (one for each thing its holder kills, up to 50: they heal, they buy back a drained soul, they
 * raise zombies), draining a soul, immortality, the effects it refuses, finding anyone by name, the Soulworld.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SoulStone {

    public static final String DRAINED = "flash_soul_drained";
    public static final String SOULS = "SoulStorage";
    public static final int MAX_SOULS = 50;

    private SoulStone() {
    }

    public static boolean drained(Entity e) {
        return e.getPersistentData().getBoolean(DRAINED);
    }

    static boolean has(ServerPlayer p, String type) {
        return MindStone.has(p, type);
    }

    public static int souls(ServerPlayer p) {
        return InfinityServer.score(p, SOULS);
    }

    static void addSouls(ServerPlayer p, int n) {
        InfinityServer.setScore(p, SOULS, Math.max(0, Math.min(MAX_SOULS, souls(p) + n)));
    }

    /** Every kill while the storage is on: a soul (not clones nor the already drained). */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && has(p, "flash:soul_storage")
                && !(event.getEntity() instanceof StoneClone) && !drained(event.getEntity())) {
            addSouls(p, 1);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.SOUL, event.getEntity().getX(),
                    event.getEntity().getY() + 1.0D, event.getEntity().getZ(), 8, 0.3D, 0.5D, 0.3D, 0.05D);
        }
        // immortality: it does not die
        if (event.getEntity() instanceof ServerPlayer p && has(p, "palladium:immortality")) {
            event.setCanceled(true);
            p.setHealth(1.0F);
        }
    }

    static void heal(ServerPlayer p, InfPowers.Ability a) {
        int cost = a.json.has("cost") ? a.json.get("cost").getAsInt() : 1;
        float amount = a.json.has("heal") ? a.json.get("heal").getAsFloat() : 4.0F;
        if (p.getHealth() >= p.getMaxHealth()) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.soul_full"), true);
            return;
        }
        if (souls(p) < cost) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.soul_none"), true);
            return;
        }
        addSouls(p, -cost);
        p.heal(amount);
        SpaceStone.sound(p.level(), p.position(), "soul_stone", 0.7F, 1.4F);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.0D, p.getZ(), 15, 0.3D,
                0.5D, 0.3D, 0.05D);
    }

    static void drain(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(16.0D));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(
                end.subtract(eye)).inflate(1.0D), e -> e instanceof Mob, eye.distanceToSqr(end));
        if (eh == null || !(eh.getEntity() instanceof Mob m) || m instanceof StoneClone
                || m.getType().is(TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                new ResourceLocation("forge", "bosses"))) || InfinityImmunity.immune(m, "soul")) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (p.isShiftKeyDown()) {
            if (!drained(m) || souls(p) < 1) {
                return;
            }
            addSouls(p, -1);
            m.getPersistentData().remove(DRAINED);
            m.setNoAi(false);
        } else {
            if (drained(m) || m.isNoAi()) {
                return;
            }
            m.getPersistentData().putBoolean(DRAINED, true);
            m.setNoAi(true);
            addSouls(p, 1);
        }
        SpaceStone.sound(level, m.position(), "soul_stone", 1.0F, p.isShiftKeyDown() ? 1.4F : 0.6F);
        Vec3 a = m.position().add(0.0D, m.getBbHeight() * 0.5D, 0.0D);
        Vec3 b = p.position().add(0.0D, 1.0D, 0.0D);
        for (int i = 0; i <= 12; i++) {
            Vec3 at = p.isShiftKeyDown() ? b.lerp(a, i / 12.0D) : a.lerp(b, i / 12.0D);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    /** /infinity soul zombies: up to 6, each one a soul. */
    public static int zombies(ServerPlayer p) {
        int n = Math.min(6, souls(p));
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < n; i++) {
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z == null) {
                continue;
            }
            double ang = i * Math.PI / 3.0D;
            z.moveTo(p.getX() + Math.cos(ang) * 2.0D, p.getY(), p.getZ() + Math.sin(ang) * 2.0D, p.getYRot(), 0.0F);
            z.finalizeSpawn(level, level.getCurrentDifficultyAt(z.blockPosition()), MobSpawnType.MOB_SUMMONED, null,
                    null);
            z.setPersistenceRequired();
            z.getPersistentData().putUUID("flash_summoner", p.getUUID());
            level.addFreshEntity(z);
            level.sendParticles(ParticleTypes.SOUL, z.getX(), z.getY() + 1.0D, z.getZ(), 10, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        addSouls(p, -n);
        return n;
    }

    /** The summoned zombies don't go for their summoner. */
    @SubscribeEvent
    public static void onTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player pl && event.getEntity().getPersistentData().hasUUID(
                "flash_summoner") && pl.getUUID().equals(event.getEntity().getPersistentData().getUUID(
                "flash_summoner"))) {
            event.setCanceled(true);
        }
    }

    /** The online player of that name, else the closest entity with that name (not soul-immune). */
    public static LivingEntity locate(ServerPlayer p, String name) {
        ServerPlayer target = p.server.getPlayerList().getPlayerByName(name);
        if (target != null && !InfinityImmunity.immune(target, "soul")) {
            return target;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ServerLevel level : p.server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof LivingEntity l && e.hasCustomName() && name.equals(e.getCustomName().getString())
                        && !InfinityImmunity.immune(e, "soul")) {
                    double d = e.level() == p.level() ? e.distanceToSqr(p) : 1.0E12D;
                    if (d < bd) {
                        bd = d;
                        best = l;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Effects refused
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        for (String key : InfinityServer.state(p).enabled) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a != null && "flash:effect_immunity".equals(a.type)) {
                ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(event.getEffectInstance().getEffect());
                if (id != null && InfinityPowers.strings(a.json.get("effects")).contains(id.toString())) {
                    event.setResult(Event.Result.DENY);
                    return;
                }
            }
        }
    }

    static void effectImmunity(ServerPlayer p, InfPowers.Ability a, boolean start) {
        if (!start) {
            return;
        }
        for (String id : InfinityPowers.strings(a.json.get("effects"))) {
            MobEffect e = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(id));
            if (e != null) {
                p.removeEffect(e);
            }
        }
    }

    // ------------------------------------------------------------------
    // The Soulworld (the original's pocket dimension)
    // ------------------------------------------------------------------

    public static final ResourceKey<Level> SOULWORLD = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(FlashMod.MODID, "soulworld"));

    static void enter(ServerPlayer p, InfPowers.Ability a) {
        ServerLevel to = p.server.getLevel(SOULWORLD);
        if (to == null || p.level() == to) {
            return;
        }
        double range = a.json.has("range") ? a.json.get("range").getAsDouble() : 5.0D;
        List<Entity> taken = p.serverLevel().getEntities(p, p.getBoundingBox().inflate(range), e -> e instanceof
                LivingEntity && !InfinityImmunity.immune(e, "soul") && !e.isPassenger());
        send(p, to);
        for (Entity e : taken) {
            send(e, to);
        }
        p.addTag("in_soul");
        SpaceStone.sound(to, p.position(), "soul_stone", 1.0F, 0.8F);
    }

    static void exit(ServerPlayer p, InfPowers.Ability a) {
        if (p.level().dimension() != SOULWORLD) {
            return;
        }
        double range = a.json.has("range") ? a.json.get("range").getAsDouble() : 5.0D;
        List<Entity> taken = p.serverLevel().getEntities(p, p.getBoundingBox().inflate(range), e -> e instanceof
                LivingEntity && !e.isPassenger());
        back(p);
        for (Entity e : taken) {
            back(e);
        }
        p.removeTag("in_soul");
    }

    private static void send(Entity e, ServerLevel to) {
        CompoundTag from = new CompoundTag();
        from.putString("Dim", e.level().dimension().location().toString());
        from.putDouble("X", e.getX());
        from.putDouble("Y", e.getY());
        from.putDouble("Z", e.getZ());
        e.getPersistentData().put("flash_soul_from", from);
        teleport(e, to, new Vec3(e.getX(), Math.max(to.getMinBuildHeight() + 70, 100), e.getZ()), true);
    }

    private static void back(Entity e) {
        CompoundTag from = e.getPersistentData().getCompound("flash_soul_from");
        ServerLevel to = e.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(from.contains("Dim") ? from.getString("Dim") : "minecraft:overworld")));
        if (to == null) {
            to = e.getServer().overworld();
        }
        Vec3 at = from.contains("X") ? new Vec3(from.getDouble("X"), from.getDouble("Y"), from.getDouble("Z"))
                : new Vec3(e.getX(), 200.0D, e.getZ());
        if (!from.contains("X") && e instanceof LivingEntity l) {
            l.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
        }
        e.getPersistentData().remove("flash_soul_from");
        teleport(e, to, at, false);
    }

    private static void teleport(Entity e, ServerLevel to, Vec3 at, boolean slowFall) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(to, at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
            if (slowFall) {
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
            }
        } else {
            e.changeDimension(to, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity, ServerLevel dest,
                        java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> d) {
                    return new net.minecraft.world.level.portal.PortalInfo(at, Vec3.ZERO, entity.getYRot(),
                            entity.getXRot());
                }
            });
        }
    }
}

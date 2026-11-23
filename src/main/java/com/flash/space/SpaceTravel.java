package com.flash.space;

import com.flash.FlashMod;
import java.lang.reflect.Method;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Space. Whoever goes higher than the Overworld's ceiling (Y 320) leaves it: into Ad Astra's Earth orbit when that mod
 * is installed, otherwise into this mod's own space (flash:space, a starry void). Only those with powers survive
 * there: heroes (Flash, Hulk, Superman, Thor, the Witch, Storm, Spider, Doom, Wonder Woman, Yas), whoever carries
 * an Infinity Stone, Lex in his armour and the Lanterns with a ring; anybody else runs out of air, freezes and dies.
 * Falling back out of this mod's space (below Y 0) drops him back over the Overworld at the same spot.
 * The Lanterns mod takes its ring bearers to its own space (as Lanternas Verdes does with Ad Astra): those are left to
 * it. In Ad Astra's worlds, too, the powered need neither oxygen nor a suit.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class SpaceTravel {

    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(FlashMod.MODID, "space"));
    private static final ResourceKey<Level> AD_ASTRA_ORBIT = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation("ad_astra", "earth_orbit"));
    private static final ResourceKey<Level> LANTERN_SPACE = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation("lanternasverdes", "espaco"));
    private static final String COOLDOWN = "flash_space_cd";
    private static Method lanternRing;
    private static boolean lanternLooked;

    private SpaceTravel() {
    }

    /** Ring bearer of the Lanterns mod (by reflection, when it is installed). */
    private static boolean lantern(Player p) {
        if (!lanternLooked) {
            lanternLooked = true;
            if (ModList.get().isLoaded("lanternasverdes")) {
                try {
                    lanternRing = Class.forName("com.lanternasverdes.data.AnelData").getMethod("temAnel",
                            Player.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    lanternRing = null;
                }
            }
        }
        if (lanternRing == null) {
            return false;
        }
        try {
            return (boolean) lanternRing.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Has powers: survives space. */
    public static boolean powered(ServerPlayer p) {
        return p.isCreative() || p.isSpectator() || com.flash.power.HeroPowers.hasAny(p)
                || !com.flash.infinity.InfinityServer.state(p).powers.isEmpty()
                || com.flash.lex.LexSuit.active(p) || lantern(p);
    }

    /** A world without air: this mod's space, the Lanterns' and Ad Astra's (orbits and planets). */
    public static boolean airless(Level level) {
        ResourceKey<Level> k = level.dimension();
        return k == SPACE || k == LANTERN_SPACE || "ad_astra".equals(k.location().getNamespace());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !p.isAlive()) {
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        long cd = p.getPersistentData().getLong(COOLDOWN);
        // up: over the Overworld's ceiling
        if (level.dimension() == Level.OVERWORLD && p.getY() > level.getMaxBuildHeight() && now >= cd
                && !lantern(p)) {
            boolean adAstra = ModList.get().isLoaded("ad_astra");
            ServerLevel to = p.server.getLevel(adAstra ? AD_ASTRA_ORBIT : SPACE);
            if (to == null) {
                to = p.server.getLevel(SPACE);
            }
            if (to != null) {
                go(p, to, p.getX(), to == p.server.getLevel(SPACE) ? 200.0D : 120.0D, p.getZ(), now);
                p.displayClientMessage(Component.translatable(powered(p) ? "message.flash.space_in"
                        : "message.flash.space_in_danger"), true);
            }
            return;
        }
        if (level.dimension() == SPACE) {
            // floating: slow fall in the void
            if (!p.isFallFlying()) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
            }
            // down: back over the Overworld
            if (p.getY() < 0.0D && now >= cd) {
                ServerLevel over = p.server.overworld();
                go(p, over, p.getX(), over.getMaxBuildHeight() - 10.0D, p.getZ(), now);
                p.displayClientMessage(Component.translatable("message.flash.space_out"), true);
                return;
            }
        }
        if (!airless(level)) {
            return;
        }
        if (powered(p)) {
            p.setAirSupply(p.getMaxAirSupply());
            p.setTicksFrozen(0);
        } else if (level.dimension() == SPACE) {
            // no air and the cold of the void (the Lanterns' space and Ad Astra do their own)
            p.setAirSupply(Math.max(-20, p.getAirSupply() - 8));
            p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 4));
            if (p.getAirSupply() <= -20 && now % 20L == 0L) {
                p.hurt(p.damageSources().drown(), 3.0F);
            }
        }
    }

    private static void go(ServerPlayer p, ServerLevel to, double x, double y, double z, long now) {
        p.getPersistentData().putLong(COOLDOWN, now + 40L);
        Vec3 v = p.getDeltaMovement();
        p.teleportTo(to, x, y, z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(v.x, 0.0D, v.z);
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
    }

    /** The powered don't drown, freeze or suffocate (Ad Astra's oxygen and acid rain) out there. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !airless(p.level()) || !powered(p)) {
            return;
        }
        DamageSource s = event.getSource();
        String ns = s.typeHolder().unwrapKey().map(k -> k.location().getNamespace()).orElse("");
        if (s.is(DamageTypeTags.IS_DROWNING) || s.is(DamageTypeTags.IS_FREEZING) || "ad_astra".equals(ns)) {
            event.setCanceled(true);
        }
    }
}

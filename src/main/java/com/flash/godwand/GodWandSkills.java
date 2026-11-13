package com.flash.godwand;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Yas' God Wand skills, without the wand: her R key does what the wand's use button did (press to start, hold to
 * charge, let go to cast), each skill a mode of hers; Spatial Path is on its own key in the Reality mode.
 * <p>
 * Skills (God Wand's numbers): 0 Spatial Path, 1 Singularity, 2 Earth Drill, 3 Supernova, 4 Stone Cannon,
 * 5 I am Atomic, 6 Fuga. Ported from God Wand by HyIsNoob (MIT code; assets used with the author's permission).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class GodWandSkills {

    public static final int PATH = 0;
    public static final int SINGULARITY = 1;
    public static final int EARTH_DRILL = 2;
    public static final int SUPERNOVA = 3;
    public static final int STONE_CANNON = 4;
    public static final int ATOMIC = 5;
    public static final int FUGA = 6;

    /** The key went down (value: 1 with Shift), came up, or the charge is dropped (another mode, a screen). */
    public static final int PRESS = 0;
    public static final int RELEASE = 1;
    public static final int CANCEL = 2;

    private record Charge(int skill, long started) {
    }

    private static final Map<UUID, Charge> CHARGING = new HashMap<>();
    /** God Wand's own code is changing blocks now (Yas' destruction guard need not look: it is not Hand of God). */
    public static volatile boolean busy;

    private GodWandSkills() {
    }

    /** Her destruction switch (K menu): off, the skills still hit, the land is only lifted and put back. */
    public static boolean destroys(Player p) {
        return p != null && com.flash.yas.YasDestruction.destroys(p);
    }

    public static boolean charging(ServerPlayer p, int skill) {
        Charge c = CHARGING.get(p.getUUID());
        return c != null && c.skill == skill;
    }

    private static int chargeTicks(ServerPlayer p, Charge c) {
        return (int) Math.max(0L, p.serverLevel().getGameTime() - c.started);
    }

    // ------------------------------------------------------------------
    // The keys (YasWandPacket)
    // ------------------------------------------------------------------

    public static void input(ServerPlayer p, int skill, int action, int value) {
        if (!com.flash.yas.YasPowers.has(p) || p.isSpectator() || skill < 0 || skill > 6) {
            return;
        }
        busy = true;
        try {
            switch (action) {
                case PRESS -> press(p, skill, value == 1);
                case RELEASE -> release(p, skill);
                default -> cancel(p);
            }
        } finally {
            busy = false;
        }
    }

    private static void press(ServerPlayer p, int skill, boolean shift) {
        if (skill == PATH) {
            if (shift) {
                SpatialBridge.toggle(p);
            } else {
                SpatialPathWalker.toggle(p);
            }
            return;
        }
        if (CHARGING.containsKey(p.getUUID())) {
            return;
        }
        if (skill == SINGULARITY && ShockwaveSlicer.hasActiveTask(p) && ShockwaveSlicer.triggerEarlySnap(p)) {
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            p.displayClientMessage(Component.translatable("message.godwand.collapsed"), true);
            return;
        }
        if (!SkillCooldowns.ready(p, skill)) {
            return;
        }
        switch (skill) {
            case SUPERNOVA -> SupernovaManager.beginCharge(p);
            case ATOMIC -> {
                if (!AtomicManager.beginCharge(p)) {
                    return;
                }
            }
            case FUGA -> {
                if (!FugaManager.beginCharge(p)) {
                    return;
                }
            }
            default -> {
            }
        }
        startCharge(p, skill);
    }

    private static void startCharge(ServerPlayer p, int skill) {
        CHARGING.put(p.getUUID(), new Charge(skill, p.serverLevel().getGameTime()));
        sync(p, skill);
    }

    private static void stopCharge(ServerPlayer p) {
        if (CHARGING.remove(p.getUUID()) != null) {
            sync(p, -1);
        }
    }

    /** Who charges what, for the others to see it (the orb in her hand, the stone, the bow of fire...). */
    private static void sync(ServerPlayer p, int skill) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new com.flash.network.YasWandChargePacket(p.getId(), skill));
    }

    public static void cancel(ServerPlayer p) {
        Charge c = CHARGING.get(p.getUUID());
        if (c == null) {
            return;
        }
        SupernovaManager.cancelCharge(p);
        AtomicManager.cancelCharge(p);
        FugaManager.cancelCharge(p);
        stopCharge(p);
    }

    private static void release(ServerPlayer p, int skill) {
        Charge c = CHARGING.get(p.getUUID());
        if (c == null || c.skill != skill) {
            return;
        }
        int chargeDuration = chargeTicks(p, c);
        ServerLevel world = p.serverLevel();
        switch (skill) {
            case FUGA -> {
                boolean ready = chargeDuration >= FugaSequence.CHARGE_TICKS;
                FugaManager.releaseCharge(p);
                if (ready) {
                    SkillCooldowns.start(p, FUGA, GodWandConfig.skillCooldownTicks(FUGA, 1));
                }
            }
            case ATOMIC -> {
                boolean committed = chargeDuration >= AtomicSequence.CHARGE_TICKS;
                AtomicManager.releaseCharge(p);
                if (committed) {
                    SkillCooldowns.start(p, ATOMIC, GodWandConfig.skillCooldownTicks(ATOMIC, 1));
                }
            }
            case SUPERNOVA -> {
                SupernovaManager.cancelCharge(p);
                if (chargeDuration < SupernovaSequence.CHARGE_TICKS) {
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                            2.0F, 0.8F);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.PLAYERS, 2.0F, 1.2F);
                    p.displayClientMessage(Component.translatable("message.godwand.cancelled"), true);
                }
            }
            case EARTH_DRILL -> {
                int level = drillLevel(chargeDuration);
                RatioCollapseManager.cast(p, level);
                SkillCooldowns.start(p, EARTH_DRILL, GodWandConfig.skillCooldownTicks(EARTH_DRILL, level));
            }
            case STONE_CANNON -> {
                StoneCannonManager.fire(p, chargeDuration);
                SkillCooldowns.start(p, STONE_CANNON, GodWandConfig.skillCooldownTicks(STONE_CANNON,
                        StoneCannonManager.calculateChargeLevel(chargeDuration)));
            }
            case SINGULARITY -> fireOrb(p, chargeDuration);
            default -> {
            }
        }
        stopCharge(p);
    }

    private static int drillLevel(int chargeTicks) {
        return chargeTicks >= 68 ? 4 : chargeTicks >= 44 ? 3 : chargeTicks >= 22 ? 2 : 1;
    }

    public static String chargeMeter(int level, int maximum) {
        int filled = Mth.clamp(level, 0, maximum);
        return "§f[§b" + "■".repeat(filled) + "§8" + "□".repeat(maximum - filled) + "§f]";
    }

    private static void fireOrb(ServerPlayer player, int chargeDuration) {
        ServerLevel world = player.serverLevel();
        int level = chargeDuration >= 45 ? 3 : chargeDuration >= 20 ? 2 : 1;
        Vec3 look = player.getViewVector(1.0F);
        Vec3 spawnPos = player.getEyePosition().add(look.scale(2.15)).add(0.0, -0.16, 0.0);
        ArcaneOrbEntity orb = new ArcaneOrbEntity(world, player, level);
        orb.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        orb.setDeltaMovement(look.scale(2.4 + level * 0.35));
        world.addFreshEntity(orb);
        SkillCooldowns.start(player, SINGULARITY, GodWandConfig.skillCooldownTicks(SINGULARITY, level));
        double recoil = switch (level) {
            case 1 -> 0.45;
            case 2 -> 0.85;
            default -> 1.35;
        };
        player.push(-look.x * recoil, -look.y * recoil * 0.4 + 0.18, -look.z * recoil);
        player.hurtMarked = true;
        world.sendParticles(ParticleTypes.FLASH, spawnPos.x, spawnPos.y, spawnPos.z, 2, 0.1, 0.1, 0.1, 0.0);
        world.sendParticles(ParticleTypes.SONIC_BOOM, spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.sendParticles(new DustParticleOptions(new Vector3f(0.0F, 0.95F, 1.0F), 1.5F), spawnPos.x, spawnPos.y,
                spawnPos.z, 20, 0.3, 0.3, 0.3, 0.2);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 0.75F, 1.3F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.7F, 1.8F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), GodWandSounds.ev(SoundEvents.GENERIC_EXPLODE),
                SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    // ------------------------------------------------------------------
    // While she holds (God Wand's onUseTick)
    // ------------------------------------------------------------------

    private static void chargeTick(ServerPlayer player, Charge c) {
        int chargeTicks = chargeTicks(player, c);
        ServerLevel world = player.serverLevel();
        switch (c.skill) {
            case ATOMIC -> {
                if (chargeTicks >= AtomicSequence.CHARGE_TICKS) {
                    AtomicManager.arm(player);
                }
            }
            case SINGULARITY -> {
                if (chargeTicks % 8 == 0) {
                    float pitch = 1.3F + Math.min(chargeTicks / 45.0F, 1.0F) * 0.6F;
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
                            SoundSource.PLAYERS, 0.45F, pitch);
                }
                String meter;
                if (chargeTicks >= 45) {
                    meter = chargeMeter(3, 3);
                    if (chargeTicks == 45) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.75F, 1.5F);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.8F);
                    }
                } else if (chargeTicks >= 20) {
                    meter = chargeMeter(2, 3);
                    if (chargeTicks == 20) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.55F, 1.2F);
                    }
                } else {
                    meter = chargeMeter(1, 3);
                }
                player.displayClientMessage(Component.translatable("message.godwand.meter",
                        Component.translatable("skill.godwand.1"), meter), true);
            }
            case STONE_CANNON -> {
                int level = StoneCannonManager.calculateChargeLevel(chargeTicks);
                StoneCannonManager.tickCharge(player, chargeTicks, level);
                player.displayClientMessage(Component.translatable("message.godwand.meter",
                        Component.translatable("skill.godwand.4"), chargeMeter(level, 4)), true);
            }
            case SUPERNOVA -> {
                if (chargeTicks >= SupernovaSequence.CHARGE_TICKS) {
                    SupernovaManager.cast(player);
                    SkillCooldowns.start(player, SUPERNOVA, GodWandConfig.skillCooldownTicks(SUPERNOVA, 1));
                    stopCharge(player);
                    player.displayClientMessage(Component.translatable("message.godwand.released"), true);
                }
            }
            case EARTH_DRILL -> drillTick(player, world, chargeTicks);
            default -> {
            }
        }
    }

    private static void drillTick(ServerPlayer player, ServerLevel sw, int chargeTicks) {
        int level = drillLevel(chargeTicks);
        if (chargeTicks == 22 || chargeTicks == 44 || chargeTicks == 68) {
            float pitch = level == 4 ? 1.8F : (level == 3 ? 1.4F : 1.1F);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 0.65F, pitch);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NETHERITE_BLOCK_PLACE,
                    SoundSource.PLAYERS, 0.65F, pitch);
        }
        if (chargeTicks % 2 == 0) {
            sw.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX() + (Math.random() - 0.5) * 1.8,
                    player.getY() + 0.1, player.getZ() + (Math.random() - 0.5) * 1.8, 1, 0.0, 0.06 + level * 0.03, 0.0,
                    0.01);
        }
        double reach = switch (level) {
            case 2 -> 45.0;
            case 3 -> 65.0;
            case 4 -> 85.0;
            default -> 28.0;
        };
        HitResult hit = player.pick(reach, 0.0F, false);
        Vec3 targetPos = hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit
                ? Vec3.atCenterOf(bHit.getBlockPos())
                : player.getEyePosition().add(player.getViewVector(1.0F).scale(reach * 0.7));
        if (chargeTicks % 3 == 0) {
            sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, targetPos.x, targetPos.y + 0.5, targetPos.z, 2, 0.4, 0.2,
                    0.4, 0.05);
        }
        if (chargeTicks % 10 == 0) {
            float pitch = 0.7F + level * 0.25F;
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 1.4F, pitch);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NETHERITE_BLOCK_PLACE,
                    SoundSource.PLAYERS, 1.2F, pitch);
        }
        player.displayClientMessage(Component.translatable("message.godwand.meter",
                Component.translatable("skill.godwand.2"), chargeMeter(level, 4)), true);
    }

    // ------------------------------------------------------------------
    // Ticks, invulnerability and the cleaning up
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        busy = true;
        try {
            tickAll(server);
        } finally {
            busy = false;
        }
    }

    private static void tickAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SpatialPathWalker.tickPlayer(player);
        }
        for (Iterator<Map.Entry<UUID, Charge>> it = CHARGING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Charge> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || !com.flash.yas.YasPowers.has(p)) {
                if (p != null) {
                    SupernovaManager.cancelCharge(p);
                    AtomicManager.cancelCharge(p);
                    FugaManager.cancelCharge(p);
                    sync(p, -1);
                }
                it.remove();
            }
        }
        for (Map.Entry<UUID, Charge> e : new java.util.ArrayList<>(CHARGING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                chargeTick(p, e.getValue());
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            ShockwaveSlicer.tick(level);
            RatioCollapseManager.tick(level);
            SupernovaManager.tick(level);
            AtomicManager.tick(level);
            FugaManager.tick(level);
            SpatialBridge.tickWorld(level);
            SpatialDisplacementManager.tick(level);
        }
    }

    /** I am Atomic and Fuga keep her safe while they last. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p
                && (AtomicManager.invulnerable(p) || FugaManager.invulnerable(p))) {
            event.setCanceled(true);
        }
    }

    private static void drop(ServerPlayer p) {
        cancel(p);
        SpatialPathWalker.deactivate(p);
        SkillCooldowns.reset(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            drop(p);
            CHARGING.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            drop(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SpatialPathWalker.deactivate(p);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ShockwaveSlicer.clearAllTasks();
        RatioCollapseManager.clearAllTasks();
        SupernovaManager.clearAllTasks();
        AtomicManager.clearAllTasks();
        FugaManager.clearAllTasks();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SpatialBridge.clearVisuals(level);
            SpatialDisplacementManager.emergencyRestoreAll(level);
        }
        CHARGING.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SpatialDisplacementManager.clearRuntimeState();
        SpatialBridge.resetRuntime();
        SpatialPathWalker.resetRuntime();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DisplacementPersistentState.getServerState(level).restoreAndCleanup(level);
        }
    }

    /** A display of ours come back with its chunk: kept if a record still has it, else a leftover (gone). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk()
                && event.getEntity() instanceof net.minecraft.world.entity.Display.BlockDisplay d
                && d.getTags().contains("godwand_displaced") && !SpatialDisplacementManager.reattach(d)) {
            event.setCanceled(true);
        }
    }
}

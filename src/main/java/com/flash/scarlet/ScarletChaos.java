package com.flash.scarlet;

import com.flash.FlashMod;
import com.flash.entity.ChaosEnergy;
import com.flash.network.FlashNetwork;
import com.flash.network.ScarletOfferPacket;
import com.flash.registry.ModEntities;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The two ways to the Scarlet Witch, each a 10 % chance, for a player with no power:
 * <ul>
 * <li>joining the world: the energy of chaos may appear before him;</li>
 * <li>dying: he may come back at once (as with a Totem of Undying), and the energy appears.</li>
 * </ul>
 * The energy asks whether he gives in to chaos: yes, he becomes the Scarlet Witch; no, it fades away.
 * <p>
 * {@code /scarlet chance <1..100>} sets the chance (percent, kept with the world); {@code /scarlet debug} tells it;
 * {@code /scarlet roll <join|death> [player]} rolls once for real (and on a hit, does it); {@code /scarlet offer
 * [player]} brings the energy at once.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class ScarletChaos {

    /** Percent (10 unless changed with /scarlet chance, kept with the world). */
    public static int chance = 10;
    private static final Map<UUID, Integer> OFFERED = new HashMap<>();
    /** Offers waiting a moment (after joining or coming back): player -> server tick to show it. */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private ScarletChaos() {
    }

    /** The dice: 1..100; a hit is {@link #chance} or less. */
    public static int roll(RandomSource random) {
        return 1 + random.nextInt(100);
    }

    /** Without any power (and not a test dummy). */
    public static boolean eligible(ServerPlayer p) {
        return !(p instanceof net.minecraftforge.common.util.FakePlayer) && !com.flash.power.HeroPowers.hasAny(p)
                && !com.flash.yas.YasPowers.has(p) && !ScarletPowers.has(p) && !com.flash.storm.StormPowers.has(p);
    }


    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !eligible(p)) {
            return;
        }
        int roll = roll(p.getRandom());
        if (roll <= chance) {
            PENDING.put(p.getUUID(), p.server.getTickCount() + 80);
        }
    }

    /** A death without powers: the chance of coming back, and the energy right after. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled() || !eligible(p)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        int roll = roll(p.getRandom());
        if (roll <= chance) {
            event.setCanceled(true);
            revive(p);
            PENDING.put(p.getUUID(), p.server.getTickCount() + 25);
        }
    }

    /** As a Totem of Undying: one heart, the bad effects gone, regeneration, absorption, fire resistance. */
    private static void revive(ServerPlayer p) {
        p.setHealth(1.0F);
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        p.level().broadcastEntityEvent(p, (byte) 35);
        p.serverLevel().sendParticles(ScarletPowers.RED, p.getX(), p.getY() + 1.0D, p.getZ(), 60, 0.5D, 0.8D, 0.5D,
                0.1D);
        p.displayClientMessage(Component.translatable("message.flash.chaos.revived"), true);
    }

    /** The energy appears before him and asks. */
    public static void offer(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Integer old = OFFERED.remove(p.getUUID());
        if (old != null && level.getEntity(old) instanceof ChaosEnergy e) {
            e.discard();
        }
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        Vec3 at = p.getEyePosition().add(flat.scale(2.2D)).add(0.0D, -0.4D, 0.0D);
        ChaosEnergy e = ModEntities.CHAOS_ENERGY.get().create(level);
        if (e == null) {
            return;
        }
        e.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        e.setFor(p.getUUID());
        level.addFreshEntity(e);
        OFFERED.put(p.getUUID(), e.getId());
        ScarletPowers.play(level, at, "telecinese_tensao", SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ScarletOfferPacket(e.getId()));
    }

    public static void answer(ServerPlayer p, boolean accept) {
        Integer id = OFFERED.remove(p.getUUID());
        if (id == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        Entity e = level.getEntity(id);
        Vec3 at = e != null ? e.position() : p.position();
        if (e != null) {
            e.discard();
        }
        if (accept && eligible(p)) {
            ScarletPowers.grant(p);
            level.sendParticles(ScarletPowers.RED, p.getX(), p.getY() + 1.0D, p.getZ(), 120, 0.6D, 1.0D, 0.6D, 0.2D);
            level.sendParticles(ScarletPowers.energy(), p.getX(), p.getY() + 1.0D, p.getZ(), 60, 0.6D, 1.0D, 0.6D,
                    0.3D);
            ScarletPowers.play(level, p.position(), "realidade_onda", SoundEvents.EVOKER_CAST_SPELL, 0.6F);
            p.sendSystemMessage(Component.translatable("message.flash.chaos.accepted"));
        } else {
            level.sendParticles(ScarletPowers.RED, at.x, at.y + 0.3D, at.z, 40, 0.3D, 0.3D, 0.3D, 0.05D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
            p.displayClientMessage(Component.translatable("message.flash.chaos.refused"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        for (Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            if (now < en.getValue()) {
                continue;
            }
            it.remove();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p != null && p.isAlive() && eligible(p)) {
                offer(p);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Integer id = OFFERED.remove(event.getEntity().getUUID());
        if (id != null && event.getEntity().level().getEntity(id) instanceof ChaosEnergy e) {
            e.discard();
        }
        PENDING.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------
    // The test commands (added to /scarlet)
    // ------------------------------------------------------------------

    public static LiteralArgumentBuilder<CommandSourceStack> commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        // /scarlet chance <1..100>: the chance (percent) of both ways
        root.then(Commands.literal("chance").then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                .executes(c -> setChance(c.getSource(), IntegerArgumentType.getInteger(c, "percent")))));
        // /scarlet debug: the chance now
        root.then(Commands.literal("debug").executes(c -> {
            c.getSource().sendSuccess(() -> Component.translatable("commands.flash.chaos.debug", chance), false);
            return chance;
        }));
        for (String way : new String[] {"join", "death"}) {
            root.then(Commands.literal("roll").then(Commands.literal(way)
                    .executes(c -> rollOnce(c.getSource(), c.getSource().getPlayerOrException(), way))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(c -> rollOnce(c.getSource(), EntityArgument.getPlayer(c, "player"), way)))));
        }
        root.then(Commands.literal("offer")
                .executes(c -> forceOffer(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> forceOffer(c.getSource(), EntityArgument.getPlayer(c, "player")))));
        return root;
    }

    private static int setChance(CommandSourceStack src, int percent) {
        chance = percent;
        Saved.get(src.getServer()).setDirty();
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            com.flash.power.HeroAdvancements.sync(p, true);
        }
        src.sendSuccess(() -> Component.translatable("commands.flash.chaos.chance_set", percent), true);
        return percent;
    }

    private static int rollOnce(CommandSourceStack src, ServerPlayer p, String way) {
        int roll = roll(p.getRandom());
        boolean hit = roll <= chance;
        src.sendSuccess(() -> Component.translatable(hit ? "message.flash.chaos.debug_hit"
                : "message.flash.chaos.debug_miss", Component.translatable("message.flash.chaos.way." + way), roll,
                chance), false);
        if (hit) {
            if (!eligible(p)) {
                src.sendFailure(Component.translatable("commands.flash.chaos.not_eligible", p.getDisplayName()));
                return 0;
            }
            if ("death".equals(way)) {
                revive(p);
                PENDING.put(p.getUUID(), p.server.getTickCount() + 25);
            } else {
                offer(p);
            }
        }
        return hit ? 1 : 0;
    }

    private static int forceOffer(CommandSourceStack src, ServerPlayer p) {
        offer(p);
        src.sendSuccess(() -> Component.translatable("commands.flash.chaos.offered", p.getDisplayName()), true);
        return 1;
    }

    /** The chance, kept with the world. */
    static final class Saved extends net.minecraft.world.level.saveddata.SavedData {

        static Saved get(net.minecraft.server.MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Saved::load, Saved::new, "flash_scarlet_chaos");
        }

        static Saved load(net.minecraft.nbt.CompoundTag tag) {
            if (tag.contains("Chance")) {
                chance = Math.max(1, Math.min(100, tag.getInt("Chance")));
            }
            return new Saved();
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
            tag.putInt("Chance", chance);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        chance = 10;
        Saved.get(event.getServer());
    }

    static boolean offered(ServerPlayer p) {
        return OFFERED.containsKey(p.getUUID());
    }

    /** Registered with /scarlet (ScarletCommands). */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        // (the subcommands are added by ScarletCommands)
    }
}

package com.flash.power;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroProgressPacket;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The mod's advancements (the vanilla ones: a toast and the chat message "has made the advancement..."), in their
 * own tab {@code flash:heroes/*}:
 * <ul>
 * <li>becoming each hero (Flash, Hulk, Red Hulk, Superman, Thor) and wearing Lex Luthor's whole suit;</li>
 * <li>the way to Thor: winning a raid (the Hero of the Village effect), killing 100 hostile creatures, and then
 * <b>Worthy</b>, which lets the player lift the Mjolnir of the crater (ThorCrater) or take an Asgardian capsule.</li>
 * </ul>
 * The advancements themselves are the record (so the vanilla {@code /advancement} works too); the kills are counted
 * in the player data. {@code /conquista <id>|todas [player]} grants them (and the kills with "cacador").
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HeroAdvancements {

    public static final String ROOT = "root";
    public static final String FLASH = "flash";
    public static final String HULK = "hulk";
    public static final String RED_HULK = "red_hulk";
    public static final String SUPERMAN = "superman";
    public static final String THOR = "thor";
    public static final String LEX = "lex";
    public static final String VILLAGE = "hero_of_the_village";
    public static final String HUNTER = "monster_hunter";
    public static final String WORTHY = "worthy";
    public static final String YAS = "yas";
    /** The heroes the transcendence asks for (YasTranscendence). */
    public static final String[] HEROES = {FLASH, HULK, RED_HULK, SUPERMAN, THOR, LEX};
    /** In the order of the bits of the client sync. */
    public static final String[] ALL = {ROOT, FLASH, HULK, RED_HULK, SUPERMAN, THOR, LEX, VILLAGE, HUNTER, WORTHY,
            YAS};

    /** Hostile creatures to kill to be worthy. */
    public static final int KILLS_NEEDED = 100;
    private static final String KILLS = "flash_hostile_kills";

    private static final Map<UUID, Long> SENT = new HashMap<>();

    private HeroAdvancements() {
    }

    private static Advancement advancement(ServerPlayer player, String id) {
        return player.server.getAdvancements().getAdvancement(new ResourceLocation(FlashMod.MODID, "heroes/" + id));
    }

    public static boolean has(ServerPlayer player, String id) {
        Advancement a = advancement(player, id);
        return a != null && player.getAdvancements().getOrStartProgress(a).isDone();
    }

    /** Grants an advancement (all its criteria); false when it was already done or does not exist. */
    public static boolean award(ServerPlayer player, String id) {
        Advancement a = advancement(player, id);
        if (a == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(a);
        if (progress.isDone()) {
            return false;
        }
        if (!ROOT.equals(id)) {
            award(player, ROOT);
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(a, criterion);
        }
        return true;
    }

    public static int kills(ServerPlayer player) {
        return player.getPersistentData().getInt(KILLS);
    }

    /** Worthy of Mjolnir: the advancement (raid won and 100 hostile creatures killed). */
    public static boolean isWorthy(ServerPlayer player) {
        return has(player, WORTHY);
    }

    // ------------------------------------------------------------------
    // What earns them
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0
                || p instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        if (HeroPowers.Hero.FLASH.has(p) && com.flash.power.FlashPowers.hasPermanentPowers(p)) {
            award(p, FLASH);
        }
        if (com.flash.hulk.HulkPowers.has(p) && !com.flash.hulk.HulkPowers.isTemporary(p)) {
            award(p, HULK);
            if (com.flash.hulk.HulkPowers.isRed(p)) {
                award(p, RED_HULK);
            }
        }
        if (com.flash.superman.SupermanPowers.has(p) && com.flash.superman.SupermanPowers.temporaryTicks(p) <= 0) {
            award(p, SUPERMAN);
        }
        if (com.flash.thor.ThorPowers.has(p)) {
            award(p, THOR);
        }
        if (com.flash.yas.YasPowers.has(p)) {
            award(p, YAS);
        }
        if (com.flash.item.LexArmorItem.wearingFull(p)) {
            award(p, LEX);
        }
        if (p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            award(p, VILLAGE);
        }
        if (kills(p) >= KILLS_NEEDED) {
            award(p, HUNTER);
        }
        if (!has(p, WORTHY) && has(p, VILLAGE) && has(p, HUNTER)) {
            award(p, WORTHY);
            p.displayClientMessage(Component.translatable("message.flash.worthy"), false);
        }
        if (p.tickCount % 40 == 0) {
            sync(p, false);
        }
    }

    /** A hostile creature killed by the player (by his hand, his arrows, his powers...). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer p)) {
            return;
        }
        int kills = kills(p) + 1;
        p.getPersistentData().putInt(KILLS, kills);
        if (kills == KILLS_NEEDED) {
            award(p, HUNTER);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KILLS)) {
            event.getEntity().getPersistentData().putInt(KILLS, old.getInt(KILLS));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            award(p, ROOT);
            sync(p, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    /** The kills and which advancements are done, for the hero guide of the K menu. */
    public static void sync(ServerPlayer p, boolean force) {
        int mask = 0;
        for (int i = 0; i < ALL.length; i++) {
            if (has(p, ALL[i])) {
                mask |= 1 << i;
            }
        }
        int kills = Math.min(kills(p), 100000);
        long key = ((long) kills << 32) | (mask & 0xFFFFFFFFL);
        Long last = SENT.get(p.getUUID());
        if (!force && last != null && last == key) {
            return;
        }
        SENT.put(p.getUUID(), key);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HeroProgressPacket(kills, mask));
    }

    // ------------------------------------------------------------------
    // /conquista <id>|todas [player]
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("conquista")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b))
                        .executes(c -> grant(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "id"), c.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(EntityArgument.getPlayer(c, "player"),
                                        StringArgumentType.getString(c, "id"), c.getSource())))));
    }

    /** The ids of the command (Portuguese, as the rest of the mod's commands). */
    private static final String[][] COMMAND_IDS = {
            {"flash", FLASH}, {"hulk", HULK}, {"hulk_vermelho", RED_HULK}, {"superman", SUPERMAN}, {"thor", THOR},
            {"lex", LEX}, {"heroi_da_vila", VILLAGE}, {"cacador", HUNTER}, {"digno", WORTHY}, {"yas", YAS}};

    private static java.util.List<String> ids() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String[] pair : COMMAND_IDS) {
            out.add(pair[0]);
        }
        out.add("todas");
        return out;
    }

    private static int grant(ServerPlayer player, String id, net.minecraft.commands.CommandSourceStack source) {
        java.util.List<String> targets = new java.util.ArrayList<>();
        if ("todas".equals(id)) {
            for (String[] pair : COMMAND_IDS) {
                targets.add(pair[1]);
            }
        } else {
            for (String[] pair : COMMAND_IDS) {
                if (pair[0].equals(id) || pair[1].equals(id)) {
                    targets.add(pair[1]);
                }
            }
        }
        if (targets.isEmpty()) {
            source.sendFailure(Component.translatable("commands.flash.advancement.unknown", id));
            return 0;
        }
        for (String t : targets) {
            if (WORTHY.equals(t)) {
                // worthy means both steps: they come with it
                award(player, VILLAGE);
                giveKills(player);
                award(player, HUNTER);
            }
            if (HUNTER.equals(t)) {
                giveKills(player);
            }
            award(player, t);
        }
        sync(player, true);
        source.sendSuccess(() -> Component.translatable("commands.flash.advancement.done", id,
                player.getDisplayName()), true);
        return 1;
    }

    private static void giveKills(ServerPlayer player) {
        if (kills(player) < KILLS_NEEDED) {
            player.getPersistentData().putInt(KILLS, KILLS_NEEDED);
        }
    }
}

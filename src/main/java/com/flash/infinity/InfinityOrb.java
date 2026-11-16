package com.flash.infinity;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The Orb of Morag: the shell that hides the Power Stone. Its rings turn left and right; each world has its own random
 * combination of 15 turns (orb_lock_progress counts the right ones, a wrong one starts over); the whole combination
 * opens it (power_stone_open_stage 15, orb_opened 1). Open, the stone can be taken out (the world's one Power Stone:
 * once taken from any Orb it is gone from all of them) and a stone put back in.
 */
public final class InfinityOrb {

    public static final String STAGE = "power_stone_open_stage";
    public static final String PROGRESS = "orb_lock_progress";
    public static final String OPENED = "orb_opened";
    public static final int OPEN = 15;
    public static final int LENGTH = 15;
    /** The client's copy of the world's flag (InfinityServer.sync). */
    public static boolean clientTaken;
    public static boolean clientEyeTaken;

    private InfinityOrb() {
    }

    // ------------------------------------------------------------------
    // The world's data: the combination, whether the Power Stone was taken
    // ------------------------------------------------------------------

    static final class Data extends SavedData {
        String combination = "";
        boolean stoneTaken;
        /** The Eye of Agamotto's Time Stone, likewise one per world. */
        boolean eyeTaken;

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.combination = t.getString("combination");
            d.stoneTaken = t.getBoolean("stone_taken");
            d.eyeTaken = t.getBoolean("eye_taken");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putString("combination", this.combination);
            t.putBoolean("stone_taken", this.stoneTaken);
            t.putBoolean("eye_taken", this.eyeTaken);
            return t;
        }
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "flash_infinity_orb");
    }

    public static boolean taken(MinecraftServer server) {
        return data(server).stoneTaken;
    }

    /** On either side: whether the world's Power Stone has left the Orbs. */
    public static boolean worldTaken() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return taken(server);
        }
        return clientTaken;
    }

    public static boolean eyeTaken(MinecraftServer server) {
        return data(server).eyeTaken;
    }

    public static boolean worldEyeTaken() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return eyeTaken(server);
        }
        return clientEyeTaken;
    }

    /** The stones' destruction: the Orb's Power Stone and the Eye's Time Stone go too. */
    public static void destroyStone(MinecraftServer server) {
        Data d = data(server);
        d.stoneTaken = true;
        d.eyeTaken = true;
        d.setDirty();
    }

    // ------------------------------------------------------------------
    // The Eye of Agamotto (flash:eye_of_agamotto abilities: open, close, remove_stone, insert_stone)
    // ------------------------------------------------------------------

    static void eye(ServerPlayer p, String action) {
        ItemStack eye = InfinityContainers.eye(p);
        if (eye.isEmpty()) {
            return;
        }
        switch (action) {
            case "open" -> {
                eye.getOrCreateTag().putBoolean("flash_open", true);
                p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS,
                        1.0F, 1.2F);
                SpaceStone.sound(p.level(), p.position(), "time_stone", 0.6F, 1.0F);
            }
            case "close" -> {
                eye.getOrCreateTag().putBoolean("flash_open", false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS,
                        1.0F, 1.2F);
            }
            case "remove_stone" -> {
                CompoundTag t = eye.getTag();
                InfinityStone in;
                if (t != null && t.contains(InfinityContainers.STONE)) {
                    in = InfinityStone.byId(t.getString(InfinityContainers.STONE));
                } else if (eye.is(InfinityItems.EYE_OF_AGAMOTTO.get())) {
                    Data d = data(p.server);
                    if (d.eyeTaken) {
                        p.displayClientMessage(Component.translatable("message.flash.infinity.eye_taken")
                                .withStyle(ChatFormatting.GREEN), true);
                        return;
                    }
                    d.eyeTaken = true;
                    d.setDirty();
                    in = InfinityStone.TIME;
                } else {
                    in = null;
                }
                if (in == null) {
                    return;
                }
                InfinityContainers.setStone(eye, null);
                give(p, new ItemStack(in.item()));
                p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        1.0F, 1.0F);
                SpaceStone.sound(p.level(), p.position(), "stone_remove", 1.0F, 1.0F);
            }
            case "insert_stone" -> {
                if (InfinityContainers.stoneIn(eye) != null) {
                    return;
                }
                for (ItemStack h : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
                    InfinityStone s = InfinityStone.of(h);
                    if (s == null) {
                        continue;
                    }
                    h.shrink(1);
                    if (s == InfinityStone.TIME && eye.is(InfinityItems.EYE_OF_AGAMOTTO.get())) {
                        eye.getOrCreateTag().remove(InfinityContainers.STONE);
                        Data d = data(p.server);
                        d.eyeTaken = false;
                        d.setDirty();
                    } else {
                        InfinityContainers.setStone(eye, s);
                    }
                    p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 1.0F, 1.0F);
                    SpaceStone.sound(p.level(), p.position(), "infinity_stone_equip", 1.0F, 1.0F);
                    return;
                }
            }
            default -> {
            }
        }
    }

    static String combination(MinecraftServer server) {
        Data d = data(server);
        if (d.combination.length() != LENGTH) {
            net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create();
            StringBuilder b = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                b.append(r.nextBoolean() ? 'L' : 'R');
            }
            d.combination = b.toString();
            d.setDirty();
        }
        return d.combination;
    }

    // ------------------------------------------------------------------
    // Held
    // ------------------------------------------------------------------

    public static boolean is(ItemStack s) {
        return s.is(InfinityItems.ORB.get());
    }

    public static ItemStack held(Player p, String slot) {
        if ("mainhand".equals(slot)) {
            return is(p.getMainHandItem()) ? p.getMainHandItem() : ItemStack.EMPTY;
        }
        if ("offhand".equals(slot)) {
            return is(p.getOffhandItem()) ? p.getOffhandItem() : ItemStack.EMPTY;
        }
        ItemStack m = held(p, "mainhand");
        return m.isEmpty() ? held(p, "offhand") : m;
    }

    public static boolean isOpen(Player p) {
        return InfinityServer.score(p, STAGE) >= OPEN;
    }

    /** {"slot": ..., "stone": true/false}: an orb there, with or without a stone in it. */
    static boolean test(Player p, JsonObject c) {
        ItemStack o = held(p, c.has("slot") ? c.get("slot").getAsString() : null);
        if (o.isEmpty()) {
            return false;
        }
        return !c.has("stone") || c.get("stone").getAsBoolean() == (InfinityContainers.stoneIn(o) != null);
    }

    // ------------------------------------------------------------------
    // /infinity orb turn left|right, take, insert, reset, sync
    // ------------------------------------------------------------------

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("orb")
                .then(Commands.literal("turn")
                        .then(Commands.literal("left").executes(c -> turn(c.getSource(), true)))
                        .then(Commands.literal("right").executes(c -> turn(c.getSource(), false))))
                .then(Commands.literal("take").executes(c -> take(c.getSource())))
                .then(Commands.literal("insert").executes(c -> insert(c.getSource())))
                .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
                .then(Commands.literal("sync").executes(c -> sync(c.getSource())))
                .then(Commands.literal("combination").requires(s -> s.hasPermission(2)).executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(combination(c.getSource().getServer())),
                            false);
                    return 1;
                }));
    }

    private static ServerPlayer holder(CommandSourceStack src) {
        Entity e = src.getEntity();
        return e instanceof ServerPlayer p && !held(p, null).isEmpty() ? p : null;
    }

    private static int turn(CommandSourceStack src, boolean left) {
        ServerPlayer p = holder(src);
        if (p == null) {
            return 0;
        }
        p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (InfinityServer.score(p, STAGE) >= OPEN) {
            openEffects(p);
            return 1;
        }
        String comb = combination(p.server);
        int prog = InfinityServer.score(p, PROGRESS);
        if (prog < 0 || prog >= comb.length() || comb.charAt(prog) != (left ? 'L' : 'R')) {
            // wrong: the rings slip back
            p.playNotifySound(SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            particles(p, ParticleTypes.SMOKE, 0.5D, 8, 0.2D, 0.01D);
            InfinityServer.setScore(p, PROGRESS, 0);
            return 1;
        }
        prog++;
        if (prog >= comb.length()) {
            openEffects(p);
            unlock(p);
        } else {
            InfinityServer.setScore(p, PROGRESS, prog);
        }
        return 1;
    }

    private static void openEffects(ServerPlayer p) {
        p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        particles(p, ParticleTypes.END_ROD, 1.0D, 10, 0.15D, 0.05D);
    }

    public static void unlock(ServerPlayer p) {
        InfinityServer.setScore(p, PROGRESS, 0);
        InfinityServer.setScore(p, OPENED, 1);
        InfinityServer.setScore(p, STAGE, OPEN);
    }

    private static int reset(CommandSourceStack src) {
        ServerPlayer p = holder(src);
        if (p == null) {
            return 0;
        }
        p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        InfinityServer.setScore(p, STAGE, 0);
        InfinityServer.setScore(p, OPENED, 0);
        InfinityServer.setScore(p, PROGRESS, 0);
        return 1;
    }

    private static int take(CommandSourceStack src) {
        ServerPlayer p = holder(src);
        if (p == null || InfinityServer.score(p, STAGE) < OPEN) {
            return 0;
        }
        ItemStack orb = held(p, null);
        CompoundTag t = orb.getTag();
        if (t != null && t.contains(InfinityContainers.STONE)) {
            InfinityStone s = InfinityStone.byId(t.getString(InfinityContainers.STONE));
            if (s == null) {
                return 0;
            }
            InfinityContainers.setStone(orb, null);
            give(p, new ItemStack(s.item()));
            SpaceStone.sound(p.level(), p.position(), "stone_remove", 1.0F, 1.0F);
            return 1;
        }
        Data d = data(p.server);
        if (d.stoneTaken) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.orb_taken")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return 0;
        }
        d.stoneTaken = true;
        d.setDirty();
        InfinityContainers.setStone(orb, null);
        give(p, new ItemStack(InfinityStone.POWER.item()));
        SpaceStone.sound(p.level(), p.position(), "stone_remove", 1.0F, 1.0F);
        return 1;
    }

    private static int insert(CommandSourceStack src) {
        ServerPlayer p = holder(src);
        if (p == null || InfinityServer.score(p, STAGE) < OPEN) {
            return 0;
        }
        ItemStack orb = held(p, null);
        if (InfinityContainers.stoneIn(orb) != null) {
            return 0;
        }
        for (ItemStack h : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
            InfinityStone s = InfinityStone.of(h);
            if (s == null) {
                continue;
            }
            if (s == InfinityStone.POWER) {
                // the world's Power Stone back in its Orb
                h.shrink(1);
                orb.getOrCreateTag().remove(InfinityContainers.STONE);
                Data d = data(p.server);
                d.stoneTaken = false;
                d.setDirty();
            } else {
                InfinityContainers.setStone(orb, s);
                h.shrink(1);
            }
            SpaceStone.sound(p.level(), p.position(), "infinity_stone_equip", 1.0F, 1.0F);
            return 1;
        }
        return 0;
    }

    private static int sync(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) {
            InfinityServer.sync(p, true);
        }
        return 1;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
    }

    private static void particles(ServerPlayer p, ParticleOptions o, double up, int n, double spread, double speed) {
        p.serverLevel().sendParticles(p, o, true, p.getX(), p.getY() + up, p.getZ(), n, spread, spread, spread,
                speed);
    }
}

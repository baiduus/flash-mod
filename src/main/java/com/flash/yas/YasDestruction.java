package com.flash.yas;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.YasDestroyPacket;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Yas' block destruction switch (her K menu, as the Hulk's, Superman's and Thor's): off, her Hand of God powers
 * (the sun and the moon, the disintegration, the heat vision, the electrokinesis... and her black hole) still hit
 * as hard, but no block is broken. The Manipulation of Reality is not destruction: it still rewrites the land.
 * <p>
 * How: every block change passes the LevelChunkGuardMixin; while some Yas has it off, a change made from Hand of
 * God's code near her (the nearest Yas decides) is refused. Hand of God's crater is set to be made block by block
 * meanwhile (its direct way would go around the guard), and its flying debris is turned off.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasDestruction {

    private static volatile boolean anyOff;
    private static Thread serverThread;
    private static final Map<String, Object> SAVED = new HashMap<>();

    private YasDestruction() {
    }

    public static boolean destroys(Player p) {
        return !p.getPersistentData().getCompound(YasPowers.KEY).getBoolean("NoDestroy");
    }

    public static void set(ServerPlayer p, boolean on) {
        if (!YasPowers.has(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData().getCompound(YasPowers.KEY);
        tag.putBoolean("NoDestroy", !on);
        p.getPersistentData().put(YasPowers.KEY, tag);
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new YasDestroyPacket(destroys(p)));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        serverThread = Thread.currentThread();
        boolean off = false;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (YasPowers.has(p) && !destroys(p)) {
                off = true;
                break;
            }
        }
        if (off != anyOff) {
            anyOff = off;
            tune(off);
        }
    }

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && YasPowers.has(p)) {
            sync(p);
        }
    }

    /** Hand of God's crater made through the guard, and no debris flying off, while the destruction is off. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tune(boolean off) {
        try {
            Class<?> cfg = Class.forName("handofgod.Config");
            String[] ints = {"BLOCOS_VOANDO", "LUA_BLOCOS_VOANDO", "BLOCOS_VOANDO_POR_TICK"};
            for (String f : ints) {
                Object v = cfg.getField(f).get(null);
                if (v instanceof net.minecraftforge.common.ForgeConfigSpec.IntValue iv) {
                    if (off) {
                        SAVED.putIfAbsent(f, iv.get());
                        iv.set(0);
                    } else if (SAVED.containsKey(f)) {
                        iv.set((Integer) SAVED.remove(f));
                    }
                }
            }
            Object method = cfg.getField("METODO_CRATERA").get(null);
            if (method instanceof net.minecraftforge.common.ForgeConfigSpec.EnumValue ev) {
                if (off) {
                    SAVED.putIfAbsent("METODO_CRATERA", ev.get());
                    Class<? extends Enum> type = (Class<? extends Enum>) Class.forName("handofgod.Config$MetodoCratera");
                    ev.set(Enum.valueOf(type, "SETBLOCK"));
                } else if (SAVED.containsKey("METODO_CRATERA")) {
                    ev.set(SAVED.remove("METODO_CRATERA"));
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // no Hand of God, or it changed: the guard alone
        }
    }

    /** From the mixin: refuse this block change? (Hand of God's code, a Yas with the destruction off near.) */
    public static boolean refuse(LevelChunk chunk, BlockPos pos, BlockState state) {
        if (!anyOff || com.flash.godwand.GodWandSkills.busy || Thread.currentThread() != serverThread
                || !(chunk.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        String from = StackWalker.getInstance().walk(s -> s.limit(48)
                .map(f -> f.getClassName())
                .filter(n -> n.startsWith("handofgod."))
                .findFirst().orElse(null));
        if (from == null || from.startsWith("handofgod.realidade") || from.startsWith("handofgod.tempo")) {
            return false;
        }
        // the nearest Yas decides
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (!YasPowers.has(p)) {
                continue;
            }
            double d = p.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest != null && !destroys(nearest);
    }
}

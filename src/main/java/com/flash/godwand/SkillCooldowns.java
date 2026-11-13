package com.flash.godwand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The cooldowns of Yas' God Wand skills (shown in her HUD as the other heroes' rows: ids gw_0..gw_6). */
public final class SkillCooldowns {

    private static final Map<UUID, long[]> UNTIL = new HashMap<>();

    private SkillCooldowns() {
    }

    public static String id(int mode) {
        return "gw_" + mode;
    }

    private static long[] of(ServerPlayer p) {
        return UNTIL.computeIfAbsent(p.getUUID(), k -> new long[7]);
    }

    public static void reset(ServerPlayer player) {
        UNTIL.remove(player.getUUID());
        for (int mode = 0; mode < 7; mode++) {
            com.flash.godwand.GodWandNet.to(player, new com.flash.network.ScarletCooldownPacket(id(mode), 0));
        }
    }

    public static int remaining(ServerPlayer player, int mode) {
        if (mode < 0 || mode >= 7) {
            return 0;
        }
        return (int) Math.max(0L, of(player)[mode] - player.serverLevel().getGameTime());
    }

    public static boolean ready(Player user, int mode) {
        if (!(user instanceof ServerPlayer player)) {
            return true;
        }
        int left = remaining(player, mode);
        if (left == 0) {
            return true;
        }
        player.displayClientMessage(Component.translatable("message.godwand.skill_cooldown",
                Component.translatable("skill.godwand." + mode), (left + 19) / 20), true);
        return false;
    }

    public static void start(Player user, int mode, int ticks) {
        if (user instanceof ServerPlayer player && mode >= 0 && mode < 7) {
            of(player)[mode] = player.serverLevel().getGameTime() + ticks;
            com.flash.godwand.GodWandNet.to(player, new com.flash.network.ScarletCooldownPacket(id(mode), ticks));
        }
    }

    public static void sync(ServerPlayer player, int mode) {
    }
}

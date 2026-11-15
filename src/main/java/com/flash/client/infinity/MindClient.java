package com.flash.client.infinity;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/** The Mind Stone on the client: the disguises (whose skin and name each player wears), intangibility's flag. */
public final class MindClient {

    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();
    private static boolean hooked;

    private MindClient() {
    }

    static void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        com.flash.infinity.MindStone.clientIntangible = p -> p == Minecraft.getInstance().player
                && InfinityClient.anyOn("palladium:intangibility");
        com.flash.infinity.MindStone.DisguiseNames.client = p -> NAMES.get(p.getId());
    }

    static void disguise(int entity, String name) {
        hook();
        if (name == null || name.isEmpty()) {
            NAMES.remove(entity);
        } else {
            NAMES.put(entity, name);
            load(name);
        }
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entity);
        if (e instanceof Player p) {
            p.refreshDisplayName();
        }
    }

    private static void load(String name) {
        if (SKINS.containsKey(name)) {
            return;
        }
        SKINS.put(name, null);
        SkullBlockEntity.updateGameprofile(new GameProfile(null, name), profile -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {
                if (type == com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN) {
                    SKINS.put(name, location);
                }
            }, true));
        });
    }

    /** The disguise's skin of that player (null: its own). */
    public static ResourceLocation skin(AbstractClientPlayer p) {
        String n = NAMES.get(p.getId());
        return n == null ? null : SKINS.get(n);
    }

    public static void reset() {
        NAMES.clear();
    }
}

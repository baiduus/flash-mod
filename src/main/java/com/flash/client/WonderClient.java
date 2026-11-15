package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroActionPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.wonder.WonderAbilities;
import com.flash.wonder.WonderPowers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * Wonder Woman on the client: who she is (her suit, flying, her guard up) and the keys of her modes (Lasso, Bracelets,
 * Warrior: R and Z). Her light: WonderVfx; her arms: WonderPose; her shield and lasso worn: WonderLayer.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class WonderClient {

    private static final Map<Integer, Integer> WONDERS = new HashMap<>();

    private WonderClient() {
    }

    public static void reset() {
        WONDERS.clear();
        WonderVfx.reset();
    }

    public static void set(int entity, boolean has, int data) {
        if (has) {
            WONDERS.put(entity, data);
        } else {
            WONDERS.remove(entity);
        }
    }

    public static boolean isWonder(Entity e) {
        return e != null && WONDERS.containsKey(e.getId());
    }

    public static boolean flying(Player p) {
        if (!isWonder(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying
                : (WONDERS.get(p.getId()) & WonderPowers.F_FLYING) != 0;
    }

    public static boolean suited(Entity e) {
        Integer d = WONDERS.get(e.getId());
        return d != null && (d & WonderPowers.F_UNSUITED) == 0;
    }

    public static boolean guarding(Entity e) {
        Integer d = WONDERS.get(e.getId());
        return d != null && (d & WonderPowers.F_GUARD) != 0;
    }

    /** Her suit's id (the tiara or not). */
    public static String suit(Entity e) {
        Integer d = WONDERS.get(e.getId());
        int i = d == null ? 0 : d & 0xFF;
        return WonderPowers.SUITS[i < 0 || i >= WonderPowers.SUITS.length ? 0 : i];
    }

    public static int suitIndex(Entity e) {
        Integer d = WONDERS.get(e.getId());
        return d == null ? 0 : Math.min(WonderPowers.SUITS.length - 1, d & 0xFF);
    }

    static Iterable<Integer> ids() {
        return WONDERS.keySet();
    }

    private static boolean useWas;

    /** Every tick from YasAbilities with her mode now (null: not her). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "lasso" -> {
                if (pressed) {
                    use("lasso", WonderAbilities.LASSO);
                }
                if (variant) {
                    use("lasso_whip", WonderAbilities.LASSO_SPIN);
                }
            }
            case "bracelets" -> {
                if (pressed) {
                    use("bracelet_guard", WonderAbilities.GUARD);
                }
                if (variant) {
                    use("bracelet_clash", WonderAbilities.CLASH);
                }
            }
            case "warrior" -> {
                if (pressed) {
                    use("shield_throw", WonderAbilities.SHIELD_THROW);
                }
                if (variant) {
                    use("cataclysm", WonderAbilities.CATACLYSM);
                }
            }
            default -> {
            }
        }
    }

    private static void use(String cd, int action) {
        if (ScarletClient.coolingDown(cd)) {
            ScarletClient.denied(cd);
        } else {
            send(action, 0);
        }
    }

    public static void send(int action, int value) {
        FlashNetwork.CHANNEL.sendToServer(new HeroActionPacket(HeroSyncPacket.WONDER, action, value));
    }
}

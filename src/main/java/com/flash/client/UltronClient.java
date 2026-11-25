package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroActionPacket;
import com.flash.network.HeroSyncPacket;
import com.flash.ultron.UltronAbilities;
import com.flash.ultron.UltronPowers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ultron on the client: who he is (flying, his beam) and the keys of his modes (Energy, Legion, Control: R and Z).
 * His body: UltronLayer (the player's skin is hidden under it).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltronClient {

    private static final Map<Integer, Integer> ULTRONS = new HashMap<>();

    private UltronClient() {
    }

    public static void reset() {
        ULTRONS.clear();
    }

    public static void set(int entity, boolean has, int data) {
        if (has) {
            ULTRONS.put(entity, data);
        } else {
            ULTRONS.remove(entity);
        }
    }

    public static boolean isUltron(Entity e) {
        return e != null && ULTRONS.containsKey(e.getId());
    }

    public static boolean flying(Player p) {
        if (!isUltron(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying
                : (ULTRONS.get(p.getId()) & UltronPowers.F_FLYING) != 0;
    }

    public static boolean beaming(Entity e) {
        Integer d = ULTRONS.get(e.getId());
        return d != null && (d & UltronPowers.F_BEAM) != 0;
    }

    private static boolean useWas;

    /** Every tick from YasAbilities with his mode now (null: not him). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "ultron_energy" -> {
                if (pressed) {
                    use("u_blast", UltronAbilities.BLAST);
                }
                if (variant) {
                    use("u_beam", UltronAbilities.ANNIHILATE);
                }
            }
            case "legion" -> {
                if (pressed) {
                    use("u_legion", UltronAbilities.LEGION);
                }
                if (variant) {
                    use("u_command", UltronAbilities.COMMAND);
                }
            }
            case "control" -> {
                if (pressed) {
                    use("u_hack", UltronAbilities.HACK);
                }
                if (variant) {
                    use("u_emp", UltronAbilities.EMP);
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
        FlashNetwork.CHANNEL.sendToServer(new HeroActionPacket(HeroSyncPacket.ULTRON, action, value));
    }

    /** The skin never shows through his body. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!event.isCanceled() && isUltron(event.getEntity())) {
            UltronLayer.hideSkin(event.getRenderer().getModel());
        }
    }
}

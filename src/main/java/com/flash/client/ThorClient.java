package com.flash.client;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.ThorAbilityPacket;
import com.flash.thor.ThorAbility;
import com.flash.thor.ThorPowers;
import com.flash.thor.ThorState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Thor keys: the abilities share the Flash ability keys (V Overcharge, J Hammer Spark). */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ThorClient {

    private static final boolean[] KEY_DOWN = new boolean[ThorAbility.values().length];

    private ThorClient() {
    }

    public static KeyMapping key(ThorAbility ability) {
        if (ability.specialKey() == ThorAbility.KEY_V) {
            return FlashKeys.INCREASE_SPEED;
        }
        if (ability.specialKey() == ThorAbility.KEY_J) {
            return FlashKeys.TOGGLE_SPEED;
        }
        return FlashAbilityKeys.mapping(ability.keySlot());
    }

    /** True when an ability is running for the local player (HUD highlight). */
    public static boolean active(ThorAbility ability) {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return false;
        }
        return switch (ability) {
            case FLIGHT -> p.getAbilities().flying;
            case LIGHTNING_BLAST -> ThorState.flag(p, ThorPowers.F_BLAST);
            case HAMMER_SPIN -> ThorState.flag(p, ThorPowers.F_SPIN);
            case WARRIORS_FURY -> ThorState.flag(p, ThorPowers.F_FURY);
            case OVERCHARGE -> ThorState.flag(p, ThorPowers.F_OVERCHARGE);
            case GOD_OF_THUNDER -> ThorState.flag(p, ThorPowers.F_ULTIMATE);
            case HAMMER_SPARK -> ThorState.flag(p, ThorPowers.F_COMBO);
            default -> false;
        };
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
            return;
        }
        boolean thor = ThorState.isThor(mc.player);
        boolean active = thor && mc.screen == null && !mc.player.isSpectator();
        ThorAbility[] abilities = ThorAbility.values();
        for (int i = 0; i < abilities.length; i++) {
            ThorAbility a = abilities[i];
            KeyMapping key = key(a);
            boolean down = active && key != null && key.isDown();
            if (down == KEY_DOWN[i]) {
                continue;
            }
            KEY_DOWN[i] = down;
            if (down || a.kind() == ThorAbility.Kind.HOLD) {
                FlashNetwork.CHANNEL.sendToServer(new ThorAbilityPacket(i, down));
            }
        }
    }
}

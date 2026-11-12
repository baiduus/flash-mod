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
        if (ability.hidden() || ability.keySlot() == null) {
            return null;
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

    private static final int TAP_TICKS = 5;
    private static final int LAUNCH_AT = TAP_TICKS + 20;
    private static int flightHeld = -1;
    private static boolean flightLaunched;

    /**
     * Flight key on the ground: a tap takes off as before; held, Thor coils and after about a second the ground
     * cracks and he shoots ~20 blocks up, flying at the top. In the air it just toggles flight.
     */
    private static boolean flightKey(Minecraft mc, int index, boolean down) {
        var p = mc.player;
        boolean ground = p.onGround() && !p.getAbilities().flying && !p.isInWater() && !p.isPassenger();
        boolean was = KEY_DOWN[index];
        KEY_DOWN[index] = down;
        if (down && !was) {
            if (!ground) {
                FlashNetwork.CHANNEL.sendToServer(new ThorAbilityPacket(index, true));
                return true;
            }
            flightHeld = 0;
            flightLaunched = false;
            return true;
        }
        if (flightHeld < 0) {
            return true;
        }
        if (down) {
            if (flightLaunched) {
                return true;
            }
            flightHeld++;
            if (flightHeld == TAP_TICKS) {
                SupermanClient.thorCoiling = true;
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(0));
            } else if (flightHeld >= LAUNCH_AT) {
                SupermanClient.thorCoiling = false;
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(1));
                flightLaunched = true;
                SupermanClient.hoverAtTop();
            }
            return true;
        }
        SupermanClient.thorCoiling = false;
        if (!flightLaunched) {
            if (flightHeld >= TAP_TICKS) {
                FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(2));
            }
            FlashNetwork.CHANNEL.sendToServer(new ThorAbilityPacket(index, true));
        }
        flightHeld = -1;
        flightLaunched = false;
        return true;
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
            if (a == ThorAbility.FLIGHT && flightKey(mc, i, down)) {
                continue;
            }
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

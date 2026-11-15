package com.flash.power;

import com.flash.FlashMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The flying heroes move the player themselves and set the game's own flying speed to 0 (or their own speed). When
 * the power goes (the syringe, a command, another power, in creative...) and nothing of the mod flies any more, the
 * game's flight is put back as it was: speed 0.05 (creative flight works again) and, out of creative, no flight.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class FlightGuard {

    private static final float VANILLA = 0.05F;

    private FlightGuard() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) {
            return;
        }
        var ab = p.getAbilities();
        if (Math.abs(ab.getFlyingSpeed() - VANILLA) < 1.0E-4F) {
            return;
        }
        for (HeroPowers.Hero h : HeroPowers.Hero.values()) {
            if (h.has(p)) {
                return;
            }
        }
        if (com.flash.lex.LexSuit.active(p)) {
            return;
        }
        ab.setFlyingSpeed(VANILLA);
        if (!p.isCreative() && !p.isSpectator()) {
            ab.mayfly = false;
            ab.flying = false;
        }
        p.onUpdateAbilities();
    }
}

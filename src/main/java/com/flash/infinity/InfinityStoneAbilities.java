package com.flash.infinity;

import net.minecraft.server.level.ServerPlayer;

/**
 * The stones' own ability types (the original's infinity:* and the palladium ones that need code): the Mind Stone's
 * flight here; the rest arrive stone by stone (Etapa 2).
 */
public final class InfinityStoneAbilities {

    private InfinityStoneAbilities() {
    }

    static void run(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a, boolean on, boolean was) {
        // Etapa 2: each stone's abilities
    }

    /** The Mind Stone's flight (the original's flight speed attribute): free flight while on. */
    static void flight(ServerPlayer p, InfPowers.Ability a, boolean on) {
        if (p.isCreative() || p.isSpectator() || com.flash.power.HeroPowers.hasAny(p)) {
            return;
        }
        var ab = p.getAbilities();
        if (on) {
            if (!ab.mayfly || ab.getFlyingSpeed() != 0.1F) {
                ab.mayfly = true;
                ab.setFlyingSpeed(0.1F);
                p.onUpdateAbilities();
            }
            if (ab.flying) {
                p.fallDistance = 0.0F;
            }
        } else if (ab.mayfly) {
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }
    }
}

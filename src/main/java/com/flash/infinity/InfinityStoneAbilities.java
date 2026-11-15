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
        boolean start = on && !was;
        switch (a.type) {
            // the Space Stone
            case "flash:telekinesis" -> SpaceStone.telekinesis(p, s, a, on, was);
            case "flash:black_hole" -> SpaceStone.blackHoleToggle(p, on, was);
            case "flash:jump_portal" -> {
                if (start) {
                    SpaceStone.jump(p, a.json.has("range") ? a.json.get("range").getAsDouble() : 256.0D);
                }
            }
            case "flash:portal" -> {
                if (start) {
                    InfinityServer.open(p, "portal");
                }
            }
            case "flash:movement_lock" -> SpaceStone.movementLock(p, on);
            // the Mind Stone
            case "palladium:intangibility" -> MindStone.intangibilityTick(p, s, a, on);
            case "flash:clones" -> {
                if (start) {
                    MindStone.clones(p, a);
                }
            }
            case "flash:mind_reading" -> {
                if (start) {
                    MindStone.readMind(p);
                }
            }
            case "flash:disguise" -> {
                if (start) {
                    InfinityServer.open(p, "disguise");
                }
            }
            // the Reality Stone
            case "flash:reality_copy" -> {
                if (start) {
                    RealityStone.copy(p, s, a);
                }
            }
            case "flash:reality_paste" -> {
                if (start) {
                    RealityStone.paste(p, a);
                }
            }
            case "flash:dynamic_scale" -> RealityStone.scale(p, a, on);
            case "flash:score_step" -> {
                if (start) {
                    RealityStone.scoreStep(p, a);
                }
            }
            case "palladium:invisibility" -> RealityStone.invisible(p, on);
            case "palladium:ability_wheel" -> {
                if (start) {
                    InfinityServer.open(p, "wheel:" + a.key());
                }
            }
            case "palladium:energy_beam" -> InfinityBeams.tick(p, a, on, a.json.has("damage")
                    ? a.json.get("damage").getAsFloat() : 5.0F, false);
            default -> {
            }
        }
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

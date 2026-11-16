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
            case "flash:telekinesis" -> HogBridge.telekinesis(p, s, a, on, was);
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
            // the Power Stone
            case "flash:power_level" -> {
                if (on) {
                    PowerStone.levelTick(p, s, a);
                }
            }
            case "flash:power_level_change" -> {
                if (start) {
                    PowerStone.change(p, a);
                }
            }
            case "flash:energy_blast" -> {
                if (start) {
                    PowerStone.energyBlast(p, a);
                }
            }
            case "flash:rocket_burst" -> {
                if (start) {
                    PowerStone.rocketBurst(p, a);
                }
            }
            case "flash:super_punch" -> {
                if (start) {
                    PowerStone.superPunch(p, a);
                }
            }
            case "flash:power_cyclone" -> {
                if (on) {
                    if ("unprotected_cyclone".equals(a.id)) {
                        PowerStone.unprotected(p, a, s);
                    } else {
                        PowerStone.cyclone(p, a, s);
                    }
                }
            }
            case "flash:meteor_storm" -> {
                if (start) {
                    PowerStone.meteorStorm(p, a);
                }
            }
            case "flash:power_blast" -> InfinityBeams.tick(p, a, on, Math.max(10.0F, 10.0F * PowerStone.damage(p)),
                    PowerStone.maxed(p));
            // the Time Stone
            case "flash:time_flow" -> TimeStone.flow(p, a, on, was);
            case "flash:time_flow_change" -> {
                if (start) {
                    TimeStone.flowChange(p, a);
                }
            }
            case "flash:time_freeze" -> {
                if (!HogBridge.freeze(p, on, was)) {
                    TimeStone.freeze(p, on, was);
                }
            }
            case "flash:projectile_stop" -> TimeStone.projectileStop(p, on);
            case "palladium:water_walk" -> TimeStone.waterWalk(p, on);
            // the Soul Stone
            case "flash:soul_heal" -> {
                if (start) {
                    SoulStone.heal(p, a);
                }
            }
            case "flash:soul_drain" -> {
                if (start) {
                    SoulStone.drain(p);
                }
            }
            case "flash:effect_immunity" -> SoulStone.effectImmunity(p, a, start);
            case "flash:pocket_dimension_enter" -> {
                if (start) {
                    SoulStone.enter(p, a);
                }
            }
            case "flash:pocket_dimension_exit" -> {
                if (start) {
                    SoulStone.exit(p, a);
                }
            }
            case "palladium:energy_beam" -> InfinityBeams.tick(p, a, on, a.json.has("damage")
                    ? a.json.get("damage").getAsFloat() : 5.0F, false);
            // the Gauntlet and the Eye of Agamotto
            case "flash:set_gauntlet_skin" -> {
                if (start) {
                    InfinityGauntlet.setSkinAbility(p, a);
                }
            }
            case "flash:eye_of_agamotto" -> {
                if (start) {
                    InfinityOrb.eye(p, a.id);
                }
            }
            // Yas' powers, borrowed (Hand of God)
            case "flash:hog_time" -> HogBridge.time(p, a, on, was);
            case "flash:hog_reality" -> HogBridge.reality(p, a, on, was);
            // the Cosmi-Rod
            case "flash:shockwave" -> {
                if (start) {
                    InfinityExtras.shockwave(p, a);
                }
            }
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
            // a hero's flight: speed 0 here, steered by the aimed flight (SupermanClient) as the others'
            if (!ab.mayfly || ab.getFlyingSpeed() != 0.0F) {
                ab.mayfly = true;
                ab.setFlyingSpeed(0.0F);
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

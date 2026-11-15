package com.flash.client;

import com.flash.FlashMod;
import com.flash.doom.DoomAbilities;
import com.flash.doom.DoomPowers;
import com.flash.network.FlashNetwork;
import com.flash.network.HeroActionPacket;
import com.flash.network.HeroSyncPacket;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Doctor Doom on the client: who he is (his flags: flying, the force field), the keys of his modes (Gauntlets,
 * Force Field, Sorcery: R and Z, R held for the beam) and the flames of his boot thrusters while he flies (the
 * light of his powers: DoomVfx).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class DoomClient {

    private static final Map<Integer, Integer> DOOMS = new HashMap<>();

    private DoomClient() {
    }

    public static void reset() {
        DOOMS.clear();
        DoomVfx.reset();
    }

    public static void set(int entity, boolean has, int flags) {
        if (has) {
            DOOMS.put(entity, flags);
        } else {
            DOOMS.remove(entity);
        }
    }

    public static boolean isDoom(Entity e) {
        return DOOMS.containsKey(e.getId());
    }

    public static boolean flying(Player p) {
        if (!isDoom(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying
                : (DOOMS.get(p.getId()) & DoomPowers.F_FLYING) != 0;
    }

    /** His armour on (the suit key takes it off; his powers stay). */
    public static boolean suited(Entity e) {
        Integer f = DOOMS.get(e.getId());
        return f != null && (f & DoomPowers.F_UNSUITED) == 0;
    }

    /** God Emperor Doom. */
    public static boolean god(Entity e) {
        Integer f = DOOMS.get(e.getId());
        return f != null && (f & DoomPowers.F_GOD) != 0;
    }

    private static int beamCd(Minecraft mc) {
        int t = ScarletClient.cd("doom_beam");
        return mc.player != null && god(mc.player) ? t / 2 : t;
    }

    public static boolean shielded(Entity e) {
        Integer f = DOOMS.get(e.getId());
        return f != null && (f & DoomPowers.F_SHIELD) != 0;
    }

    static Iterable<Integer> ids() {
        return DOOMS.keySet();
    }

    // ------------------------------------------------------------------
    // The keys of his modes
    // ------------------------------------------------------------------

    private static boolean useWas;
    private static int useHeld = -1;
    private static int beamTicks;
    private static final int BEAM_AFTER = 6;
    private static final int BEAM_MAX = 100;

    /** Every tick from YasAbilities with his mode now (null: not him). */
    public static void tickMode(Minecraft mc, String mode, boolean variant) {
        if (mode == null || mc.player == null) {
            useWas = false;
            useHeld = -1;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !useWas;
        useWas = down;
        switch (mode) {
            case "gauntlets" -> {
                if (pressed) {
                    useHeld = 0;
                    beamTicks = 0;
                } else if (down && useHeld >= 0) {
                    useHeld++;
                    if (useHeld >= BEAM_AFTER) {
                        if (ScarletClient.coolingDown("doom_beam")) {
                            if (useHeld == BEAM_AFTER) {
                                ScarletClient.denied("doom_beam");
                            }
                        } else if (++beamTicks > BEAM_MAX) {
                            ScarletClient.startLocal("doom_beam", beamCd(mc));
                        } else if (useHeld % 3 == 0) {
                            send(DoomAbilities.BEAM);
                        }
                    }
                } else if (!down && useHeld >= 0) {
                    if (useHeld < BEAM_AFTER) {
                        use("doom_blast", DoomAbilities.BLAST);
                    } else if (beamTicks > 20 && !ScarletClient.coolingDown("doom_beam")) {
                        ScarletClient.startLocal("doom_beam",
                                beamCd(mc) * Math.min(beamTicks, BEAM_MAX) / BEAM_MAX);
                    }
                    useHeld = -1;
                }
                if (variant) {
                    use("discharge", DoomAbilities.DISCHARGE);
                }
            }
            case "force" -> {
                if (pressed) {
                    use("force_field", DoomAbilities.SHIELD);
                }
                if (variant) {
                    use("force_wave", DoomAbilities.WAVE);
                }
            }
            case "beyonder" -> {
                if (pressed) {
                    use("annihilate", DoomAbilities.ANNIHILATE);
                }
                if (variant) {
                    use("rewrite", DoomAbilities.REWRITE);
                }
            }
            case "sorcery" -> {
                if (pressed) {
                    use("mystic_bolt", DoomAbilities.MYSTIC);
                }
                if (variant) {
                    use("ritual", DoomAbilities.RITUAL);
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
            send(action);
        }
    }

    private static void send(int action) {
        FlashNetwork.CHANNEL.sendToServer(new HeroActionPacket(HeroSyncPacket.DOOM, action, 0));
    }

    // ------------------------------------------------------------------
    // The boot thrusters
    // ------------------------------------------------------------------

    /** His two boots, where the thrusters burn. */
    static Vec3[] boots(Player p, float pt) {
        Vec3 base = p.getPosition(pt);
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        double cx = Math.cos(yaw) * 0.13D;
        double cz = Math.sin(yaw) * 0.13D;
        return new Vec3[] {base.add(cx, 0.05D, cz), base.add(-cx, 0.05D, -cz)};
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || DOOMS.isEmpty()) {
            return;
        }
        for (Player p : mc.level.players()) {
            if (!flying(p) || p.isInvisible()) {
                continue;
            }
            Vec3 v = p.getDeltaMovement();
            // out of the soles of his boots, wherever the flight pose has put them
            Vec3[] feet = p instanceof net.minecraft.client.player.AbstractClientPlayer ap ? DoomLayer.feet(ap) : null;
            Vec3[] b = boots(p, 1.0F);
            for (int i = 0; i < 2; i++) {
                Vec3 at = feet != null ? feet[i * 2] : b[i].add(0.0D, -0.1D, 0.0D);
                Vec3 dir = feet != null ? feet[i * 2 + 1] : new Vec3(0.0D, -1.0D, 0.0D);
                mc.level.addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, dir.x * 0.18D + v.x * 0.6D,
                        dir.y * 0.18D + v.y * 0.6D, dir.z * 0.18D + v.z * 0.6D);
                if (p.tickCount % 3 == 0) {
                    mc.level.addParticle(ParticleTypes.SMOKE, at.x + dir.x * 0.2D, at.y + dir.y * 0.2D,
                            at.z + dir.z * 0.2D, dir.x * 0.06D + v.x * 0.5D, dir.y * 0.06D + v.y * 0.5D,
                            dir.z * 0.06D + v.z * 0.5D);
                }
            }
        }
    }
}

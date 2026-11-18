package com.flash.infinity;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The Time and Reality Stones borrow two of Yas' powers from the Hand of God mod (called by reflection, as Yas
 * does): Temporal's fast-forward (held) and Reality's rewriting of the land (hold to charge, let go to cast). Off
 * the Overworld, or without Hand of God, the stones fall back on their own (time add / the mod's reality wave).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.flash.FlashMod.MODID)
public final class HogBridge {

    private static boolean looked;
    private static Method tempo;
    private static Method gesto;

    private HogBridge() {
    }

    private static void lookup() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            tempo = Class.forName("handofgod.tempo.Tempo").getMethod("tecla", ServerPlayer.class, byte.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            tempo = null;
        }
        try {
            gesto = Class.forName("handofgod.realidade.GestoRealidade").getMethod("receber", ServerPlayer.class,
                    byte.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            gesto = null;
        }
    }

    private static boolean call(Method m, ServerPlayer p, int action) {
        if (m == null) {
            return false;
        }
        try {
            m.invoke(null, p, (byte) action);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** flash:hog_time (held): Hand of God's Accelerate, the same as Yas' Z in Temporal. */
    static void time(ServerPlayer p, InfPowers.Ability a, boolean on, boolean was) {
        lookup();
        boolean hog = tempo != null && p.level().dimension() == Level.OVERWORLD;
        if (on && !was) {
            SpaceStone.sound(p.serverLevel(), p.position(), "time_stone", 1.0F, 1.0F);
            if (hog) {
                call(tempo, p, 0);
            }
        } else if (!on && was && hog) {
            call(tempo, p, 1);
        }
        if (on && !hog) {
            // no Hand of God here: the day itself runs fast
            for (var l : p.server.getAllLevels()) {
                l.setDayTime(l.getDayTime() + 100L);
            }
        }
    }

    /** flash:hog_reality (held): charge while held, rewrite the land when let go (again: undo). */
    static void reality(ServerPlayer p, InfPowers.Ability a, boolean on, boolean was) {
        lookup();
        // Hand of God wants the main hand empty: with something in it, the mod's own wave
        boolean hog = gesto != null && p.level().dimension() == Level.OVERWORLD && p.getMainHandItem().isEmpty();
        if (on && !was) {
            REALITY_HOG.put(p.getUUID(), hog);
            if (hog) {
                com.flash.yas.YasRealityWave.aimHog(p);
                call(gesto, p, 0);
            } else {
                com.flash.yas.YasRealityWave.startFor(p);
            }
        } else if (!on && was && Boolean.TRUE.equals(REALITY_HOG.remove(p.getUUID()))) {
            call(gesto, p, 1);
        }
    }

    private static boolean lookedDust;
    private static Method desfazer;

    /** Hand of God's disintegration (its dust, as Yas'), on any creature: false when it is not there. */
    static boolean dust(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity e,
                        int group, String owner) {
        if (!lookedDust) {
            lookedDust = true;
            try {
                desfazer = Class.forName("handofgod.desintegracao.Desintegracao").getDeclaredMethod("desfazer",
                        net.minecraft.server.level.ServerLevel.class, net.minecraft.world.entity.LivingEntity.class,
                        int.class, String.class);
                desfazer.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                desfazer = null;
            }
        }
        if (desfazer == null) {
            return false;
        }
        try {
            desfazer.invoke(null, level, e, group, owner);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // The Space Stone's telekinesis: the Scarlet Witch's (Hand of God's hold, no implosion, torn apart for five
    // seconds by right-click), light blue (BlueTk, client)
    // ------------------------------------------------------------------

    private static boolean lookedTk;
    private static Method grab;
    private static Method letGo;
    private static final java.util.Map<java.util.UUID, Integer> TK = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long> TEAR_COOLDOWN = new java.util.HashMap<>();

    private static boolean tkReady() {
        if (!lookedTk) {
            lookedTk = true;
            try {
                Class<?> t = Class.forName("handofgod.telecinese.Telecinese");
                grab = t.getDeclaredMethod("agarrar", ServerPlayer.class, int.class);
                grab.setAccessible(true);
                letGo = t.getDeclaredMethod("soltar", ServerPlayer.class);
                letGo.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                grab = null;
            }
        }
        return grab != null;
    }

    /** The Space Stone's holder: Hand of God's telekinesis is his too (YasHandOfGod), as Yas'. */
    public static boolean tkHolder(ServerPlayer p) {
        return InfinityServer.state(p).powers.contains("space_stone") && !com.flash.yas.YasPowers.has(p)
                && !com.flash.scarlet.ScarletPowers.has(p);
    }

    static void telekinesis(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a, boolean on, boolean was) {
        if (!tkReady()) {
            // no Hand of God: the stone's own hold
            SpaceStone.telekinesis(p, s, a, on, was);
        }
    }

    static void release(ServerPlayer p) {
        Integer id = TK.remove(p.getUUID());
        if (id != null) {
            blue(p, id, false);
        }
    }

    static boolean attack(ServerPlayer p) {
        return false;
    }

    /** Right-click: torn apart, whole again five seconds later. */
    static boolean use(ServerPlayer p) {
        if (!tkHolder(p) || com.flash.yas.YasTelekinesis.held(p) == null) {
            return false;
        }
        long now = p.level().getGameTime();
        Long last = TEAR_COOLDOWN.get(p.getUUID());
        if (last == null || now - last >= 40L) {
            TEAR_COOLDOWN.put(p.getUUID(), now);
            Integer id = TK.get(p.getUUID());
            if (id != null) {
                KEEP_BLUE.put(id, now + 60L);
            }
            com.flash.scarlet.ScarletAbilities.dismemberBy(p);
        }
        return true;
    }

    private static final java.util.Map<Integer, Long> KEEP_BLUE = new java.util.HashMap<>();

    /** Every tick: what each holder holds, light blue for everyone (BlueTk mixins). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !tkReady()) {
            return;
        }
        long t = event.getServer().overworld().getGameTime();
        KEEP_BLUE.entrySet().removeIf(en -> {
            if (t < en.getValue()) {
                return false;
            }
            com.flash.network.FlashNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                    new com.flash.network.SoulGrayPacket(-2 - en.getKey(), false));
            return true;
        });
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            net.minecraft.world.entity.LivingEntity held = tkHolder(p) ? com.flash.yas.YasTelekinesis.held(p) : null;
            Integer was = TK.get(p.getUUID());
            int now = held == null ? -1 : held.getId();
            if (was != null && was != now) {
                TK.remove(p.getUUID());
                Long keep = KEEP_BLUE.get(was);
                if (keep == null) {
                    blue(p, was, false);
                }
            }
            if (now >= 0 && (was == null || was != now)) {
                TK.put(p.getUUID(), now);
                blue(p, now, true);
            }
        }
    }

    private static net.minecraft.world.entity.Entity aimed(ServerPlayer p, double range) {
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(p.getLookAngle().scale(range));
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e.isAlive() && !e.isSpectator()
                        && !InfinityImmunity.immune(e, "telekinesis"), eye.distanceToSqr(end));
        return hit == null ? null : hit.getEntity();
    }

    private static void blue(ServerPlayer p, int target, boolean on) {
        com.flash.network.FlashNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.flash.network.SoulGrayPacket(-2 - p.getId(), on));
        com.flash.network.FlashNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.flash.network.SoulGrayPacket(-2 - target, on));
    }

    private static boolean lookedSlow;
    private static Method slowKey;
    private static Method slowOn;

    /**
     * The Time Stone's Time Freeze: Yas' (Hand of God's Super speed: the world all but stopped around her, blows and
     * explosions held in time) toggled with the ability. False: Hand of God is not there (the stone's own freeze).
     */
    static boolean freeze(ServerPlayer p, boolean on, boolean was) {
        if (!lookedSlow) {
            lookedSlow = true;
            try {
                Class<?> c = Class.forName("handofgod.tempo.CameraLenta");
                slowKey = c.getMethod("tecla", ServerPlayer.class);
                slowOn = c.getMethod("ativa", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                slowKey = null;
            }
        }
        if (slowKey == null) {
            return false;
        }
        try {
            boolean active = (Boolean) slowOn.invoke(null, p);
            if ((on && !was && !active) || (!on && was && active)) {
                slowKey.invoke(null, p);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return true;
    }

    /** Holding the Power Stone (its Pull the Sun: Hand of God's Celestial, YasHandOfGod). */
    /** The Reality Stone's holder: Yas' (and the Scarlet Witch's) Manipulation of Reality, by Hand of God. */
    public static boolean realityHolder(ServerPlayer p) {
        return InfinityServer.state(p).powers.contains("reality_stone") && !com.flash.yas.YasPowers.has(p)
                && !com.flash.scarlet.ScarletPowers.has(p);
    }

    public static boolean sunHolder(ServerPlayer p) {
        return InfinityServer.state(p).powers.contains("power_stone") && !com.flash.yas.YasPowers.has(p);
    }

    private static final java.util.Map<java.util.UUID, Boolean> REALITY_HOG = new java.util.HashMap<>();
}

package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.InfinitySyncPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The stones' abilities on the server: what each player bought (kept with the player), its settings, what it has on
 * (the ability key: an action, a toggle, held, pressed twice), the cooldowns; every tick each ability of every power
 * the player has is worked out (unlocked? on?) and run (InfinityAbilities). The player's client gets a copy
 * (InfinitySyncPacket) for the bar and the tree.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityServer {

    private static final String KEY = "flash_infinity";
    /** The scores the client is told (the original's power level, size, the Orb's lock). */
    public static final String[] SCORES = {"DamageCycle", "SizeCycle", "power_stone_open_stage", "orb_opened",
            "orb_lock_progress", "SoulStorage", "TimeFlow"};

    /** A player's running state (what is not kept with the player). */
    public static final class State implements InfinityPowers.View {
        final ServerPlayer player;
        Set<String> powers = new LinkedHashSet<>();
        final Set<String> toggled = new HashSet<>();
        final Set<String> held = new HashSet<>();
        final Set<String> pulse = new HashSet<>();
        final Map<String, Long> armed = new HashMap<>();
        final Map<String, Long> cooldown = new HashMap<>();
        final Set<String> enabled = new HashSet<>();
        final Set<String> wasEnabled = new HashSet<>();
        /** While the enabled abilities are being worked out: the last pass's, read by "ability_enabled". */
        Set<String> lookup;
        /** Ticks an ability has been on (the original's animation timers). */
        final Map<String, Integer> timers = new HashMap<>();
        CompoundTag sent;
        ListTag sentVisual;

        State(ServerPlayer p) {
            this.player = p;
        }

        CompoundTag data() {
            CompoundTag t = this.player.getPersistentData().getCompound(KEY);
            if (!this.player.getPersistentData().contains(KEY)) {
                this.player.getPersistentData().put(KEY, t);
            }
            return t;
        }

        @Override
        public Player player() {
            return this.player;
        }

        @Override
        public Set<String> powers() {
            return this.powers;
        }

        @Override
        public boolean purchased(String key) {
            // every ability comes with its stone, like the other heroes' powers (no buying)
            return true;
        }

        void buy(String key) {
            CompoundTag d = data();
            ListTag l = d.getList("bought", Tag.TAG_STRING);
            l.add(StringTag.valueOf(key));
            d.put("bought", l);
        }

        @Override
        public boolean settingOn(String setting) {
            for (Tag e : data().getList("off", Tag.TAG_STRING)) {
                if (e.getAsString().equals(setting)) {
                    return false;
                }
            }
            return true;
        }

        public void setSetting(String setting, boolean on) {
            CompoundTag d = data();
            ListTag l = d.getList("off", Tag.TAG_STRING);
            l.removeIf(e -> e.getAsString().equals(setting));
            if (!on) {
                l.add(StringTag.valueOf(setting));
            }
            d.put("off", l);
        }

        @Override
        public boolean enabled(String key) {
            return (this.lookup != null ? this.lookup : this.enabled).contains(key);
        }

        @Override
        public boolean cooling(String key) {
            Long t = this.cooldown.get(key);
            return t != null && this.player.level().getGameTime() < t;
        }

        @Override
        public int score(String objective) {
            return InfinityServer.score(this.player, objective);
        }

        @Override
        public int timer(String key) {
            return this.timers.getOrDefault(key, 0);
        }

        public boolean toggledOn(String key) {
            return this.toggled.contains(key);
        }

        public void untoggle(String key) {
            this.toggled.remove(key);
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** The ability types others must know are on (they change how the player looks). */
    static final Set<String> VISUAL = Set.of("palladium:render_layer", "palladium:invisibility",
            "palladium:intangibility", "flash:dynamic_scale", "flash:force_field", "palladium:energy_beam",
            "flash:power_blast", "flash:power_radiation", "palladium:aim", "flash:power_cyclone", "flash:telekinesis",
            "palladium:vibrate", "palladium:animation_timer", "palladium:entity_glow", "flash:time_freeze");

    private InfinityServer() {
    }

    public static State state(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.player != p) {
            s = new State(p);
            STATES.put(p.getUUID(), s);
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Scores (the original keeps some values in the scoreboard)
    // ------------------------------------------------------------------

    public static int score(Player p, String objective) {
        Scoreboard sb = p.level().getScoreboard();
        Objective o = sb.getObjective(objective);
        if (o == null || !sb.hasPlayerScore(p.getScoreboardName(), o)) {
            return 0;
        }
        return sb.getOrCreatePlayerScore(p.getScoreboardName(), o).getScore();
    }

    public static void setScore(Player p, String objective, int value) {
        Scoreboard sb = p.level().getScoreboard();
        Objective o = sb.getObjective(objective);
        if (o == null) {
            o = sb.addObjective(objective, ObjectiveCriteria.DUMMY, Component.literal(objective),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        sb.getOrCreatePlayerScore(p.getScoreboardName(), o).setScore(value);
    }

    // ------------------------------------------------------------------
    // From the client: buy, the ability key, a setting
    // ------------------------------------------------------------------

    public static final int BUY = 0;
    public static final int PRESS = 1;
    public static final int RELEASE = 2;
    public static final int SETTING = 3;

    public static final int SCROLL = 4;
    public static final int ATTACK = 5;
    public static final int USE = 6;
    public static final int PORTAL = 7;
    public static final int DISGUISE = 8;
    public static final int MIND_NAME = 9;
    public static final int OPEN_GAUNTLET = 10;

    public static void handle(ServerPlayer p, int op, String key) {
        switch (op) {
            case SCROLL -> {
                SpaceStone.scroll(p, "+".equals(key) ? -1 : 1);
                return;
            }
            case ATTACK -> {
                if (HogBridge.attack(p)) {
                    return;
                }
                SpaceStone.attack(p);
                return;
            }
            case USE -> {
                if (HogBridge.use(p)) {
                    return;
                }
                SpaceStone.useWhileHolding(p);
                return;
            }
            case DISGUISE -> {
                MindStone.setDisguise(p, key.trim());
                return;
            }
            case OPEN_GAUNTLET -> {
                GauntletMenu.open(p);
                return;
            }
            case MIND_NAME -> {
                MindStone.readByName(p, key.trim());
                return;
            }
            case PORTAL -> {
                String[] parts = key.split("\\|");
                try {
                    SpaceStone.portalTo(p, parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                } catch (RuntimeException e) {
                    p.displayClientMessage(Component.translatable("message.flash.infinity.bad_portal"), true);
                }
                return;
            }
            default -> {
            }
        }
        InfPowers.Ability a = InfPowers.ability(key);
        if (a == null) {
            return;
        }
        State s = state(p);
        if (!s.powers.contains(a.power.id)) {
            return;
        }
        switch (op) {
            case BUY -> buy(p, s, a);
            case PRESS -> press(p, s, a);
            case RELEASE -> s.held.remove(key);
            case SETTING -> InfinityAbilities.clickToggle(p, s, a);
            default -> {
            }
        }
    }

    private static void buy(ServerPlayer p, State s, InfPowers.Ability a) {
        if (!InfinityPowers.buyable(s, a)) {
            return;
        }
        int aspects = a.aspects();
        int levels = a.levels();
        if (!p.isCreative()) {
            if (p.experienceLevel < levels || count(p) < aspects) {
                p.displayClientMessage(Component.translatable("message.flash.infinity.cannot_buy", aspects, levels),
                        true);
                return;
            }
            take(p, aspects);
            if (levels > 0) {
                p.giveExperienceLevels(-levels);
            }
        }
        s.buy(a.key());
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
        sync(p, true);
    }

    /** Aspects of Existence in the inventory. */
    public static int count(Player p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) {
            if (st.is(InfinityItems.ASPECTS_OF_EXISTANCE.get())) {
                n += st.getCount();
            }
        }
        for (ItemStack st : p.getInventory().offhand) {
            if (st.is(InfinityItems.ASPECTS_OF_EXISTANCE.get())) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static void take(Player p, int n) {
        for (var list : java.util.List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (ItemStack st : list) {
                if (n <= 0) {
                    return;
                }
                if (st.is(InfinityItems.ASPECTS_OF_EXISTANCE.get())) {
                    int t = Math.min(n, st.getCount());
                    st.shrink(t);
                    n -= t;
                }
            }
        }
    }

    private static void press(ServerPlayer p, State s, InfPowers.Ability a) {
        String key = a.key();
        if (!InfinityPowers.unlocked(s, a) || a.key == InfPowers.Key.NONE) {
            return;
        }
        long now = p.level().getGameTime();
        switch (a.key) {
            case ACTION -> {
                if (!s.cooling(key)) {
                    s.pulse.add(key);
                }
            }
            case HELD -> {
                if (!s.cooling(key)) {
                    s.held.add(key);
                }
            }
            case TOGGLE -> {
                if (s.toggled.remove(key)) {
                    if (a.cooldown > 0) {
                        s.cooldown.put(key, now + a.cooldown);
                    }
                } else if (!s.cooling(key)) {
                    s.toggled.add(key);
                }
            }
            case DOUBLE_ACTION, DOUBLE_TOGGLE -> {
                if (a.key == InfPowers.Key.DOUBLE_TOGGLE && s.toggled.remove(key)) {
                    return;
                }
                Long armedAt = s.armed.get(key);
                if (armedAt != null && now - armedAt <= 60) {
                    s.armed.remove(key);
                    if (a.key == InfPowers.Key.DOUBLE_ACTION) {
                        s.pulse.add(key);
                    } else {
                        s.toggled.add(key);
                    }
                } else {
                    s.armed.put(key, now);
                    if (a.keyMessage != null) {
                        p.displayClientMessage(Component.literal(a.keyMessage), true);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        State s = state(p);
        Set<String> now = InfinityPowers.powers(p);
        boolean empty = now.isEmpty() && s.powers.isEmpty() && s.wasEnabled.isEmpty();
        s.powers = now;
        if (empty) {
            if (s.sent != null && !s.sent.isEmpty()) {
                sync(p, true);
            }
            return;
        }
        long time = p.level().getGameTime();
        // abilities switched on by other abilities ("ability_enabled"): worked out again until nothing changes, so
        // the order they come in does not matter (the force field's protection, the flow of time's steps...)
        s.lookup = new HashSet<>(s.wasEnabled);
        for (int pass = 0; pass < 4; pass++) {
            s.enabled.clear();
            for (String pid : now) {
                InfPowers.Power power = InfPowers.get(pid);
                if (power == null) {
                    continue;
                }
                for (InfPowers.Ability a : power.abilities.values()) {
                    String key = a.key();
                    boolean on = InfinityPowers.unlocked(s, a);
                    if (on) {
                        on = switch (a.key) {
                            case NONE -> true;
                            case ACTION, DOUBLE_ACTION -> s.pulse.contains(key);
                            case HELD -> s.held.contains(key);
                            case TOGGLE, DOUBLE_TOGGLE -> s.toggled.contains(key);
                        };
                    }
                    if (on) {
                        on = InfinityPowers.all(s, a, a.enabling);
                    }
                    if (!on && (a.key == InfPowers.Key.TOGGLE || a.key == InfPowers.Key.DOUBLE_TOGGLE)
                            && s.toggled.contains(key) && !InfinityPowers.unlocked(s, a)) {
                        s.toggled.remove(key);
                    }
                    if (on) {
                        s.enabled.add(key);
                    }
                }
            }
            if (s.enabled.equals(s.lookup)) {
                break;
            }
            s.lookup = new HashSet<>(s.enabled);
        }
        s.lookup = null;
        // the original's animation timers: up to their max while on, back down while off
        for (String pid : now) {
            InfPowers.Power power = InfPowers.get(pid);
            if (power == null) {
                continue;
            }
            for (InfPowers.Ability a : power.abilities.values()) {
                if (!"palladium:animation_timer".equals(a.type)) {
                    continue;
                }
                int max = a.json.has("max_value") ? a.json.get("max_value").getAsInt() : 10;
                int v = s.timers.getOrDefault(a.key(), a.json.has("start_value") ? a.json.get("start_value").getAsInt()
                        : 0);
                v = s.enabled.contains(a.key()) ? Math.min(max, v + 1) : Math.max(0, v - 1);
                if (v == 0) {
                    s.timers.remove(a.key());
                } else {
                    s.timers.put(a.key(), v);
                }
            }
        }
        // what is on runs (rising edge first, every tick, falling edge)
        Set<String> all = new HashSet<>(s.enabled);
        all.addAll(s.wasEnabled);
        for (String key : all) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a == null) {
                continue;
            }
            boolean on = s.enabled.contains(key);
            boolean was = s.wasEnabled.contains(key);
            if (!"palladium:animation_timer".equals(a.type)) {
                if (on) {
                    s.timers.merge(key, 1, Integer::sum);
                } else {
                    s.timers.remove(key);
                }
            }
            try {
                InfinityAbilities.run(p, s, a, on, was);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().warn("[flash] infinity ability {}: {}", key, e.toString());
            }
            if (was && !on && a.key == InfPowers.Key.HELD && a.cooldown > 0) {
                s.cooldown.put(key, time + a.cooldown);
            }
            if (on && !was && (a.key == InfPowers.Key.ACTION || a.key == InfPowers.Key.DOUBLE_ACTION)
                    && a.cooldown > 0) {
                s.cooldown.put(key, time + a.cooldown);
            }
        }
        s.pulse.clear();
        s.wasEnabled.clear();
        s.wasEnabled.addAll(s.enabled);
        s.armed.values().removeIf(t -> time - t > 60);
        sync(p, false);
    }

    /** Opens a menu of the stones on the player's screen ("portal", "disguise", "mind"...). */
    public static void open(ServerPlayer p, String screen) {
        CompoundTag t = new CompoundTag();
        t.putString("open", screen);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new InfinitySyncPacket(t));
    }

    /** The client's copy: powers, bought, settings off, on, cooldowns left, scores. */
    public static void sync(ServerPlayer p, boolean force) {
        State s = state(p);
        CompoundTag t = new CompoundTag();
        ListTag pw = new ListTag();
        for (String x : s.powers) {
            pw.add(StringTag.valueOf(x));
        }
        t.put("powers", pw);
        t.put("bought", s.data().getList("bought", Tag.TAG_STRING).copy());
        t.put("off", s.data().getList("off", Tag.TAG_STRING).copy());
        ListTag en = new ListTag();
        for (String x : s.enabled) {
            en.add(StringTag.valueOf(x));
        }
        t.put("on", en);
        ListTag tog = new ListTag();
        for (String x : s.toggled) {
            tog.add(StringTag.valueOf(x));
        }
        t.put("toggled", tog);
        CompoundTag cd = new CompoundTag();
        long time = p.level().getGameTime();
        for (Map.Entry<String, Long> e : s.cooldown.entrySet()) {
            if (e.getValue() > time) {
                cd.putLong(e.getKey(), e.getValue() - time);
            }
        }
        CompoundTag sc = new CompoundTag();
        for (String o : SCORES) {
            sc.putInt(o, score(p, o));
        }
        t.put("scores", sc);
        t.putBoolean("orb_taken", InfinityOrb.taken(p.server));
        t.putBoolean("eye_taken", InfinityOrb.eyeTaken(p.server));
        ListTag tags = new ListTag();
        for (String x : p.getTags()) {
            tags.add(StringTag.valueOf(x));
        }
        t.put("tags", tags);
        CompoundTag tm = new CompoundTag();
        for (Map.Entry<String, Integer> e : s.timers.entrySet()) {
            tm.putInt(e.getKey(), e.getValue());
        }
        // the cooldown counts down by itself on the client: sent only when it starts
        CompoundTag key = t.copy();
        ListTag cdKeys = new ListTag();
        for (String k : cd.getAllKeys()) {
            cdKeys.add(StringTag.valueOf(k));
        }
        key.put("cd", cdKeys);
        // what others see (the shields, invisibility, size...): to everyone around
        ListTag vis = new ListTag();
        for (String x : s.enabled) {
            InfPowers.Ability a = InfPowers.ability(x);
            if (a != null && (VISUAL.contains(a.type) || "force_field".equals(a.id))) {
                vis.add(StringTag.valueOf(x));
            } else if (a != null && a.json.has("attribute")
                    && "palladium:flight_speed".equals(a.json.get("attribute").getAsString())) {
                // the stones' flight: a hero's (its pose, its aura) for everyone around
                vis.add(StringTag.valueOf("flash:flyer"));
                if (p.getAbilities().flying) {
                    vis.add(StringTag.valueOf("flash:flying"));
                }
            }
        }
        if (!vis.equals(s.sentVisual)) {
            s.sentVisual = vis;
            CompoundTag v = new CompoundTag();
            v.putInt("entity", p.getId());
            v.put("visual", vis);
            FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                    new InfinitySyncPacket(v));
        }
        if (!force && key.equals(s.sent)) {
            return;
        }
        s.sent = key;
        t.put("cd", cd);
        t.put("timers", tm);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new InfinitySyncPacket(t));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            State s = STATES.get(target.getUUID());
            if (s != null && s.sentVisual != null && !s.sentVisual.isEmpty()) {
                CompoundTag v = new CompoundTag();
                v.putInt("entity", target.getId());
                v.put("visual", s.sentVisual.copy());
                FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new InfinitySyncPacket(v));
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KEY)) {
            event.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        State s = STATES.remove(event.getEntity().getUUID());
        if (s != null && event.getEntity() instanceof ServerPlayer p) {
            for (String key : s.wasEnabled) {
                InfPowers.Ability a = InfPowers.ability(key);
                if (a != null) {
                    InfinityAbilities.run(p, s, a, false, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            for (String o : SCORES) {
                if (p.level().getScoreboard().getObjective(o) == null) {
                    setScore(p, o, 0);
                }
            }
            if (p.level().getScoreboard().getPlayerTeam("peaceful") == null) {
                p.level().getScoreboard().addPlayerTeam("peaceful");
            }
            sync(p, true);
        }
    }
}

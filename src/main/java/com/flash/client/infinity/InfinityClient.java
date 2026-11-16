package com.flash.client.infinity;

import com.flash.infinity.InfinityStone;

import com.flash.FlashMod;
import com.flash.client.FlashKeys;
import com.flash.infinity.InfPowers;
import com.flash.infinity.InfinityPowers;
import com.flash.infinity.InfinityServer;
import com.flash.network.FlashNetwork;
import com.flash.network.InfinityActionPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The stones on the player's client: its copy of the server's state (InfinitySyncPacket), the abilities that can be
 * used now (the original's ability bar), picked with Alt + scroll as the heroes' modes and used with the ability key
 * (R: pressed, held, pressed twice, as each ability asks). While a stone gives abilities, it has the keys (not the
 * hero's modes).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class InfinityClient {

    private static Set<String> powers = new LinkedHashSet<>();
    private static final Set<String> BOUGHT = new HashSet<>();
    private static final Set<String> OFF = new HashSet<>();
    private static final Set<String> ON = new HashSet<>();
    private static final Set<String> TOGGLED = new HashSet<>();
    private static final Map<String, Long> COOLDOWN = new HashMap<>();
    private static final Map<String, Long> COOLDOWN_LEN = new HashMap<>();
    private static final Map<String, Integer> SCORES = new HashMap<>();
    private static final Map<String, Integer> TIMERS = new HashMap<>();
    private static final Map<Integer, Set<String>> VISUAL = new HashMap<>();
    private static String selected;
    private static String pressed;

    static {
        MindClient.hook();
        RealityClient.hook();
    }

    private InfinityClient() {
    }

    public static void receive(CompoundTag t) {
        if (t.contains("visual")) {
            Set<String> v = new HashSet<>();
            for (Tag e : t.getList("visual", Tag.TAG_STRING)) {
                v.add(e.getAsString());
            }
            if (v.isEmpty()) {
                VISUAL.remove(t.getInt("entity"));
            } else {
                VISUAL.put(t.getInt("entity"), v);
            }
            return;
        }
        if (t.contains("disguise_entity")) {
            MindClient.disguise(t.getInt("disguise_entity"), t.getString("disguise"));
            return;
        }
        if (t.contains("scale_entity")) {
            RealityClient.scale(t.getInt("scale_entity"), t.getFloat("scale"));
            return;
        }
        if (t.contains("reality_block")) {
            RealityClient.saved(t.getCompound("reality_block"));
            return;
        }
        if (t.contains("mind")) {
            Minecraft.getInstance().setScreen(new MindCardScreen(t.getCompound("mind")));
            return;
        }
        if (t.contains("open")) {
            InfinityScreens.open(t.getString("open"));
            return;
        }
        MindClient.hook();
        Set<String> p = new LinkedHashSet<>();
        for (Tag e : t.getList("powers", Tag.TAG_STRING)) {
            p.add(e.getAsString());
        }
        powers = p;
        fill(BOUGHT, t, "bought");
        fill(OFF, t, "off");
        fill(ON, t, "on");
        fill(TOGGLED, t, "toggled");
        long now = now();
        CompoundTag cd = t.getCompound("cd");
        for (String k : cd.getAllKeys()) {
            long left = cd.getLong(k);
            Long cur = COOLDOWN.get(k);
            if (cur == null || cur <= now) {
                COOLDOWN_LEN.put(k, left);
            }
            COOLDOWN.put(k, now + left);
        }
        CompoundTag sc = t.getCompound("scores");
        for (String k : sc.getAllKeys()) {
            SCORES.put(k, sc.getInt(k));
        }
        com.flash.infinity.InfinityOrb.clientTaken = t.getBoolean("orb_taken");
        com.flash.infinity.InfinityOrb.clientEyeTaken = t.getBoolean("eye_taken");
        // the player's tags (the burn, the clones...): the conditions read them on this side too
        if (Minecraft.getInstance().player != null && t.contains("tags")) {
            Set<String> own = Minecraft.getInstance().player.getTags();
            own.clear();
            for (Tag e : t.getList("tags", Tag.TAG_STRING)) {
                own.add(e.getAsString());
            }
        }
        TIMERS.clear();
        CompoundTag tm = t.getCompound("timers");
        for (String k : tm.getAllKeys()) {
            TIMERS.put(k, tm.getInt(k));
        }
    }

    private static void fill(Set<String> set, CompoundTag t, String key) {
        set.clear();
        for (Tag e : t.getList(key, Tag.TAG_STRING)) {
            set.add(e.getAsString());
        }
    }

    public static void reset() {
        powers = new LinkedHashSet<>();
        BOUGHT.clear();
        OFF.clear();
        ON.clear();
        TOGGLED.clear();
        COOLDOWN.clear();
        SCORES.clear();
        VISUAL.clear();
        MindClient.reset();
        RealityClient.reset();
        selected = null;
        pressed = null;
    }

    static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    /** The client's view of the conditions. */
    public static final InfinityPowers.View VIEW = new InfinityPowers.View() {
        @Override
        public Player player() {
            return Minecraft.getInstance().player;
        }

        @Override
        public Set<String> powers() {
            return powers;
        }

        @Override
        public boolean purchased(String key) {
            // every ability comes with its stone, like the other heroes' powers
            return true;
        }

        @Override
        public boolean settingOn(String setting) {
            return !OFF.contains(setting);
        }

        @Override
        public boolean enabled(String key) {
            return ON.contains(key);
        }

        @Override
        public boolean cooling(String key) {
            Long t = COOLDOWN.get(key);
            return t != null && now() < t;
        }

        @Override
        public int score(String objective) {
            return SCORES.getOrDefault(objective, 0);
        }

        @Override
        public int timer(String key) {
            return TIMERS.getOrDefault(key, 0);
        }
    };

    public static Set<String> powers() {
        return powers;
    }

    public static boolean hasPowers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        for (String p : powers) {
            // the Snap's burn stays a day after the Gauntlet is off: it is no reason for the stones' screen
            if ("burn".equals(p)) {
                continue;
            }
            InfPowers.Power pw = InfPowers.get(p);
            if (pw != null && !pw.abilities.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** An ability of that type is on for that entity (what everyone sees: shields, invisibility...). */
    public static boolean visible(net.minecraft.world.entity.Entity e, String type) {
        Set<String> v = VISUAL.get(e.getId());
        if (v == null) {
            return false;
        }
        for (String k : v) {
            InfPowers.Ability a = InfPowers.ability(k);
            if (a != null && type.equals(a.type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean visibleKey(net.minecraft.world.entity.Entity e, String key) {
        Set<String> v = VISUAL.get(e.getId());
        return v != null && v.contains(key);
    }

    public static boolean on(String key) {
        return ON.contains(key);
    }

    public static boolean toggled(String key) {
        return TOGGLED.contains(key);
    }

    public static int cooldownLeft(String key) {
        Long t = COOLDOWN.get(key);
        return t == null ? 0 : (int) Math.max(0L, t - now());
    }

    public static int cooldownLength(String key) {
        return (int) (long) COOLDOWN_LEN.getOrDefault(key, 1L);
    }

    /** The stones' order of the modes, then everything else (the Gauntlet, the containers...). */
    private static final List<String> MODE_ORDER = List.of("space_stone", "mind_stone", "reality_stone",
            "power_stone", "time_stone", "soul_stone", "infinity_gauntlet");
    /** The mode now: one power (a stone) and its abilities. */
    private static String mode;

    /** Every ability that can be used now, power by power. */
    public static List<InfPowers.Ability> allBar() {
        List<InfPowers.Ability> out = new ArrayList<>();
        if (Minecraft.getInstance().player == null) {
            return out;
        }
        for (String pid : modes()) {
            out.addAll(barOf(pid));
        }
        return out;
    }

    private static List<InfPowers.Ability> barOf(String pid) {
        List<InfPowers.Ability> out = new ArrayList<>();
        InfPowers.Power pw = InfPowers.get(pid);
        if (pw == null) {
            return out;
        }
        for (InfPowers.Ability a : pw.bar()) {
            if (InfinityPowers.unlocked(VIEW, a)) {
                out.add(a);
            }
        }
        return out;
    }

    /** The modes there are now: each power (stone) with something to use, the stones first. */
    public static List<String> modes() {
        List<String> out = new ArrayList<>();
        if (Minecraft.getInstance().player == null) {
            return out;
        }
        List<String> ps = new ArrayList<>(powers);
        ps.sort(java.util.Comparator.comparingInt(pid -> {
            int i = MODE_ORDER.indexOf(pid);
            return i < 0 ? 100 : i;
        }));
        for (String pid : ps) {
            if (!out.contains(pid) && !barOf(pid).isEmpty()) {
                out.add(pid);
            }
        }
        return out;
    }

    /** The mode now (null: none). */
    public static String mode() {
        List<String> ms = modes();
        if (ms.isEmpty()) {
            return null;
        }
        if (mode == null || !ms.contains(mode)) {
            mode = ms.get(0);
        }
        return mode;
    }

    /** The abilities of the mode now, in its order. */
    public static List<InfPowers.Ability> bar() {
        String m = mode();
        return m == null ? new ArrayList<>() : barOf(m);
    }

    public static InfPowers.Ability selected() {
        List<InfPowers.Ability> bar = bar();
        if (bar.isEmpty()) {
            return null;
        }
        for (InfPowers.Ability a : bar) {
            if (a.key().equals(selected)) {
                return a;
            }
        }
        selected = bar.get(0).key();
        return bar.get(0);
    }

    /** The mode's name: its power's. */
    public static String modeName(String pid) {
        InfinityStone st = InfinityStone.byId(pid);
        if (st != null) {
            return new net.minecraft.world.item.ItemStack(st.item()).getHoverName().getString();
        }
        InfPowers.Power pw = pid == null ? null : InfPowers.get(pid);
        return pw == null ? "" : pw.name;
    }

    private static void step(int dir) {
        List<InfPowers.Ability> bar = bar();
        if (bar.isEmpty()) {
            return;
        }
        InfPowers.Ability cur = selected();
        int i = Math.max(0, bar.indexOf(cur));
        InfPowers.Ability next = bar.get(Math.floorMod(i + dir, bar.size()));
        selected = next.key();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.flash.infinity.selected",
                    InfinityScreen.title(next)), true);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.6F);
        }
    }

    /** The stones have the keys now (Alt + scroll and R). */
    public static boolean takesKeys() {
        return !bar().isEmpty();
    }

    public static void send(int op, String key) {
        FlashNetwork.CHANNEL.sendToServer(new InfinityActionPacket(op, key));
    }

    /** One of the player's abilities of that type is on now. */
    public static boolean anyOn(String type) {
        for (String k : ON) {
            InfPowers.Ability a = InfPowers.ability(k);
            if (a != null && type.equals(a.type)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDelta() == 0.0D) {
            return;
        }
        if (!Screen.hasAltDown()) {
            // telekinesis holds something: closer / farther
            if (anyOn("flash:telekinesis")) {
                event.setCanceled(true);
                send(InfinityServer.SCROLL, event.getScrollDelta() > 0.0D ? "+" : "-");
                return;
            }
            // the wheel steps a value (size, power level, the flow of time) while its switch is on
            List<InfPowers.Ability> wheel = byKeyType(event.getScrollDelta() > 0.0D ? "scroll_up" : "scroll_down");
            if (!wheel.isEmpty()) {
                event.setCanceled(true);
                for (InfPowers.Ability a : wheel) {
                    send(InfinityServer.PRESS, a.key());
                }
            }
            return;
        }
        // Alt + scroll: the next stone's mode
        List<String> ms = modes();
        if (ms.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        int i = Math.max(0, ms.indexOf(mode()));
        int dir = event.getScrollDelta() > 0.0D ? -1 : 1;
        mode = ms.get(Math.floorMod(i + dir, ms.size()));
        selected = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InfinityStone st = InfinityStone.byId(mode);
            mc.player.displayClientMessage(Component.translatable("message.flash.infinity.mode",
                    Component.literal(modeName(mode)).withStyle(s -> s.withColor(st == null ? 0xE0C080 : st.glint))),
                    true);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.2F);
        }
    }

    /** The unlocked abilities used by that key type now (scroll_up, scroll_down, right_click). */
    static List<InfPowers.Ability> byKeyType(String keyType) {
        List<InfPowers.Ability> out = new ArrayList<>();
        for (String pid : powers) {
            InfPowers.Power pw = InfPowers.get(pid);
            if (pw == null) {
                continue;
            }
            for (InfPowers.Ability a : pw.abilities.values()) {
                if (keyType.equals(a.keyType) && a.key != InfPowers.Key.NONE && InfinityPowers.unlocked(VIEW, a)
                        && !VIEW.cooling(a.key())) {
                    out.add(a);
                }
            }
        }
        return out;
    }

    /** Attacking throws what telekinesis holds (or the black hole); using locks it in space. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && !anyOn("flash:telekinesis")) {
            List<InfPowers.Ability> rc = byKeyType("right_click");
            if (!rc.isEmpty()) {
                event.setCanceled(true);
                event.setSwingHand(true);
                for (InfPowers.Ability a : rc) {
                    send(InfinityServer.PRESS, a.key());
                }
                return;
            }
        }
        if (event.isAttack() && (anyOn("flash:telekinesis") || anyOn("flash:black_hole"))) {
            event.setCanceled(true);
            event.setSwingHand(true);
            send(InfinityServer.ATTACK, "");
        } else if (event.isUseItem() && anyOn("flash:telekinesis")) {
            event.setCanceled(true);
            send(InfinityServer.USE, "");
        }
    }

    /** The movement lock: no walking, strafing or jumping. */
    @SubscribeEvent
    public static void onInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (anyOn("flash:movement_lock")) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            pressed = null;
            return;
        }
        // Z: the mode's next ability (Shift: the one before)
        if (takesKeys()) {
            while (FlashKeys.YAS_VARIANT.consumeClick()) {
                if (mc.screen == null) {
                    step(Screen.hasShiftDown() ? -1 : 1);
                }
            }
        }
        InfPowers.Ability a = selected();
        boolean down = a != null && FlashKeys.YAS_USE.isDown() && mc.screen == null;
        if (down && pressed == null) {
            pressed = a.key();
            if (VIEW.cooling(pressed) && a.key != InfPowers.Key.TOGGLE) {
                mc.player.displayClientMessage(Component.translatable("message.flash.infinity.cooldown",
                        InfinityScreen.title(a), (cooldownLeft(pressed) + 19) / 20), true);
            }
            send(InfinityServer.PRESS, pressed);
        } else if (!down && pressed != null) {
            send(InfinityServer.RELEASE, pressed);
            pressed = null;
        }
    }
}

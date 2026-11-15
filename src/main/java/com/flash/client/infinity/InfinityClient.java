package com.flash.client.infinity;

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
            return BOUGHT.contains(key);
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

    /** The abilities that can be used now, in the bar's order (power by power). */
    public static List<InfPowers.Ability> bar() {
        List<InfPowers.Ability> out = new ArrayList<>();
        if (Minecraft.getInstance().player == null) {
            return out;
        }
        for (String pid : powers) {
            InfPowers.Power pw = InfPowers.get(pid);
            if (pw == null) {
                continue;
            }
            for (InfPowers.Ability a : pw.bar()) {
                if (InfinityPowers.unlocked(VIEW, a)) {
                    out.add(a);
                }
            }
        }
        return out;
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
            }
            return;
        }
        List<InfPowers.Ability> bar = bar();
        if (bar.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        InfPowers.Ability cur = selected();
        int i = Math.max(0, bar.indexOf(cur));
        int dir = event.getScrollDelta() > 0.0D ? 1 : -1;
        InfPowers.Ability next = bar.get(Math.floorMod(i + dir, bar.size()));
        selected = next.key();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.flash.infinity.selected",
                    InfinityScreen.title(next)), true);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.6F);
        }
    }

    /** Attacking throws what telekinesis holds (or the black hole); using locks it in space. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
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

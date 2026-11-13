package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Yas's combat modes over the powers of Hand of God (embedded): each mode is one power (Celestial, Temporal,
 * Reality, Matter, Telekinesis, Super speed, Heat vision, Void, Electrokinesis) and they all use the same keys:
 * <ul>
 * <li>{@link FlashKeys#YAS_USE} (R): the ability of the mode (held or pressed as Hand of God wants it; Shift still
 * gives the area / cone variants);</li>
 * <li>{@link FlashKeys#YAS_VARIANT} (Z): the second key of a mode that has one (Temporal: R marks and rewinds, Z
 * speeds up the day);</li>
 * <li>Alt + scroll, or a click in the K menu, changes the mode; the panel at the bottom right shows the mode (icon,
 * name, its abilities).</li>
 * </ul>
 * How: Hand of God lets only the chosen power act and reads its own keys, so the mode is chosen in it
 * ({@code Selecao.escolher}) and its keys are pointed at R / Z for the mode's power and left unbound for the others
 * (they come back as they were when she is no longer Yas). Reached by reflection.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class YasAbilities {

    /** The nine modes, in order (Hand of God's {@code Poder} names). */
    public static final String[] POWERS = {"CELESTE", "TEMPORAL", "REALIDADE", "MATERIA", "TELECINESE",
            "SUPERVELOCIDADE", "VISAO_TERMICA", "VOID", "ELETROCINESE"};
    /** Hand of God's key of each mode, put on R (Temporal's second key goes on Z). */
    private static final String[] MAIN_KEY = {"celeste", "voltar", "realidade", "desintegrar", "telecinese",
            "camera_lenta", "visao_termica", "sombra", "eletrocinese"};
    private static final String VARIANT_KEY = "acelerar";
    /** Every Hand of God key the modes manage. */
    private static final String[] ALL_KEYS = {"celeste", "acelerar", "voltar", "realidade", "desintegrar",
            "telecinese", "camera_lenta", "visao_termica", "sombra", "eletrocinese"};

    private static boolean looked;
    private static Method escolher;
    private static Method avisar;
    private static Method atual;
    private static Method linhas;
    private static Method icone;
    private static Field cor;
    private static Object[] poderes;

    /** The user's own Hand of God keys, kept while the modes borrow them. */
    private static final Map<String, InputConstants.Key> ORIGINAL = new HashMap<>();
    private static String applied = "";

    private YasAbilities() {
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                Class<?> poder = Class.forName("handofgod.Poder");
                Class<?> selecao = Class.forName("handofgod.client.Selecao");
                escolher = selecao.getMethod("escolher", poder);
                avisar = selecao.getMethod("avisarServidor");
                atual = selecao.getMethod("poder");
                linhas = Class.forName("handofgod.client.Teclas").getMethod("linhas", poder);
                cor = poder.getField("cor");
                try {
                    icone = Class.forName("handofgod.client.Icones").getDeclaredMethod("de", poder);
                    icone.setAccessible(true);
                } catch (ReflectiveOperationException e) {
                    icone = null;
                }
                poderes = new Object[POWERS.length];
                for (int i = 0; i < POWERS.length; i++) {
                    poderes[i] = poder.getMethod("valueOf", String.class).invoke(null, POWERS[i]);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                poderes = null;
            }
        }
        return poderes != null;
    }

    public static boolean available() {
        return ready();
    }

    public static Component name(int i) {
        return Component.translatable("poder.hand_of_god." + POWERS[i].toLowerCase(java.util.Locale.ROOT));
    }

    /** The mode now (-1 none chosen yet). */
    public static int selected() {
        if (!ready()) {
            return -1;
        }
        try {
            Object p = atual.invoke(null);
            for (int i = 0; i < poderes.length; i++) {
                if (poderes[i] == p) {
                    return i;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // nothing chosen
        }
        return -1;
    }

    /** Picks a mode (Alt + scroll, the K menu). */
    public static void select(int i) {
        if (!ready() || i < 0 || i >= POWERS.length) {
            return;
        }
        if (i != selected()) {
            try {
                escolher.invoke(null, poderes[i]);
                avisar.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                // Hand of God changed: nothing to do
            }
        }
        bind(i);
    }

    /** What the mode does, key by key (Hand of God's own lines, with the keys now on R / Z). */
    @SuppressWarnings("unchecked")
    public static List<Component> lines(int i) {
        if (!ready() || i < 0) {
            return List.of();
        }
        try {
            return (List<Component>) linhas.invoke(null, poderes[i]);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return List.of();
        }
    }

    public static int color(int i) {
        try {
            return 0xFF000000 | cor.getInt(poderes[i]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0xFFFFE89A;
        }
    }

    /** The pixel-art icon of the mode (Hand of God's: '#' the colour, '+' lighter), {@code px} pixels a dot. */
    public static void drawIcon(GuiGraphics g, int i, int x, int y, int px) {
        String[] rows = null;
        if (icone != null && i >= 0) {
            try {
                rows = (String[]) icone.invoke(null, poderes[i]);
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                rows = null;
            }
        }
        int c = i >= 0 ? color(i) : 0xFFFFE89A;
        if (rows == null) {
            g.fill(x + px * 3, y + px * 3, x + px * 9, y + px * 9, c);
            return;
        }
        int light = 0xFF000000 | ((((c >> 16) & 255) + 255) / 2 << 16) | ((((c >> 8) & 255) + 255) / 2 << 8)
                | (((c & 255) + 255) / 2);
        for (int r = 0; r < rows.length; r++) {
            for (int k = 0; k < rows[r].length(); k++) {
                char ch = rows[r].charAt(k);
                if (ch == '#' || ch == '+') {
                    g.fill(x + k * px, y + r * px, x + (k + 1) * px, y + (r + 1) * px, ch == '#' ? c : light);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // The keys
    // ------------------------------------------------------------------

    private static KeyMapping hogKey(String name) {
        String full = "key.hand_of_god." + name;
        for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
            if (m.getName().equals(full)) {
                return m;
            }
        }
        return null;
    }

    /** Hand of God's keys pointed at R / Z for the mode, the others unbound. */
    private static void bind(int mode) {
        String want = mode + ":" + FlashKeys.YAS_USE.saveString() + ":" + FlashKeys.YAS_VARIANT.saveString();
        if (want.equals(applied)) {
            return;
        }
        for (String n : ALL_KEYS) {
            KeyMapping k = hogKey(n);
            if (k == null) {
                continue;
            }
            ORIGINAL.putIfAbsent(n, k.getKey());
            InputConstants.Key to = InputConstants.UNKNOWN;
            if (mode >= 0 && n.equals(MAIN_KEY[mode])) {
                to = FlashKeys.YAS_USE.getKey();
            } else if (mode >= 0 && "TEMPORAL".equals(POWERS[mode]) && n.equals(VARIANT_KEY)) {
                to = FlashKeys.YAS_VARIANT.getKey();
            }
            k.setKey(to);
        }
        KeyMapping.resetMapping();
        applied = want;
    }

    /** Back to the user's own Hand of God keys. */
    private static void unbind() {
        if (ORIGINAL.isEmpty()) {
            return;
        }
        for (Map.Entry<String, InputConstants.Key> e : ORIGINAL.entrySet()) {
            KeyMapping k = hogKey(e.getKey());
            if (k != null) {
                k.setKey(e.getValue());
            }
        }
        ORIGINAL.clear();
        KeyMapping.resetMapping();
        applied = "";
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !PowerTheme.localYas() || !ready()) {
            unbind();
            return;
        }
        int mode = selected();
        if (mode < 0) {
            select(0);
        } else {
            bind(mode);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        unbind();
    }

    // ------------------------------------------------------------------
    // The flight key (as Superman's and Thor's, the same key)
    // ------------------------------------------------------------------

    private static final int TAP_TICKS = 5;
    private static final int LAUNCH_AT = TAP_TICKS + 20;
    private static int flightHeld = -1;
    private static boolean flightWas;
    private static boolean flightLaunched;

    /**
     * The flight key: a tap toggles the flight (on the ground she lifts off); held on the ground she coils and
     * after about a second shoots ~30 blocks up and flies at the top.
     */
    @SubscribeEvent
    public static void onFlightTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (event.phase != TickEvent.Phase.END || p == null || !PowerTheme.localYas()) {
            flightHeld = -1;
            flightWas = false;
            return;
        }
        KeyMapping k = FlashAbilityKeys.mapping(com.flash.ability.FlashAbility.WIND_HANDS);
        boolean down = k != null && k.isDown() && mc.screen == null;
        boolean was = flightWas;
        flightWas = down;
        boolean ground = p.onGround() && !p.getAbilities().flying && !p.isInWater() && !p.isPassenger();
        if (down && !was) {
            if (!ground) {
                p.getAbilities().flying = !p.getAbilities().flying;
                p.onUpdateAbilities();
                return;
            }
            flightHeld = 0;
            flightLaunched = false;
            return;
        }
        if (flightHeld < 0) {
            return;
        }
        if (down) {
            if (flightLaunched) {
                return;
            }
            flightHeld++;
            if (flightHeld == TAP_TICKS) {
                SupermanClient.thorCoiling = true;
                com.flash.network.FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(0));
            } else if (flightHeld >= LAUNCH_AT) {
                SupermanClient.thorCoiling = false;
                com.flash.network.FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(1));
                flightLaunched = true;
                SupermanClient.hoverAtTop();
            }
            return;
        }
        SupermanClient.thorCoiling = false;
        if (!flightLaunched) {
            if (flightHeld >= TAP_TICKS) {
                com.flash.network.FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(2));
            } else {
                // a tap on the ground: up and flying
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.setDeltaMovement(p.getDeltaMovement().x, 0.5D, p.getDeltaMovement().z);
            }
        }
        flightHeld = -1;
        flightLaunched = false;
    }

    /**
     * Hand of God's own ability wheel / menu never opens (Yas uses her modes; nobody else has the powers) and its
     * ability HUD is replaced by the mode panel.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreen(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && event.getNewScreen().getClass().getName()
                .equals("handofgod.client.PainelTela")) {
            event.setCanceled(true);
        }
    }

    private static final net.minecraft.resources.ResourceLocation HOG_HUD =
            new net.minecraft.resources.ResourceLocation("hand_of_god", "habilidade_hud");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (HOG_HUD.equals(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.player.isSpectator() && PowerTheme.localYas()
                && ready();
    }

    private static Method holding;

    /** Hand of God's telekinesis is holding a creature now (client). */
    private static boolean holding() {
        try {
            if (holding == null) {
                holding = Class.forName("handofgod.client.TelecineseCliente").getMethod("ocupado");
            }
            return (Boolean) holding.invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /**
     * Alt + scroll: the next / previous mode. Scroll alone while the telekinesis holds a creature: it comes closer
     * (down) or goes farther (up) instead of the hotbar.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active() || event.getScrollDelta() == 0.0D) {
            return;
        }
        if (!Screen.hasAltDown()) {
            if (holding()) {
                event.setCanceled(true);
                com.flash.network.FlashNetwork.CHANNEL.sendToServer(
                        new com.flash.network.YasTelekinesisPacket(event.getScrollDelta() > 0.0D ? 1 : -1));
            }
            return;
        }
        event.setCanceled(true);
        int cur = selected();
        int dir = event.getScrollDelta() > 0.0D ? 1 : -1; // up: the next mode, down: back
        int next = cur < 0 ? 0 : Math.floorMod(cur + dir, POWERS.length);
        select(next);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.flash.yas_mode", name(next)), true);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.6F);
        }
    }

    /** The lines for the panel, cut to a width (Hand of God's text can be long). */
    public static List<String> panelLines(net.minecraft.client.gui.Font font, int mode, int width) {
        List<String> out = new ArrayList<>();
        for (Component c : lines(mode)) {
            for (net.minecraft.util.FormattedCharSequence seq : font.split(c, width)) {
                StringBuilder sb = new StringBuilder();
                seq.accept((index, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                out.add(sb.toString());
            }
        }
        return out;
    }
}

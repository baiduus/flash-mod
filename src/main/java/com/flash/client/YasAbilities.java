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

    private static Component hogName(int i) {
        return Component.translatable("poder.hand_of_god." + POWERS[i].toLowerCase(java.util.Locale.ROOT));
    }

    /** Hand of God's power now (-1 none chosen yet). */
    private static int hogSelected() {
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

    private static void hogSelect(int i) {
        if (!ready() || i < 0 || i >= POWERS.length) {
            return;
        }
        if (i != hogSelected()) {
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
    private static List<Component> hogLines(int i) {
        if (!ready() || i < 0) {
            return List.of();
        }
        List<Component> out = new ArrayList<>();
        try {
            out.addAll((List<Component>) linhas.invoke(null, poderes[i]));
        } catch (ReflectiveOperationException | ClassCastException e) {
            // only ours
        }
        String extra = "hud.flash.yas.extra." + POWERS[i].toLowerCase(java.util.Locale.ROOT);
        if (net.minecraft.client.resources.language.I18n.exists(extra)) {
            out.add(Component.translatable(extra).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        return out;
    }

    private static int hogColor(int i) {
        try {
            return 0xFF000000 | cor.getInt(poderes[i]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0xFFFFE89A;
        }
    }

    /** The pixel-art icon of the mode (Hand of God's: '#' the colour, '+' lighter), {@code px} pixels a dot. */
    private static void hogDrawIcon(GuiGraphics g, int i, int c, int x, int y, int px) {
        String[] rows = null;
        if (icone != null && i >= 0) {
            try {
                rows = (String[]) icone.invoke(null, poderes[i]);
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                rows = null;
            }
        }
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
    // The modes of the hero now (Yas: the nine of Hand of God; the Scarlet Witch: Reality and Telekinesis of
    // Hand of God, then her own three, ScarletClient)
    // ------------------------------------------------------------------

    public static final String[] SCARLET_MODES = {"realidade", "telecinese", "mind", "energy", "necro"};
    private static final int[] SCARLET_COLORS = {0xFFE0263A, 0xFFFF3B5C, 0xFFB0306F, 0xFFFF2A2A, 0xFF8E1B2E};
    /** Her own modes' icons (12 x 12, '#' the colour, '+' lighter). */
    private static final String[][] SCARLET_ICONS = {
            null, null,
            {"....####....", "..##++++##..", ".#+......+#.", "#+..####..+#", "#..#....#..#", "#..#.##.#..#",
                    "#..#.##.#..#", "#..#....#..#", "#+..####..+#", ".#+......+#.", "..##++++##..", "....####...."},
            {".....#......", "....##......", "...###......", "..####++....", ".#####++++..", "######+++++#",
                    "#+++++######", "..++++#####.", "....++####..", "......###...", "......##....", "......#....."},
            {"...######...", "..#++++++#..", ".#+######+#.", ".#+#.##.#+#.", ".#+#.##.#+#.", ".#++####++#.",
                    "..#++##++#..", "...#+##+#...", "...#.##.#...", "...#....#...", "....####....", "............"}};

    public static boolean scarlet() {
        return PowerTheme.localScarlet();
    }

    public static int count() {
        return scarlet() ? SCARLET_MODES.length : POWERS.length;
    }

    /** Hand of God's power of a mode (-1: one of the Scarlet Witch's own). */
    public static int hogOf(int i) {
        if (!scarlet()) {
            return i;
        }
        return i == 0 ? 2 : i == 1 ? 4 : -1;
    }

    public static String modeId(int i) {
        return scarlet() ? SCARLET_MODES[i] : POWERS[i].toLowerCase(java.util.Locale.ROOT);
    }

    private static int scarletMode;

    /** The mode now (-1 none chosen yet). */
    public static int selected() {
        return scarlet() ? scarletMode : hogSelected();
    }

    /** Picks a mode (Alt + scroll, the K menu). */
    public static void select(int i) {
        if (i < 0 || i >= count()) {
            return;
        }
        if (scarlet()) {
            scarletMode = i;
        }
        int h = hogOf(i);
        if (h >= 0) {
            hogSelect(h);
        }
        bind(h);
    }

    public static Component name(int i) {
        int h = hogOf(i);
        return h >= 0 ? hogName(h) : Component.translatable("scarlet.flash.mode." + SCARLET_MODES[i]);
    }

    public static Component desc(int i) {
        int h = hogOf(i);
        return h >= 0 ? Component.translatable("poder.hand_of_god." + POWERS[h].toLowerCase(java.util.Locale.ROOT)
                + ".desc") : Component.translatable("scarlet.flash.mode." + SCARLET_MODES[i] + ".desc");
    }

    /** The mode has a second key (Z). */
    public static boolean usesVariant(int i) {
        if (scarlet()) {
            return hogOf(i) < 0;
        }
        return "TEMPORAL".equals(POWERS[i]) || "CELESTE".equals(POWERS[i]);
    }

    public static List<Component> lines(int i) {
        if (i < 0 || i >= count()) {
            return List.of();
        }
        int h = hogOf(i);
        List<Component> out = new ArrayList<>(h >= 0 ? hogLines(h) : List.of());
        KeyMapping fly = FlashAbilityKeys.mapping(com.flash.ability.FlashAbility.WIND_HANDS);
        if (fly != null) {
            out.add(Component.translatable("hud.flash.yas.flight_key", fly.getTranslatedKeyMessage())
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        String base = scarlet() ? "hud.flash.scarlet." + SCARLET_MODES[i] : null;
        if (base != null) {
            for (int n = 1; n <= 8 && net.minecraft.client.resources.language.I18n.exists(base + "." + n); n++) {
                out.add(Component.translatable(base + "." + n).withStyle(h >= 0 ? net.minecraft.ChatFormatting.RED
                        : net.minecraft.ChatFormatting.GRAY));
            }
        }
        return out;
    }

    public static int color(int i) {
        if (scarlet()) {
            return i >= 0 && i < SCARLET_COLORS.length ? SCARLET_COLORS[i] : 0xFFE0263A;
        }
        return i >= 0 ? hogColor(i) : 0xFFFFE89A;
    }

    /** The pixel-art icon of the mode ('#' the colour, '+' lighter), {@code px} pixels a dot. */
    public static void drawIcon(GuiGraphics g, int i, int x, int y, int px) {
        int c = color(i);
        if (scarlet() && i >= 0 && i < SCARLET_ICONS.length && SCARLET_ICONS[i] != null) {
            int light = 0xFF000000 | ((((c >> 16) & 255) + 255) / 2 << 16) | ((((c >> 8) & 255) + 255) / 2 << 8)
                    | (((c & 255) + 255) / 2);
            String[] rows = SCARLET_ICONS[i];
            for (int r = 0; r < rows.length; r++) {
                for (int k = 0; k < rows[r].length(); k++) {
                    char ch = rows[r].charAt(k);
                    if (ch == '#' || ch == '+') {
                        g.fill(x + k * px, y + r * px, x + (k + 1) * px, y + (r + 1) * px, ch == '#' ? c : light);
                    }
                }
            }
            return;
        }
        hogDrawIcon(g, hogOf(i), c, x, y, px);
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
        String want = mode + ":" + scarlet() + ":" + FlashKeys.YAS_USE.saveString() + ":" + FlashKeys.YAS_VARIANT.saveString();
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
            if (mode >= 0 && n.equals(MAIN_KEY[mode]) && !"TELECINESE".equals(POWERS[mode])
                    && !"REALIDADE".equals(POWERS[mode])) {
                // (the telekinesis and the reality are held by hand below, not by the key)
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
        if (mc.player == null || !(PowerTheme.localYas() || PowerTheme.localScarlet()) || !ready()) {
            unbind();
            ScarletClient.tickMode(mc, null, false);
            return;
        }
        int mode = selected();
        if (mode < 0) {
            select(0);
            mode = 0;
        }
        int h = hogOf(mode);
        if (h >= 0 && hogSelected() != h) {
            hogSelect(h);
        }
        bind(h);
        // R again: what the last press was still sounding stops, the new one starts alone (no pile of echoes)
        boolean useDown = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        if (useDown && !soundWas) {
            restartSounds(mc);
        }
        soundWas = useDown;
        telekinesis(mc, h >= 0 && "TELECINESE".equals(POWERS[h]));
        reality(mc, h >= 0 && "REALIDADE".equals(POWERS[h]));
        boolean variant = false;
        while (FlashKeys.YAS_VARIANT.consumeClick()) {
            variant = true;
        }
        if (scarlet()) {
            ScarletClient.tickMode(mc, h < 0 ? SCARLET_MODES[mode] : null, variant && mc.screen == null);
        } else if (variant && "CELESTE".equals(POWERS[mode]) && mc.screen == null) {
            com.flash.network.FlashNetwork.CHANNEL.sendToServer(new com.flash.network.YasBlackHolePacket());
        }
    }

    private static boolean soundWas;
    /** Hand of God's sounds of a power in use (not the hum of a sun, a moon or a black hole). */
    private static final String[] POWER_SOUNDS = {
            "carga", "soltar", "descida", "chegada", "arremesso", "voo",
            "impacto_estalo", "brasas", "impacto_sugar", "impacto_queda", "impacto_corpo", "impacto_ronco",
            "impacto_longe", "onda_choque", "lua_carga", "lua_soltar", "lua_chegada", "lua_arremesso",
            "lua_voo", "lua_impacto_sugar", "lua_impacto_estalo", "lua_impacto_queda", "lua_impacto_corpo", "lua_impacto_ronco",
            "lua_impacto_longe", "lua_volta", "lua_falha", "energia_descida", "energia_pulso", "energia_chiado",
            "energia_impacto", "lua_despedacar", "realidade_carga", "realidade_onda", "realidade_desfeita", "tempo_acelera",
            "tempo_tique", "tempo_taque", "tempo_tique_reverso", "tempo_taque_reverso", "tempo_marcar", "tempo_volta_inicio",
            "tempo_volta", "tempo_volta_fim", "estalo", "poeira", "poeira_volta", "lenta_ligar",
            "lenta_desligar", "lenta_golpe", "lenta_impacto", "lenta_explosao", "lenta_estrondo", "telecinese_agarrar",
            "telecinese_segurar", "telecinese_arremesso", "telecinese_tensao", "telecinese_estouro", "telecinese_rasgo", "telecinese_cauda",
            "laser_carga", "laser_feixe", "laser_fritura", "laser_estalo", "laser_corte", "sombra_golpe",
            "sombra_golpe2", "eletro_carga", "eletro_descida", "eletro_estalo", "eletro_trovao", "eletro_longe",
            "eletro_arco", "eletro_golpe", "eletro_mergulho"};

    private static void restartSounds(Minecraft mc) {
        var sounds = mc.getSoundManager();
        for (String n : POWER_SOUNDS) {
            sounds.stop(new net.minecraft.resources.ResourceLocation("hand_of_god", n), null);
        }
    }

    /**
     * The Scarlet Witch's telekinesis: no implosion (the attack click does nothing while she holds a creature),
     * and the use click tears it apart only for five seconds (ScarletAbilities.dismember).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!scarlet() || "NADA".equals(tkState())) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem() && holding()) {
            if (ScarletClient.coolingDown("dismember")) {
                ScarletClient.denied("dismember");
                return;
            }
            com.flash.network.FlashNetwork.CHANNEL.sendToServer(
                    new com.flash.network.ScarletActionPacket(com.flash.network.ScarletActionPacket.DISMEMBER, -1, 0));
        }
    }

    // ------------------------------------------------------------------
    // The reality: on the ground as Hand of God, on a creature a change
    // ------------------------------------------------------------------

    private static boolean rlWas;
    /** The press started on a creature: Hand of God does not see it until the key is let go. */
    private static boolean rlOnCreature;

    private static void reality(Minecraft mc, boolean on) {
        KeyMapping k = hogKey("realidade");
        if (k == null) {
            return;
        }
        boolean down = on && FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !rlWas;
        rlWas = down;
        if (pressed) {
            net.minecraft.world.entity.Entity target = creatureAimed(mc, 64.0D);
            rlOnCreature = target != null;
            if (target != null && scarlet() && ScarletClient.coolingDown("reality_mob")) {
                ScarletClient.denied("reality_mob");
            } else if (target != null) {
                com.flash.network.FlashNetwork.CHANNEL.sendToServer(
                        new com.flash.network.YasRealityPacket(target.getId()));
            }
        }
        if (!down) {
            rlOnCreature = false;
        }
        if (on || k.isDown()) {
            k.setDown(down && !rlOnCreature);
        }
    }

    /** The creature (not a player) under the crosshair, up to {@code range} blocks, walls in the way. */
    public static net.minecraft.world.entity.Entity creatureAimed(Minecraft mc, double range) {
        var p = mc.player;
        if (p == null) {
            return null;
        }
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(p.getLookAngle().scale(range));
        var block = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                p));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            end = block.getLocation();
        }
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                e -> e instanceof net.minecraft.world.entity.Mob && e.isAlive(), eye.distanceToSqr(end));
        return hit == null ? null : hit.getEntity();
    }

    // ------------------------------------------------------------------
    // The telekinesis: a toggle and a charged throw
    // ------------------------------------------------------------------

    /** Ticks the key must be held (while holding a creature) for a throw instead of a plain release. */
    private static final int THROW_AFTER = 6;
    /** Ticks to the full throw (~200 blocks). */
    private static final int THROW_FULL = 40;
    /** Hand of God's telekinesis key, held down by hand (not by the keyboard) for the toggle. */
    private static boolean tkDown;
    private static boolean tkWas;
    private static boolean tkGrabbed;
    private static int tkWait;
    /** Ticks the key is held while holding a creature (-1: not). */
    private static int tkHeld = -1;

    /** 0..1 of the throw being charged, or -1. */
    public static float throwCharge() {
        return tkHeld >= THROW_AFTER ? Math.min(1.0F, (tkHeld - THROW_AFTER) / (float) THROW_FULL) : -1.0F;
    }

    private static void telekinesis(Minecraft mc, boolean on) {
        KeyMapping k = hogKey("telecinese");
        if (k == null) {
            return;
        }
        if (!on) {
            if (tkDown) {
                tkDown = false;
                k.setDown(false);
            }
            tkHeld = -1;
            tkWas = false;
            return;
        }
        boolean down = FlashKeys.YAS_USE.isDown() && mc.screen == null;
        boolean pressed = down && !tkWas;
        tkWas = down;
        boolean holding = grabbing();
        if (!tkDown) {
            if (pressed && tkAim(mc) != null) {
                if (scarlet() && ScarletClient.coolingDown("tk_grab")) {
                    ScarletClient.denied("tk_grab");
                } else {
                    // first press: lift the creature aimed at (kept until the next press); nothing aimed: nothing
                    tkDown = true;
                    tkGrabbed = false;
                    tkWait = 0;
                    tkHeld = -1;
                }
            }
        } else if (!tkGrabbed) {
            if (holding) {
                tkGrabbed = true;
            } else if (++tkWait > 20) {
                tkDown = false; // nothing was caught
            }
        } else if (!holding) {
            tkDown = false; // it got away (died, refused...)
            tkHeld = -1;
            if (scarlet()) {
                ScarletClient.startLocal("tk_grab", ScarletClient.cd("tk_grab"));
            }
        } else if (pressed) {
            tkHeld = 0;
        } else if (tkHeld >= 0) {
            if (down) {
                tkHeld++;
            } else {
                if (tkHeld >= THROW_AFTER && scarlet() && ScarletClient.coolingDown("tk_throw")) {
                    ScarletClient.denied("tk_throw");
                } else if (tkHeld >= THROW_AFTER) {
                    int charge = Math.round(throwCharge() * 100.0F);
                    if (scarlet()) {
                        ScarletClient.startLocal("tk_throw", ScarletClient.cd("tk_throw"));
                    }
                    com.flash.network.FlashNetwork.CHANNEL.sendToServer(
                            new com.flash.network.YasTelekinesisPacket(1, charge));
                }
                tkHeld = -1;
                tkDown = false; // the second press lets it go (thrown when charged)
            }
        }
        k.setDown(tkDown);
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
        if (event.phase != TickEvent.Phase.END || p == null || !(PowerTheme.localYas() || PowerTheme.localScarlet())) {
            flightHeld = -1;
            flightWas = false;
            return;
        }
        KeyMapping k = FlashAbilityKeys.mapping(com.flash.ability.FlashAbility.WIND_HANDS);
        // the key itself, whatever else shares it: it flies in every mode
        boolean down = k != null && mc.screen == null && (k.isDown() || (!k.isUnbound()
                && k.getKey().getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getKey().getValue())));
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
                // the client moves its player: the leap itself here (Yas ~30 blocks, the Scarlet Witch ~20), the
                // server adds the cracks, the burst and the sound
                double up = PowerTheme.localScarlet() ? 2.0D : 2.6D;
                p.setDeltaMovement(p.getDeltaMovement().x, up, p.getDeltaMovement().z);
                p.hasImpulse = true;
                SupermanClient.hoverAtTop();
            }
            return;
        }
        SupermanClient.thorCoiling = false;
        if (!flightLaunched) {
            if (flightHeld >= TAP_TICKS) {
                com.flash.network.FlashNetwork.CHANNEL.sendToServer(new com.flash.network.SupermanLaunchPacket(2));
            } else {
                // a tap on the ground: a hop, and she flies from its top (flying while still on the ground would
                // be switched off at once by the game)
                p.setDeltaMovement(p.getDeltaMovement().x, 0.75D, p.getDeltaMovement().z);
                p.hasImpulse = true;
                SupermanClient.hoverAtTop();
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
        return mc.player != null && mc.screen == null && !mc.player.isSpectator()
                && (PowerTheme.localYas() || PowerTheme.localScarlet()) && ready();
    }

    private static Method holding;

    /** Hand of God's telekinesis is holding a creature now (client). */
    private static java.lang.reflect.Field meuField;
    private static Method aimMethod;

    /** Hand of God's telekinesis state of this player ("NADA", "PEDINDO", "SEGURANDO", ...). */
    private static String tkState() {
        try {
            if (meuField == null) {
                meuField = Class.forName("handofgod.client.TelecineseCliente").getDeclaredField("meu");
                meuField.setAccessible(true);
            }
            Object v = meuField.get(null);
            return v == null ? "NADA" : v.toString();
        } catch (ReflectiveOperationException | LinkageError e) {
            return "NADA";
        }
    }

    /** A creature (or player) is held now (not only the key down with nothing caught). */
    private static boolean holding() {
        return "SEGURANDO".equals(tkState());
    }

    /** Something is caught or being caught (the toggle stays down meanwhile). */
    private static boolean grabbing() {
        String s = tkState();
        return !"NADA".equals(s);
    }

    /** What Hand of God's telekinesis would catch now (its own aim and reach), or null. */
    private static net.minecraft.world.entity.Entity tkAim(Minecraft mc) {
        try {
            if (aimMethod == null) {
                aimMethod = Class.forName("handofgod.client.TelecineseCliente").getDeclaredMethod("mira",
                        Minecraft.class);
                aimMethod.setAccessible(true);
            }
            return (net.minecraft.world.entity.Entity) aimMethod.invoke(null, mc);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return creatureAimed(mc, 24.0D);
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
        int next = cur < 0 ? 0 : Math.floorMod(cur + dir, count());
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

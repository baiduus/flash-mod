package com.flash.client;

import com.flash.FlashMod;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Yas's abilities the way of the other heroes of the mod, over the powers of Hand of God (embedded): no ability
 * wheel needed.
 * <ul>
 * <li>the panel at the bottom right lists the nine powers with their keys, the chosen one lit
 * (FlashAbilityHud);</li>
 * <li>Alt + scroll picks the power;</li>
 * <li>pressing the key of any power picks it at once, so the key just uses it (Hand of God only lets the chosen
 * power act).</li>
 * </ul>
 * Hand of God is reached by reflection ({@code handofgod.client.Selecao}, {@code Teclas}, {@code Poder}).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class YasAbilities {

    /** The nine powers, in the order of the panel (Hand of God's {@code Poder} names). */
    public static final String[] POWERS = {"CELESTE", "TEMPORAL", "REALIDADE", "MATERIA", "TELECINESE",
            "SUPERVELOCIDADE", "VISAO_TERMICA", "VOID", "ELETROCINESE"};

    private static boolean looked;
    private static Class<?> poderClass;
    private static Method escolher;
    private static Method avisar;
    private static Method atual;
    private static Method tecla;
    private static Object[] poderes;

    private YasAbilities() {
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                poderClass = Class.forName("handofgod.Poder");
                Class<?> selecao = Class.forName("handofgod.client.Selecao");
                escolher = selecao.getMethod("escolher", poderClass);
                avisar = selecao.getMethod("avisarServidor");
                atual = selecao.getMethod("poder");
                tecla = Class.forName("handofgod.client.Teclas").getMethod("de", poderClass);
                poderes = new Object[POWERS.length];
                for (int i = 0; i < POWERS.length; i++) {
                    poderes[i] = poderClass.getMethod("valueOf", String.class).invoke(null, POWERS[i]);
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

    /** The name of a power (Hand of God's own translation). */
    public static Component name(int i) {
        return Component.translatable("poder.hand_of_god." + POWERS[i].toLowerCase(java.util.Locale.ROOT));
    }

    public static KeyMapping key(int i) {
        if (!ready()) {
            return null;
        }
        try {
            return (KeyMapping) tecla.invoke(null, poderes[i]);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** The power chosen now (-1 none). */
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

    public static void select(int i) {
        if (!ready() || i == selected()) {
            return;
        }
        try {
            escolher.invoke(null, poderes[i]);
            avisar.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // Hand of God changed: nothing to do
        }
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.player.isSpectator() && PowerTheme.localYas()
                && ready();
    }

    /** Every Hand of God key of a power (the Temporal one has two: speed up the day, marker / rewind). */
    private static java.util.List<KeyMapping> keys(int i) {
        java.util.List<KeyMapping> out = new java.util.ArrayList<>();
        KeyMapping main = key(i);
        if (main != null) {
            out.add(main);
        }
        if ("TEMPORAL".equals(POWERS[i])) {
            for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
                if (m != main && (m.getName().equals("key.hand_of_god.acelerar")
                        || m.getName().equals("key.hand_of_god.voltar"))) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** The key of a power, pressed: that power is chosen before Hand of God reads the key. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !active() || Screen.hasAltDown()) {
            return;
        }
        for (int i = 0; i < POWERS.length; i++) {
            for (KeyMapping k : keys(i)) {
                if (!k.isUnbound() && k.matches(event.getKey(), event.getScanCode())) {
                    select(i);
                    return;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !active()) {
            return;
        }
        for (int i = 0; i < POWERS.length; i++) {
            for (KeyMapping k : keys(i)) {
                if (!k.isUnbound() && k.matchesMouse(event.getButton())) {
                    select(i);
                    return;
                }
            }
        }
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
     * Scroll: holding a creature by telekinesis, it comes closer (down) or goes farther (up) instead of the hotbar;
     * Alt + scroll: the next / previous power.
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
        int dir = event.getScrollDelta() > 0.0D ? -1 : 1;
        int next = cur < 0 ? 0 : Math.floorMod(cur + dir, POWERS.length);
        select(next);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.flash.yas_selected", name(next)), true);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.6F);
        }
    }
}

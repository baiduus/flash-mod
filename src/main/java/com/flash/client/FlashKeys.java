package com.flash.client;

import com.flash.FlashMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Configurable speed controls.
 *
 * <p>{@code V} increases the level; holding Ctrl while pressing the same
 * mapped key decreases it. A separate optional decrease mapping is provided
 * for players who prefer a dedicated key. The mouse wheel path has its own
 * client config because vanilla key mappings cannot represent scroll
 * notches.</p>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FlashKeys {

    public static final String CATEGORY = "key.categories.flash.speed";

    public static final KeyMapping OPEN_PROGRESS = new KeyMapping(
            "key.flash.progress",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    public static final KeyMapping INCREASE_SPEED = new KeyMapping(
            "key.flash.speed.up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    /**
     * Optional dedicated decrease key. It is unbound by default because
     * Ctrl + the increase key already decreases the level and binding the same
     * key twice would create a conflict in the controls screen.
     */
    public static final KeyMapping DECREASE_SPEED = new KeyMapping(
            "key.flash.speed.down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** Turns the speed off / back on, keeping the level (default J). */
    public static final KeyMapping TOGGLE_SPEED = new KeyMapping(
            "key.flash.speed.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    /** Puts the suit on / takes it off using the ring in the K-screen slot (default U). */
    public static final KeyMapping TOGGLE_SUIT = new KeyMapping(
            "key.flash.suit.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            CATEGORY
    );

    /** Bruce Banner <-> Hulk (default H). */
    public static final KeyMapping HULK_TRANSFORM = new KeyMapping(
            "key.flash.hulk.transform",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    /** Yas: the ability of the mode chosen with Alt + scroll (default R). */
    public static final KeyMapping YAS_USE = new KeyMapping(
            "key.flash.yas.use",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    /** Yas: the variant of the mode (Temporal: speed up the day; default Z). */
    public static final KeyMapping YAS_VARIANT = new KeyMapping(
            "key.flash.yas.variant",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    private FlashKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROGRESS);
        event.register(INCREASE_SPEED);
        event.register(DECREASE_SPEED);
        event.register(TOGGLE_SPEED);
        event.register(TOGGLE_SUIT);
        event.register(HULK_TRANSFORM);
        event.register(YAS_USE);
        event.register(YAS_VARIANT);
    }
}

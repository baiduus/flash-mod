package com.flash.client;

import com.flash.FlashMod;
import com.flash.ability.FlashAbility;
import com.flash.network.FlashAbilityPacket;
import com.flash.network.FlashNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Independent, configurable keys for every skill. They live in their own
 * controls category so they never overwrite movement, inventory or the speed
 * keys. Hold skills send a release event when the key or the screen changes.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FlashAbilityKeys {

    public static final String CATEGORY = "key.categories.flash.abilities";

    private static final FlashAbility[] ORDER = {
            FlashAbility.BARRAGE,
            FlashAbility.WIND_HANDS,
            FlashAbility.PHASING,
            FlashAbility.SPEED_TORNADO,
            FlashAbility.LIGHTNING_STRIKE,
            FlashAbility.MASS_PUNCH,
            FlashAbility.SPEED_MANIPULATION,
            FlashAbility.ACCELERATION_FORCE,
            FlashAbility.DASH_PUNCH,
            FlashAbility.TIME_SLOW
    };

    private static final KeyMapping[] MAPPINGS = new KeyMapping[ORDER.length];
    private static final boolean[] WAS_DOWN = new boolean[ORDER.length];

    static {
        int[] defaults = {
                GLFW.GLFW_KEY_R, // Barragem
                GLFW.GLFW_KEY_G, // Tornados com as mãos
                GLFW.GLFW_KEY_X, // Vibração molecular (segurar)
                GLFW.GLFW_KEY_C, // Tornado de velocidade (segurar)
                GLFW.GLFW_KEY_B, // Lançamento de raios
                GLFW.GLFW_KEY_N, // Soco de massa infinita
                GLFW.GLFW_KEY_M, // Manipulação de velocidade
                GLFW.GLFW_KEY_P, // Força de Aceleração
                GLFW.GLFW_KEY_V, // Corrida de socos
                GLFW.GLFW_KEY_H  // Câmera lenta
        };
        for (int i = 0; i < ORDER.length; i++) {
            MAPPINGS[i] = new KeyMapping(ORDER[i].keyName(),
                    InputConstants.Type.KEYSYM, defaults[i], CATEGORY);
        }
    }

    private FlashAbilityKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : MAPPINGS) {
            event.register(mapping);
        }
    }

    public static KeyMapping mapping(FlashAbility ability) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == ability) {
                return MAPPINGS[i];
            }
        }
        return null;
    }

    /** True while the key of a skill is held (used for instant, optimistic client effects). */
    public static boolean isHeld(FlashAbility ability) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == ability) {
                return WAS_DOWN[i];
            }
        }
        return false;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean canUse = minecraft.player != null
                && minecraft.screen == null
                && FlashClientData.hasPowers();

        for (int i = 0; i < ORDER.length; i++) {
            FlashAbility ability = ORDER[i];
            boolean down = canUse && MAPPINGS[i].isDown();
            if (down == WAS_DOWN[i]) {
                continue;
            }
            boolean had = WAS_DOWN[i];
            WAS_DOWN[i] = down;
            if (down) {
                FlashNetwork.CHANNEL.sendToServer(
                        new FlashAbilityPacket(ability.ordinal(), true));
            } else if (had) {
                FlashNetwork.CHANNEL.sendToServer(
                        new FlashAbilityPacket(ability.ordinal(), false));
            }
        }
    }

    public static void releaseAll() {
        for (int i = 0; i < ORDER.length; i++) {
            if (!WAS_DOWN[i]) {
                continue;
            }
            WAS_DOWN[i] = false;
            FlashNetwork.CHANNEL.sendToServer(
                    new FlashAbilityPacket(ORDER[i].ordinal(), false));
        }
    }
}

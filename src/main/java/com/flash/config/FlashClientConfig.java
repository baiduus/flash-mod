package com.flash.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side options that cannot be represented by a vanilla {@code
 * KeyMapping} (the mouse wheel is not a key).
 */
public final class FlashClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue MOUSE_WHEEL_CONTROLS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Flash speed controls").push("controls");
        MOUSE_WHEEL_CONTROLS = builder
                .comment("Allow changing the speed level with the mouse wheel while sneaking.")
                .define("mouseWheelControls", true);
        builder.pop();
        SPEC = builder.build();
    }

    private FlashClientConfig() {
    }
}

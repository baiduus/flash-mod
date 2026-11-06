package com.flash.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side options edited from the Config tab of the K screen.
 */
public final class FlashClientConfig {

    public static final ForgeConfigSpec SPEC;
    /** false: V raises and Ctrl+V lowers the level. true: Ctrl + mouse wheel instead. Exclusive. */
    public static final ForgeConfigSpec.BooleanValue SCROLL_MODE;
    public static final ForgeConfigSpec.BooleanValue WALL_RUN;
    public static final ForgeConfigSpec.BooleanValue SHOW_ABILITY_HUD;
    public static final ForgeConfigSpec.BooleanValue BODY_LIGHTNING;
    /** False: the suit mask covers the mouth (closed). True: the mask leaves a standard open mouth. */
    public static final ForgeConfigSpec.BooleanValue MASK_OPEN_MOUTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Flash speed controls").push("controls");
        SCROLL_MODE = builder
                .comment("false = V / Ctrl+V change the level; true = Ctrl + mouse wheel changes it (only one works).")
                .define("scrollMode", false);
        WALL_RUN = builder
                .comment("Climb walls by holding jump while the speed is on.")
                .define("wallRun", true);
        builder.pop();
        builder.comment("Flash visuals").push("visuals");
        SHOW_ABILITY_HUD = builder
                .comment("Show the ability list at the bottom right.")
                .define("showAbilityHud", true);
        BODY_LIGHTNING = builder
                .comment("Draw the lightning around your own body in third person.")
                .define("bodyLightning", true);
        MASK_OPEN_MOUTH = builder
                .comment("Suit mask: false covers the mouth, true shows a standard mouth")
                .define("maskOpenMouth", false);
        builder.pop();
        SPEC = builder.build();
    }

    private FlashClientConfig() {
    }

    public static void toggle(ForgeConfigSpec.BooleanValue value) {
        value.set(!value.get());
        value.save();
    }
}

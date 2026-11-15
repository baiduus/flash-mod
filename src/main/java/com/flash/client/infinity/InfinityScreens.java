package com.flash.client.infinity;

import net.minecraft.client.Minecraft;

/** The stones' menus the server opens (InfinityServer.open). */
public final class InfinityScreens {

    private InfinityScreens() {
    }

    static void open(String screen) {
        Minecraft mc = Minecraft.getInstance();
        switch (screen) {
            case "portal" -> mc.setScreen(new PortalScreen());
            case "disguise" -> mc.setScreen(new DisguiseScreen());
            case "mind_name" -> mc.setScreen(new MindNameScreen());
            default -> {
                if (screen.startsWith("wheel:")) {
                    com.flash.infinity.InfPowers.Ability a = com.flash.infinity.InfPowers.ability(screen.substring(6));
                    if (a != null) {
                        mc.setScreen(new WheelScreen(a));
                    }
                }
            }
        }
    }
}

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
            default -> {
            }
        }
    }
}

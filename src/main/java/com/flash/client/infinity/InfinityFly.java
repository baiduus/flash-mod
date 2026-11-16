package com.flash.client.infinity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Who flies by the stones (a hero's flight, pose and aura: SupermanClient, SupermanPose, InfinityAuraLayer). */
public final class InfinityFly {

    private InfinityFly() {
    }

    public static boolean is(Entity e) {
        return e instanceof Player && InfinityClient.visibleKey(e, "flash:flyer");
    }

    public static boolean flying(Player p) {
        if (!is(p)) {
            return false;
        }
        return p == Minecraft.getInstance().player ? p.getAbilities().flying
                : InfinityClient.visibleKey(p, "flash:flying");
    }
}

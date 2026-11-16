package com.flash.infinity;

import net.minecraft.server.level.ServerPlayer;

/**
 * The original's advancements are gone (no toasts nor their sound for the stones and the Gauntlet); what one of them
 * gave is given here directly: a best friend's soul, for letting your own pet die.
 */
public final class InfinityAdvancements {

    private InfinityAdvancements() {
    }

    public static void grant(ServerPlayer p, String id) {
        if ("tamed_wolf_death".equals(id)) {
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput()
                    .withPermission(2), "function flash:give_soul");
        }
    }
}

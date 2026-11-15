package com.flash.infinity;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** The original's advancements (flash:infinity/...): the ones its own triggers gave, granted here. */
public final class InfinityAdvancements {

    private InfinityAdvancements() {
    }

    public static void grant(ServerPlayer p, String id) {
        Advancement adv = p.server.getAdvancements().getAdvancement(new ResourceLocation("flash", "infinity/" + id));
        if (adv == null) {
            return;
        }
        AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) {
            return;
        }
        for (String c : progress.getRemainingCriteria()) {
            p.getAdvancements().award(adv, c);
        }
    }
}

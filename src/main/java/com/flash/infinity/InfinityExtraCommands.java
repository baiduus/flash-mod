package com.flash.infinity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** The /infinity commands of the Orb, the Gauntlet and the Soul Guardian (filled in by their parts). */
final class InfinityExtraCommands {

    private InfinityExtraCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> orb() {
        return Commands.literal("orb");
    }

    static LiteralArgumentBuilder<CommandSourceStack> gauntlet() {
        return Commands.literal("gauntlet");
    }

    static LiteralArgumentBuilder<CommandSourceStack> soulGuardian() {
        return Commands.literal("soulguardian");
    }
}

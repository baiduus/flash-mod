package com.flash.item;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * One thing a {@link SyringeItem} can carry.
 *
 * <p>The syringe stores only the content id in its NBT ({@code Content}), so
 * adding a new substance later means adding one entry to
 * {@link SyringeContents}: a capsule item, a model index for the liquid and
 * what happens when it is injected. Nothing else in the syringe changes.</p>
 */
public record SyringeContent(String id, int modelIndex, Supplier<Item> capsule, Injector injector) {

    /** Result of an injection attempt. */
    @FunctionalInterface
    public interface Injector {
        /** @return true when the content was used up (the syringe becomes empty). */
        boolean inject(ServerPlayer player);
    }
}

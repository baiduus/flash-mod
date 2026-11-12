package com.flash.power;

import com.flash.FlashMod;
import com.flash.hulk.HulkPowers;
import com.flash.registry.ModItems;
import com.flash.superman.SupermanPowers;
import com.flash.thor.ThorPowers;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The powers of every hero seen the same way, so the syringes and capsules work with all of them (and with the
 * ones to come: a new hero is one more entry here).
 *
 * <p>Extraction: an empty syringe used by a hero takes the power out into a capsule of that hero. The progression
 * belongs to the player and the kind of power, not to the capsule: what the player had is kept in his
 * "hero memory" (persistent, survives deaths and restarts). Injecting a capsule of a power gives it back at the
 * level that player had reached with it before, or at level 1 if he never had it. A capsule holds no levels of its
 * own, so it can not be used to copy or grow anybody's progress.
 *
 * <p>Only one power at a time: a capsule that gives a power is refused, on the server, while the player has any
 * power (the temporary ones of the V-9 and the Gamma capsule follow their own rules).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HeroPowers {

    /** The player's past powers: hero id -> the saved data of that power. */
    public static final String MEMORY = "flash_hero_memory";

    public enum Hero {
        FLASH("flash", FlashPowers.ROOT, ModItems.FLASH_ESSENCE_CAPSULE),
        HULK("hulk", HulkPowers.KEY, ModItems.HULK_ESSENCE_CAPSULE),
        SUPERMAN("superman", SupermanPowers.KEY, ModItems.KRYPTONIAN_BLOOD_CAPSULE),
        THOR("thor", ThorPowers.KEY, ModItems.THOR_ESSENCE_CAPSULE);

        private final String id;
        private final String key;
        private final Supplier<Item> capsule;

        Hero(String id, String key, Supplier<Item> capsule) {
            this.id = id;
            this.key = key;
            this.capsule = capsule;
        }

        public String id() {
            return this.id;
        }

        public Item capsule() {
            return this.capsule.get();
        }

        /** Has this power at all (permanent or temporary). */
        public boolean has(Player p) {
            return switch (this) {
                case FLASH -> FlashPowers.hasPowers(p) || FlashPowers.hasPermanentPowers(p)
                        || p.getPersistentData().contains(FlashV9.KEY);
                case HULK -> HulkPowers.has(p);
                case SUPERMAN -> SupermanPowers.has(p);
                case THOR -> ThorPowers.has(p);
            };
        }

        /** Only given for a while (V-9, Gamma capsule, Kryptonian baby...): it can not be taken out. */
        public boolean temporary(Player p) {
            return switch (this) {
                case FLASH -> !FlashPowers.hasPermanentPowers(p) || FlashV9.isActive(p);
                case HULK -> HulkPowers.isTemporary(p) || HulkPowers.boostTicks(p) > 0;
                case SUPERMAN -> SupermanPowers.temporaryTicks(p) > 0;
                case THOR -> false;
            };
        }

        public int level(Player p) {
            return switch (this) {
                case FLASH -> FlashPowers.getMaxLevel(p);
                case HULK -> HulkPowers.level(p);
                case SUPERMAN -> SupermanPowers.level(p);
                case THOR -> ThorPowers.level(p);
            };
        }

        /** Takes the power away (as becoming another hero does). */
        void revoke(ServerPlayer p) {
            switch (this) {
                case FLASH -> {
                    FlashPowers.revoke(p);
                    p.getPersistentData().remove(FlashV9.KEY);
                    com.flash.ability.FlashAbilityManager.reset(p);
                    FlashSpeed.remove(p);
                    FlashStep.remove(p);
                    FlashStatusEffects.reset(p);
                    com.flash.network.FlashSyncPacket.send(p);
                    com.flash.network.FlashVisualSyncPacket.send(p);
                }
                case HULK -> {
                    if (HulkPowers.isTransformed(p)) {
                        HulkPowers.setTransformed(p, false);
                    }
                    HulkPowers.revoke(p);
                }
                case SUPERMAN -> SupermanPowers.revoke(p);
                case THOR -> ThorPowers.revoke(p);
            }
        }

        /** The power given for the first time (level 1). */
        void grantNew(ServerPlayer p) {
            switch (this) {
                case FLASH -> {
                    FlashPowers.grant(p);
                    afterFlash(p);
                }
                case HULK -> HulkPowers.grant(p, false);
                case SUPERMAN -> SupermanPowers.grant(p, false);
                case THOR -> ThorPowers.grant(p, false);
            }
        }

        /** The saved power put back as it was (in civilian form). */
        void restore(ServerPlayer p, CompoundTag saved) {
            p.getPersistentData().put(this.key, saved.copy());
            switch (this) {
                case FLASH -> afterFlash(p);
                case HULK -> HulkPowers.setTransformed(p, false);
                case SUPERMAN -> {
                    SupermanPowers.setForm(p, false);
                    SupermanPowers.sync(p);
                }
                case THOR -> {
                    ThorPowers.setForm(p, false);
                    ThorPowers.sync(p);
                }
            }
        }

        CompoundTag save(Player p) {
            return p.getPersistentData().getCompound(this.key).copy();
        }

        private static void afterFlash(ServerPlayer p) {
            FlashSpeed.apply(p);
            FlashStep.apply(p);
            FlashStatusEffects.onLevelChanged(p);
            com.flash.ability.FlashAbilityManager.reset(p);
            com.flash.network.FlashSyncPacket.send(p);
            com.flash.network.FlashVisualSyncPacket.send(p);
        }

        public Component displayName() {
            return Component.translatable("hero.flash." + this.id);
        }
    }

    private HeroPowers() {
    }

    /** The power the player has right now (any kind), or null. */
    public static Hero current(Player player) {
        for (Hero h : Hero.values()) {
            if (h.has(player)) {
                return h;
            }
        }
        return null;
    }

    public static boolean hasAny(Player player) {
        return current(player) != null;
    }

    /** The level the player had with a power before taking it out (0 = never had it). */
    public static int rememberedLevel(Player player, Hero hero) {
        CompoundTag mem = player.getPersistentData().getCompound(MEMORY);
        if (!mem.contains(hero.id())) {
            return 0;
        }
        CompoundTag saved = mem.getCompound(hero.id());
        return Math.max(1, hero == Hero.FLASH ? saved.getInt("maxLevel") : saved.getInt("level"));
    }

    // ------------------------------------------------------------------
    // Extraction: empty syringe
    // ------------------------------------------------------------------

    /** Whether an empty syringe would take something out of this player (checked before the injection starts). */
    public static boolean canExtract(Player player) {
        Hero h = current(player);
        return h != null && !h.temporary(player);
    }

    /** The empty syringe: the power goes into a capsule, the player is left without powers. */
    public static boolean extract(ServerPlayer player) {
        Hero hero = current(player);
        if (hero == null) {
            player.displayClientMessage(Component.translatable("message.flash.extract_none"), true);
            return false;
        }
        if (hero.temporary(player)) {
            player.displayClientMessage(Component.translatable("message.flash.extract_temporary"), true);
            return false;
        }
        int level = hero.level(player);
        CompoundTag mem = player.getPersistentData().getCompound(MEMORY).copy();
        mem.put(hero.id(), hero.save(player));
        player.getPersistentData().put(MEMORY, mem);
        hero.revoke(player);
        ItemStack capsule = new ItemStack(hero.capsule());
        player.getInventory().placeItemBackInInventory(capsule);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL_DRAGONBREATH,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.translatable("message.flash.extract_done", hero.displayName(), level),
                false);
        return true;
    }

    // ------------------------------------------------------------------
    // Injection of a power capsule
    // ------------------------------------------------------------------

    public static boolean inject(ServerPlayer player, Hero hero) {
        Hero has = current(player);
        if (has != null) {
            player.displayClientMessage(Component.translatable("message.flash.inject_has_power", has.displayName()),
                    true);
            return false;
        }
        CompoundTag mem = player.getPersistentData().getCompound(MEMORY);
        if (mem.contains(hero.id())) {
            hero.restore(player, mem.getCompound(hero.id()));
            player.displayClientMessage(Component.translatable("message.flash.inject_restored", hero.displayName(),
                    hero.level(player)), false);
        } else {
            hero.grantNew(player);
            player.displayClientMessage(Component.translatable("message.flash.inject_new", hero.displayName()), false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    /** The hero memory survives the death of the player. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(MEMORY)) {
            event.getEntity().getPersistentData().put(MEMORY, old.getCompound(MEMORY).copy());
        }
    }
}

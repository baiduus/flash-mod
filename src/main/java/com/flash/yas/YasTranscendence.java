package com.flash.yas;

import com.flash.FlashMod;
import com.flash.network.FlashNetwork;
import com.flash.network.YasFxPacket;
import com.flash.power.HeroAdvancements;
import com.flash.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The transcendence, asked of the Guardian of the floating island (YasGuardian).
 *
 * <p>Ready (every hero of the mod conquered, and in the inventory a capsule of each power - Speed Force, gamma,
 * Kryptonian blood, Asgardian essence - and the four pieces of Lex Luthor's suit): the player rises and hangs in
 * the air for 10 s, wrapped in very pale yellow lightning, a white beam of light runs down through him; at the end
 * the capsules are spent and he is Yas.
 *
 * <p>Not ready: the Guardian answers with a beam of black energy and black lightning. For 3 s nothing gets the
 * player out of it (he is held in place, no damage, potion, totem, logging out or creative mode saves him) and
 * then he dies.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class YasTranscendence {

    public static final int ASCEND_TICKS = 200;
    public static final int DOOM_TICKS = 60;
    /** Logged out while doomed: dies when he comes back. */
    private static final String DOOMED = "flash_yas_doomed";
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(FlashMod.MODID, "transcendence"));

    private enum Kind { ASCEND, DOOM }

    private static final class Seq {
        final Kind kind;
        final Vec3 anchor;
        final int source;
        int ticks;
        final boolean free;

        Seq(Kind kind, Vec3 anchor, int source, boolean free) {
            this.kind = kind;
            this.anchor = anchor;
            this.source = source;
            this.free = free;
        }
    }

    private static final Map<UUID, Seq> RUNNING = new HashMap<>();

    private YasTranscendence() {
    }

    public static boolean running(ServerPlayer p) {
        return RUNNING.containsKey(p.getUUID());
    }

    // ------------------------------------------------------------------
    // The requirements
    // ------------------------------------------------------------------

    private record Need(String key, Predicate<ItemStack> item) {
    }

    private static final Need[] ITEMS = {
            new Need("item.flash.flash_essence_capsule", s -> s.is(ModItems.FLASH_ESSENCE_CAPSULE.get())),
            new Need("item.flash.hulk_essence_capsule", s -> s.is(ModItems.HULK_ESSENCE_CAPSULE.get())
                    || s.is(ModItems.RED_HULK_ESSENCE_CAPSULE.get())),
            new Need("item.flash.kryptonian_blood_capsule", s -> s.is(ModItems.KRYPTONIAN_BLOOD_CAPSULE.get())),
            new Need("item.flash.thor_essence_capsule", s -> s.is(ModItems.THOR_ESSENCE_CAPSULE.get())),
            new Need("item.flash.lex_armor_helmet", s -> s.is(ModItems.LEX_HELMET.get())),
            new Need("item.flash.lex_armor_chestplate", s -> s.is(ModItems.LEX_CHESTPLATE.get())),
            new Need("item.flash.lex_armor_leggings", s -> s.is(ModItems.LEX_LEGGINGS.get())),
            new Need("item.flash.lex_armor_boots", s -> s.is(ModItems.LEX_BOOTS.get()))};
    /** The capsules are given up to the transcendence (the suit stays). */
    private static final int CONSUMED = 4;

    private static boolean holds(ServerPlayer p, Predicate<ItemStack> item) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (item.test(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** What the player still lacks (empty: ready). */
    public static List<Component> missing(ServerPlayer p) {
        List<Component> out = new ArrayList<>();
        for (String hero : HeroAdvancements.HEROES) {
            if (!HeroAdvancements.has(p, hero)) {
                out.add(Component.translatable("advancements.flash." + hero + ".title"));
            }
        }
        for (Need n : ITEMS) {
            if (!holds(p, n.item())) {
                out.add(Component.translatable(n.key()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The answer to the Guardian
    // ------------------------------------------------------------------

    public static void answer(ServerPlayer p, Entity guardian, boolean yes) {
        if (running(p)) {
            return;
        }
        if (!yes) {
            p.displayClientMessage(Component.translatable("message.flash.yas_refused").withStyle(ChatFormatting.GRAY,
                    ChatFormatting.ITALIC), false);
            return;
        }
        List<Component> missing = missing(p);
        if (missing.isEmpty()) {
            begin(p, guardian, false);
        } else {
            Component list = Component.empty();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) {
                    list = list.copy().append(", ");
                }
                list = list.copy().append(missing.get(i));
            }
            p.displayClientMessage(Component.translatable("message.flash.yas_not_ready", list)
                    .withStyle(ChatFormatting.DARK_RED), false);
            doom(p, guardian);
        }
    }

    /** The transcendence itself ({@code free}: by command, nothing asked or spent). */
    public static void begin(ServerPlayer p, Entity guardian, boolean free) {
        RUNNING.put(p.getUUID(), new Seq(Kind.ASCEND, p.position(), guardian != null ? guardian.getId() : -1, free));
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        p.setNoGravity(true);
        p.displayClientMessage(Component.translatable("message.flash.yas_begin").withStyle(ChatFormatting.YELLOW),
                false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
        fx(p, YasFxPacket.ASCEND, guardian != null ? guardian.getId() : -1, ASCEND_TICKS);
    }

    /** The punishment: 3 s held in the black beam, then death. */
    public static void doom(ServerPlayer p, Entity guardian) {
        RUNNING.put(p.getUUID(), new Seq(Kind.DOOM, p.position().add(0.0D, 0.4D, 0.0D),
                guardian != null ? guardian.getId() : -1, false));
        p.getPersistentData().putBoolean(DOOMED, true);
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        p.setNoGravity(true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0F,
                0.5F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.6F);
        fx(p, YasFxPacket.DOOM, guardian != null ? guardian.getId() : -1, DOOM_TICKS);
    }

    private static void fx(ServerPlayer p, int kind, int source, int ticks) {
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new YasFxPacket(kind, p.getId(), source, ticks));
    }

    // ------------------------------------------------------------------
    // The sequences
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        Seq s = RUNNING.get(p.getUUID());
        if (s == null) {
            return;
        }
        int t = s.ticks++;
        Vec3 target;
        if (s.kind == Kind.ASCEND) {
            double rise = Math.min(1.0D, t / 60.0D);
            rise = rise * rise * (3.0D - 2.0D * rise);
            target = s.anchor.add(0.0D, 3.0D * rise + Math.sin(t * 0.1D) * 0.12D, 0.0D);
            if (t == 100) {
                // the white beam
                p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                        2.0F, 0.5F);
                p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5F,
                        1.2F);
            }
            if (t % 20 == 10) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                        0.5F + t / 300.0F, 1.6F);
            }
        } else {
            target = s.anchor;
            if (t % 10 == 0) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F,
                        0.6F + t / 60.0F);
            }
        }
        // held where the sequence wants him: no walking, flying, pearls or speed get him out
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        if (p.position().distanceToSqr(target) > 0.0025D) {
            p.teleportTo(target.x, target.y, target.z);
        }
        int end = s.kind == Kind.ASCEND ? ASCEND_TICKS : DOOM_TICKS;
        if (t >= end) {
            RUNNING.remove(p.getUUID());
            p.setNoGravity(false);
            if (s.kind == Kind.ASCEND) {
                finishAscend(p, s);
            } else {
                die(p, s);
            }
        }
    }

    private static void finishAscend(ServerPlayer p, Seq s) {
        if (!s.free) {
            // the capsules go into her (one of each)
            for (int n = 0; n < CONSUMED; n++) {
                Inventory inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (ITEMS[n].item().test(inv.getItem(i))) {
                        inv.getItem(i).shrink(1);
                        break;
                    }
                }
            }
        }
        YasPowers.grant(p);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5F, 0.8F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F,
                1.4F);
        fx(p, YasFxPacket.ASCEND_END, s.source, 0);
        p.displayClientMessage(Component.translatable("message.flash.yas_done").withStyle(ChatFormatting.GOLD),
                false);
    }

    private static void die(ServerPlayer p, Seq s) {
        p.getPersistentData().remove(DOOMED);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0F, 0.7F);
        fx(p, YasFxPacket.DOOM_END, s.source, 0);
        kill(p, s.source >= 0 ? p.level().getEntity(s.source) : null);
    }

    private static void kill(ServerPlayer p, Entity by) {
        var types = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        DamageSource source = new DamageSource(types.getHolderOrThrow(DAMAGE), by);
        p.hurt(source, Float.MAX_VALUE);
        if (p.isAlive()) {
            p.setHealth(0.0F);
            p.die(source);
        }
    }

    /** Nothing hurts him while in a sequence (the ending decides). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && RUNNING.containsKey(p.getUUID())
                && !event.getSource().is(DAMAGE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Seq s = RUNNING.remove(p.getUUID());
            p.setNoGravity(false);
            if (s != null && s.kind == Kind.ASCEND) {
                // logged out in the middle: it is finished all the same
                finishAscend(p, s);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().getBoolean(DOOMED)) {
            // there is no escaping it
            p.getPersistentData().remove(DOOMED);
            doom(p, null);
        }
    }

}

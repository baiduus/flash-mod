package com.flash.dummy;

import com.flash.FlashMod;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /dummy <power> [level]}: a test dummy in front of you with that power's look, life and toughness (as a
 * player with it would have), to try the attacks and the resistance of each hero. Every hit shows its damage to who
 * hit it; when its life runs out it says how many hits and how much damage it took, and stands up again.
 * {@code /dummy remove} takes away the nearest, {@code /dummy clear} all of them. Dummies are not saved.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HeroDummies {

    public static final String[] POWERS = {"none", "flash", "hulk", "red_hulk", "superman", "thor", "lex", "yas",
            "scarlet"};
    private static final List<HeroDummy> ALL = new ArrayList<>();

    private HeroDummies() {
    }

    public static boolean is(Entity e) {
        return e instanceof HeroDummy;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dummy")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("remove").executes(c -> remove(c.getSource(), false)))
                .then(Commands.literal("clear").executes(c -> remove(c.getSource(), true)))
                .then(Commands.argument("power", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(POWERS, b))
                        .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "power"), 10))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "power"),
                                        IntegerArgumentType.getInteger(c, "level"))))));
    }

    private static int spawn(CommandSourceStack src, String power, int level) {
        if (!List.of(POWERS).contains(power)) {
            src.sendFailure(Component.translatable("commands.flash.dummy.unknown", power, String.join(", ", POWERS)));
            return 0;
        }
        ServerLevel world = src.getLevel();
        Vec3 at = src.getPosition();
        float yaw = 0.0F;
        if (src.getEntity() != null) {
            Entity me = src.getEntity();
            Vec3 look = me.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
            at = me.position().add(flat.scale(3.0D));
            yaw = me.getYRot() + 180.0F;
        }
        String name = ("Dummy_" + power).replace("red_hulk", "RedHulk");
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        HeroDummy d = new HeroDummy(world, new GameProfile(UUID.randomUUID(), name), power);
        d.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        d.setYHeadRot(yaw);
        d.yBodyRot = yaw;
        d.setGameMode(GameType.SURVIVAL);
        MinecraftServer server = world.getServer();
        server.getPlayerList().broadcastAll(info(List.of(d)));
        world.addNewPlayer(d);
        ALL.add(d);
        try {
            give(d, power, level);
        } catch (RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Dummy power {} failed", power, ex);
        }
        d.setHealth(d.getMaxHealth());
        Component what = Component.translatable("commands.flash.dummy.power." + power);
        src.sendSuccess(() -> Component.translatable("commands.flash.dummy.spawned", what, level), true);
        return 1;
    }

    /** The power, as the command of each hero gives it (transformed, at that level). */
    private static void give(HeroDummy d, String power, int level) {
        switch (power) {
            case "flash" -> {
                com.flash.power.FlashPowers.grant(d);
                com.flash.power.FlashPowers.setMaxLevel(d, level);
                com.flash.power.FlashPowers.setSelectedLevel(d, level);
                equip(d, com.flash.registry.ModItems.FLASH_SUIT_HELMET.get().getDefaultInstance(),
                        com.flash.registry.ModItems.FLASH_SUIT_CHESTPLATE.get().getDefaultInstance(),
                        com.flash.registry.ModItems.FLASH_SUIT_LEGGINGS.get().getDefaultInstance(),
                        com.flash.registry.ModItems.FLASH_SUIT_BOOTS.get().getDefaultInstance());
            }
            case "hulk", "red_hulk" -> {
                com.flash.hulk.HulkPowers.grant(d, false);
                com.flash.hulk.HulkPowers.setLevel(d, level, 0.0F);
                if ("red_hulk".equals(power)) {
                    com.flash.hulk.HulkPowers.setRed(d, true);
                }
                com.flash.hulk.HulkPowers.setTransformed(d, true);
            }
            case "superman" -> {
                com.flash.superman.SupermanPowers.grant(d, false);
                com.flash.superman.SupermanPowers.setLevel(d, level, 0.0F);
                com.flash.superman.SupermanPowers.setForm(d, true);
            }
            case "thor" -> {
                com.flash.thor.ThorPowers.grant(d, false);
                com.flash.thor.ThorPowers.setLevel(d, level, 0.0F);
                com.flash.thor.ThorPowers.setForm(d, true);
            }
            case "lex" -> equip(d, com.flash.registry.ModItems.LEX_HELMET.get().getDefaultInstance(),
                    com.flash.registry.ModItems.LEX_CHESTPLATE.get().getDefaultInstance(),
                    com.flash.registry.ModItems.LEX_LEGGINGS.get().getDefaultInstance(),
                    com.flash.registry.ModItems.LEX_BOOTS.get().getDefaultInstance());
            case "yas" -> com.flash.yas.YasPowers.grant(d);
            case "scarlet" -> com.flash.scarlet.ScarletPowers.grant(d);
            default -> {
            }
        }
        // the life its power gives (attributes set in its tick)
        safeTick(d);
        d.setHealth(d.getMaxHealth());
    }

    private static void equip(HeroDummy d, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        d.setItemSlot(EquipmentSlot.HEAD, head);
        d.setItemSlot(EquipmentSlot.CHEST, chest);
        d.setItemSlot(EquipmentSlot.LEGS, legs);
        d.setItemSlot(EquipmentSlot.FEET, feet);
    }

    private static ClientboundPlayerInfoUpdatePacket info(List<ServerPlayer> who) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), who);
    }

    private static int remove(CommandSourceStack src, boolean all) {
        List<HeroDummy> gone = new ArrayList<>();
        if (all) {
            gone.addAll(ALL);
        } else {
            HeroDummy near = null;
            double best = 32.0D * 32.0D;
            for (HeroDummy d : ALL) {
                double dist = d.position().distanceToSqr(src.getPosition());
                if (d.level() == src.getLevel() && dist < best) {
                    best = dist;
                    near = d;
                }
            }
            if (near != null) {
                gone.add(near);
            }
        }
        for (HeroDummy d : gone) {
            discard(d);
        }
        int n = gone.size();
        src.sendSuccess(() -> Component.translatable("commands.flash.dummy.removed", n), true);
        return n;
    }

    private static void discard(HeroDummy d) {
        ALL.remove(d);
        MinecraftServer server = d.getServer();
        if (d.level() instanceof ServerLevel level) {
            level.removePlayerImmediately(d, Entity.RemovalReason.DISCARDED);
        }
        if (server != null) {
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(d.getUUID())));
        }
    }

    private static void safeTick(HeroDummy d) {
        try {
            d.doTick();
        } catch (RuntimeException ex) {
            // a power that needs a real connection: the dummy just stands
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ALL.isEmpty()) {
            return;
        }
        for (HeroDummy d : new ArrayList<>(ALL)) {
            if (d.isRemoved()) {
                ALL.remove(d);
                continue;
            }
            Vec3 at = d.position();
            safeTick(d);
            // it stays where it was put
            d.setDeltaMovement(Vec3.ZERO);
            if (d.position().distanceToSqr(at) > 1.0E-4D) {
                d.moveTo(at.x, at.y, at.z, d.getYRot(), d.getXRot());
            }
            d.getFoodData().setFoodLevel(20);
        }
    }

    /** The damage of every hit, to who hit it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof HeroDummy d) || event.isCanceled()) {
            return;
        }
        float dmg = event.getAmount();
        d.hits++;
        d.total += dmg;
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            float left = Math.max(0.0F, d.getHealth() - dmg);
            p.displayClientMessage(Component.translatable("message.flash.dummy.hit",
                    String.format(java.util.Locale.ROOT, "%.1f", dmg),
                    String.format(java.util.Locale.ROOT, "%.1f", left),
                    String.format(java.util.Locale.ROOT, "%.0f", d.getMaxHealth()), d.hits,
                    String.format(java.util.Locale.ROOT, "%.1f", d.total)), true);
        }
    }

    static void defeated(HeroDummy d, DamageSource source) {
        Entity by = source.getEntity();
        Component msg = Component.translatable("message.flash.dummy.down", d.getDisplayName(), d.hits,
                String.format(java.util.Locale.ROOT, "%.1f", d.total));
        if (by instanceof ServerPlayer p) {
            p.sendSystemMessage(msg);
        }
        d.hits = 0;
        d.total = 0.0F;
        d.setHealth(d.getMaxHealth());
        d.clearFire();
        d.deathTime = 0;
        d.invulnerableTime = 20;
    }

    /** Who joins sees the dummies too. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !ALL.isEmpty()) {
            p.connection.send(info(new ArrayList<>(ALL)));
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (HeroDummy d : new ArrayList<>(ALL)) {
            discard(d);
        }
    }
}

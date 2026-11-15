package com.flash.infinity;

import com.flash.FlashMod;
import com.flash.infinity.entity.StoneClone;
import com.flash.network.FlashNetwork;
import com.flash.network.InfinitySyncPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Mind Stone: intangibility (through blocks and every blow, 10 s and then a recharge; endless with all six stones),
 * the illusions (clones), reading a mind, the disguise. Its pacifying, the hero, the knowledge (every recipe) and night
 * vision are its commands (InfinityAbilities); its beams are InfinityBeams.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class MindStone {

    /** The client's answer (InfinityClient): is the local player intangible now. */
    public static Predicate<Player> clientIntangible = p -> false;

    private MindStone() {
    }

    public static boolean intangible(Player p) {
        if (p.level().isClientSide) {
            return clientIntangible.test(p);
        }
        return p instanceof ServerPlayer sp && has(sp, "palladium:intangibility");
    }

    static boolean has(ServerPlayer p, String type) {
        for (String key : InfinityServer.state(p).enabled) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a != null && type.equals(a.type)) {
                return true;
            }
        }
        return false;
    }

    /** All six stones' powers: intangibility never runs out (the original's EndlessIntangibility). */
    static boolean allSix(InfinityServer.State s) {
        for (InfinityStone st : InfinityStone.values()) {
            if (!s.powers.contains(st.power())) {
                return false;
            }
        }
        return true;
    }

    /** The toggle of intangibility: 10 s at most, then off (and its recharge). */
    static void intangibilityTick(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a, boolean on) {
        if (!on || !"intangibility".equals(a.id)) {
            return;
        }
        if (!allSix(s) && s.timer(a.key()) >= 200) {
            s.untoggle(a.key());
            s.cooldown.put(a.key(), p.level().getGameTime() + a.cooldown);
        }
        // on the ground it keeps to the floor (only flying it goes through floors)
        if (!p.getAbilities().flying && p.level().getBlockState(p.blockPosition().below()).isSolidRender(p.level(),
                p.blockPosition().below()) && p.getDeltaMovement().y < 0.0D) {
            p.setDeltaMovement(p.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            p.setOnGround(true);
            p.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        if (event.phase == TickEvent.Phase.END && intangible(p)) {
            p.fallDistance = 0.0F;
            if (!p.getAbilities().flying) {
                var below = p.blockPosition().below();
                if (p.level().getBlockState(below).isSolidRender(p.level(), below) && p.getDeltaMovement().y < 0.0D
                        && p.getY() - Math.floor(p.getY()) < 0.05D) {
                    p.setDeltaMovement(p.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                    p.setOnGround(true);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Clones
    // ------------------------------------------------------------------

    static void clones(ServerPlayer p, InfPowers.Ability a) {
        int count = a.json.has("count") ? a.json.get("count").getAsInt() : 3;
        int life = a.json.has("lifetime") ? a.json.get("lifetime").getAsInt() : 1200;
        double spread = a.json.has("spread") ? a.json.get("spread").getAsDouble() : 2.0D;
        float health = a.json.has("health") ? a.json.get("health").getAsFloat() : 20.0F;
        ServerLevel level = p.serverLevel();
        for (StoneClone c : level.getEntitiesOfClass(StoneClone.class, p.getBoundingBox().inflate(256.0D),
                c -> p.getUUID().equals(c.owner()))) {
            c.vanish();
        }
        for (int i = 0; i < count; i++) {
            double ang = i * Math.PI * 2.0D / count;
            Vec3 at = p.position().add(Math.cos(ang) * spread, 0.0D, Math.sin(ang) * spread);
            StoneClone c = StoneClone.create(level, p, life, health);
            c.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0F);
            level.addFreshEntity(c);
            StoneClone.poof(level, at.x, at.y + 1.0D, at.z);
        }
        SpaceStone.sound(level, p.position(), "mind_stone_phase", 1.0F, 1.0F);
    }

    /** Mobs going for the player pick at random between the player and its clones near it. */
    @SubscribeEvent
    public static void confuse(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer p) || !(event.getEntity() instanceof Mob)) {
            return;
        }
        List<StoneClone> near = p.serverLevel().getEntitiesOfClass(StoneClone.class, p.getBoundingBox().inflate(16.0D),
                c -> p.getUUID().equals(c.owner()));
        if (near.isEmpty()) {
            return;
        }
        int i = p.getRandom().nextInt(near.size() + 1);
        if (i < near.size()) {
            event.setNewTarget(near.get(i));
        }
    }

    // ------------------------------------------------------------------
    // Reading a mind
    // ------------------------------------------------------------------

    static void readMind(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(32.0D));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(
                end.subtract(eye)).inflate(1.0D), e -> e instanceof LivingEntity && !e.isSpectator(),
                eye.distanceToSqr(end));
        if (eh == null) {
            if (canLocate(p)) {
                InfinityServer.open(p, "mind_name");
            } else {
                p.displayClientMessage(Component.translatable("message.flash.infinity.no_mind"), true);
            }
            return;
        }
        show(p, (LivingEntity) eh.getEntity());
    }

    /** Read by name (the Soul Stone's locating: from afar). */
    public static void readByName(ServerPlayer p, String name) {
        if (!canLocate(p)) {
            return;
        }
        LivingEntity found = SoulStone.locate(p, name);
        if (found == null) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.not_found", name), true);
            return;
        }
        show(p, found);
    }

    static boolean canLocate(ServerPlayer p) {
        InfinityServer.State s = InfinityServer.state(p);
        for (InfPowers.Power pw : InfPowers.all().values()) {
            for (InfPowers.Ability a : pw.abilities.values()) {
                if ("flash:locate_player".equals(a.type) && s.purchased(a.key())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void show(ServerPlayer p, LivingEntity e) {
        if (InfinityImmunity.immune(e, "mental")) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.mind_shielded"), true);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putString("open", "mind");
        CompoundTag d = new CompoundTag();
        d.putString("name", e.getDisplayName().getString());
        d.putString("type", e.getType().getDescription().getString());
        d.putFloat("health", e.getHealth());
        d.putFloat("max", e.getMaxHealth());
        d.putFloat("absorption", e.getAbsorptionAmount());
        d.putInt("armor", e.getArmorValue());
        if (e instanceof Mob m && m.getTarget() != null) {
            d.putString("target", m.getTarget().getDisplayName().getString());
        }
        if (e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() != null) {
            d.putString("owner", o.getOwner().getDisplayName().getString());
        }
        if (SoulStone.drained(e)) {
            d.putBoolean("drained", true);
        }
        if (e instanceof Player pl) {
            d.putInt("food", pl.getFoodData().getFoodLevel());
            d.putInt("level", pl.experienceLevel);
        }
        if (canLocate(p)) {
            d.putString("where", e.level().dimension().location() + " " + e.blockPosition().toShortString());
        }
        ListTag effects = new ListTag();
        for (MobEffectInstance ef : e.getActiveEffects()) {
            if (effects.size() < 8) {
                effects.add(ef.save(new CompoundTag()));
            }
        }
        d.put("effects", effects);
        ListTag gear = new ListTag();
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            gear.add(e.getItemBySlot(slot).save(new CompoundTag()));
        }
        d.put("gear", gear);
        ListTag inv = new ListTag();
        List<ItemStack> items = new ArrayList<>();
        if (e instanceof Player pl) {
            items.addAll(pl.getInventory().items);
        } else if (e instanceof net.minecraft.world.entity.npc.InventoryCarrier ic) {
            for (int i = 0; i < ic.getInventory().getContainerSize(); i++) {
                items.add(ic.getInventory().getItem(i));
            }
        } else if (e instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse h && h.hasChest()) {
            d.putBoolean("chested", true);
        }
        for (ItemStack st : items) {
            inv.add(st.save(new CompoundTag()));
        }
        d.put("inventory", inv);
        t.put("mind", d);
        FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new InfinitySyncPacket(t));
        SpaceStone.sound(p.level(), p.position(), "mind_stone_phase", 0.6F, 1.4F);
    }

    // ------------------------------------------------------------------
    // The disguise
    // ------------------------------------------------------------------

    private static final String DISGUISE = "flash_disguise";

    public static String disguise(Player p) {
        return p.getPersistentData().getString(DISGUISE);
    }

    /** The menu's answer: wear that player's skin and name ("" takes it off). */
    public static void setDisguise(ServerPlayer p, String name) {
        if (!canDisguise(p)) {
            return;
        }
        if (!name.isEmpty() && !name.matches("[A-Za-z0-9_]{3,16}")) {
            p.displayClientMessage(Component.translatable("message.flash.infinity.bad_name"), true);
            return;
        }
        if (name.isEmpty()) {
            p.getPersistentData().remove(DISGUISE);
            p.displayClientMessage(Component.translatable("message.flash.infinity.disguise_off"), true);
        } else {
            p.getPersistentData().putString(DISGUISE, name);
        }
        broadcastDisguise(p);
    }

    static boolean canDisguise(ServerPlayer p) {
        InfinityServer.State s = InfinityServer.state(p);
        for (InfPowers.Power pw : InfPowers.all().values()) {
            for (InfPowers.Ability a : pw.abilities.values()) {
                if ("flash:disguise".equals(a.type) && s.purchased(a.key())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The disguise is worn while its ability is unlocked (losing the stone takes it off). */
    static String worn(ServerPlayer p) {
        String d = disguise(p);
        if (d.isEmpty()) {
            return "";
        }
        InfinityServer.State s = InfinityServer.state(p);
        for (String pid : s.powers) {
            InfPowers.Power pw = InfPowers.get(pid);
            if (pw == null) {
                continue;
            }
            for (InfPowers.Ability a : pw.abilities.values()) {
                if ("flash:disguise".equals(a.type) && InfinityPowers.unlocked(s, a)) {
                    return d;
                }
            }
        }
        return "";
    }

    static void broadcastDisguise(ServerPlayer p) {
        String w = worn(p);
        p.getPersistentData().putString("flash_disguise_worn", w);
        CompoundTag t = new CompoundTag();
        t.putInt("disguise_entity", p.getId());
        t.putString("disguise", w);
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new InfinitySyncPacket(t));
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    @SubscribeEvent
    public static void onDisguiseTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p && p.tickCount % 20 == 0) {
            String w = worn(p);
            if (!w.equals(p.getPersistentData().getString("flash_disguise_worn"))) {
                broadcastDisguise(p);
            }
        }
    }

    @SubscribeEvent
    public static void onName(PlayerEvent.NameFormat event) {
        String w = event.getEntity().level().isClientSide ? DisguiseNames.client(event.getEntity())
                : event.getEntity().getPersistentData().getString("flash_disguise_worn");
        if (w != null && !w.isEmpty()) {
            event.setDisplayname(Component.literal(w));
        }
    }

    @SubscribeEvent
    public static void onTabName(PlayerEvent.TabListNameFormat event) {
        String w = event.getEntity().getPersistentData().getString("flash_disguise_worn");
        if (!w.isEmpty()) {
            event.setDisplayName(Component.literal(w));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            String w = target.getPersistentData().getString("flash_disguise_worn");
            if (!w.isEmpty()) {
                CompoundTag t = new CompoundTag();
                t.putInt("disguise_entity", target.getId());
                t.putString("disguise", w);
                FlashNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new InfinitySyncPacket(t));
            }
        }
    }

    /** The client's names of disguised players (set by MindClient). */
    public static final class DisguiseNames {
        public static java.util.function.Function<Player, String> client = p -> null;

        private DisguiseNames() {
        }

        static String client(Player p) {
            return client.apply(p);
        }
    }
}

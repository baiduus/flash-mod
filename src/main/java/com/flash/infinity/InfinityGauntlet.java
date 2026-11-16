package com.flash.infinity;

import com.flash.FlashMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Infinity Gauntlet: its six slots (space, mind, reality, power, time, soul; any stone in any slot, kept on the
 * item), its skins, the Snap (half of all life, the hostile, the other players) and what it costs (burnt, half a
 * heart, a day), the destruction of the stones. Worn in a hand (the offhand is its place: the original's Curios hands
 * slot), it gives the gauntlet's power and every stone's.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityGauntlet {

    public static final String STONES = "flash_stones";
    public static final String SKIN = "flash_skin";
    public static final String SKIN_ITEM = "flash_skin_item";
    public static final int SLOTS = 6;

    private InfinityGauntlet() {
    }

    public static boolean isGauntlet(ItemStack s) {
        return s.is(InfinityItems.INFINITY_GAUNTLET.get());
    }

    /** The gauntlet worn (the offhand first, else the main hand), or empty. */
    public static ItemStack worn(Player p) {
        if (isGauntlet(p.getOffhandItem())) {
            return p.getOffhandItem();
        }
        return isGauntlet(p.getMainHandItem()) ? p.getMainHandItem() : ItemStack.EMPTY;
    }

    /** The stone in each of the six slots (null: empty). */
    public static InfinityStone[] slots(ItemStack g) {
        InfinityStone[] out = new InfinityStone[SLOTS];
        CompoundTag t = g.getTag();
        if (t == null || !t.contains(STONES)) {
            return out;
        }
        ListTag l = t.getList(STONES, Tag.TAG_STRING);
        for (int i = 0; i < Math.min(SLOTS, l.size()); i++) {
            out[i] = InfinityStone.byId(l.getString(i));
        }
        return out;
    }

    public static void setSlots(ItemStack g, InfinityStone[] s) {
        ListTag l = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            l.add(StringTag.valueOf(s[i] == null ? "" : s[i].id));
        }
        g.getOrCreateTag().put(STONES, l);
    }

    public static List<InfinityStone> stones(ItemStack g) {
        List<InfinityStone> out = new ArrayList<>();
        for (InfinityStone s : slots(g)) {
            if (s != null && !out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    public static boolean has(ItemStack g, InfinityStone s) {
        return stones(g).contains(s);
    }

    public static String skin(ItemStack g) {
        CompoundTag t = g.getTag();
        return t == null ? "" : t.getString(SKIN);
    }

    static void addPowers(Player p, Set<String> out) {
        ItemStack g = worn(p);
        if (g.isEmpty()) {
            return;
        }
        for (InfinityStone s : stones(g)) {
            out.add(s.power());
        }
    }

    // ------------------------------------------------------------------
    // Conditions
    // ------------------------------------------------------------------

    static boolean test(InfinityPowers.View v, InfPowers.Ability a, JsonObject c, String type) {
        Player p = v.player();
        ItemStack g = worn(p);
        switch (type) {
            case "flash:gauntlet_slot": {
                if (g.isEmpty()) {
                    return false;
                }
                for (InfinityStone s : stones(g)) {
                    if (InfinityPowers.matches(new ItemStack(s.item()), c.get("item"))) {
                        return true;
                    }
                }
                return false;
            }
            case "flash:stone_equipped":
                return !g.isEmpty() && c.has("stone") && has(g, InfinityStone.byId(strip(c.get("stone"))));
            case "flash:gauntlet_visible":
                return !g.isEmpty();
            case "flash:gauntlet_arm": {
                if (g.isEmpty()) {
                    return false;
                }
                HumanoidArm arm = "left".equals(c.get("arm").getAsString()) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                boolean off = g == p.getOffhandItem();
                HumanoidArm worn = off ? p.getMainArm().getOpposite() : p.getMainArm();
                return worn == arm;
            }
            case "flash:gauntlet_skin": {
                String want = c.has("skin") ? c.get("skin").getAsString() : "*";
                String have = g.isEmpty() ? "" : skin(g);
                return "*".equals(want) ? !have.isEmpty() : want.equals(have);
            }
            case "flash:held_stone":
                return InfinityStone.of(p.getMainHandItem()) != null || InfinityStone.of(p.getOffhandItem()) != null;
            case "flash:custom_gauntlet":
                return false;
            case "flash:holding_unlock_item": {
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(c.get("item").getAsString()));
                return it != null && p.getMainHandItem().is(it) && !g.isEmpty() && g != p.getMainHandItem();
            }
            default:
                return false;
        }
    }

    private static String strip(JsonElement e) {
        String s = e.getAsString();
        return s.substring(s.indexOf(':') + 1);
    }

    // ------------------------------------------------------------------
    // The stones going in (the gauntlet's menu: GauntletMenu)
    // ------------------------------------------------------------------

    static void inserted(ServerPlayer p, ItemStack g) {
        SpaceStone.sound(p.level(), p.position(), "infinity_stone_equip", 1.0F, 1.0F);
        if (stones(g).size() == SLOTS) {
            InfinityAdvancements.grant(p, "6_stones");
        }
    }

    // ------------------------------------------------------------------
    // Skins (data/flash/infinity_gauntlet_skins: unlocked by an item, generated into the gauntlet's tree)
    // ------------------------------------------------------------------

    public static final String[] SKIN_IDS = {"netherite_gauntlet", "nano_gauntlet", "fantastic_four_skin",
            "finger_gauntlet", "soul_netherite_gauntlet", "endosym_gauntlet", "sith_gauntlet", "rose_gauntlet",
            "ftg_gauntlet", "cartoon_gauntlet", "panther_gauntlet", "ultron_gauntlet", "midnight", "52-d_gauntlet",
            "blaze_inc_gauntlet", "dragon_gauntlet", "gauntlet_burn", "hand_of_terror", "cheeselander"};
    private static final Map<String, JsonObject> SKINS = new LinkedHashMap<>();

    public static Map<String, JsonObject> skins() {
        if (SKINS.isEmpty()) {
            for (String id : SKIN_IDS) {
                JsonObject o = InfPowers.read("/data/flash/infinity_gauntlet_skins/" + id + ".json");
                if (o != null) {
                    SKINS.put(id, o);
                }
            }
        }
        return SKINS;
    }

    /** Right-clicking (or holding) a skin's item with the gauntlet worn: that skin, the item kept to give back. */
    static boolean tryApplySkin(ServerPlayer p, boolean holdOnly) {
        ItemStack g = worn(p);
        ItemStack held = p.getMainHandItem();
        if (g.isEmpty() || g == held || held.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, JsonObject> en : skins().entrySet()) {
            JsonObject o = en.getValue();
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(InfPowers.str(o, "unlock_item", "air")));
            if (it == null || !held.is(it)) {
                continue;
            }
            boolean hold = "hold".equals(InfPowers.str(o, "apply_on", "right_click"));
            if (hold != holdOnly) {
                continue;
            }
            String cur = skin(g);
            if (en.getKey().equals(cur)) {
                return false;
            }
            // a burnt gauntlet only takes a skin that can replace the burn
            if (!cur.isEmpty() && "gauntlet_burn".equals(cur) && p.getTags().contains("burn")
                    && !InfinityPowers.strings(o.get("can_replace")).contains("gauntlet_burn")) {
                return false;
            }
            giveBack(p, g);
            ItemStack one = held.split(1);
            g.getOrCreateTag().putString(SKIN, en.getKey());
            g.getOrCreateTag().put(SKIN_ITEM, one.save(new CompoundTag()));
            String snd = InfPowers.str(o, "apply_sound", "minecraft:item.armor.equip_netherite");
            SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(snd.replace("flash:",
                    "flash:infinity.")));
            if (se == null) {
                se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(snd));
            }
            if (se != null) {
                p.level().playSound(null, p.blockPosition(), se, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            InfinityAbilities.commands(p, o.get("apply_commands"));
            if ("nano_gauntlet".equals(en.getKey())) {
                p.getPersistentData().putLong("flash_nano_since", p.level().getGameTime());
            }
            return true;
        }
        return false;
    }

    private static void giveBack(ServerPlayer p, ItemStack g) {
        CompoundTag t = g.getTag();
        if (t != null && t.contains(SKIN_ITEM)) {
            ItemStack back = ItemStack.of(t.getCompound(SKIN_ITEM));
            t.remove(SKIN_ITEM);
            if (!back.isEmpty() && !p.getInventory().add(back)) {
                p.drop(back, false);
            }
        }
    }

    /** "Return to Original Gauntlet": the skin off, its item given back. */
    static void clearSkin(ServerPlayer p, InfPowers.Ability a) {
        ItemStack g = worn(p);
        if (g.isEmpty()) {
            return;
        }
        giveBack(p, g);
        g.getOrCreateTag().remove(SKIN);
        InfinityAbilities.commands(p, a.json.get("commands"));
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getHand() == InteractionHand.MAIN_HAND
                && tryApplySkin(p, false)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // The Snap
    // ------------------------------------------------------------------

    public enum SnapType { HALF, HOSTILE, PLAYERS }

    private static final class Session {
        final ServerPlayer snapper;
        final List<Entity> targets;
        int tick;

        Session(ServerPlayer snapper, List<Entity> targets) {
            this.snapper = snapper;
            this.targets = targets;
        }
    }

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> SNAP_HOSTILE = TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE, new ResourceLocation(FlashMod.MODID, "snap_hostile"));

    public static boolean snap(ServerPlayer p, SnapType type) {
        if (!SESSIONS.isEmpty()) {
            return false;
        }
        List<Entity> targets = new ArrayList<>();
        MinecraftServer server = p.server;
        switch (type) {
            case HALF -> {
                // half of all life, chunk by chunk
                Map<Long, List<Entity>> byChunk = new HashMap<>();
                for (ServerLevel level : server.getAllLevels()) {
                    for (Entity e : level.getAllEntities()) {
                        if (e instanceof LivingEntity && e != p && e.isAlive() && !e.getTags().contains("snapped")
                                && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) {
                            long key = ChunkPos.asLong(e.chunkPosition().x, e.chunkPosition().z) ^ level.dimension()
                                    .location().hashCode();
                            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
                        }
                    }
                }
                for (List<Entity> l : byChunk.values()) {
                    java.util.Collections.shuffle(l);
                    targets.addAll(l.subList(0, (l.size() + (p.getRandom().nextBoolean() ? 1 : 0)) / 2));
                }
            }
            case HOSTILE -> {
                for (ServerLevel level : server.getAllLevels()) {
                    for (Entity e : level.getAllEntities()) {
                        if (e.getType().is(SNAP_HOSTILE) && e.isAlive()) {
                            targets.add(e);
                        }
                    }
                }
            }
            case PLAYERS -> {
                for (ServerPlayer o : server.getPlayerList().getPlayers()) {
                    if (o != p && !o.isCreative() && !o.isSpectator()) {
                        targets.add(o);
                    }
                }
            }
        }
        SESSIONS.add(new Session(p, targets));
        p.level().playSound(null, p.blockPosition(), InfinitySounds.get("use_infinity_gauntlet"), SoundSource.PLAYERS,
                2.0F, 1.0F);
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            o.displayClientMessage(Component.translatable("message.flash.infinity.snap", p.getDisplayName()), false);
        }
        burn(p);
        return true;
    }

    /** Dust: the snapped fade away over five seconds (players to the Soulworld, the rest gone). */
    private static void dust(Session s, Entity e) {
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive()) {
            return;
        }
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.45F, 0.35F,
                0.28F), 1.2F), e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(), 50, 0.6D, 0.6D, 0.6D, 0.0D);
        e.addTag("snapped");
        if (e instanceof ServerPlayer sp) {
            ServerLevel soul = sp.server.getLevel(SoulStone.SOULWORLD);
            if (soul != null) {
                sp.teleportTo(soul, 1000.0D, 78.0D, 1000.0D, sp.getYRot(), sp.getXRot());
            } else {
                sp.hurt(level.damageSources().magic(), Float.MAX_VALUE);
            }
        } else if (e instanceof LivingEntity l) {
            l.hurt(level.damageSources().indirectMagic(s.snapper, s.snapper), 1.0E7F);
            if (l.isAlive()) {
                l.discard();
            }
        }
    }

    // ------------------------------------------------------------------
    // The burn (the Snap's price: half a heart, a whole day)
    // ------------------------------------------------------------------

    private static final UUID BURN = UUID.fromString("5d6b1c58-6a43-4a0f-9b7f-2f2c8f1e0b6a");

    static void burn(ServerPlayer p) {
        p.addTag("burn");
        p.addTag("burn_texture");
        p.getPersistentData().putLong("flash_burn_expires", p.level().getGameTime() + 24000L);
        halfHeart(p);
        ItemStack g = worn(p);
        if (!g.isEmpty()) {
            giveBack(p, g);
            g.getOrCreateTag().putString(SKIN, "gauntlet_burn");
        }
    }

    private static void halfHeart(Player p) {
        AttributeInstance h = p.getAttribute(Attributes.MAX_HEALTH);
        if (h != null && h.getModifier(BURN) == null) {
            h.addPermanentModifier(new AttributeModifier(BURN, "Snap burn", -1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
            h.removeModifier(BURN);
            double base = h.getValue();
            h.addPermanentModifier(new AttributeModifier(BURN, "Snap burn", 1.0D - base,
                    AttributeModifier.Operation.ADDITION));
        }
        if (p.getHealth() > 1.0F) {
            p.setHealth(1.0F);
        }
    }

    private static void heal(ServerPlayer p) {
        p.removeTag("burn");
        p.removeTag("burn_texture");
        p.getPersistentData().remove("flash_burn_expires");
        AttributeInstance h = p.getAttribute(Attributes.MAX_HEALTH);
        if (h != null) {
            h.removeModifier(BURN);
        }
        p.displayClientMessage(Component.translatable("message.flash.infinity.burn_healed"), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
            return;
        }
        if (p.getTags().contains("burn")) {
            long exp = p.getPersistentData().getLong("flash_burn_expires");
            if (exp > 0 && p.level().getGameTime() >= exp) {
                heal(p);
            } else {
                halfHeart(p);
            }
        }
        // the Nano Gauntlet's skin, worn a whole day, heals the burn
        ItemStack g = worn(p);
        if (!g.isEmpty() && "nano_gauntlet".equals(skin(g)) && p.getTags().contains("burn")
                && p.level().getGameTime() - p.getPersistentData().getLong("flash_nano_since") >= 24000L) {
            heal(p);
        }
        tryApplySkin(p, true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains("flash_burn_expires")) {
            event.getEntity().getPersistentData().putLong("flash_burn_expires", old.getLong("flash_burn_expires"));
            for (String t : new String[] {"burn", "burn_texture", "burn_right"}) {
                if (event.getOriginal().getTags().contains(t)) {
                    event.getEntity().addTag(t);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Destroying the stones
    // ------------------------------------------------------------------

    static void destroyTick(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a) {
        int t = s.timer(a.key());
        if (t % 10 == 0) {
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0D, p.getZ(), 30, 0.5D, 0.8D,
                    0.5D, 0.15D);
        }
        int max = a.json.has("max_value") ? a.json.get("max_value").getAsInt() : 140;
        if (t >= max) {
            destroyStones(p);
            s.untoggle(a.key());
        }
    }

    public static void destroyStones(ServerPlayer p) {
        ItemStack g = worn(p);
        if (!g.isEmpty()) {
            setSlots(g, new InfinityStone[SLOTS]);
        }
        for (var list : List.of(p.getInventory().items, p.getInventory().offhand, p.getInventory().armor)) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack st = list.get(i);
                if (InfinityStone.of(st) != null) {
                    list.set(i, ItemStack.EMPTY);
                } else if (InfinityContainers.isContainer(st) && InfinityContainers.stoneIn(st) != null) {
                    list.set(i, InfinityContainers.emptied(st));
                }
            }
        }
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1.0D, p.getZ(), 1, 0.0D, 0.0D,
                0.0D, 0.0D);
        level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                2.0F, 0.6F);
        InfinityAdvancements.grant(p, "destroy_stones");
        burn(p);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        for (java.util.Iterator<Session> it = SESSIONS.iterator(); it.hasNext(); ) {
            Session s = it.next();
            s.tick++;
            int per = Math.max(1, s.targets.size() / 100 + 1);
            for (int i = 0; i < per && !s.targets.isEmpty(); i++) {
                dust(s, s.targets.remove(s.targets.size() - 1));
            }
            if (s.targets.isEmpty()) {
                it.remove();
            }
        }
    }
}

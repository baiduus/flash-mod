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

    /** A skin with apply_on "hold": applied as soon as its item is held in the main hand with the gauntlet worn. */
    static void tryHoldSkin(ServerPlayer p) {
        ItemStack g = worn(p);
        ItemStack held = p.getMainHandItem();
        if (g.isEmpty() || g == held || held.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JsonObject> en : skins().entrySet()) {
            JsonObject o = en.getValue();
            if (!"hold".equals(InfPowers.str(o, "apply_on", "right_click"))) {
                continue;
            }
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(InfPowers.str(o, "unlock_item", "air")));
            if (it == null || !held.is(it)) {
                continue;
            }
            String cur = skin(g);
            if (en.getKey().equals(cur) || !cur.isEmpty() && !InfinityPowers.strings(o.get("can_replace"))
                    .contains(cur)) {
                return;
            }
            apply(p, g, held, en.getKey(), o);
            InfinityAbilities.commands(p, o.get("apply_commands"));
            return;
        }
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
        if (a != null) {
            InfinityAbilities.commands(p, a.json.get("commands"));
        }
    }

    /** flash:set_gauntlet_skin: 'skin' on the worn gauntlet (the unlock item in the main hand used up), or none. */
    static void setSkinAbility(ServerPlayer p, InfPowers.Ability a) {
        String id = InfPowers.str(a.json, "skin", "");
        if (id.isEmpty()) {
            clearSkin(p, a);
            return;
        }
        JsonObject o = skins().get(id);
        ItemStack g = worn(p);
        ItemStack held = p.getMainHandItem();
        if (o == null || g.isEmpty() || g == held) {
            return;
        }
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(InfPowers.str(o, "unlock_item", "air")));
        if (it == null || !held.is(it) || id.equals(skin(g))) {
            return;
        }
        apply(p, g, held, id, o);
        InfinityAbilities.commands(p, a.json.get("commands"));
    }

    private static void apply(ServerPlayer p, ItemStack g, ItemStack held, String id, JsonObject o) {
        giveBack(p, g);
        ItemStack one = held.split(1);
        g.getOrCreateTag().putString(SKIN, id);
        g.getOrCreateTag().put(SKIN_ITEM, one.save(new CompoundTag()));
        String snd = InfPowers.str(o, "apply_sound", "minecraft:item.armor.equip_netherite");
        SoundEvent se = snd.startsWith("flash:") && !snd.startsWith("flash:infinity.")
                ? InfinitySounds.get(snd.substring(6)) : ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(snd));
        if (se != null) {
            p.level().playSound(null, p.blockPosition(), se, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if ("nano_gauntlet".equals(id)) {
            p.getPersistentData().putLong("flash_nano_since", p.level().getGameTime());
        }
    }

    /** The abilities the skin files generate into the gauntlet's power (the original's GauntletSkinAbilities). */
    static Map<String, JsonObject> generatedAbilities() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        int free = 0;
        int index = 0;
        for (Map.Entry<String, JsonObject> en : skins().entrySet()) {
            String id = en.getKey();
            JsonObject o = en.getValue();
            com.google.gson.JsonArray pos = o.has("gui_position") && o.get("gui_position").isJsonArray()
                    ? o.getAsJsonArray("gui_position").deepCopy() : null;
            if (pos == null) {
                double x;
                double y;
                do {
                    x = -1.0D + (free % 5) * 0.5D;
                    y = 2.5D + (free / 5) * 1.0D;
                    free++;
                } while (used.contains(x + "," + y));
                pos = new com.google.gson.JsonArray();
                pos.add(x);
                pos.add(y);
            }
            used.add(pos.get(0).getAsDouble() + "," + pos.get(1).getAsDouble());
            String icon = "flash:textures/item/gauntlet_skins/" + id + ".png";
            String title = InfPowers.str(o, "title", id);
            boolean secret = InfPowers.bool(o, "hidden", false);
            boolean hold = "hold".equals(InfPowers.str(o, "apply_on", "right_click"));
            if (!hold) {
                JsonObject ab = new JsonObject();
                ab.addProperty("type", "flash:set_gauntlet_skin");
                ab.addProperty("skin", id);
                ab.addProperty("icon", icon);
                ab.addProperty("title", title);
                ab.addProperty("bar_color", InfPowers.str(o, "bar_color", "light_gray"));
                ab.addProperty("hidden", true);
                ab.addProperty("hidden_in_bar", true);
                ab.addProperty("list_index", index++);
                ab.add("gui_position", pos.deepCopy());
                if (o.has("description")) {
                    ab.add("description", o.get("description").deepCopy());
                }
                ab.add("commands", o.has("apply_commands") ? o.get("apply_commands").deepCopy()
                        : new com.google.gson.JsonArray());
                JsonObject cond = new JsonObject();
                com.google.gson.JsonArray enabling = new com.google.gson.JsonArray();
                JsonObject act = new JsonObject();
                act.addProperty("type", "palladium:action");
                act.addProperty("cooldown", 0);
                act.addProperty("key_type", "right_click");
                act.addProperty("needs_empty_hand", false);
                act.addProperty("allow_scrolling_when_crouching", true);
                enabling.add(act);
                com.google.gson.JsonArray unlocking = new com.google.gson.JsonArray();
                List<String> over = InfinityPowers.strings(o.get("can_replace"));
                if (over.isEmpty()) {
                    unlocking.add(skinCondition(""));
                } else {
                    JsonObject or = new JsonObject();
                    or.addProperty("type", "palladium:or");
                    com.google.gson.JsonArray l = new com.google.gson.JsonArray();
                    l.add(skinCondition(""));
                    for (String r : over) {
                        l.add(skinCondition(r));
                    }
                    or.add("conditions", l);
                    unlocking.add(or);
                }
                JsonObject inSlot = new JsonObject();
                inSlot.addProperty("type", "palladium:item_in_slot");
                JsonObject item = new JsonObject();
                item.addProperty("item", InfPowers.str(o, "unlock_item", "minecraft:air"));
                inSlot.add("item", item);
                inSlot.addProperty("slot", "mainhand");
                unlocking.add(inSlot);
                cond.add("enabling", enabling);
                cond.add("unlocking", unlocking);
                ab.add("conditions", cond);
                out.put(id.replace('-', '_'), ab);
            }
            if (!secret) {
                JsonObject tree = new JsonObject();
                tree.addProperty("type", "palladium:dummy");
                tree.addProperty("icon", icon);
                tree.addProperty("title", title);
                tree.addProperty("bar_color", "light_gray");
                tree.addProperty("hidden", false);
                tree.addProperty("hidden_in_bar", true);
                tree.add("gui_position", pos.deepCopy());
                if (o.has("description")) {
                    tree.add("description", o.get("description").deepCopy());
                } else {
                    tree.addProperty("description", "Hold " + InfPowers.str(o, "unlock_item", "?") + " and "
                            + (hold ? "keep it in your main hand" : "right-click") + " with the gauntlet worn.");
                }
                JsonObject cond = new JsonObject();
                com.google.gson.JsonArray unlocking = new com.google.gson.JsonArray();
                unlocking.add(skinCondition(id));
                cond.add("unlocking", unlocking);
                tree.add("conditions", cond);
                out.put(id.replace('-', '_') + "_tree", tree);
            }
        }
        return out;
    }

    private static JsonObject skinCondition(String skin) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "flash:gauntlet_skin");
        c.addProperty("skin", skin);
        return c;
    }

    // ------------------------------------------------------------------
    // The Gauntlet's fist: much more than a punch (more with every stone in it)
    // ------------------------------------------------------------------

    public static final float PUNCH_BASE = 12.0F;
    public static final float PUNCH_PER_STONE = 4.0F;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPunch(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p
                || !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        ItemStack g = worn(p);
        if (g.isEmpty()) {
            return;
        }
        int n = stones(g).size();
        event.setAmount(event.getAmount() + PUNCH_BASE + PUNCH_PER_STONE * n);
        LivingEntity t = event.getEntity();
        net.minecraft.world.phys.Vec3 push = t.position().subtract(p.position()).multiply(1.0D, 0.0D, 1.0D);
        if (push.lengthSqr() > 1.0E-4D) {
            push = push.normalize().scale(0.6D + 0.15D * n);
            t.push(push.x, 0.25D + 0.03D * n, push.z);
            t.hurtMarked = true;
        }
        if (p.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + t.getBbHeight() * 0.5D, t.getZ(), 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            level.playSound(null, t.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 0.35F, 0.6F);
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
        boolean off = !g.isEmpty() && g == p.getOffhandItem();
        HumanoidArm arm = off ? p.getMainArm().getOpposite() : p.getMainArm();
        if (arm == HumanoidArm.RIGHT) {
            p.addTag("burn_right");
        } else {
            p.removeTag("burn_right");
        }
        if (!g.isEmpty()) {
            giveBack(p, g);
            g.getOrCreateTag().putString(SKIN, "gauntlet_burn");
        }
    }

    /** Half a heart of maximum health, whatever else changes it (re-applied every second). */
    private static void halfHeart(Player p) {
        AttributeInstance h = p.getAttribute(Attributes.MAX_HEALTH);
        if (h != null) {
            AttributeModifier cur = h.getModifier(BURN);
            double without = h.getValue() - (cur == null ? 0.0D : cur.getAmount());
            double want = 1.0D - without;
            if (cur == null || Math.abs(cur.getAmount() - want) > 1.0E-4D) {
                h.removeModifier(BURN);
                h.addPermanentModifier(new AttributeModifier(BURN, "Snap burn", want,
                        AttributeModifier.Operation.ADDITION));
            }
        }
        if (p.getHealth() > 1.0F) {
            p.setHealth(1.0F);
        }
    }

    private static void heal(ServerPlayer p) {
        p.removeTag("burn");
        p.removeTag("burn_texture");
        p.removeTag("burn_right");
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
        tryHoldSkin(p);
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
        InfinityOrb.destroyStone(p.server);
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

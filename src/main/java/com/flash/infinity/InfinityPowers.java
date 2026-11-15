package com.flash.infinity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Which powers a player has now, and the conditions of the abilities, the same on the server and the client.
 * <ul>
 * <li>A power comes from an item where the original puts it (its item_powers): a stone in a hand, worn on the head
 * (the original's Curios head slot: here the helmet slot) or round the neck (here the chest slot), the Tesseract,
 * the Aether, the Orb, the Eye of Agamotto, the Cosmi-Rod... and from the stones set in the worn Infinity Gauntlet
 * and in the Scepter / the Eye (InfinityContainers).</li>
 * <li>A condition (the original's, kept as written in its power files) is checked against a {@link View}: what the
 * player bought, its settings, what is on, its cooldowns and scores.</li>
 * </ul>
 */
public final class InfinityPowers {

    /** A rule of the original's item_powers: in that slot, one of those items, gives that power. */
    private record Grant(String slot, List<Item> items, List<String> powers) {
    }

    private static final List<Grant> GRANTS = new ArrayList<>();
    private static boolean loaded;

    private static final String[] GRANT_FILES = {"bottle", "cosmi_rod", "cosmi_rod2", "empty_aether", "empty_aether2",
            "eye_of_agamotto", "eye_of_agamotto2", "infinity_gauntlet", "infinity_gauntlet_curios",
            "infinity_gauntlet_mainhand", "mind_stone", "mind_stone2", "mind_stone_head", "orb", "orb2", "power_stone",
            "power_stone2", "power_stone_head", "reality_stone", "reality_stone2", "reality_stone_head",
            "soul_guardian_telephone", "soul_stone", "soul_stone2", "soul_stone_head", "space_stone", "space_stone2",
            "space_stone_head", "tesseract", "tesseract_offhand", "thanos_classic_chestplate", "time_stone",
            "time_stone2", "time_stone3", "time_stone4", "time_stone_head"};

    private InfinityPowers() {
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (String f : GRANT_FILES) {
            try (InputStream in = InfinityPowers.class.getResourceAsStream("/data/flash/infinity_item_powers/" + f
                    + ".json")) {
                if (in == null) {
                    continue;
                }
                JsonObject o = com.google.gson.JsonParser.parseReader(new InputStreamReader(in,
                        StandardCharsets.UTF_8)).getAsJsonObject();
                List<Item> items = new ArrayList<>();
                for (String id : strings(o.get("item"))) {
                    Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                    if (it != null && it != net.minecraft.world.item.Items.AIR) {
                        items.add(it);
                    }
                }
                List<String> powers = new ArrayList<>();
                for (String id : strings(o.get("power"))) {
                    powers.add(id.substring(id.indexOf(':') + 1));
                }
                GRANTS.add(new Grant(o.get("slot").getAsString(), items, powers));
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("[flash] item power {}: {}", f, e.toString());
            }
        }
    }

    static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null) {
            return out;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsString());
            }
        } else {
            out.add(e.getAsString());
        }
        return out;
    }

    /** The stack in one of the original's slots (Curios slots mapped to the vanilla ones). */
    public static ItemStack slot(Player p, String slot) {
        return switch (slot) {
            case "mainhand" -> p.getMainHandItem();
            case "offhand", "curios:hands", "trinkets:offhand/glove" -> p.getOffhandItem();
            case "head", "curios:head" -> p.getItemBySlot(EquipmentSlot.HEAD);
            case "chest", "curios:necklace", "curios:body" -> p.getItemBySlot(EquipmentSlot.CHEST);
            case "legs" -> p.getItemBySlot(EquipmentSlot.LEGS);
            case "feet" -> p.getItemBySlot(EquipmentSlot.FEET);
            default -> ItemStack.EMPTY;
        };
    }

    /** The powers this player has now (its order: the order the trees are shown in). */
    public static Set<String> powers(Player p) {
        load();
        Set<String> out = new LinkedHashSet<>();
        for (Grant g : GRANTS) {
            ItemStack s = slot(p, g.slot);
            if (!s.isEmpty() && g.items.contains(s.getItem())) {
                out.addAll(g.powers);
            }
        }
        InfinityContainers.addPowers(p, out);
        if (p.getTags().contains("burn")) {
            out.add("burn");
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Conditions
    // ------------------------------------------------------------------

    /** What the conditions are checked against (the server's state, or the client's copy of it). */
    public interface View {
        Player player();

        Set<String> powers();

        boolean purchased(String key);

        boolean settingOn(String setting);

        boolean enabled(String key);

        boolean cooling(String key);

        int score(String objective);

        int timer(String key);
    }

    public static boolean unlocked(View v, InfPowers.Ability a) {
        if (!a.buys.isEmpty() && !v.purchased(a.key())) {
            return false;
        }
        return all(v, a, a.unlocking);
    }

    /** Can be bought now: everything else it asks for is there. */
    public static boolean buyable(View v, InfPowers.Ability a) {
        return !a.buys.isEmpty() && !v.purchased(a.key()) && all(v, a, a.unlocking);
    }

    static boolean all(View v, InfPowers.Ability a, List<JsonObject> conds) {
        for (JsonObject c : conds) {
            if (!test(v, a, c)) {
                return false;
            }
        }
        return true;
    }

    private static String abilityKey(InfPowers.Ability a, JsonObject c) {
        String power = c.has("power") && c.get("power").isJsonPrimitive() ? c.get("power").getAsString() : "null";
        String ab = c.get("ability").getAsString();
        String pid = "null".equals(power) ? a.power.id : power.substring(power.indexOf(':') + 1);
        return pid + "/" + ab;
    }

    public static boolean test(View v, InfPowers.Ability a, JsonObject c) {
        Player p = v.player();
        String type = c.has("type") ? c.get("type").getAsString() : "";
        switch (type) {
            case "palladium:true":
                return true;
            case "palladium:not":
                return !any(v, a, c.getAsJsonArray("conditions"));
            case "palladium:or":
                return any(v, a, c.getAsJsonArray("conditions"));
            case "palladium:and": {
                for (JsonElement e : c.getAsJsonArray("conditions")) {
                    if (!test(v, a, e.getAsJsonObject())) {
                        return false;
                    }
                }
                return true;
            }
            case "palladium:ability_unlocked": {
                InfPowers.Ability o = InfPowers.ability(abilityKey(a, c));
                return o != null && unlocked(v, o);
            }
            case "palladium:ability_enabled":
                return v.enabled(abilityKey(a, c));
            case "palladium:ability_on_cooldown":
                return v.cooling(abilityKey(a, c));
            case "palladium:has_power": {
                String pw = c.get("power").getAsString();
                return v.powers().contains(pw.substring(pw.indexOf(':') + 1));
            }
            case "palladium:crouching":
                return p.isCrouching();
            case "palladium:is_moving":
                return p.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
            case "palladium:is_hovering_or_flying":
                return p.getAbilities().flying || !p.onGround() && Math.abs(p.getDeltaMovement().y) < 0.05D;
            case "palladium:has_tag":
                return p.getTags().contains(c.get("tag").getAsString());
            case "palladium:objective_score": {
                int s = v.score(c.get("objective").getAsString());
                int min = c.has("min_score") ? c.get("min_score").getAsInt() : Integer.MIN_VALUE;
                int max = c.has("max_score") ? c.get("max_score").getAsInt() : Integer.MAX_VALUE;
                return s >= min && s <= max;
            }
            case "palladium:animation_timer_ability": {
                int t = v.timer(abilityKey(a, c));
                return t >= c.get("min").getAsInt() && t <= c.get("max").getAsInt();
            }
            case "palladium:item_in_slot": {
                ItemStack s = slot(p, c.get("slot").getAsString());
                return matches(s, c.get("item"));
            }
            case "flash:setting":
                return v.settingOn(c.get("setting").getAsString());
            case "flash:stormbreaker_near":
                return InfinityContainers.stormbreakerNear(p);
            default:
                return InfinityContainers.test(v, a, c, type);
        }
    }

    private static boolean any(View v, InfPowers.Ability a, JsonArray conds) {
        for (JsonElement e : conds) {
            if (test(v, a, e.getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    /** An ingredient as the original writes it: {"item": id} or {"tag": id} (or a list of them). */
    public static boolean matches(ItemStack s, JsonElement ing) {
        if (ing == null || s.isEmpty()) {
            return false;
        }
        if (ing.isJsonArray()) {
            for (JsonElement e : ing.getAsJsonArray()) {
                if (matches(s, e)) {
                    return true;
                }
            }
            return false;
        }
        JsonObject o = ing.getAsJsonObject();
        if (o.has("item")) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(o.get("item").getAsString()));
            return it != null && s.is(it);
        }
        if (o.has("tag")) {
            return s.is(net.minecraft.tags.ItemTags.create(new ResourceLocation(o.get("tag").getAsString())));
        }
        return false;
    }
}

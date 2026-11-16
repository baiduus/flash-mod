package com.flash.infinity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ability trees of the Infinity Stones and their companions, read as the original defines them (its power files,
 * shipped in data/flash/infinity_powers): every ability with its type, its title and description, its icon and place
 * in the tree, how it is switched on (a key: an action, a toggle, held...; or by itself) and what unlocks it (Aspects
 * of Existence to spend, other abilities first, other stones...). What each type does: InfinityAbilities.
 */
public final class InfPowers {

    /** The powers the mod uses (the "ground" ones of the original only change how a dropped item is drawn). */
    public static final String[] IDS = {"space_stone", "mind_stone", "reality_stone", "power_stone", "time_stone",
            "soul_stone", "infinity_gauntlet", "orb", "eye_of_agamotto", "empty_aether", "cosmi_rod",
            "soul_guardian_telephone", "xp", "tesseract", "thanos_classic_chestplate", "burn"};

    public enum Key { NONE, ACTION, TOGGLE, HELD, DOUBLE_ACTION, DOUBLE_TOGGLE }

    public static final class Ability {
        public final Power power;
        public final String id;
        public final String type;
        public final JsonObject json;
        public final String title;
        public final JsonElement description;
        public final String icon;
        public final float guiX;
        public final float guiY;
        public final boolean hidden;
        public final boolean hiddenInBar;
        public final int listIndex;
        public final Key key;
        public final int cooldown;
        public final String keyMessage;
        /** The original's key type: key_bind (the ability key), scroll_up / scroll_down (the mouse wheel)... */
        public final String keyType;
        /** The enabling conditions other than the key's. */
        public final List<JsonObject> enabling = new ArrayList<>();
        public final List<JsonObject> unlocking = new ArrayList<>();
        /** Aspects of Existence (or another item) to spend, and experience levels. */
        public final List<JsonObject> buys = new ArrayList<>();

        Ability(Power power, String id, JsonObject o) {
            this.power = power;
            this.id = id;
            this.json = o;
            this.type = str(o, "type", "palladium:dummy");
            this.title = o.has("title") && o.get("title").isJsonPrimitive() ? o.get("title").getAsString() : id;
            this.description = o.get("description");
            this.icon = o.has("icon") && o.get("icon").isJsonPrimitive() ? o.get("icon").getAsString() : null;
            float x = 0.0F;
            float y = 0.0F;
            if (o.has("gui_position") && o.get("gui_position").isJsonArray()) {
                JsonArray a = o.getAsJsonArray("gui_position");
                x = a.get(0).getAsFloat();
                y = a.get(1).getAsFloat();
            }
            this.guiX = x;
            this.guiY = y;
            this.hidden = bool(o, "hidden", false);
            this.hiddenInBar = bool(o, "hidden_in_bar", false);
            this.listIndex = o.has("list_index") ? o.get("list_index").getAsInt() : -1;
            Key k = Key.NONE;
            int cd = 0;
            String msg = null;
            String kt = "key_bind";
            JsonObject cond = o.has("conditions") ? o.getAsJsonObject("conditions") : new JsonObject();
            if (cond.has("enabling")) {
                for (JsonElement e : cond.getAsJsonArray("enabling")) {
                    JsonObject c = e.getAsJsonObject();
                    String t = str(c, "type", "");
                    Key ck = switch (t) {
                        case "palladium:action" -> Key.ACTION;
                        case "palladium:toggle" -> Key.TOGGLE;
                        case "palladium:held" -> Key.HELD;
                        case "flash:double_action" -> Key.DOUBLE_ACTION;
                        case "flash:double_toggle" -> Key.DOUBLE_TOGGLE;
                        default -> null;
                    };
                    if (ck != null) {
                        k = ck;
                        cd = c.has("cooldown") ? c.get("cooldown").getAsInt() : 0;
                        msg = c.has("message") ? c.get("message").getAsString() : null;
                        kt = c.has("key_type") ? c.get("key_type").getAsString() : "key_bind";
                    } else {
                        this.enabling.add(c);
                    }
                }
            }
            if (cond.has("unlocking")) {
                for (JsonElement e : cond.getAsJsonArray("unlocking")) {
                    JsonObject c = e.getAsJsonObject();
                    String t = str(c, "type", "");
                    if (t.equals("palladium:item_buyable") || t.equals("palladium:experience_level_buyable")) {
                        this.buys.add(c);
                    } else {
                        this.unlocking.add(c);
                    }
                }
            }
            this.key = k;
            this.cooldown = cd;
            this.keyMessage = msg;
            this.keyType = kt;
        }

        public String key() {
            return this.power.id + "/" + this.id;
        }

        /** Shown in the tree (the original hides "hidden" ones). */
        public boolean inTree() {
            return !this.hidden;
        }

        /** Picked with Alt + scroll and used with the ability key. */
        public boolean inBar() {
            return !this.hiddenInBar && this.key != Key.NONE && "key_bind".equals(this.keyType);
        }

        /** Aspects of Existence to spend (0: none). */
        public int aspects() {
            int n = 0;
            for (JsonObject b : this.buys) {
                if (b.has("amount")) {
                    n += b.get("amount").getAsInt();
                }
            }
            return n;
        }

        public int levels() {
            int n = 0;
            for (JsonObject b : this.buys) {
                if (b.has("xp_level")) {
                    n += b.get("xp_level").getAsInt();
                }
            }
            return n;
        }

        public String setting() {
            return str(this.json, "setting", null);
        }
    }

    public static final class Power {
        public final String id;
        public final String name;
        public final String icon;
        public final String background;
        public final boolean persistent;
        public final Map<String, Ability> abilities = new LinkedHashMap<>();
        public final JsonObject json;

        Power(String id, JsonObject o) {
            this.id = id;
            this.json = o;
            this.name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : id;
            this.icon = str(o, "icon", null);
            this.background = str(o, "background", null);
            this.persistent = bool(o, "persistent_data", false);
            if (o.has("abilities")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("abilities").entrySet()) {
                    if (e.getValue().isJsonObject() && "gauntlet_skins".equals(str(e.getValue().getAsJsonObject(),
                            "flash:generate", null))) {
                        // the gauntlet skin files' abilities, generated in their place (GauntletSkinAbilities)
                        for (Map.Entry<String, JsonObject> g : InfinityGauntlet.generatedAbilities().entrySet()) {
                            this.abilities.put(g.getKey(), new Ability(this, g.getKey(), g.getValue()));
                        }
                    } else if (e.getValue().isJsonObject()) {
                        this.abilities.put(e.getKey(), new Ability(this, e.getKey(), e.getValue().getAsJsonObject()));
                    }
                }
            }
        }

        public Ability ability(String id) {
            return this.abilities.get(id);
        }

        /** Its abilities in the order of the bar (list_index). */
        public List<Ability> bar() {
            List<Ability> out = new ArrayList<>();
            for (Ability a : this.abilities.values()) {
                if (a.inBar()) {
                    out.add(a);
                }
            }
            out.sort((a, b) -> Integer.compare(a.listIndex < 0 ? 999 : a.listIndex, b.listIndex < 0 ? 999
                    : b.listIndex));
            return out;
        }
    }

    private static final Map<String, Power> POWERS = new LinkedHashMap<>();

    static {
        for (String id : IDS) {
            JsonObject o = read("/data/flash/infinity_powers/" + id + ".json");
            if (o != null) {
                POWERS.put(id, new Power(id, o));
            }
        }
    }

    private InfPowers() {
    }

    public static Power get(String id) {
        if (id == null) {
            return null;
        }
        int c = id.indexOf(':');
        return POWERS.get(c >= 0 ? id.substring(c + 1) : id);
    }

    public static Ability ability(String key) {
        int s = key.indexOf('/');
        Power p = s < 0 ? null : get(key.substring(0, s));
        return p == null ? null : p.ability(key.substring(s + 1));
    }

    public static Map<String, Power> all() {
        return Collections.unmodifiableMap(POWERS);
    }

    static JsonObject read(String path) {
        try (InputStream in = InfPowers.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("[flash] infinity power {} not read: {}", path, e.toString());
            return null;
        }
    }

    static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }
}

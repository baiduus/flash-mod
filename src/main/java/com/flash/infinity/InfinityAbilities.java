package com.flash.infinity;

import com.flash.FlashMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What each type of ability does while on (rising edge, every tick, falling edge), as the original's ability types:
 * <ul>
 * <li>palladium:command: its commands run as the player (first tick, every tick, last tick);</li>
 * <li>palladium:attribute_modifier: the attribute changed while on (the Mind Stone's flight speed: flight);</li>
 * <li>palladium:damage_immunity: those kinds of damage do not reach the player while on;</li>
 * <li>flash:potion_effect: an effect kept up;</li>
 * <li>flash:toggle: a switch clicked in the tree (a setting of the stone, or a game rule);</li>
 * <li>the stones' own types: InfinityStoneAbilities.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityAbilities {

    private InfinityAbilities() {
    }

    /** One ability, this tick: on now, on the tick before. */
    static void run(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a, boolean on, boolean was) {
        JsonObject j = a.json;
        switch (a.type) {
            case "palladium:command" -> {
                if (on && !was) {
                    commands(p, j.get("first_tick_commands"));
                }
                if (on) {
                    commands(p, j.get("commands"));
                }
                if (!on && was) {
                    commands(p, j.get("last_tick_commands"));
                }
            }
            case "palladium:attribute_modifier" -> attribute(p, a, on);
            case "flash:potion_effect" -> {
                if (on) {
                    MobEffect e = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(j.get("effect")
                            .getAsString()));
                    int refresh = j.has("refresh_at") ? j.get("refresh_at").getAsInt() : 10;
                    MobEffectInstance cur = e == null ? null : p.getEffect(e);
                    if (e != null && (cur == null || cur.getDuration() <= refresh)) {
                        p.addEffect(new MobEffectInstance(e, j.has("duration") ? j.get("duration").getAsInt() : 20,
                                j.has("amplifier") ? j.get("amplifier").getAsInt() : 0, false,
                                !InfPowers.bool(j, "hide_particles", false)));
                    }
                }
            }
            default -> InfinityStoneAbilities.run(p, s, a, on, was);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    static void commands(ServerPlayer p, JsonElement list) {
        if (list == null || !list.isJsonArray()) {
            return;
        }
        CommandSourceStack src = p.createCommandSourceStack().withSuppressedOutput().withPermission(2);
        for (JsonElement e : list.getAsJsonArray()) {
            String cmd = e.getAsString().trim();
            if (cmd.isEmpty() || cmd.startsWith("superpower ")) {
                continue;
            }
            // the original's ids are this mod's now
            cmd = cmd.replace(" infinity:", " flash:").replace("advancement grant @s only flash:",
                    "advancement grant @s only flash:infinity/");
            p.server.getCommands().performPrefixedCommand(src, cmd);
        }
    }

    /** Commands run as the player at a spot (a beam's commands where it hits a block). */
    static void commandsAt(ServerPlayer p, JsonElement list, net.minecraft.world.phys.Vec3 at) {
        if (list == null || !list.isJsonArray()) {
            return;
        }
        CommandSourceStack src = p.createCommandSourceStack().withSuppressedOutput().withPermission(2)
                .withPosition(at);
        for (JsonElement e : list.getAsJsonArray()) {
            p.server.getCommands().performPrefixedCommand(src, e.getAsString().replace(" infinity:", " flash:"));
        }
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    private static final Map<String, UUID> IDS = new HashMap<>();

    private static UUID id(String key) {
        return IDS.computeIfAbsent(key, k -> UUID.nameUUIDFromBytes(("flash_infinity_" + k).getBytes()));
    }

    private static void attribute(ServerPlayer p, InfPowers.Ability a, boolean on) {
        JsonObject j = a.json;
        String attr = j.get("attribute").getAsString();
        if ("palladium:flight_speed".equals(attr)) {
            InfinityStoneAbilities.flight(p, a, on);
            return;
        }
        Attribute at = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attr));
        if (at == null) {
            return;
        }
        AttributeInstance inst = p.getAttribute(at);
        if (inst == null) {
            return;
        }
        UUID uuid = id(a.key());
        AttributeModifier cur = inst.getModifier(uuid);
        if (!on) {
            if (cur != null) {
                inst.removeModifier(uuid);
                if (p.getHealth() > p.getMaxHealth()) {
                    p.setHealth(p.getMaxHealth());
                }
            }
            return;
        }
        if (cur == null) {
            double amount = j.get("amount").getAsDouble();
            int op = j.has("operation") ? j.get("operation").getAsInt() : 0;
            inst.addTransientModifier(new AttributeModifier(uuid, "flash_infinity", amount,
                    AttributeModifier.Operation.fromValue(op)));
        }
    }

    // ------------------------------------------------------------------
    // Damage immunity
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        InfinityServer.State s = InfinityServer.state(p);
        for (String key : s.enabled) {
            InfPowers.Ability a = InfPowers.ability(key);
            if (a == null || !"palladium:damage_immunity".equals(a.type)) {
                continue;
            }
            for (String src : InfinityPowers.strings(a.json.get("damage_sources"))) {
                TagKey<DamageType> tag = TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(src));
                if (event.getSource().is(tag)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // The switches in the tree
    // ------------------------------------------------------------------

    static void clickToggle(ServerPlayer p, InfinityServer.State s, InfPowers.Ability a) {
        if (!"flash:toggle".equals(a.type) || !InfinityPowers.unlocked(s, a)) {
            return;
        }
        String setting = a.setting();
        if (setting != null) {
            s.setSetting(setting, !s.settingOn(setting));
        } else if (a.json.has("gamerule")) {
            GameRules.BooleanValue rule = p.server.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
            rule.set(!rule.get(), p.server);
        }
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.2F);
        InfinityServer.sync(p, true);
    }

    /** A switch's state (settings on by default; the daylight rule). */
    public static boolean toggleState(InfinityPowers.View v, InfPowers.Ability a) {
        String setting = a.setting();
        if (setting != null) {
            return v.settingOn(setting);
        }
        return v.player().level().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }
}

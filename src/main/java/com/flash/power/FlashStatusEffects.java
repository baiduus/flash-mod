package com.flash.power;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Vanilla status-effect version of the Flash survival bonuses.
 *
 * <p>Regeneration and Strength are now real vanilla effects instead of a
 * custom healing loop. Their amplifiers follow the selected speed level and
 * are refreshed while the player is transformed. A short refresh duration
 * avoids accumulating effects and lets the bonus disappear immediately when
 * the powers are removed or the level changes.</p>
 *
 * <p>Regeneration VI would otherwise make a player effectively immune to
 * sustained damage, so a server-side healing ceiling is applied separately by
 * {@link #allowHeal(ServerPlayer, float)}. The status effect and its icon still
 * follow the requested progression.</p>
 */
public final class FlashStatusEffects {

    private static final int REFRESH_INTERVAL = 20;
    private static final int EFFECT_DURATION = 60;
    private static final float MAX_REGENERATION_HEAL_PER_SECOND = 4.0F;

    private static final Map<UUID, Owned> OWNED = new ConcurrentHashMap<>();
    private static final Map<UUID, HealWindow> HEAL_WINDOWS = new ConcurrentHashMap<>();

    private FlashStatusEffects() {
    }

    public static int regenerationAmplifier(int level) {
        if (level <= 0) {
            return -1;
        }
        if (level <= 2) {
            return 0; // Regeneration I
        }
        if (level <= 4) {
            return 1; // Regeneration II
        }
        if (level <= 6) {
            return 2; // Regeneration III
        }
        if (level <= 8) {
            return 3; // Regeneration IV
        }
        if (level == 9) {
            return 4; // Regeneration V
        }
        return 5; // Regeneration VI
    }

    public static int strengthAmplifier(int level) {
        if (level <= 0) {
            return -1;
        }
        if (level <= 2) {
            return 0; // Strength I
        }
        if (level <= 4) {
            return 1; // Strength II
        }
        if (level <= 6) {
            return 2; // Strength III
        }
        if (level <= 8) {
            return 3; // Strength IV
        }
        if (level == 9) {
            return 4; // Strength V
        }
        return 5; // Strength VI
    }

    /** Level 1 = Haste IV (x1.8) ... level 10 = Haste XXXI (about x7 faster mining). */
    public static int hasteAmplifier(int level) {
        return Math.min(40, Math.max(0, level) * 3);
    }

    public static String regenerationName(int level) {
        int amp = regenerationAmplifier(level);
        return amp < 0 ? "-" : "Regeneration " + roman(amp + 1);
    }

    public static String strengthName(int level) {
        int amp = strengthAmplifier(level);
        return amp < 0 ? "-" : "Strength " + roman(amp + 1);
    }

    public static void tick(ServerPlayer player) {
        int level = currentLevel(player);
        if (level <= 0) {
            removeOwned(player);
            return;
        }

        Owned owned = OWNED.get(player.getUUID());
        if (owned != null && owned.level == level
                && player.tickCount % REFRESH_INTERVAL != 0) {
            return;
        }
        apply(player, level);
    }

    public static void onLevelChanged(ServerPlayer player) {
        int level = currentLevel(player);
        if (level <= 0) {
            removeOwned(player);
        } else {
            apply(player, level);
        }
    }

    public static void reset(ServerPlayer player) {
        removeOwned(player);
        HEAL_WINDOWS.remove(player.getUUID());
    }

    public static void clear() {
        OWNED.clear();
        HEAL_WINDOWS.clear();
    }

    /**
     * Safety ceiling applied by the LivingHealEvent handler. It keeps the
     * high-level Regeneration icon/progression but prevents level 10 from
     * becoming permanent invulnerability.
     */
    public static float allowHeal(ServerPlayer player, float amount) {
        if (amount <= 0.0F || !isRegenerationOurs(player)) {
            return amount;
        }
        long now = player.level().getGameTime();
        HealWindow window = HEAL_WINDOWS.computeIfAbsent(
                player.getUUID(), id -> new HealWindow(now));
        if (now - window.startedAt >= 20L) {
            window.startedAt = now;
            window.healed = 0.0F;
        }
        float remaining = MAX_REGENERATION_HEAL_PER_SECOND - window.healed;
        if (remaining <= 0.0F) {
            return 0.0F;
        }
        float allowed = Math.min(amount, remaining);
        window.healed += allowed;
        return allowed;
    }

    public static boolean isRegenerationOurs(ServerPlayer player) {
        Owned owned = OWNED.get(player.getUUID());
        if (owned == null || owned.regeneration < 0) {
            return false;
        }
        MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
        return current != null
                && current.getAmplifier() == owned.regeneration
                && current.getDuration() <= EFFECT_DURATION + 5;
    }

    private static void apply(ServerPlayer player, int level) {
        if (!player.isAlive()) {
            removeOwned(player);
            return;
        }

        int regen = regenerationAmplifier(level);
        int strength = strengthAmplifier(level);
        Owned previous = OWNED.getOrDefault(player.getUUID(), new Owned(level, -1, -1, -1));

        int ownedRegen = applyEffect(player, MobEffects.REGENERATION, regen,
                previous.regeneration, EFFECT_DURATION);
        int ownedStrength = applyEffect(player, MobEffects.DAMAGE_BOOST, strength,
                previous.strength, EFFECT_DURATION);

        int ownedHaste = applyEffect(player, MobEffects.DIG_SPEED, hasteAmplifier(level),
                previous.haste, EFFECT_DURATION);

        OWNED.put(player.getUUID(), new Owned(level, ownedRegen, ownedStrength, ownedHaste));
    }

    private static int applyEffect(ServerPlayer player, MobEffect effect,
                                   int desired, int previouslyOwned, int duration) {
        MobEffectInstance current = player.getEffect(effect);
        boolean currentIsOurs = isOurs(current, previouslyOwned, duration);

        if (desired < 0) {
            if (currentIsOurs) {
                player.removeEffect(effect);
            }
            return -1;
        }

        if (current != null && !currentIsOurs
                && current.getAmplifier() > desired) {
            // A stronger external potion/effect is active. Leave it alone.
            return -1;
        }

        if (currentIsOurs && current.getAmplifier() == desired) {
            if (current.getDuration() > duration / 2) {
                // Already refreshed recently; avoid needless packet churn.
                return desired;
            }
            // Same tier: a normal addEffect refreshes the duration without
            // creating a hidden effect.
            player.addEffect(new MobEffectInstance(effect, duration, desired,
                    false, false, true));
            return desired;
        }

        // Vanilla stores a lower amplifier as a hidden effect when the player
        // already has a stronger one. Remove our previous tier first so a
        // level downgrade is applied immediately instead of lingering.
        if (currentIsOurs) {
            player.removeEffect(effect);
        }
        player.addEffect(new MobEffectInstance(effect, duration, desired,
                false, false, true));
        return desired;
    }

    private static void removeOwned(ServerPlayer player) {
        Owned owned = OWNED.remove(player.getUUID());
        if (owned == null) {
            return;
        }
        removeIfOurs(player, MobEffects.REGENERATION, owned.regeneration, EFFECT_DURATION);
        removeIfOurs(player, MobEffects.DAMAGE_BOOST, owned.strength, EFFECT_DURATION);
        removeIfOurs(player, MobEffects.DIG_SPEED, owned.haste, EFFECT_DURATION);
        HEAL_WINDOWS.remove(player.getUUID());
    }

    private static void removeIfOurs(ServerPlayer player, MobEffect effect,
                                     int ownedAmplifier, int duration) {
        if (ownedAmplifier < 0) {
            return;
        }
        MobEffectInstance current = player.getEffect(effect);
        if (isOurs(current, ownedAmplifier, duration)) {
            player.removeEffect(effect);
        }
    }

    private static boolean isOurs(MobEffectInstance instance, int amplifier, int duration) {
        return instance != null
                && amplifier >= 0
                && instance.getAmplifier() == amplifier
                && instance.getDuration() <= duration + 5;
    }

    private static int currentLevel(ServerPlayer player) {
        return FlashPowers.hasPowers(player)
                ? FlashPowers.getSelectedLevel(player)
                : FlashSpeedLevels.MIN_LEVEL;
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> Integer.toString(value);
        };
    }

    private record Owned(int level, int regeneration, int strength, int haste) {
    }

    private static final class HealWindow {
        private long startedAt;
        private float healed;

        private HealWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}

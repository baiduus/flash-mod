package com.flash.power;

import com.flash.FlashMod;
import com.flash.yas.YasPowers;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every hero of the mod (except Yas) is set on the Green Lanterns' scale (lanternasverdes), the four rings stretched
 * over the ten levels of a hero (heroes without levels count as the top ring):
 * <ul>
 * <li><b>Strength</b>: suited, a punch is a suited Lantern's (fist + Super Strength 10, x1.75, at most 17.5); out of
 * the suit, a Lantern's without one (the hand or the weapon x1.75); every other hit (the abilities) at most 30, the
 * Lanterns' top for their constructs.</li>
 * <li><b>Resistance</b>: the ring's defence (20 % .. 50 %); suited also the suit's (x0.88 .. x0.56), its armour
 * (2 .. 20 points) and half of explosions and fire. The heroes' own factors are gone; their immunities and the
 * defence of their abilities (Thor's spinning hammer, Doom's shield...) stay.</li>
 * </ul>
 * Yas is apart (YasImmortal): she never takes damage and every hit of hers takes off at least 1000.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HeroBalance {

    /** The Lanterns' top for a blow of the body (NerfTropas.TETO_CORPO). */
    public static final float MELEE_CAP = 17.5F;
    /** The Lanterns' top for every other hit (NerfTropas.TETO). */
    public static final float HIT_CAP = 30.0F;
    /** The ring's strength on a punch (BuffTropas.SOCO). */
    public static final float PUNCH_BUFF = 1.75F;

    private static final float[] RING_DEFENCE = {0.2F, 0.3F, 0.4F, 0.5F};
    private static final float[] SUIT_DAMAGE = {0.88F, 0.8F, 0.69F, 0.56F};
    private static final float[] SUIT_ARMOUR = {2.0F, 6.0F, 12.0F, 20.0F};

    private HeroBalance() {
    }

    /** Any hero of the mod, Lex in his suit too. */
    public static boolean hero(Player p) {
        return isHero(p) || com.flash.lex.LexSuit.active(p);
    }

    /** Has the powers of any hero of the mod (Yas too; Lex's suit is not a power: hero()). */
    public static boolean isHero(Player p) {
        return com.flash.superman.SupermanPowers.isSuperman(p)
                || com.flash.hulk.HulkPowers.isTransformed(p)
                || com.flash.thor.ThorPowers.isThor(p)
                || FlashPowers.hasPowers(p)
                || YasPowers.has(p)
                || com.flash.scarlet.ScarletPowers.has(p)
                || com.flash.storm.StormPowers.has(p)
                || com.flash.spider.SpiderPowers.has(p)
                || com.flash.doom.DoomPowers.has(p)
                || com.flash.wonder.WonderPowers.has(p)
                || com.flash.ultron.UltronPowers.has(p);
    }

    /** In the suit: Superman, Thor, Wonder Woman, Spider-Man, Doom, the Flash in his; the others always are. */
    public static boolean suited(Player p) {
        if (com.flash.superman.SupermanPowers.suited(p) || com.flash.thor.ThorPowers.suited(p)
                || com.flash.wonder.WonderPowers.suited(p) || com.flash.spider.SpiderPowers.suited(p)
                || com.flash.doom.DoomPowers.suited(p) || com.flash.lex.LexSuit.active(p)
                || com.flash.hulk.HulkPowers.isTransformed(p) || com.flash.storm.StormPowers.has(p)
                || com.flash.scarlet.ScarletPowers.has(p) || com.flash.ultron.UltronPowers.has(p)) {
            return true;
        }
        return FlashPowers.hasPowers(p)
                && com.flash.item.FlashSuitUtil.isSuit(p.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** 0 (the first ring) .. 1 (the last): the highest of the hero's levels, heroes without levels at the top. */
    public static float rank(Player p) {
        int level = 0;
        boolean levelled = false;
        if (com.flash.superman.SupermanPowers.has(p)) {
            level = Math.max(level, com.flash.superman.SupermanPowers.level(p));
            levelled = true;
        }
        if (com.flash.thor.ThorPowers.has(p)) {
            level = Math.max(level, com.flash.thor.ThorPowers.level(p));
            levelled = true;
        }
        if (com.flash.hulk.HulkPowers.isTransformed(p)) {
            level = Math.max(level, com.flash.hulk.HulkPowers.activeLevel(p));
            levelled = true;
        }
        if (FlashPowers.hasPowers(p)) {
            level = Math.max(level, FlashPowers.getSelectedLevel(p));
            levelled = true;
        }
        boolean other = com.flash.scarlet.ScarletPowers.has(p) || com.flash.storm.StormPowers.has(p)
                || com.flash.spider.SpiderPowers.has(p) || com.flash.doom.DoomPowers.has(p)
                || com.flash.wonder.WonderPowers.has(p) || com.flash.ultron.UltronPowers.has(p)
                || com.flash.lex.LexSuit.active(p);
        if (!levelled || other) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (level - 1) / 9.0F));
    }

    /** The four rings' values, read at a rank (0..1). */
    private static float ring(float[] values, float rank) {
        float x = rank * (values.length - 1);
        int i = Math.min(values.length - 2, (int) x);
        return values[i] + (values[i + 1] - values[i]) * (x - i);
    }

    /** How much of a hit a hero takes (before the armour of the suit). */
    public static float defenceFactor(Player p, DamageSource source) {
        float rank = rank(p);
        float f = 1.0F - ring(RING_DEFENCE, rank);
        if (suited(p)) {
            f *= ring(SUIT_DAMAGE, rank);
            if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) {
                f *= 0.5F;
            }
        }
        return f;
    }

    /** Damage cut in the suit at a hero level (1..10), in percent, before the suit's armour (for the screens). */
    public static int suitedCut(int level) {
        float rank = Math.max(0.0F, Math.min(1.0F, (level - 1) / 9.0F));
        return Math.round((1.0F - (1.0F - ring(RING_DEFENCE, rank)) * ring(SUIT_DAMAGE, rank)) * 100.0F);
    }

    /** The suit's armour points (none out of it). */
    public static float suitArmour(Player p) {
        return suited(p) ? ring(SUIT_ARMOUR, rank(p)) : 0.0F;
    }

    /** The hero behind a hit (his arrows and thrown things too), Yas included. */
    public static Player heroAttacker(DamageSource source) {
        Entity e = source.getEntity();
        if (e == null && source.getDirectEntity() instanceof Projectile pr) {
            e = pr.getOwner();
        }
        return e instanceof Player p && !p.level().isClientSide && hero(p) ? p : null;
    }

    /** A blow of the hand: the hero's own melee hit. */
    public static boolean melee(DamageSource source, Player attacker) {
        return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker;
    }

    /** Out of the suit, a punch as a Lantern's without one: the hand or the weapon, x1.75. */
    private static float unsuitedPunch(Player p) {
        double weapon = 1.0D;
        for (AttributeModifier m : p.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                weapon += m.getAmount();
            }
        }
        return (float) Math.min(MELEE_CAP, weapon * PUNCH_BUFF);
    }

    /** A hero's hit on the Lanterns' scale (after the punch bonuses, at HIGHEST, before any defence). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeroHit(LivingHurtEvent event) {
        Player hero = heroAttacker(event.getSource());
        if (hero == null || hero == event.getEntity() || YasPowers.has(hero)) {
            return;
        }
        float cap;
        if (melee(event.getSource(), hero)) {
            cap = suited(hero) ? MELEE_CAP : unsuitedPunch(hero);
        } else {
            cap = HIT_CAP;
        }
        if (event.getAmount() > cap) {
            event.setAmount(cap);
        }
    }

    /** A hero hit: the Lanterns' defence (ring, suit and its armour). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeroHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(victim instanceof Player p) || !hero(p) || YasPowers.has(p)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float amount = event.getAmount() * defenceFactor(p, event.getSource());
        float armour = suitArmour(p);
        if (armour > 0.0F && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            amount = CombatRules.getDamageAfterAbsorb(amount, armour, 0.0F);
        }
        event.setAmount(amount);
    }
}

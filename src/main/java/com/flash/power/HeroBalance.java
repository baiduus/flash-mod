package com.flash.power;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The balance between the heroes (Flash, Hulk, Superman, Thor and the ones to come).
 *
 * <p>Reference: the slap of Superman (about 44 at level 10, three of them kill an Iron Golem). Every hero's abilities
 * are multiples of his own punch (a base value of 12 is one punch), sized by reach, area, cooldown and number of hits
 * so that no single combo goes much over three punches on one target.
 *
 * <p>Hero against hero: the resistance each hero has against monsters (Superman shrugs off a Warden) would make
 * fights endless or instant, so damage from a hero to a hero ignores it and is set the same way for everybody: 30 %
 * of the raw hit, scaled to the victim's maximum health so every hero lasts as long as a 50-health body would. A
 * fight between two heroes of the same level then takes several abilities, decided by how they are used. The
 * regeneration of each hero is not touched.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class HeroBalance {

    /** Part of a hero's raw hit that another hero takes. */
    public static final float HERO_VS_HERO = 0.3F;
    /** The health every hero is balanced around in a hero fight. */
    public static final float REFERENCE_HEALTH = 50.0F;

    private static final Map<UUID, Float> RAW = new HashMap<>();

    private HeroBalance() {
    }

    /** Has the powers of any hero active right now. */
    public static boolean isHero(Player player) {
        return com.flash.superman.SupermanPowers.isSuperman(player)
                || com.flash.hulk.HulkPowers.isTransformed(player)
                || com.flash.thor.ThorPowers.isThor(player)
                || FlashPowers.hasPowers(player);
    }

    private static Player heroAttacker(DamageSource source) {
        Entity e = source.getEntity();
        if (e == null && source.getDirectEntity() instanceof Projectile p) {
            e = p.getOwner();
        }
        return e instanceof Player p && isHero(p) ? p : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurtFirst(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        RAW.remove(victim.getUUID());
        if (victim.level().isClientSide || !(victim instanceof Player v) || !isHero(v)) {
            return;
        }
        Player attacker = heroAttacker(event.getSource());
        if (attacker != null && attacker != victim) {
            RAW.put(victim.getUUID(), event.getAmount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurtLast(LivingHurtEvent event) {
        Float raw = RAW.remove(event.getEntity().getUUID());
        if (raw == null || event.isCanceled()) {
            return;
        }
        float health = Math.max(20.0F, event.getEntity().getMaxHealth());
        // (blocks that stop a hit, as Thor's, cancel it before this)
        event.setAmount(raw * HERO_VS_HERO * (health / REFERENCE_HEALTH));
    }
}

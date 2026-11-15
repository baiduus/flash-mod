package com.flash.doom;

import com.flash.FlashMod;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Doctor Doom's evolution into God Emperor Doom (Secret Wars: the power of the Beyonders stolen).
 * <ol>
 * <li>As Doom, he absorbs the power of the four great beings of the world when they fall near him (64 blocks): the
 * Ender Dragon, the Wither, the Warden and the Elder Guardian (each once, kept with his power).</li>
 * <li>With the four absorbed, crouching, he uses a beacon holding a Nether Star (spent): the ritual of Secret Wars,
 * 10 seconds rising in the light, and he is God Emperor Doom.</li>
 * </ol>
 * God Emperor Doom (DoomPowers / DoomAbilities): takes 3 % of the damage (Yas about 2 %), +40 punch, +15 hearts,
 * heals fast, poison and wither cannot touch him, never hungers nor drowns; every power hits 2.5 times as hard,
 * reaches farther and comes back twice as fast; he flies nearly as fast as Yas; and the Beyonder mode: R the
 * annihilation (a beam that unmakes all in its way), Z Secret Wars (reality rewritten round him: every enemy in 28
 * blocks struck down by the power of a god, his wounds undone).
 * <p>
 * {@code /doom god | ungod [player]}.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class DoomAscension {

    /** The four beings whose power he takes (their bit, their id for the texts). */
    public static final String[] BEINGS = {"ender_dragon", "wither", "warden", "elder_guardian"};
    private static final String ABSORBED = "absorbed";
    private static final int ASCEND_TICKS = 200;
    public static final double REWRITE_RADIUS = 28.0D;

    private static final Map<UUID, Integer> ASCENDING = new HashMap<>();

    private DoomAscension() {
    }

    public static int absorbed(ServerPlayer p) {
        return DoomPowers.tag(p).getInt(ABSORBED);
    }

    public static boolean ready(ServerPlayer p) {
        return absorbed(p) == (1 << BEINGS.length) - 1;
    }

    private static int being(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.ENDER_DRAGON) {
            return 0;
        }
        if (t == EntityType.WITHER) {
            return 1;
        }
        if (t == EntityType.WARDEN) {
            return 2;
        }
        return t == EntityType.ELDER_GUARDIAN ? 3 : -1;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        int b = being(dead);
        if (b < 0 || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = dead.position().add(0.0D, dead.getBbHeight() * 0.5D, 0.0D);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(64.0D),
                pl -> DoomPowers.has(pl) && pl.isAlive())) {
            CompoundTag tag = DoomPowers.tag(p);
            int got = tag.getInt(ABSORBED);
            if ((got & (1 << b)) != 0) {
                continue;
            }
            got |= 1 << b;
            tag.putInt(ABSORBED, got);
            DoomPowers.putTag(p, tag);
            int n = Integer.bitCount(got);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 2.0F,
                    0.5F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                    1.0F, 0.6F);
            DoomAbilities.fx(p, DoomAbilities.FX_ABSORB, at, p.position(), 1.0F, -1);
            p.sendSystemMessage(Component.translatable("message.flash.doom.absorbed",
                    Component.translatable("gui.flash.doom.being." + BEINGS[b]), n, BEINGS.length)
                    .withStyle(ChatFormatting.DARK_GREEN));
            if (n == BEINGS.length && !DoomPowers.god(p)) {
                p.sendSystemMessage(Component.translatable("message.flash.doom.ready")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        }
    }

    /** The ritual of Secret Wars: crouching, a beacon, a Nether Star in hand. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || !DoomPowers.has(p)
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BEACON)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (DoomPowers.god(p) || ASCENDING.containsKey(p.getUUID())) {
            return;
        }
        if (!ready(p)) {
            int got = absorbed(p);
            Component list = Component.empty();
            boolean first = true;
            for (int i = 0; i < BEINGS.length; i++) {
                if ((got & (1 << i)) == 0) {
                    list = list.copy().append(first ? "" : ", ")
                            .append(Component.translatable("gui.flash.doom.being." + BEINGS[i]));
                    first = false;
                }
            }
            p.displayClientMessage(Component.translatable("message.flash.doom.need_beings", list)
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        var hand = p.getItemInHand(event.getHand());
        if (!hand.is(Items.NETHER_STAR)) {
            p.displayClientMessage(Component.translatable("message.flash.doom.need_star")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!p.isCreative()) {
            hand.shrink(1);
        }
        begin(p);
    }

    private static void begin(ServerPlayer p) {
        ASCENDING.put(p.getUUID(), 0);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5F,
                0.6F);
        DoomAbilities.fx(p, DoomAbilities.FX_ASCEND, p.position(), p.position(), ASCEND_TICKS, -1);
        p.sendSystemMessage(Component.translatable("message.flash.doom.ascending")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }

    /** Made God Emperor at once (the command). */
    public static void makeGod(ServerPlayer p) {
        if (!DoomPowers.has(p)) {
            DoomPowers.grant(p);
        }
        CompoundTag tag = DoomPowers.tag(p);
        tag.putInt(ABSORBED, (1 << BEINGS.length) - 1);
        DoomPowers.putTag(p, tag);
        DoomPowers.setGod(p, true);
    }

    public static void unmakeGod(ServerPlayer p) {
        DoomPowers.setGod(p, false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ASCENDING.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = ASCENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive() || !DoomPowers.has(p)) {
                it.remove();
                continue;
            }
            int t = en.getValue() + 1;
            en.setValue(t);
            ServerLevel level = p.serverLevel();
            // he rises, held in the light
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5, t < ASCEND_TICKS / 2 ? 0 : 255, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 4, false, false));
            p.fallDistance = 0.0F;
            if (t % 10 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0D, p.getZ(), 30, 1.5D, 1.5D, 1.5D,
                        0.1D);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0D, p.getZ(), 20, 2.0D, 2.0D,
                        2.0D, 0.05D);
            }
            if (t % 40 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                        2.0F, 0.5F + t / (float) ASCEND_TICKS);
            }
            if (t < ASCEND_TICKS) {
                continue;
            }
            it.remove();
            DoomPowers.setGod(p, true);
            for (int i = 0; i < 6; i++) {
                var bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    double a = i * Math.PI / 3.0D;
                    bolt.moveTo(p.getX() + Math.cos(a) * 6.0D, p.getY() - 2.0D, p.getZ() + Math.sin(a) * 6.0D);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS,
                    2.0F, 0.6F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5F,
                    0.7F);
            DoomAbilities.fx(p, DoomAbilities.FX_REWRITE, p.position(), p.position(), 20.0F, -1);
            event.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.flash.doom.god", p.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    false);
        }
    }

    /**
     * Secret Wars: reality rewritten round him. Every enemy within 28 blocks is struck down by the power of a god (a
     * bolt from the sky, thrown up, held), and his own wounds are undone.
     */
    static void rewrite(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 c = p.position();
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(REWRITE_RADIUS),
                e -> DoomAbilities.foe(p, e) && e.distanceToSqr(p) < REWRITE_RADIUS * REWRITE_RADIUS)) {
            // 24 * 2.5: 60 a creature
            DoomAbilities.hurt(p, e, 24.0F);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.2D, 1.1D, e.getDeltaMovement().z * 0.2D);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            if (n++ < 12) {
                var bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(e.getX(), e.getY(), e.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.clearFire();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2F, 1.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5F, 1.2F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.6F);
        DoomAbilities.fx(p, DoomAbilities.FX_REWRITE, c, c, (float) REWRITE_RADIUS, -1);
    }
}

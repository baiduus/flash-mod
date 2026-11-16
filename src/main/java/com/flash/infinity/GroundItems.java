package com.flash.infinity;

import com.flash.FlashMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/**
 * Ground items (the original's ground/GroundItems, data/flash/infinity_ground_items): the stones and their containers
 * lying on the ground. Dropped, thrown or placed by a structure, they never despawn, can't be destroyed, aren't
 * picked up by walking over them; right-clicking picks one up (or, holding a "combine" item, turns it into the
 * result: a stone into its empty container), hitting one breaks it open into its "release" items (the Tesseract's
 * rays, the Aether's plume). A one-of-a-kind ("unique") one vanishes from the ground while a player carries it. Each
 * is reached through an invisible interaction entity kept on it (vanilla's own way to make a spot clickable).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class GroundItems {

    private static final String[] FILES = {"aether", "cosmi-rod", "double_edged_sword", "empty_aether",
            "empty_scepter", "eye_of_agamotto", "infinity_gauntlet", "mind_stone", "orb", "power_stone",
            "reality_stone", "scepter", "single_edged_sword", "soul_stone", "space_stone", "tesseract", "time_stone"};
    static final String TAG = "flash_ground";
    static final String HITBOX = "flash_ground_hitbox";
    private static final String LINK = "flash_ground_item";

    record Def(Item item, boolean unique, List<Item> release, int color, String effect, int duration,
               Map<Item, Item> combine) {
    }

    private static Map<Item, Def> defs;
    private static final List<Entity> PENDING = new ArrayList<>();
    /** The ground items in the world and their hitboxes. */
    private static final Map<ItemEntity, Entity> LIVE = new HashMap<>();
    /** Openings in progress: item entity -> ticks left. */
    private static final Map<ItemEntity, Integer> OPENING = new HashMap<>();

    private GroundItems() {
    }

    private static Item item(String id) {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return i == null || i == net.minecraft.world.item.Items.AIR ? null : i;
    }

    static Map<Item, Def> defs() {
        if (defs == null) {
            defs = new HashMap<>();
            for (String f : FILES) {
                JsonObject o = InfPowers.read("/data/flash/infinity_ground_items/" + f + ".json");
                if (o == null) {
                    continue;
                }
                Item it = item(InfPowers.str(o, "item", "air"));
                if (it == null) {
                    continue;
                }
                List<Item> rel = new ArrayList<>();
                int color = 0xFFFFFF;
                String effect = "";
                int dur = 0;
                if (o.has("release")) {
                    JsonObject r = o.getAsJsonObject("release");
                    for (String s : InfinityPowers.strings(r.get("items"))) {
                        Item x = item(s);
                        if (x != null) {
                            rel.add(x);
                        }
                    }
                    String c = InfPowers.str(r, "color", "#FFFFFF");
                    try {
                        color = Integer.parseInt(c.replace("#", ""), 16);
                    } catch (NumberFormatException ignored) {
                    }
                    if (r.has("opening")) {
                        JsonObject op = r.getAsJsonObject("opening");
                        effect = InfPowers.str(op, "effect", "rays");
                        dur = Math.min(1200, op.has("duration") ? op.get("duration").getAsInt() : 100);
                    }
                }
                Map<Item, Item> comb = new HashMap<>();
                if (o.has("combine")) {
                    for (JsonElement e : o.getAsJsonArray("combine")) {
                        JsonObject c = e.getAsJsonObject();
                        Item a = item(InfPowers.str(c, "item", "air"));
                        Item b = item(InfPowers.str(c, "result", "air"));
                        if (a != null && b != null) {
                            comb.put(a, b);
                        }
                    }
                }
                defs.put(it, new Def(it, o.has("unique") && o.get("unique").getAsBoolean(), rel, color, effect, dur,
                        comb));
            }
        }
        return defs;
    }

    public static Def get(ItemStack s) {
        return s.isEmpty() ? null : defs().get(s.getItem());
    }

    // ------------------------------------------------------------------
    // Becoming a ground item
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity e = event.getEntity();
        if (e instanceof ItemEntity ie && get(ie.getItem()) != null) {
            ie.setUnlimitedLifetime();
            ie.setNeverPickUp();
            ie.setInvulnerable(true);
            ie.addTag(TAG);
            PENDING.add(ie);
        } else if (e instanceof ArmorStand as && as.isInvisible() && !as.getTags().contains(TAG) && standItem(as)
                != null) {
            // a structure's display stand (the Orb on Morag, the Eye in the Sanctum): its item on the ground
            PENDING.add(as);
        } else if (e.getTags().contains(HITBOX)) {
            // a hitbox loaded back from the save: made again for its item
            PENDING.add(e);
        }
    }

    private static ItemStack standItem(ArmorStand as) {
        ItemStack found = null;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = as.getItemBySlot(s);
            if (st.isEmpty()) {
                continue;
            }
            if (get(st) == null || found != null) {
                return null;
            }
            found = st;
        }
        return found;
    }

    private static void hitbox(ServerLevel level, ItemEntity ie) {
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:interaction");
        t.putFloat("width", 0.5F);
        t.putFloat("height", 0.5F);
        t.putBoolean("response", true);
        Entity box = EntityType.create(t, level).orElse(null);
        if (box == null) {
            return;
        }
        box.addTag(HITBOX);
        box.getPersistentData().putUUID(LINK, ie.getUUID());
        box.moveTo(ie.getX(), ie.getY(), ie.getZ());
        level.addFreshEntity(box);
        LIVE.put(ie, box);
    }

    public static void spawn(ServerLevel level, Vec3 at, ItemStack stack, Vec3 motion) {
        ItemEntity ie = new ItemEntity(level, at.x, at.y, at.z, stack);
        ie.setDeltaMovement(motion);
        level.addFreshEntity(ie);
    }

    // ------------------------------------------------------------------
    // Tick: hitboxes follow, the void gives them back, the unique vanish while carried, openings play
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!PENDING.isEmpty()) {
            List<Entity> now = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Entity e : now) {
                if (!(e.level() instanceof ServerLevel level) || e.isRemoved()) {
                    continue;
                }
                if (e instanceof ItemEntity ie) {
                    if (!LIVE.containsKey(ie)) {
                        hitbox(level, ie);
                    }
                } else if (e instanceof ArmorStand as) {
                    ItemStack st = standItem(as);
                    if (st != null) {
                        ItemEntity ie = new ItemEntity(level, as.getX(), as.getY() + 0.2D, as.getZ(), st.copy());
                        ie.setDeltaMovement(Vec3.ZERO);
                        level.addFreshEntity(ie);
                    }
                    as.discard();
                } else if (e.getTags().contains(HITBOX)) {
                    UUID id = e.getPersistentData().hasUUID(LINK) ? e.getPersistentData().getUUID(LINK) : null;
                    if (id == null || !(level.getEntity(id) instanceof ItemEntity ie) || LIVE.containsKey(ie)) {
                        e.discard();
                    } else {
                        LIVE.put(ie, e);
                    }
                }
            }
        }
        net.minecraft.server.MinecraftServer server = event.getServer();
        boolean second = server.getTickCount() % 20 == 0;
        java.util.Set<Item> carried = new java.util.HashSet<>();
        if (second) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                for (ItemStack s : p.getInventory().items) {
                    carried.add(s.getItem());
                }
                carried.add(p.getOffhandItem().getItem());
                for (ItemStack s : p.getInventory().armor) {
                    carried.add(s.getItem());
                }
            }
        }
        for (Iterator<Map.Entry<ItemEntity, Entity>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemEntity, Entity> en = it.next();
            ItemEntity ie = en.getKey();
            Entity box = en.getValue();
            if (ie.isRemoved() || box.isRemoved()) {
                box.discard();
                if (!ie.isRemoved() && ie.level() instanceof ServerLevel) {
                    PENDING.add(ie);
                }
                it.remove();
                continue;
            }
            Def d = get(ie.getItem());
            if (d == null) {
                box.discard();
                it.remove();
                continue;
            }
            if (second && d.unique() && carried.contains(d.item())) {
                ie.discard();
                box.discard();
                it.remove();
                continue;
            }
            // the void gives it back: on top of the ground where it fell
            if (ie.getY() < ie.level().getMinBuildHeight() - 8) {
                ServerLevel level = (ServerLevel) ie.level();
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ie.getBlockX(), ie.getBlockZ());
                if (y <= level.getMinBuildHeight()) {
                    var spawn = level.getSharedSpawnPos();
                    ie.teleportTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D);
                } else {
                    ie.teleportTo(ie.getX(), y + 1.0D, ie.getZ());
                }
                ie.setDeltaMovement(Vec3.ZERO);
            }
            if (box.distanceToSqr(ie) > 0.0025D) {
                box.teleportTo(ie.getX(), ie.getY(), ie.getZ());
            }
        }
        for (Iterator<Map.Entry<ItemEntity, Integer>> it = OPENING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemEntity, Integer> en = it.next();
            ItemEntity ie = en.getKey();
            if (ie.isRemoved()) {
                it.remove();
                continue;
            }
            Def d = get(ie.getItem());
            int left = en.getValue() - 1;
            if (d == null) {
                it.remove();
                continue;
            }
            openingFx((ServerLevel) ie.level(), ie.position(), d, left);
            if (left <= 0) {
                it.remove();
                release((ServerLevel) ie.level(), ie, d);
            } else {
                en.setValue(left);
            }
        }
    }

    // ------------------------------------------------------------------
    // Right-click: pick up / combine; hit: break open
    // ------------------------------------------------------------------

    private static ItemEntity owner(Entity box) {
        for (Map.Entry<ItemEntity, Entity> en : LIVE.entrySet()) {
            if (en.getValue() == box) {
                return en.getKey();
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getTarget().getTags().contains(HITBOX)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemEntity ie = owner(event.getTarget());
        if (ie == null || OPENING.containsKey(ie)) {
            return;
        }
        Def d = get(ie.getItem());
        ItemStack held = p.getMainHandItem();
        if (d != null && !held.isEmpty() && d.combine().containsKey(held.getItem())) {
            Item result = d.combine().get(held.getItem());
            held.shrink(1);
            ItemStack r = new ItemStack(result);
            ie.setItem(r);
            ServerLevel level = p.serverLevel();
            level.playSound(null, ie.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F,
                    1.0F);
            SpaceStone.sound(level, ie.position(), "infinity_stone_equip", 1.0F, 1.0F);
            return;
        }
        ItemStack s = ie.getItem().copy();
        ie.discard();
        event.getTarget().discard();
        LIVE.remove(ie);
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getTarget().getTags().contains(HITBOX)) {
            return;
        }
        event.setCanceled(true);
        ItemEntity ie = owner(event.getTarget());
        if (ie == null || OPENING.containsKey(ie)) {
            return;
        }
        Def d = get(ie.getItem());
        if (d == null || d.release().isEmpty()) {
            return;
        }
        if (d.duration() > 0) {
            OPENING.put(ie, d.duration());
            p.serverLevel().playSound(null, ie.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS,
                    1.0F, 1.4F);
        } else {
            release(p.serverLevel(), ie, d);
        }
    }

    private static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F,
                (rgb & 255) / 255.0F), size);
    }

    private static void openingFx(ServerLevel level, Vec3 at, Def d, int left) {
        float done = 1.0F - left / (float) Math.max(1, d.duration());
        if ("plume".equals(d.effect())) {
            // squares burst out and pour upward
            level.sendParticles(dust(d.color(), 1.5F), at.x, at.y + 0.5D, at.z, 6 + (int) (done * 14), 0.2D,
                    0.6D + done, 0.2D, 0.05D);
            if (left % 10 == 0) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.6F);
            }
        } else {
            // the Tesseract: rises in a growing burst of rays
            double r = 0.3D + done * 1.5D;
            for (int i = 0; i < 10; i++) {
                double a = (left * 0.21D + i * Math.PI * 2.0D / 10.0D);
                level.sendParticles(dust(d.color(), 1.0F), at.x + Math.cos(a) * r * 0.3D, at.y + 0.3D + done,
                        at.z + Math.sin(a) * r * 0.3D, 1, Math.cos(a) * r, 0.0D, Math.sin(a) * r, 0.0D);
            }
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3D + done, at.z, 2, 0.1D, 0.1D, 0.1D,
                    0.05D * (1.0D + done));
            if (left % 20 == 0) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.5F);
            }
        }
    }

    private static void release(ServerLevel level, ItemEntity ie, Def d) {
        Vec3 at = ie.position().add(0.0D, 0.5D, 0.0D);
        ie.discard();
        Entity box = LIVE.remove(ie);
        if (box != null) {
            box.discard();
        }
        level.sendParticles(dust(d.color(), 2.0F), at.x, at.y, at.z, 60, 0.4D, 0.4D, 0.4D, 0.1D);
        if ("plume".equals(d.effect())) {
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.8F, 1.2F);
        } else {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F,
                    1.0F);
        }
        for (Item i : d.release()) {
            if (i == InfinityItems.TIME_STONE.get() && ie.getItem().is(InfinityItems.EYE_OF_AGAMOTTO.get())) {
                InfinityOrb.Data od = InfinityOrb.data(level.getServer());
                od.eyeTaken = true;
                od.setDirty();
            }
            spawn(level, at, new ItemStack(i), new Vec3((level.random.nextDouble() - 0.5D) * 0.2D, 0.3D,
                    (level.random.nextDouble() - 0.5D) * 0.2D));
        }
    }
}

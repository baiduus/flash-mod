package com.flash.infinity;

import com.flash.FlashMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Getting around the Infinity worlds (the original's TesseractTravel and ExplorerMaps): the Tesseract, right-clicked,
 * takes its holder to the next dimension in turn (the Overworld, the Nether, the End, then every other one: Vormir,
 * Morag, Svartalfheim, the Garden, space...) at the same spot, wherever there is a safe place to land within 16
 * blocks; the Soul Guardian's explorer maps fill in with the way to the Sanctum, the Norse church, the Morag temple,
 * the Aether chamber and Sanctuary.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityTravel {

    private static final int COOLDOWN = 20;
    private static final int PROTECTION = 100;
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_STEP = 2;
    /** Dimensions the Tesseract never goes to (the Soulworld is the Soul Stone's; the Speed Force is Flash's). */
    private static final Set<String> NEVER = Set.of("flash:soulworld", "flash:acceleration_force",
            "flash:negative_acceleration_force");
    private static final Map<UUID, Long> PROTECTED = new HashMap<>();

    private InfinityTravel() {
    }

    // ------------------------------------------------------------------
    // The Tesseract
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getItemStack().is(InfinityItems.TESSERACT.get())
                || p.getCooldowns().isOnCooldown(InfinityItems.TESSERACT.get())) {
            return;
        }
        p.getCooldowns().addCooldown(InfinityItems.TESSERACT.get(), COOLDOWN);
        travel(p);
        event.setCanceled(true);
    }

    private static int vanillaOrder(ServerLevel l) {
        ResourceKey<Level> k = l.dimension();
        return k == Level.OVERWORLD ? 0 : k == Level.NETHER ? 1 : k == Level.END ? 2 : 3;
    }

    private static List<ServerLevel> destinations(MinecraftServer server) {
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (!NEVER.contains(l.dimension().location().toString())) {
                out.add(l);
            }
        }
        out.sort(Comparator.comparingInt(InfinityTravel::vanillaOrder).thenComparing(l -> l.dimension().location()
                .toString()));
        return out;
    }

    private static void travel(ServerPlayer p) {
        List<ServerLevel> list = destinations(p.server);
        int at = list.indexOf(p.serverLevel());
        for (int i = 1; i <= list.size(); i++) {
            ServerLevel to = list.get(Math.floorMod(at + i, list.size()));
            if (to == p.serverLevel()) {
                continue;
            }
            Vec3 land;
            try {
                land = landing(to, p);
            } catch (RuntimeException e) {
                land = null;
            }
            if (land != null) {
                SoundEvent snd = InfinitySounds.get("use_infinity_gauntlet");
                p.serverLevel().playSound(null, p.blockPosition(), snd != null ? snd : SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.PLAYERS, 1.0F, 1.4F);
                p.teleportTo(to, land.x, land.y, land.z, p.getYRot(), p.getXRot());
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0.0F;
                PROTECTED.put(p.getUUID(), to.getGameTime() + PROTECTION);
                to.playSound(null, p.blockPosition(), snd != null ? snd : SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.PLAYERS, 1.0F, 1.4F);
                to.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, land.x, land.y + 1.0D, land.z,
                        40, 0.4D, 0.8D, 0.4D, 0.1D);
                if (to.getBiome(p.blockPosition()).is(net.minecraft.world.level.biome.Biomes.DESERT)) {
                    InfinityAdvancements.grant(p, "lowkey");
                }
                p.displayClientMessage(Component.translatable("message.flash.infinity.tesseract_to",
                        to.dimension().location().toString()), true);
                return;
            }
        }
        p.displayClientMessage(Component.translatable("message.flash.infinity.tesseract_nowhere"), true);
    }

    private static Vec3 landing(ServerLevel to, ServerPlayer p) {
        double scale = DimensionType.getTeleportationScale(p.level().dimensionType(), to.dimensionType());
        var border = to.getWorldBorder();
        BlockPos base = border.clampToBounds(p.getX() * scale, p.getY(), p.getZ() * scale);
        for (int r = 0; r <= SEARCH_RADIUS; r += SEARCH_STEP) {
            for (int dx = -r; dx <= r; dx += SEARCH_STEP) {
                for (int dz = -r; dz <= r; dz += SEARCH_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = base.getX() + dx;
                    int z = base.getZ() + dz;
                    if (!border.isWithinBounds(new BlockPos(x, 0, z))) {
                        continue;
                    }
                    Vec3 v = column(to, p, x, z);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /** The highest safe standing spot in a column, under the roof of a dimension with a ceiling. */
    private static Vec3 column(ServerLevel l, ServerPlayer p, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        int min = l.getMinBuildHeight();
        int top = l.dimensionType().hasCeiling() ? min + l.getLogicalHeight() - 2
                : Math.min(l.getMaxBuildHeight() - 2, l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > min; y--) {
            m.setY(y);
            Vec3 v = new Vec3(x + 0.5D, y, z + 0.5D);
            if (safe(l, p, v)) {
                return v;
            }
        }
        return null;
    }

    private static boolean safe(ServerLevel l, Player p, Vec3 v) {
        BlockPos at = BlockPos.containing(v);
        BlockState below = l.getBlockState(at.below());
        if (!below.isFaceSturdy(l, at.below(), net.minecraft.core.Direction.UP) || harmful(below)
                || below.is(BlockTags.LEAVES)) {
            return false;
        }
        AABB box = p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(v);
        if (!l.noCollision(p, box)) {
            return false;
        }
        return l.getBlockStates(box).noneMatch(InfinityTravel::harmful);
    }

    private static boolean harmful(BlockState s) {
        return !s.getFluidState().isEmpty() || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW)
                || s.is(Blocks.BARRIER) || s.is(Blocks.WITHER_ROSE);
    }

    @SubscribeEvent
    public static void onHurt(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !PROTECTED.isEmpty()) {
            Long until = PROTECTED.get(p.getUUID());
            if (until == null) {
                return;
            }
            if (p.level().getGameTime() > until) {
                PROTECTED.remove(p.getUUID());
            } else if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------
    // Explorer maps: a blank map tagged sanctummap, norsemap, moragmap, aethermap or sanctuarymap
    // ------------------------------------------------------------------

    private static final String[] MAPS = {"sanctum", "norse", "morag", "aether", "sanctuary"};
    private static final Map<String, String> STRUCTURES = Map.of("sanctum", "sanctum", "norse", "norse_village",
            "morag", "morag_temple", "aether", "aether", "sanctuary", "sanctuary");
    /** Where each lies when not in the holder's own dimension. */
    private static final Map<String, String> HOME = Map.of("sanctum", "minecraft:overworld", "norse",
            "minecraft:overworld", "morag", "flash:morag", "aether", "flash:svartelheim", "sanctuary",
            "minecraft:the_end");

    private static String blankMap(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null || !s.is(Items.MAP)) {
            return null;
        }
        for (String m : MAPS) {
            if (t.getBoolean(m + "map")) {
                return m;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            var items = p.getInventory().items;
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                String m = blankMap(s);
                if (m == null) {
                    continue;
                }
                long now = p.level().getGameTime();
                if (now - s.getOrCreateTag().getLong("flash_map_try") < 200L) {
                    continue;
                }
                s.getOrCreateTag().putLong("flash_map_try", now);
                ItemStack filled = fill(p, s, m);
                if (filled != null) {
                    items.set(i, filled);
                }
            }
        }
    }

    private static ItemStack fill(ServerPlayer p, ItemStack blank, String m) {
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, new ResourceLocation(FlashMod.MODID,
                STRUCTURES.get(m)));
        List<ServerLevel> levels = new ArrayList<>();
        levels.add(p.serverLevel());
        ServerLevel home = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(HOME.get(m))));
        if (home != null && home != p.serverLevel()) {
            levels.add(home);
        }
        for (ServerLevel l : levels) {
            BlockPos from = l == p.serverLevel() ? p.blockPosition() : BlockPos.ZERO;
            BlockPos found = l.findNearestMapStructure(tag, from, 100, false);
            if (found == null) {
                continue;
            }
            ItemStack map = MapItem.create(l, found.getX(), found.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(l, map);
            MapItemSavedData.addTargetDecoration(map, found, "+", MapDecoration.Type.RED_X);
            if (blank.hasCustomHoverName()) {
                map.setHoverName(blank.getHoverName());
            }
            return map;
        }
        return null;
    }
}

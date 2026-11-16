package com.flash.infinity;

import com.flash.FlashMod;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Infinity worlds (the original's WorldSetup, VormirFeature, VormirState, SoulGuardian, BestFriendSoulEvents):
 * Vormir's peak built chunk by chunk from its 185 pieces, the Soul Guardian on it (a villager who can't be hurt,
 * trading the Soul Stone for a best friend's soul and explorer maps for Aspects of Existence), the Soulworld's hut,
 * a best friend's soul for the player who lets their own tamed pet die.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityWorld {

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES,
            FlashMod.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(
            ForgeRegistries.VILLAGER_PROFESSIONS, FlashMod.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> VORMIR = FEATURES.register("vormir",
            VormirFeature::new);
    public static final RegistryObject<VillagerProfession> SOUL_GUARDIAN = PROFESSIONS.register("soul_guardian",
            () -> new VillagerProfession("soul_guardian", h -> false, h -> false, ImmutableSet.of(), ImmutableSet.of(),
                    null));

    public static final ResourceKey<Level> VORMIR_LEVEL = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(FlashMod.MODID, "vormir"));
    public static final BlockPos SOUL_GUARDIAN_POS = new BlockPos(212, 121, 99);
    public static final String GUARDIAN_TAG = "giveninfinitymaps";

    private InfinityWorld() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        PROFESSIONS.register(modBus);
        TempleBeacon.BLOCKS.register(modBus);
        TempleBeacon.ITEMS.register(modBus);
        TempleBeacon.BLOCK_ENTITIES.register(modBus);
        InfinityTracker.ITEMS.register(modBus);
    }

    // ------------------------------------------------------------------
    // Vormir: its pieces (data/flash/structures/vormir/X_Z.nbt), each placed at its chunk at y 63
    // ------------------------------------------------------------------

    static final class VormirFeature extends Feature<NoneFeatureConfiguration> {
        VormirFeature() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            ChunkPos c = new ChunkPos(ctx.origin());
            if (c.x < 0 || c.z < 0 || c.x > 16 || c.z > 11) {
                return false;
            }
            ServerLevel server = level.getLevel();
            Optional<StructureTemplate> t = server.getStructureManager().get(new ResourceLocation(FlashMod.MODID,
                    "vormir/" + c.x + "_" + c.z));
            if (t.isEmpty()) {
                return false;
            }
            BlockPos at = new BlockPos(c.getMinBlockX(), 63, c.getMinBlockZ());
            VormirState st = VormirState.get(server.getServer());
            boolean entities = st != null && st.claimEntities(c.toLong());
            StructurePlaceSettings s = new StructurePlaceSettings().setIgnoreEntities(!entities);
            t.get().placeInWorld(level, at, at, s, ctx.random(), 2);
            if (st != null && new ChunkPos(SOUL_GUARDIAN_POS).equals(c) && st.claimGuardian()) {
                Entity g = createSoulGuardian(server);
                if (g != null) {
                    level.addFreshEntity(g);
                }
            }
            return true;
        }
    }

    static final class VormirState extends SavedData {
        boolean guardianSpawned;
        final Set<Long> entityChunks = new HashSet<>();

        static VormirState get(MinecraftServer server) {
            return server == null ? null : server.overworld().getDataStorage().computeIfAbsent(VormirState::load,
                    VormirState::new, "flash_infinity_vormir");
        }

        static VormirState load(CompoundTag t) {
            VormirState s = new VormirState();
            s.guardianSpawned = t.getBoolean("GuardianSpawned");
            for (long l : t.getLongArray("EntityChunks")) {
                s.entityChunks.add(l);
            }
            return s;
        }

        synchronized boolean claimGuardian() {
            if (this.guardianSpawned) {
                return false;
            }
            this.guardianSpawned = true;
            setDirty();
            return true;
        }

        synchronized boolean claimEntities(long chunk) {
            boolean n = this.entityChunks.add(chunk);
            if (n) {
                setDirty();
            }
            return n;
        }

        @Override
        public synchronized CompoundTag save(CompoundTag t) {
            t.putBoolean("GuardianSpawned", this.guardianSpawned);
            t.put("EntityChunks", new LongArrayTag(this.entityChunks.stream().mapToLong(Long::longValue).toArray()));
            return t;
        }
    }

    // ------------------------------------------------------------------
    // The Soul Guardian
    // ------------------------------------------------------------------

    private static final String GUARDIAN_NBT = "{Invulnerable:1b,PersistenceRequired:1b,Tags:[\"giveninfinitymaps\"],"
            + "Offers:{Recipes:["
            + "{maxUses:1,buy:{id:\"flash:best_friends_soul\",Count:1b},sell:{id:\"flash:soul_stone\",Count:1b},xp:1,"
            + "priceMultiplier:0.0f,rewardExp:1b},"
            + map("minecraft:clock", "Sanctum Sanctorum Explorer Map", "sanctummap") + ","
            + map("minecraft:ender_eye", "Norse Village Explorer Map", "norsemap") + ","
            + map("minecraft:nether_star", "Morag Temple Explorer Map", "moragmap") + ","
            + map("minecraft:red_dye", "Aether Chamber Explorer Map", "aethermap") + ","
            + map("minecraft:book", "Sanctuary Explorer Map", "sanctuarymap")
            + "]},VillagerData:{profession:\"flash:soul_guardian\",level:2,type:\"minecraft:plains\"}}";

    private static String map(String buy, String name, String tag) {
        return "{maxUses:1,buy:{id:\"" + buy + "\",Count:1b},buyB:{id:\"flash:aspects_of_existance\",Count:16b},"
                + "sell:{id:\"minecraft:map\",Count:1b,tag:{display:{Name:'{\"text\":\"" + name + "\"}'}," + tag
                + ":1b}},xp:1,priceMultiplier:0.08f,rewardExp:1b}";
    }

    public static Entity createSoulGuardian(ServerLevel level) {
        try {
            CompoundTag t = TagParser.parseTag(GUARDIAN_NBT);
            t.putString("id", "minecraft:villager");
            Entity e = EntityType.loadEntityRecursive(t, level, x -> x);
            if (e != null) {
                e.moveTo(SOUL_GUARDIAN_POS.getX() + 0.5D, SOUL_GUARDIAN_POS.getY(), SOUL_GUARDIAN_POS.getZ() + 0.5D,
                        0.0F, 0.0F);
            }
            return e;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            com.mojang.logging.LogUtils.getLogger().error("Invalid Soul Guardian NBT", ex);
            return null;
        }
    }

    public static boolean isGuardian(Entity e) {
        return e != null && e.getTags().contains(GUARDIAN_TAG);
    }

    private static boolean canKill(DamageSource s) {
        return s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || s.getEntity() instanceof Player p && p.isCreative();
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (isGuardian(event.getEntity()) && !canKill(event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (isGuardian(e) && !canKill(event.getSource())) {
            event.setCanceled(true);
            e.setHealth(e.getMaxHealth());
        }
        // a best friend's soul: the player whose own tamed pet dies by their hand
        if (!e.level().isClientSide && event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer
                p && isOwnPet(e, p)) {
            InfinityAdvancements.grant(p, "tamed_wolf_death");
        }
    }

    private static boolean isOwnPet(LivingEntity e, Player p) {
        if (e instanceof TamableAnimal t) {
            return t.isTame() && p.getUUID().equals(t.getOwnerUUID());
        }
        if (e instanceof AbstractHorse h) {
            return h.isTamed() && p.getUUID().equals(h.getOwnerUUID());
        }
        return false;
    }

    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        if (isGuardian(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // The Soulworld's hut (996..1004, 75..83, 996..1004: where the snapped wake up)
    // ------------------------------------------------------------------

    private static final ChunkPos HUT = new ChunkPos(62, 62);
    private static final ConcurrentLinkedQueue<ChunkPos> PENDING = new ConcurrentLinkedQueue<>();
    private static final Set<ChunkPos> BUILT = new HashSet<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk c && c.getLevel() instanceof ServerLevel l
                && l.dimension() == SoulStone.SOULWORLD && c.getPos().equals(HUT)) {
            PENDING.add(c.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        ServerLevel l = event.getServer().getLevel(SoulStone.SOULWORLD);
        ChunkPos c;
        while ((c = PENDING.poll()) != null) {
            if (l != null && BUILT.add(c) && l.hasChunk(c.x, c.z)) {
                buildHut(l, c);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BUILT.clear();
        PENDING.clear();
    }

    private static void buildHut(ServerLevel l, ChunkPos c) {
        BlockState wall = Blocks.BEDROCK.defaultBlockState();
        fill(l, c, 996, 75, 996, 1004, 75, 1004, wall);
        fill(l, c, 996, 83, 996, 1004, 83, 1004, wall);
        fill(l, c, 996, 76, 996, 1004, 82, 996, wall);
        fill(l, c, 996, 76, 1004, 1004, 82, 1004, wall);
        fill(l, c, 996, 76, 997, 996, 82, 1003, wall);
        fill(l, c, 1004, 76, 997, 1004, 82, 1003, wall);
        fill(l, c, 997, 76, 997, 1003, 82, 1003, Blocks.LIGHT.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        fill(l, c, 1000, 76, 1004, 1000, 76, 1004, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        fill(l, c, 1000, 77, 1004, 1000, 77, 1004, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void fill(ServerLevel l, ChunkPos c, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = Math.max(x0, c.getMinBlockX()); x <= Math.min(x1, c.getMaxBlockX()); x++) {
            for (int z = Math.max(z0, c.getMinBlockZ()); z <= Math.min(z1, c.getMaxBlockZ()); z++) {
                for (int y = y0; y <= y1; y++) {
                    m.set(x, y, z);
                    if (l.getBlockState(m) != s) {
                        l.setBlock(m, s, 2);
                    }
                }
            }
        }
    }
}

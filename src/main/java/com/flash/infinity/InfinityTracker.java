package com.flash.infinity;

import com.flash.FlashMod;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Infinity Tracker (the original's InfinityTracker and InfinityTrackerItem): right-click picks the stone it
 * tracks (sneak: the one before); every few seconds, while someone carries one, it looks through players, entities,
 * items on the ground and the chests of loaded chunks (inside the stones' containers, the Gauntlet, shulker boxes) and
 * remembers where each stone was last seen; its needle points there. A stone sealed in an Orb gives off no signal.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityTracker {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlashMod.MODID);
    public static final RegistryObject<Item> TRACKER = ITEMS.register("infinity_tracker",
            () -> new TrackerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    static final String TRACKING = "Tracking";
    public static final String TARGET = "Target";
    public static final int POINTING = 0;
    public static final int NOT_FOUND = 1;
    public static final int IN_ORB = 2;
    private static final int SEARCH_EVERY = 100;
    private static final InfinityStone[] ORDER = {InfinityStone.SPACE, InfinityStone.MIND, InfinityStone.REALITY,
            InfinityStone.POWER, InfinityStone.TIME, InfinityStone.SOUL};
    private static final Set<LevelChunk> LOADED = new HashSet<>();

    private InfinityTracker() {
    }

    public static InfinityStone tracking(ItemStack s) {
        CompoundTag t = s.getTag();
        InfinityStone st = t == null ? null : InfinityStone.byId(t.getString(TRACKING));
        return st == null ? ORDER[0] : st;
    }

    static final class TrackerItem extends Item {
        TrackerItem(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack s = p.getItemInHand(hand);
            if (!level.isClientSide) {
                InfinityStone cur = tracking(s);
                int i = 0;
                for (int k = 0; k < ORDER.length; k++) {
                    if (ORDER[k] == cur) {
                        i = k;
                    }
                }
                InfinityStone next = ORDER[Math.floorMod(i + (p.isShiftKeyDown() ? -1 : 1), ORDER.length)];
                s.getOrCreateTag().putString(TRACKING, next.id);
                s.getOrCreateTag().remove(TARGET);
                if (p instanceof ServerPlayer sp) {
                    updateTarget(sp, s);
                }
                p.displayClientMessage(Component.translatable("item.flash.infinity_tracker.tracking",
                        new ItemStack(next.item()).getHoverName().copy().withStyle(next.tooltip)), true);
            }
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }

        @Override
        public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean changed) {
            return changed && !ItemStack.isSameItem(a, b);
        }

        @Override
        public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
            InfinityStone st = tracking(s);
            tip.add(Component.translatable("item.flash.infinity_tracker.tracking", new ItemStack(st.item())
                    .getHoverName().copy().withStyle(st.tooltip)).withStyle(ChatFormatting.GRAY));
            CompoundTag t = s.getTag() == null ? null : s.getTag().getCompound(TARGET);
            if (t != null && !t.isEmpty()) {
                int state = t.getInt("State");
                if (state == IN_ORB) {
                    tip.add(Component.translatable("item.flash.infinity_tracker.shielded")
                            .withStyle(ChatFormatting.DARK_PURPLE));
                } else if (state == NOT_FOUND) {
                    tip.add(Component.translatable("item.flash.infinity_tracker.not_found")
                            .withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    tip.add(Component.literal(t.getString("Dimension") + "  " + t.getInt("X") + ", " + t.getInt("Y")
                            + ", " + t.getInt("Z")).withStyle(ChatFormatting.AQUA));
                }
            }
            tip.add(Component.translatable("item.flash.infinity_tracker.description")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------
    // What the world remembers: where each stone was last seen
    // ------------------------------------------------------------------

    static final class Data extends SavedData {
        final CompoundTag seen = new CompoundTag();

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.seen.merge(t.getCompound("Seen"));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.put("Seen", this.seen.copy());
            return t;
        }
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "flash_infinity_tracker");
    }

    private record Sighting(MinecraftServer server, Set<InfinityStone> found, CompoundTag out) {
        void seen(InfinityStone s, Level level, BlockPos at, boolean shielded) {
            if (s == null || this.found.contains(s)) {
                return;
            }
            this.found.add(s);
            CompoundTag t = new CompoundTag();
            t.putString("Dimension", level.dimension().location().toString());
            t.putInt("X", at.getX());
            t.putInt("Y", at.getY());
            t.putInt("Z", at.getZ());
            t.putBoolean("Shielded", shielded);
            this.out.put(s.id, t);
        }
    }

    /** The stones in a stack: itself, inside a container, the Gauntlet, a shulker box (two deep). */
    private static void stonesIn(ItemStack s, Sighting v, Level level, BlockPos at, int depth) {
        if (s.isEmpty()) {
            return;
        }
        InfinityStone st = InfinityStone.of(s);
        if (st != null) {
            v.seen(st, level, at, false);
            return;
        }
        if (InfinityContainers.isContainer(s)) {
            InfinityStone in = InfinityContainers.stoneIn(s);
            v.seen(in, level, at, s.is(InfinityItems.ORB.get()) && in == InfinityStone.POWER);
        }
        if (InfinityGauntlet.isGauntlet(s)) {
            for (InfinityStone g : InfinityGauntlet.stones(s)) {
                v.seen(g, level, at, false);
            }
        }
        CompoundTag t = s.getTag();
        if (depth < 2 && t != null && t.contains("BlockEntityTag")) {
            ListTag items = t.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                stonesIn(ItemStack.of(items.getCompound(i)), v, level, at, depth + 1);
            }
        }
    }

    private static void items(Entity e, Consumer<ItemStack> c) {
        if (e instanceof Player p) {
            p.getInventory().items.forEach(c);
            p.getInventory().armor.forEach(c);
            p.getInventory().offhand.forEach(c);
            p.getEnderChestInventory().getItems().forEach(c);
        } else if (e instanceof ItemEntity ie) {
            c.accept(ie.getItem());
        } else if (e instanceof LivingEntity l) {
            for (EquipmentSlot s : EquipmentSlot.values()) {
                c.accept(l.getItemBySlot(s));
            }
        } else if (e instanceof Container ct) {
            for (int i = 0; i < ct.getContainerSize(); i++) {
                c.accept(ct.getItem(i));
            }
        }
    }

    private static void search(MinecraftServer server) {
        Sighting v = new Sighting(server, new HashSet<>(), new CompoundTag());
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                BlockPos at = e.blockPosition();
                items(e, s -> stonesIn(s, v, level, at, 0));
            }
        }
        for (LevelChunk c : List.copyOf(LOADED)) {
            if (!(c.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            for (BlockEntity be : List.copyOf(c.getBlockEntities().values())) {
                if (be instanceof Container ct) {
                    for (int i = 0; i < ct.getContainerSize(); i++) {
                        stonesIn(ct.getItem(i), v, level, be.getBlockPos(), 0);
                    }
                }
            }
        }
        // the world's Power Stone, never taken from its Orbs: sealed
        if (!InfinityOrb.taken(server) && !v.found.contains(InfinityStone.POWER)) {
            CompoundTag t = new CompoundTag();
            t.putBoolean("Shielded", true);
            v.out.put(InfinityStone.POWER.id, t);
        }
        Data d = data(server);
        for (String k : v.out.getAllKeys()) {
            d.seen.put(k, v.out.get(k));
        }
        d.setDirty();
    }

    static void updateTarget(ServerPlayer p, ItemStack s) {
        InfinityStone st = tracking(s);
        CompoundTag seen = data(p.server).seen.getCompound(st.id);
        CompoundTag t = new CompoundTag();
        if (seen.isEmpty()) {
            t.putInt("State", NOT_FOUND);
        } else if (seen.getBoolean("Shielded")) {
            t.putInt("State", IN_ORB);
        } else {
            t.putInt("State", POINTING);
            t.putString("Dimension", seen.getString("Dimension"));
            t.putInt("X", seen.getInt("X"));
            t.putInt("Y", seen.getInt("Y"));
            t.putInt("Z", seen.getInt("Z"));
        }
        if (!t.equals(s.getOrCreateTag().getCompound(TARGET))) {
            s.getOrCreateTag().put(TARGET, t);
        }
    }

    private static boolean carries(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(TRACKER.get())) {
                return true;
            }
        }
        return p.getOffhandItem().is(TRACKER.get());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 10 != 0) {
            return;
        }
        boolean any = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!carries(p)) {
                continue;
            }
            any = true;
            for (ItemStack s : p.getInventory().items) {
                if (s.is(TRACKER.get())) {
                    updateTarget(p, s);
                }
            }
            if (p.getOffhandItem().is(TRACKER.get())) {
                updateTarget(p, p.getOffhandItem());
            }
        }
        if (any && t % SEARCH_EVERY == 0) {
            search(server);
        }
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk c) {
            LOADED.add(c);
        }
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk c) {
            LOADED.remove(c);
        }
    }

    /** The needle (client): 0..1 of a turn, from the holder's facing to the target; spinning when there is none. */
    public static float angle(ItemStack s, Level level, Entity holder) {
        if (holder == null || level == null) {
            return 0.0F;
        }
        CompoundTag t = s.getTag() == null ? null : s.getTag().getCompound(TARGET);
        if (t == null || t.isEmpty() || t.getInt("State") != POINTING
                || !level.dimension().location().toString().equals(t.getString("Dimension"))) {
            return (float) ((level.getGameTime() % 40L) / 40.0D);
        }
        double dx = t.getInt("X") + 0.5D - holder.getX();
        double dz = t.getInt("Z") + 0.5D - holder.getZ();
        double target = Math.atan2(dz, dx) / (Math.PI * 2.0D);
        double facing = net.minecraft.util.Mth.positiveModulo(holder.getYRot() / 360.0D, 1.0D);
        return (float) net.minecraft.util.Mth.positiveModulo(0.5D - (facing - 0.25D - target), 1.0D);
    }
}

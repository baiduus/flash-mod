package com.flash.infinity;

import com.flash.FlashMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The rest of the Infinity Stones' world (the original's Hammer, Summons, SoulGuardian's telephone, ShockwaveAbility,
 * the scepter's any-stone recipe, Thanos' armours): the Hammer comes back from the recipes it forges; the Cosmi-Rod's
 * concussive blast; the Soul Guardian Telephone calls the Guardian over for half a day; Malekith guards the Aether.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class InfinityExtras {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlashMod.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS, FlashMod.MODID);

    public static final RegistryObject<RecipeSerializer<ScepterStoneRecipe>> SCEPTER_STONE = SERIALIZERS.register(
            "scepter_stone", () -> new SimpleCraftingRecipeSerializer<>(ScepterStoneRecipe::new));

    // ------------------------------------------------------------------
    // Thanos' armours (Classic and MCU): netherite, drawn by their models (client ThanosArmorLayer)
    // ------------------------------------------------------------------

    public static final class ThanosArmor extends ArmorItem {
        public final String model;
        public final String texture;

        ThanosArmor(Type type, String model, String texture) {
            super(ArmorMaterials.NETHERITE, type, new Item.Properties().rarity(Rarity.EPIC).fireResistant());
            this.model = model;
            this.texture = texture;
        }

        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return "flash:textures/models/armor/thanos_empty.png";
        }
    }

    private static RegistryObject<Item> armor(String set, String piece, ArmorItem.Type type) {
        String tex = "classic".equals(set) && "boots".equals(piece) ? "thanos_classic_boots" : "thanos_" + set;
        return ITEMS.register("thanos_" + set + "_" + piece, () -> new ThanosArmor(type,
                "flash:geo/infinity/thanos_" + set + "_" + piece, "flash:textures/infinity_models/" + tex + ".png"));
    }

    public static final RegistryObject<Item> THANOS_CLASSIC_HELMET = armor("classic", "helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> THANOS_CLASSIC_CHESTPLATE = armor("classic", "chestplate",
            ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> THANOS_CLASSIC_LEGGINGS = armor("classic", "leggings",
            ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> THANOS_CLASSIC_BOOTS = armor("classic", "boots", ArmorItem.Type.BOOTS);
    public static final RegistryObject<Item> THANOS_MCU_HELMET = armor("mcu", "helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> THANOS_MCU_CHESTPLATE = armor("mcu", "chestplate",
            ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> THANOS_MCU_LEGGINGS = armor("mcu", "leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> THANOS_MCU_BOOTS = armor("mcu", "boots", ArmorItem.Type.BOOTS);

    public static final List<RegistryObject<Item>> THANOS = List.of(THANOS_CLASSIC_HELMET, THANOS_CLASSIC_CHESTPLATE,
            THANOS_CLASSIC_LEGGINGS, THANOS_CLASSIC_BOOTS, THANOS_MCU_HELMET, THANOS_MCU_CHESTPLATE,
            THANOS_MCU_LEGGINGS, THANOS_MCU_BOOTS);

    private InfinityExtras() {
    }

    // ------------------------------------------------------------------
    // The scepter: an empty scepter and any stone
    // ------------------------------------------------------------------

    public static final class ScepterStoneRecipe extends CustomRecipe {
        public ScepterStoneRecipe(ResourceLocation id, CraftingBookCategory cat) {
            super(id, cat);
        }

        private static InfinityStone stone(CraftingContainer c) {
            boolean scepter = false;
            InfinityStone stone = null;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.is(InfinityItems.EMPTY_SCEPTER.get()) && !scepter) {
                    scepter = true;
                } else if (InfinityStone.of(s) != null && stone == null) {
                    stone = InfinityStone.of(s);
                } else {
                    return null;
                }
            }
            return scepter ? stone : null;
        }

        @Override
        public boolean matches(CraftingContainer c, Level level) {
            return stone(c) != null;
        }

        @Override
        public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
            InfinityStone s = stone(c);
            if (s == null) {
                return ItemStack.EMPTY;
            }
            ItemStack out = new ItemStack(InfinityItems.SCEPTER.get());
            if (s != InfinityStone.MIND) {
                InfinityContainers.setStone(out, s);
            }
            return out;
        }

        @Override
        public boolean canCraftInDimensions(int w, int h) {
            return w * h >= 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SCEPTER_STONE.get();
        }
    }

    // ------------------------------------------------------------------
    // The Hammer: given back by the plates, dusts and blades it forges
    // ------------------------------------------------------------------

    private static final Set<String> HAMMERED = Set.of("iron_plate", "steel_plate", "iron_dust", "steel_dust",
            "dwarf_star_alloy_dust", "gold_dust", "titanium_dust", "coal_dust", "gold_titanium_alloy_plate",
            "uru_blade", "gold_plate", "titanium_plate", "dwarf_star_alloy_plate", "uru_plate", "uru_dust",
            "gold_titanium_alloy_handle");

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack out = event.getCrafting();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(out.getItem());
        if (id == null || !FlashMod.MODID.equals(id.getNamespace()) || !HAMMERED.contains(id.getPath())
                || event.getEntity().level().isClientSide) {
            return;
        }
        Container inv = event.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(InfinityItems.HAMMER.get())) {
                ItemStack h = new ItemStack(InfinityItems.HAMMER.get());
                if (!event.getEntity().getInventory().add(h)) {
                    event.getEntity().drop(h, false);
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // The Cosmi-Rod's shockwave (flash:shockwave): rings of energy from the hand
    // ------------------------------------------------------------------

    private record Wave(ServerPlayer p, float damage, double speed, int duration, int[] tick) {
    }

    private static final Map<UUID, Wave> WAVES = new HashMap<>();

    static void shockwave(ServerPlayer p, InfPowers.Ability a) {
        float dmg = a.json.has("damage") ? a.json.get("damage").getAsFloat() : 5.0F;
        double speed = a.json.has("speed") ? a.json.get("speed").getAsDouble() : 3.0D;
        int dur = a.json.has("duration") ? a.json.get("duration").getAsInt() : 7;
        WAVES.put(p.getUUID(), new Wave(p, dmg, speed, dur, new int[1]));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F,
                1.6F);
    }

    private static void waveTick(Wave w) {
        ServerPlayer p = w.p();
        int t = ++w.tick()[0];
        Vec3 look = p.getLookAngle();
        Vec3 hand = p.getEyePosition().add(look.scale(1.0D)).add(0.0D, -0.3D, 0.0D);
        double grow = Math.min(4.0D, 0.6D * t);
        Vec3 c = hand.add(look.scale(grow));
        AABB box = new AABB(c, c).inflate(grow);
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI * 2.0D / 16.0D;
            Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side.lengthSqr() < 1.0E-4D) {
                side = new Vec3(1.0D, 0.0D, 0.0D);
            }
            side = side.normalize();
            Vec3 up = side.cross(look).normalize();
            Vec3 at = c.add(side.scale(Math.cos(ang) * grow * 0.5D)).add(up.scale(Math.sin(ang) * grow * 0.5D));
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (Entity e : level.getEntities(p, box, e -> e instanceof LivingEntity && e.isAlive())) {
            Vec3 to = e.position().subtract(p.position());
            if (to.dot(look) <= 0.0D) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().add(look.scale(w.speed() / 3.0D)));
            e.hurtMarked = true;
            e.hurt(level.damageSources().playerAttack(p), w.damage());
        }
    }

    // ------------------------------------------------------------------
    // The Soul Guardian Telephone: /infinity soulguardian call | return
    // ------------------------------------------------------------------

    private static final long VISIT = 12000L;
    private static final String RETURN_AT = "flash_return_at";
    private static final Map<UUID, Integer> CALLS = new HashMap<>();
    private static final Set<Entity> VISITING = new HashSet<>();

    static LiteralArgumentBuilder<CommandSourceStack> soulGuardian() {
        return Commands.literal("soulguardian")
                .then(Commands.literal("call").executes(c -> {
                    call(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("return").executes(c -> {
                    Entity g = find(c.getSource().getServer());
                    if (g == null) {
                        return 0;
                    }
                    sendHome(g);
                    return 1;
                }))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerLevel v = c.getSource().getServer().getLevel(InfinityWorld.VORMIR_LEVEL);
                    Entity g = v == null ? null : InfinityWorld.createSoulGuardian(v);
                    return g != null && v.addFreshEntity(g) ? 1 : 0;
                }));
    }

    private static Entity find(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                if (InfinityWorld.isGuardian(e) && e.isAlive()) {
                    return e;
                }
            }
        }
        return null;
    }

    private static void call(ServerPlayer p) {
        Entity g = find(p.server);
        if (g != null) {
            bring(g, p);
            return;
        }
        // not loaded: load its spot on Vormir and look again for a few seconds
        ServerLevel v = p.server.getLevel(InfinityWorld.VORMIR_LEVEL);
        if (v != null) {
            BlockPos at = InfinityWorld.SOUL_GUARDIAN_POS;
            v.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            CALLS.put(p.getUUID(), 100);
        }
    }

    private static void bring(Entity g, ServerPlayer p) {
        g.getPersistentData().putLong(RETURN_AT, p.server.overworld().getGameTime() + VISIT);
        g.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), Set.of(), g.getYRot(), g.getXRot());
        p.displayClientMessage(Component.translatable("message.flash.infinity.guardian_called")
                .withStyle(ChatFormatting.GOLD), true);
        VISITING.add(g);
    }

    private static void sendHome(Entity g) {
        g.getPersistentData().remove(RETURN_AT);
        ServerLevel v = g.getServer().getLevel(InfinityWorld.VORMIR_LEVEL);
        if (v == null) {
            return;
        }
        BlockPos at = InfinityWorld.SOUL_GUARDIAN_POS;
        v.getChunk(at);
        g.teleportTo(v, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, Set.of(), g.getYRot(), g.getXRot());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity e = event.getEntity();
        if (InfinityWorld.isGuardian(e) && e.getPersistentData().contains(RETURN_AT)) {
            VISITING.add(e);
        }
        // Malekith, guarding the Aether chamber
        if (e instanceof Husk h && h.getTags().contains("malekith") && !h.getTags().contains("flash_malekith")) {
            malekith(h);
        }
    }

    static void malekith(net.minecraft.world.entity.Mob m) {
        m.addTag("malekith");
        m.addTag("flash_malekith");
        m.setCustomName(Component.literal("Malekith").withStyle(ChatFormatting.DARK_GRAY));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        base(m, Attributes.MAX_HEALTH, 100.0D);
        base(m, Attributes.ATTACK_DAMAGE, 20.0D);
        base(m, Attributes.ARMOR, 10.0D);
        m.setHealth(100.0F);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.setHoverName(Component.literal("Malekith's Sword"));
        m.setItemSlot(EquipmentSlot.MAINHAND, sword);
        m.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static void base(net.minecraft.world.entity.Mob m, net.minecraft.world.entity.ai.attributes.Attribute a,
                             double v) {
        var i = m.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> summon() {
        return Commands.literal("summon").requires(s -> s.hasPermission(2))
                .then(Commands.literal("malekith").executes(c -> {
                    ServerLevel l = c.getSource().getLevel();
                    Husk h = net.minecraft.world.entity.EntityType.HUSK.create(l);
                    if (h == null) {
                        return 0;
                    }
                    Vec3 at = c.getSource().getPosition();
                    h.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                    malekith(h);
                    l.addFreshEntity(h);
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Wave> it = WAVES.values().iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (w.p().isRemoved() || w.tick()[0] >= w.duration()) {
                it.remove();
            } else {
                waveTick(w);
            }
        }
        MinecraftServer server = event.getServer();
        if (!CALLS.isEmpty()) {
            Entity g = find(server);
            for (Iterator<Map.Entry<UUID, Integer>> it = CALLS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Integer> en = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p != null && g != null) {
                    bring(g, p);
                    it.remove();
                } else if (p == null || en.getValue() <= 0) {
                    if (p != null) {
                        p.displayClientMessage(Component.translatable("message.flash.infinity.guardian_unreachable"),
                                true);
                    }
                    it.remove();
                } else {
                    en.setValue(en.getValue() - 1);
                }
            }
            if (CALLS.isEmpty()) {
                ServerLevel v = server.getLevel(InfinityWorld.VORMIR_LEVEL);
                if (v != null) {
                    BlockPos at = InfinityWorld.SOUL_GUARDIAN_POS;
                    v.setChunkForced(at.getX() >> 4, at.getZ() >> 4, false);
                }
            }
        }
        if (!VISITING.isEmpty() && server.getTickCount() % 20 == 0) {
            long now = server.overworld().getGameTime();
            for (Entity g : List.copyOf(VISITING)) {
                if (g.isRemoved()) {
                    VISITING.remove(g);
                } else if (!g.getPersistentData().contains(RETURN_AT)) {
                    VISITING.remove(g);
                } else if (now >= g.getPersistentData().getLong(RETURN_AT)) {
                    VISITING.remove(g);
                    sendHome(g);
                }
            }
        }
    }
}

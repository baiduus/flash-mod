package com.flash.client;

import com.flash.FlashMod;
import com.flash.entity.FlashSuitStand;
import com.flash.item.LexArmorItem;
import com.flash.lex.LexAbility;
import com.flash.lex.LexState;
import com.flash.lex.LexSuit;
import com.flash.network.FlashNetwork;
import com.flash.network.LexKeyPacket;
import com.flash.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lex Luthor's suit on the client: the keys of its systems (the ability keys of the heroes), the hum of the beams
 * while they are held, and the helmet interface: the Stark HUD of Legends: Superheroes in Lex's green (frame,
 * reticle, target lock with its readout, radar, systems panel, the battery, the vitals warning), the battery bar
 * also for a suit on an armour stand you look at.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class LexClient {

    private static final boolean[] DOWN = new boolean[LexAbility.values().length];
    private static LoopSound beamSound;
    private static LoopSound unibeamSound;

    private static ResourceLocation gui(String name) {
        return new ResourceLocation(FlashMod.MODID, "textures/gui/lex/" + name + ".png");
    }

    private static final ResourceLocation FRAME = gui("default");
    private static final ResourceLocation RETICLE = gui("rectile");
    private static final ResourceLocation TARGETING = gui("targeting");
    private static final ResourceLocation PANEL = gui("hud_panel");
    private static final ResourceLocation RADAR = gui("minimap_radar");
    private static final ResourceLocation INFO = gui("info");
    private static final ResourceLocation VITALS = gui("vitals");

    private LexClient() {
    }

    /** The local player flies the suit (four pieces, no powers of his own). */
    public static boolean pilot(Player p) {
        return p != null && LexArmorItem.wearingFull(p) && !com.flash.power.HeroBalance.isHero(p);
    }

    public static KeyMapping key(LexAbility a) {
        return FlashAbilityKeys.mapping(a.keySlot());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        boolean usable = pilot(mc.player) && mc.screen == null && !mc.player.isSpectator();
        LexAbility[] all = LexAbility.values();
        for (int i = 0; i < all.length; i++) {
            KeyMapping k = key(all[i]);
            boolean down = usable && k != null && k.isDown();
            if (down == DOWN[i]) {
                continue;
            }
            DOWN[i] = down;
            if (down || all[i].kind() == LexAbility.Kind.HOLD) {
                FlashNetwork.CHANNEL.sendToServer(new LexKeyPacket(i, down));
            }
        }
        for (Player p : mc.level.players()) {
            if (p != mc.player) {
                continue;
            }
            if (LexState.flag(p, LexSuit.F_BEAM) && (beamSound == null || beamSound.isStopped())) {
                beamSound = new LoopSound(ModSounds.LEX_REPULSOR_BEAM.get(), p, LexSuit.F_BEAM);
                mc.getSoundManager().play(beamSound);
            }
            if (LexState.flag(p, LexSuit.F_UNIBEAM) && (unibeamSound == null || unibeamSound.isStopped())) {
                unibeamSound = new LoopSound(ModSounds.LEX_REPULSOR_BEAM.get(), p, LexSuit.F_UNIBEAM);
                mc.getSoundManager().play(unibeamSound);
            }
        }
    }

    /** A loop tied to a system of the suit: it stops the moment the system does. */
    private static final class LoopSound extends AbstractTickableSoundInstance {
        private final Player player;
        private final int flag;

        LoopSound(SoundEvent sound, Player player, int flag) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.flag = flag;
            this.looping = true;
            this.volume = 0.8F;
            this.pitch = flag == LexSuit.F_UNIBEAM ? 0.7F : 1.0F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !LexState.flag(this.player, this.flag)) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    /** The helmet interface and the battery bar. */
    public static final class Hud implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            ItemStack chest = ItemStack.EMPTY;
            boolean stand = false;
            if (LexArmorItem.wearingFull(mc.player)) {
                chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            } else if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof FlashSuitStand s
                    && LexArmorItem.isLex(s.getItemBySlot(EquipmentSlot.CHEST))) {
                chest = s.getItemBySlot(EquipmentSlot.CHEST);
                stand = true;
            }
            if (chest.isEmpty()) {
                return;
            }
            float e = LexArmorItem.energy(chest);
            boolean helmet = !stand && pilot(mc.player) && mc.options.getCameraType().isFirstPerson()
                    && !LexState.flag(mc.player, LexSuit.F_OPEN) && e > 0.0F && mc.screen == null;
            if (helmet) {
                interfaceOverlay(g, mc, width, height, e, partialTick);
            }
            battery(g, mc, width, height, e, stand);
        }
    }

    /** Whole texture into a rectangle. */
    private static void image(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH,
                              float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(tex, x, y, w, h, 0.0F, 0.0F, texW, texH, texW, texH);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void interfaceOverlay(GuiGraphics g, Minecraft mc, int width, int height, float energy,
                                         float partial) {
        Player p = mc.player;
        Font font = mc.font;
        long t = mc.level.getGameTime();
        // the frame of the visor
        image(g, FRAME, 0, 0, width, height, 1024, 1024, 0.85F);
        // the reticle, turning slowly around the crosshair
        int rs = Math.min(width, height) / 4;
        g.pose().pushPose();
        g.pose().translate(width / 2.0F, height / 2.0F, 0.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((t + partial) * 0.6F));
        image(g, RETICLE, -rs / 2, -rs / 2, rs, rs, 330, 326, 0.28F);
        g.pose().popPose();
        // target lock and its readout
        Entity target = mc.crosshairPickEntity;
        if (target instanceof LivingEntity living && living.isAlive()) {
            int ts = rs + 20;
            float pulse = 0.6F + 0.4F * (float) Math.sin((t + partial) * 0.5F);
            image(g, TARGETING, width / 2 - ts / 2, height / 2 - ts / 2, ts, ts, 1024, 1024, pulse);
            int ix = width / 2 + ts / 2 - 6;
            int iy = height / 2 - ts / 2 - 10;
            image(g, INFO, ix, iy, 60, 66, 242, 266, 0.9F);
            int color = living instanceof Enemy ? 0xFFFF5A5A : 0xFF8CFF8C;
            g.drawString(font, living.getDisplayName(), ix + 18, iy + 4, color, true);
            g.drawString(font, Component.translatable("hud.flash.lex_target_hp", Math.round(living.getHealth()),
                    Math.round(living.getMaxHealth())), ix + 18, iy + 15, 0xFFB6FFB0, true);
            g.drawString(font, Component.translatable("hud.flash.lex_target_dist",
                    Math.round(living.distanceTo(p))), ix + 18, iy + 26, 0xFFB6FFB0, true);
        }
        // the radar, top right: hostile in red, the others green
        int rad = 64;
        int rx = width - rad - 14;
        int ry = 14;
        image(g, RADAR, rx, ry, rad, rad, 160, 159, 0.8F);
        double range = 32.0D;
        float yaw = (float) Math.toRadians(p.getYRot());
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range), x -> x instanceof LivingEntity)) {
            double dx = e.getX() - p.getX();
            double dz = e.getZ() - p.getZ();
            double rxp = dx * Math.cos(yaw) + dz * Math.sin(yaw);
            double rzp = dz * Math.cos(yaw) - dx * Math.sin(yaw);
            double dist = Math.sqrt(rxp * rxp + rzp * rzp);
            if (dist > range) {
                continue;
            }
            int px = rx + rad / 2 + (int) Math.round(-rxp / range * (rad / 2.0D - 4));
            int py = ry + rad / 2 + (int) Math.round(-rzp / range * (rad / 2.0D - 4));
            int col = e instanceof Enemy ? 0xFFFF4040 : e instanceof Player ? 0xFFFFFFFF : 0xFF7CFF7C;
            g.fill(px - 1, py - 1, px + 1, py + 1, col);
        }
        // the systems panel, bottom left
        int pw = 150;
        int ph = 26;
        int px = 22;
        int py = height - ph - 40;
        image(g, PANEL, px, py, pw, ph, 470, 81, 0.9F);
        g.drawString(font, Component.translatable("hud.flash.lex_title"), px + 8, py + 4, 0xFFC9A4FF, true);
        String speed = String.format(java.util.Locale.ROOT, "%.1f", p.getDeltaMovement().length() * 20.0D);
        g.drawString(font, Component.translatable("hud.flash.lex_readout", Math.round(energy), Math.round(p.getY()),
                speed), px + 8, py + 15, 0xFFB6FFB0, true);
        if (LexState.flag(p, LexSuit.F_OVERDRIVE)) {
            g.drawString(font, Component.translatable("hud.flash.lex_overdrive"), px + 8, py - 11,
                    (t / 5) % 2 == 0 ? 0xFFFF6060 : 0xFFFFC040, true);
        }
        // vitals
        if (p.getHealth() < p.getMaxHealth() * 0.3F && (t / 8) % 2 == 0) {
            image(g, VITALS, 0, 0, width, height, 1024, 1024, 0.9F);
        }
    }

    private static void battery(GuiGraphics g, Minecraft mc, int width, int height, float e, boolean stand) {
        float f = e / LexArmorItem.MAX_ENERGY;
        boolean low = e < 20.0F;
        boolean blink = low && (mc.player.tickCount / 8) % 2 == 0;
        int barH = Math.min(110, height / 2);
        int x = 8;
        int y = (height - barH) / 2;
        int w = 7;
        g.fill(x - 2, y - 2, x + w + 2, y + barH + 2, 0xA0101418);
        g.fill(x - 1, y - 1, x + w + 1, y + barH + 1, 0xFF4A2A66);
        g.fill(x, y, x + w, y + barH, 0xFF0C1410);
        int filled = Math.round(barH * f);
        int color = e <= 0.0F ? 0xFF555555 : low ? (blink ? 0xFFFF4040 : 0xFFB02020) : 0xFF6CFF6C;
        if (filled > 0) {
            g.fill(x, y + barH - filled, x + w, y + barH, color);
            g.fill(x, y + barH - filled, x + 2, y + barH, 0x40FFFFFF);
        }
        for (int i = 1; i < 10; i++) {
            int ty = y + barH * i / 10;
            g.fill(x, ty, x + w, ty + 1, 0x60000000);
        }
        String pct = Math.round(e) + "%";
        int ty = Math.max(y, y + barH - Math.max(filled, 8));
        g.drawString(mc.font, pct, x + w + 5, ty, low ? 0xFFFF6060 : 0xFFB6FFB0, true);
        Component label = Component.translatable(stand ? "hud.flash.lex_charging" : "hud.flash.lex_energy");
        g.drawString(mc.font, label, x - 2, y - 13, 0xFFC9A4FF, true);
        if (low && !stand) {
            g.drawString(mc.font, Component.translatable("hud.flash.lex_low"), x - 2, y + barH + 6,
                    blink ? 0xFFFF4040 : 0xFF902020, true);
        }
    }
}

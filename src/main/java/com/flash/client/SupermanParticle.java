package com.flash.client;

import com.flash.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/**
 * The particles of the Superman pack (grey textures tinted per type): breath
 * clouds, frost, snowflakes, glows, sparks, puffs, dust and vapor. Spawned by the
 * server with a velocity (count 0) or in a cloud.
 */
public class SupermanParticle extends TextureSheetParticle {

    /** Look of one particle type. */
    public record Style(int rgb, float alpha, float sizeMin, float sizeMax, int lifeMin, int lifeMax, float gravity,
                        float friction, float grow, boolean glow, float spin) {
    }

    private final Style style;
    private final float baseSize;
    private final float spinSpeed;

    protected SupermanParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                               SpriteSet sprites, Style style) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.style = style;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.lifetime = style.lifeMin() + this.random.nextInt(Math.max(1, style.lifeMax() - style.lifeMin() + 1));
        this.baseSize = style.sizeMin() + this.random.nextFloat() * (style.sizeMax() - style.sizeMin());
        this.quadSize = this.baseSize;
        this.gravity = style.gravity();
        this.friction = style.friction();
        this.hasPhysics = false;
        this.rCol = ((style.rgb() >> 16) & 0xFF) / 255.0F;
        this.gCol = ((style.rgb() >> 8) & 0xFF) / 255.0F;
        this.bCol = (style.rgb() & 0xFF) / 255.0F;
        this.alpha = style.alpha();
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        this.oRoll = this.roll;
        this.spinSpeed = (this.random.nextFloat() - 0.5F) * style.spin();
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = this.age / (float) Math.max(1, this.lifetime);
        this.quadSize = this.baseSize * (1.0F + this.style.grow() * t);
        this.alpha = this.style.alpha() * (t < 0.15F ? t / 0.15F : 1.0F - Math.max(0.0F, (t - 0.45F) / 0.55F));
        this.oRoll = this.roll;
        this.roll += this.spinSpeed;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.style.glow() ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SupermanParticle(level, x, y, z, dx, dy, dz, this.sprites, this.style);
        }
    }

    private static void reg(RegisterParticleProvidersEvent event, net.minecraftforge.registries.RegistryObject<SimpleParticleType> type,
                            Style style) {
        event.registerSpriteSet(type.get(), sprites -> new Provider(sprites, style));
    }

    public static void register(RegisterParticleProvidersEvent event) {
        //                      rgb       alpha size        life    grav    fric  grow  glow  spin
        reg(event, ModParticles.SM_CLOUD, new Style(0xF4F8FF, 0.55F, 0.35F, 0.7F, 14, 24, 0.0F, 0.9F, 2.2F, false, 0.15F));
        reg(event, ModParticles.SM_FROST_CLOUD, new Style(0xA8E4FF, 0.6F, 0.35F, 0.7F, 14, 24, 0.0F, 0.9F, 2.0F, true, 0.15F));
        reg(event, ModParticles.SM_SNOW, new Style(0xE6F8FF, 0.95F, 0.08F, 0.16F, 18, 30, 0.01F, 0.92F, 0.0F, true, 0.3F));
        reg(event, ModParticles.SM_FROST, new Style(0xBFEFFF, 0.85F, 0.2F, 0.4F, 16, 26, 0.0F, 0.9F, 0.6F, true, 0.1F));
        reg(event, ModParticles.SM_HEAT_GLOW, new Style(0xFF4A1E, 0.9F, 0.25F, 0.5F, 4, 8, 0.0F, 0.8F, 0.4F, true, 0.0F));
        reg(event, ModParticles.SM_SOLAR_GLOW, new Style(0xFFC23A, 0.85F, 0.4F, 1.0F, 10, 20, 0.0F, 0.85F, 0.8F, true, 0.0F));
        reg(event, ModParticles.SM_SPARKLE, new Style(0xFFE89A, 1.0F, 0.12F, 0.28F, 12, 24, 0.0F, 0.9F, -0.4F, true, 0.4F));
        reg(event, ModParticles.SM_EMBER, new Style(0xFF7A20, 1.0F, 0.05F, 0.1F, 10, 20, 0.04F, 0.96F, -0.5F, true, 0.0F));
        reg(event, ModParticles.SM_PUFF, new Style(0xD8D2C8, 0.7F, 0.5F, 1.1F, 20, 34, -0.002F, 0.88F, 1.6F, false, 0.08F));
        reg(event, ModParticles.SM_DUST, new Style(0xA89580, 0.75F, 0.6F, 1.3F, 22, 38, 0.002F, 0.86F, 1.4F, false, 0.06F));
        reg(event, ModParticles.SM_VAPOR, new Style(0xFFFFFF, 0.5F, 0.6F, 1.2F, 10, 18, 0.0F, 0.85F, 1.8F, false, 0.1F));
        reg(event, ModParticles.SM_SOLAR_RAY, new Style(0xFFD050, 0.9F, 0.2F, 0.45F, 14, 26, -0.01F, 0.95F, 0.2F, true, 0.0F));
    }
}

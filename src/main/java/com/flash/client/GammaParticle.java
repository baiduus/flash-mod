package com.flash.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Glowing gamma radiation: the 12 animated frames of the asset pack. */
public class GammaParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected GammaParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                            SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx + (this.random.nextFloat() - 0.5F) * 0.04F;
        this.yd = dy + 0.02D + this.random.nextFloat() * 0.03F;
        this.zd = dz + (this.random.nextFloat() - 0.5F) * 0.04F;
        this.lifetime = 14 + this.random.nextInt(10);
        this.quadSize *= 1.2F + this.random.nextFloat() * 0.6F;
        this.gravity = -0.01F;
        this.friction = 0.92F;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        this.alpha = 1.0F - Math.max(0.0F, (this.age - this.lifetime * 0.6F) / (this.lifetime * 0.4F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new GammaParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}

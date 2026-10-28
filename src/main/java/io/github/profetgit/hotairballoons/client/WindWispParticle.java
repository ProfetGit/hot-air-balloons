package io.github.profetgit.hotairballoons.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Small translucent streak that drifts with the wind, a little faster than the balloon, fading in and out. */
public final class WindWispParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float phase;

    private WindWispParticle(ClientLevel level, double x, double y, double z, double xa, double za, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.sprites = sprites;
        this.xd = xa;
        this.yd = 0.0;
        this.zd = za;
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 40 + random.nextInt(31);
        this.quadSize = 0.14F + random.nextFloat() * 0.08F;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.alpha = 0.0F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float t = (age + phase * 4.0F) * 0.25F;
        this.yd = Math.sin(t) * 0.006;
        this.xd += Math.cos(t * 0.7F) * 0.0006;
        this.zd += Math.sin(t * 0.9F) * 0.0006;
        float f = (float) age / lifetime;
        this.alpha = 0.7F * Math.min(1.0F, Math.min(f / 0.2F, (1.0F - f) / 0.3F));
        setSpriteFromAge(sprites);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /** Velocity args are the wisp's drift per tick (x, z); y is ignored. */
    public record Provider(FabricSpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new WindWispParticle(level, x, y, z, xa, za, sprites);
        }
    }
}

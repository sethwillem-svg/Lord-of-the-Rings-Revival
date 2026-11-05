package io.github.sethwillemsvg.lotrrevival.client.combat;

import io.github.sethwillemsvg.lotrrevival.combat.CombatParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client particles for combat.CombatParticles. parry_spark: a bright four-ray star thrown out from the contact point, fast,
 * shrinking and cooling through its four sprites, full bright. heavy_warn: a red spiked mote rising slowly around a heavy
 * wind-up and fading out.
 */
public final class CombatParticleProviders {
    private CombatParticleProviders() {}

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(CombatParticles.PARRY_SPARK.get(), Spark.Provider::new);
        e.registerSpriteSet(CombatParticles.HEAVY_WARN.get(), HeavyMote.Provider::new);
    }

    static final class Spark extends TextureSheetParticle {
        private final SpriteSet sprites;

        Spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.friction = 0.82f;
            this.gravity = 0.35f;
            this.lifetime = 6 + random.nextInt(4);
            this.quadSize = 0.08f + random.nextFloat() * 0.05f;
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) setSpriteFromAge(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;   // full bright: a spark lights itself
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            @Nullable
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
                return new Spark(level, x, y, z, vx, vy, vz, sprites);
            }
        }
    }

    static final class HeavyMote extends TextureSheetParticle {
        private final SpriteSet sprites;

        HeavyMote(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.xd = vx * 0.2;
            this.yd = 0.02 + vy * 0.2;
            this.zd = vz * 0.2;
            this.friction = 0.9f;
            this.gravity = 0f;
            this.lifetime = 14 + random.nextInt(6);
            this.quadSize = 0.12f + random.nextFloat() * 0.04f;
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            alpha = 1f - (float) age / lifetime * 0.6f;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            @Nullable
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
                return new HeavyMote(level, x, y, z, vx, vy, vz, sprites);
            }
        }
    }
}

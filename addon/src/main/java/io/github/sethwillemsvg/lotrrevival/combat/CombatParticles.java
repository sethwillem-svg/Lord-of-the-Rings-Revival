package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The combat particle types: parry_spark (a perfect or NPC parry, at the contact point) and heavy_warn (red motes around an
 * NPC winding up a heavy swing). Sprites (assets/lotr_revival/particles/*.json, textures/particle/*.png, drawn by
 * tools/sfx/make_combat_textures.py) and the client providers are in client.combat.CombatParticleProviders.
 */
public final class CombatParticles {
    private CombatParticles() {}

    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, LotrRevival.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PARRY_SPARK =
            PARTICLES.register("parry_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEAVY_WARN =
            PARTICLES.register("heavy_warn", () -> new SimpleParticleType(false));

    static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}

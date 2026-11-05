package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The combat sound events (specs/addon/combat-parry-warning.md). The sounds are synthesized for this repo by
 * tools/sfx/make_combat_sfx.sh into assets/lotr_revival/sounds/combat/; assets/lotr_revival/sounds.json maps each event to
 * its file and subtitle (lang subtitles.lotr_revival.combat.*).
 */
public final class CombatSounds {
    private CombatSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, LotrRevival.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_PERFECT = sound("combat.parry_perfect");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_GLANCE = sound("combat.parry_glance");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_BLOCK = sound("combat.shield_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAGGER = sound("combat.stagger");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_WARN = sound("combat.heavy_warn");
    public static final DeferredHolder<SoundEvent, SoundEvent> COUNTER_READY = sound("combat.counter_ready");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(LotrRevival.id(name)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}

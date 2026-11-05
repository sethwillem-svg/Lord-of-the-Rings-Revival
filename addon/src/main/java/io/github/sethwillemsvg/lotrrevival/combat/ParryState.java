package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;

/**
 * Transient combat state as NeoForge data attachments (no serializer: never saved, not copied on death).
 * Player: lotr_revival:parry (last press, lock, guard, counter). Any NPC: lotr_revival:combat_state (stagger, parry
 * cooldown, last swing heavy).
 */
public final class ParryState {
    private ParryState() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LotrRevival.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerParry>> PARRY =
            ATTACHMENTS.register("parry", () -> AttachmentType.builder(() -> new PlayerParry()).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcCombat>> COMBAT_STATE =
            ATTACHMENTS.register("combat_state", () -> AttachmentType.builder(() -> new NpcCombat()).build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public enum PressKind { SHIELD, BLADE }

    /** A player's parry state. Ticks are server game ticks. */
    public static final class PlayerParry {
        /** Tick of the last press, or Long.MIN_VALUE / 2 when none. */
        public long pressTick = NONE;
        public PressKind kind = PressKind.SHIELD;
        /** Item class at the press: light_shield, medium_shield, heavy_shield, or a blade class. */
        public String itemClass = "";
        /** The press came inside the lock left by an earlier press that did not parry. */
        public boolean pressLocked;
        /** The press gave a perfect parry (it then clears the lock and allows a new guard at once). */
        public boolean pressPerfect;
        public long guardUntil = NONE;
        public UUID counterTarget;
        public long counterUntil = NONE;
        /** Bad-client watch: invalid ParryPayloads in the current second. */
        public long badSecond = NONE;
        public int badCount;

        public boolean hasPress() {
            return pressTick != NONE;
        }

        public void clearCounter() {
            counterTarget = null;
            counterUntil = NONE;
        }
    }

    /** An NPC's combat state. */
    public static final class NpcCombat {
        public long staggerUntil = NONE;
        public long parryCooldownUntil = NONE;
        public boolean lastSwingHeavy;
        /** Stagger multiplier for this NPC (nemesis hook; LEADER rank applies its own on top). */
        public double staggerMultiplier = 1.0;
    }

    public static final long NONE = Long.MIN_VALUE / 2;

    public static PlayerParry of(Player player) {
        return player.getData(PARRY);
    }

    public static NpcCombat combat(Entity entity) {
        return entity.getData(COMBAT_STATE);
    }

    /** The tick an NPC's stagger ends (combat.test), or NONE. */
    public static long staggeredUntil(Entity npc) {
        return npc.hasData(COMBAT_STATE) ? combat(npc).staggerUntil : NONE;
    }

    public static boolean staggered(Entity npc, long now) {
        return staggeredUntil(npc) > now;
    }
}

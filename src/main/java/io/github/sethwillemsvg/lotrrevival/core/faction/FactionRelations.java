package io.github.sethwillemsvg.lotrrevival.core.faction;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Relations between factions and the deterministic hostility rules NPC targeting builds on. */
public final class FactionRelations {
    private static final Map<EntityType<?>, Optional<ResourceKey<Faction>>> TAG_CACHE = new ConcurrentHashMap<>();

    private FactionRelations() {}

    /** Drops the entity-type tag fallback cache (called when tags reload). */
    public static void clearCaches() {
        TAG_CACHE.clear();
    }

    /**
     * Relation between two factions. A faction is an ally of itself. Uses a's entry if present, else b's; when both
     * are present and differ the more hostile one wins.
     */
    public static FactionRelation relation(RegistryAccess access, ResourceKey<Faction> a, ResourceKey<Faction> b) {
        if (a.equals(b)) {
            return FactionRelation.ALLY;
        }
        Registry<Faction> registry = access.registryOrThrow(ModDataRegistries.FACTION);
        Faction fa = registry.get(a);
        Faction fb = registry.get(b);
        FactionRelation ra = fa == null ? null : fa.relations().get(b);
        FactionRelation rb = fb == null ? null : fb.relations().get(a);
        if (ra == null && rb == null) {
            return FactionRelation.NEUTRAL;
        }
        if (ra == null) {
            return rb;
        }
        if (rb == null) {
            return ra;
        }
        return ra.ordinal() >= rb.ordinal() ? ra : rb;
    }

    /** The faction an entity belongs to: its {@link FactionMember} faction, else the entity-type tag fallback. */
    public static Optional<ResourceKey<Faction>> factionOf(Entity entity) {
        if (entity instanceof FactionMember member) {
            return Optional.of(member.faction());
        }
        if (entity instanceof Player) {
            return Optional.empty();
        }
        return TAG_CACHE.computeIfAbsent(entity.getType(), type -> tagFaction(entity));
    }

    private static Optional<ResourceKey<Faction>> tagFaction(Entity entity) {
        Registry<Faction> registry = entity.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        List<ResourceKey<Faction>> matches = new ArrayList<>();
        for (ResourceKey<Faction> key : registry.registryKeySet()) {
            if (entity.getType().is(ModTags.EntityTypes.faction(key))) {
                matches.add(key);
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            matches.sort(Comparator.comparingInt((ResourceKey<Faction> k) -> registry.get(k).sortOrder()));
            LOTRRevival.LOGGER.warn(
                    "Entity type {} is in several faction tags {}; using {}",
                    EntityType.getKey(entity.getType()),
                    matches.stream().map(k -> k.location().toString()).toList(),
                    matches.get(0).location());
        }
        return Optional.of(matches.get(0));
    }

    /** Deterministic part of AI targeting. NPC rules (provocation, witnesses, hired units) layer on top. */
    public static boolean isHostile(LivingEntity attacker, LivingEntity target) {
        Optional<ResourceKey<Faction>> attackerFaction = factionOf(attacker);
        if (attackerFaction.isEmpty()) {
            return false;
        }
        RegistryAccess access = attacker.level().registryAccess();
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            Faction faction = access.registryOrThrow(ModDataRegistries.FACTION).get(attackerFaction.get());
            return faction != null && Alignment.get(player, attackerFaction.get()) < faction.hostilityThreshold();
        }
        Optional<ResourceKey<Faction>> targetFaction = factionOf(target);
        if (targetFaction.isEmpty()) {
            return isUtumno(attackerFaction.get());
        }
        if (attackerFaction.get().equals(targetFaction.get())) {
            return false;
        }
        if (isUtumno(attackerFaction.get()) || isUtumno(targetFaction.get())) {
            return true;
        }
        return relation(access, attackerFaction.get(), targetFaction.get()).isBad();
    }

    private static boolean isUtumno(ResourceKey<Faction> faction) {
        return faction.equals(Factions.UTUMNO);
    }

    /**
     * Chance that a faction NPC attacks the player this check: 1 below the hostility threshold, the grey-zone chance
     * between threshold and ceiling, else 0. Uses the NPC's own suspicion override when it has one.
     */
    public static float hostilityChance(LivingEntity npc, Player player) {
        Optional<ResourceKey<Faction>> factionKey = factionOf(npc);
        if (factionKey.isEmpty() || player.isCreative() || player.isSpectator()) {
            return 0.0f;
        }
        Faction faction = npc.level()
                .registryAccess()
                .registryOrThrow(ModDataRegistries.FACTION)
                .get(factionKey.get());
        if (faction == null) {
            return 0.0f;
        }
        float alignment = Alignment.get(player, factionKey.get());
        float threshold = faction.hostilityThreshold();
        if (alignment < threshold) {
            return 1.0f;
        }
        Optional<Suspicion> suspicion = npc instanceof FactionMember member && member.suspicion().isPresent()
                ? member.suspicion()
                : faction.suspicion();
        if (suspicion.isEmpty() || alignment >= suspicion.get().ceiling()) {
            return 0.0f;
        }
        if (alignment <= 0.0f) {
            return 1.0f;
        }
        return Math.min(1.0f, 1.0f / (suspicion.get().factor() * alignment));
    }
}

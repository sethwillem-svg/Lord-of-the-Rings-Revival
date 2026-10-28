package io.github.sethwillemsvg.lotrrevival.datagen;

import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.ANGMAR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.BLUE_MOUNTAINS;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.BREE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DALE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DOL_GULDUR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DORWINION;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DUNEDAIN_NORTH;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DUNLAND;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DURINS_FOLK;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.EASTERLING;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.FANGORN;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.FAR_HARAD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.GONDOR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.GUNDABAD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HALF_TROLL;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HARAD_JUNGLE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HIGH_ELF;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HOBBIT;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HOSTILE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HOSTILE_TREES;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.ISENGARD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.LOTHLORIEN;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.MORDOR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.NEAR_HARAD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.ROHAN;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.RUFFIANS;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.UNALIGNED;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.UTUMNO;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.WOOD_ELF;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionKind;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRegion;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.faction.Suspicion;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;

/**
 * The faction tables from specs/core/core-apis.md as Java data. Relations are declared once per pair and
 * written symmetrically into both factions. The static initializer validates the tables and fails the build
 * (runData and class loading) if a pair is declared twice with different levels, refers to an unknown
 * faction, or the resulting relation maps are not symmetric.
 */
final class FactionData {
    private FactionData() {}

    private static final List<FactionRank> LADDER_A = FactionRank.ladder(10, 50, 100, 200, 500, 1000, 1500, 3000);
    private static final List<FactionRank> LADDER_B = FactionRank.ladder(10, 50, 100, 200, 500, 1000, 2000, 3000);
    private static final List<FactionRank> LADDER_C = FactionRank.ladder(10, 50, 100, 200, 500, 1000, 2000);
    private static final List<FactionRank> LADDER_D = FactionRank.ladder(10, 100, 250, 500, 1000, 2000, 3000);
    private static final List<FactionRank> LADDER_E = FactionRank.ladder(50, 100, 200, 500, 1000, 2000, 3000);
    private static final List<FactionRank> LADDER_F = FactionRank.ladder(10, 50, 100, 250, 500, 1000, 2000);
    private static final List<FactionRank> LADDER_G = FactionRank.ladder(10, 50, 100, 250, 500, 1000, 3000);
    private static final List<FactionRank> LADDER_H = FactionRank.ladder(10, 50, 100, 200, 500, 1000, 3000);
    private static final List<FactionRank> LADDER_I = FactionRank.ladder(10, 50, 100, 250, 500);
    private static final List<FactionRank> NO_RANKS = List.of();

    /** Per-faction static definition (everything except relations). */
    record Def(
            ResourceKey<Faction> key,
            FactionRegion region,
            String color,
            List<FactionKind> kinds,
            boolean disapprovesCivilianKills,
            List<FactionRank> ranks,
            boolean isolationist,
            boolean hidden,
            float hostilityThreshold,
            Optional<Suspicion> suspicion,
            Optional<Float> fixedAlignment) {}

    static final List<Def> DEFS = new ArrayList<>();
    static final Map<ResourceKey<Faction>, Integer> SORT_ORDER = new LinkedHashMap<>();

    private static void real(
            ResourceKey<Faction> key,
            FactionRegion region,
            String color,
            boolean disapproves,
            List<FactionRank> ranks,
            FactionKind... kinds) {
        DEFS.add(new Def(
                key, region, color, List.of(kinds), disapproves, ranks, false, false, 0.0f, Optional.empty(),
                Optional.empty()));
    }

    static {
        FactionRegion w = FactionRegion.WESTLANDS;
        FactionKind free = FactionKind.FREE;
        FactionKind men = FactionKind.MEN;
        FactionKind elf = FactionKind.ELF;
        FactionKind dwarf = FactionKind.DWARF;
        FactionKind orc = FactionKind.ORC;
        FactionKind troll = FactionKind.TROLL;
        real(HOBBIT, w, "#6FA84A", true, LADDER_D, free, men);
        real(BREE, w, "#A8794A", true, LADDER_C, free, men);
        real(DUNEDAIN_NORTH, w, "#5C7C6B", true, LADDER_C, free, men);
        real(BLUE_MOUNTAINS, w, "#4F6FA8", true, LADDER_A, free, dwarf);
        real(HIGH_ELF, w, "#8FB8D9", true, LADDER_B, free, elf);
        real(GUNDABAD, w, "#6B5E52", false, LADDER_C, orc);
        real(ANGMAR, w, "#5A6B7A", false, LADDER_C, orc, troll);
        real(WOOD_ELF, w, "#3F8F5A", true, LADDER_E, free, elf);
        real(DOL_GULDUR, w, "#4A4F3A", false, LADDER_C, orc);
        real(DALE, w, "#B58A3C", true, LADDER_C, free, men);
        real(DURINS_FOLK, w, "#8A5A3C", true, LADDER_A, free, dwarf);
        real(LOTHLORIEN, w, "#D9C46A", true, LADDER_B, free, elf);
        real(DUNLAND, w, "#7A4F4F", false, LADDER_C, men);
        real(ISENGARD, w, "#E8E8E8", false, LADDER_A, orc);
        real(FANGORN, w, "#2F6B3A", true, LADDER_I, free, FactionKind.TREE);
        real(ROHAN, w, "#7BA83C", true, LADDER_F, free, men);
        real(GONDOR, w, "#3D6FB5", true, LADDER_A, free, men);
        DEFS.add(new Def(
                MORDOR, w, "#A01E1E", List.of(orc), false, LADDER_A, false, false, 0.0f,
                Optional.of(new Suspicion(100.0f, 5.0f)), Optional.empty()));
        FactionRegion rhun = FactionRegion.RHUN;
        real(DORWINION, rhun, "#8A4FA8", true, LADDER_A, free, men, elf);
        real(EASTERLING, rhun, "#B5562E", true, LADDER_A, men);
        FactionRegion harad = FactionRegion.HARAD;
        real(NEAR_HARAD, harad, "#C9A03C", true, LADDER_A, men);
        real(FAR_HARAD, harad, "#6B2E2E", false, LADDER_G, men);
        real(HARAD_JUNGLE, harad, "#2E8F7A", false, LADDER_H, free, men);
        real(HALF_TROLL, harad, "#7A6B3C", false, LADDER_C, men, troll);

        // Wood elves distrust anyone below their first rank (50).
        for (int n = 0; n < DEFS.size(); n++) {
            Def d = DEFS.get(n);
            if (d.key().equals(WOOD_ELF)) {
                DEFS.set(n, new Def(
                        d.key(), d.region(), d.color(), d.kinds(), d.disapprovesCivilianKills(), d.ranks(),
                        false, false, 50.0f, Optional.empty(), Optional.empty()));
            }
        }
        // Isolationist flag.
        for (int n = 0; n < DEFS.size(); n++) {
            Def d = DEFS.get(n);
            if (d.key().equals(HOBBIT) || d.key().equals(FANGORN)) {
                DEFS.set(n, new Def(
                        d.key(), d.region(), d.color(), d.kinds(), d.disapprovesCivilianKills(), d.ranks(),
                        true, d.hidden(), d.hostilityThreshold(), d.suspicion(), d.fixedAlignment()));
            }
        }

        // Utumno: hidden, fixed alignment, never pledgeable. No ranks (alignment never positive).
        DEFS.add(new Def(
                UTUMNO, FactionRegion.DEEPS, "#1A1A1A", List.of(orc), false, NO_RANKS, false, true, 0.0f,
                Optional.empty(), Optional.of(-66666.0f)));
        // Hidden pseudo-factions.
        pseudo(HOSTILE, "#000000", 0.0f);
        pseudo(UNALIGNED, "#808080", 0.0f);
        pseudo(HOSTILE_TREES, "#1F3F1F", -1.0f, FactionKind.TREE);
        pseudo(RUFFIANS, "#5A4A3A", 0.0f, FactionKind.MEN);

        int sort = 0;
        for (Def d : DEFS) {
            sort += 10;
            SORT_ORDER.put(d.key(), sort);
        }
    }

    private static void pseudo(ResourceKey<Faction> key, String color, float fixed, FactionKind... kinds) {
        DEFS.add(new Def(
                key, FactionRegion.DEEPS, color, List.of(kinds), false, NO_RANKS, false, true, 0.0f,
                Optional.empty(), Optional.of(fixed)));
    }

    // ---- Relations ----

    private static final Map<ResourceKey<Faction>, Map<ResourceKey<Faction>, FactionRelation>> RELATIONS =
            new LinkedHashMap<>();
    /** Unordered pair -> level, to catch duplicate or conflicting declarations. */
    private static final Map<Set<ResourceKey<Faction>>, FactionRelation> PAIRS = new LinkedHashMap<>();

    @SafeVarargs
    private static void pair(FactionRelation level, ResourceKey<Faction> a, ResourceKey<Faction>... others) {
        for (ResourceKey<Faction> b : others) {
            if (a.equals(b)) {
                throw new IllegalStateException("Faction related to itself: " + a.location());
            }
            FactionRelation previous = PAIRS.put(Set.of(a, b), level);
            if (previous != null) {
                throw new IllegalStateException(
                        "Relation declared twice: " + a.location() + " / " + b.location() + " (" + previous + ", "
                                + level + ")");
            }
            RELATIONS.computeIfAbsent(a, k -> new LinkedHashMap<>()).put(b, level);
            RELATIONS.computeIfAbsent(b, k -> new LinkedHashMap<>()).put(a, level);
        }
    }

    @SafeVarargs
    private static void ally(ResourceKey<Faction> a, ResourceKey<Faction>... b) {
        pair(FactionRelation.ALLY, a, b);
    }

    @SafeVarargs
    private static void friend(ResourceKey<Faction> a, ResourceKey<Faction>... b) {
        pair(FactionRelation.FRIEND, a, b);
    }

    @SafeVarargs
    private static void enemy(ResourceKey<Faction> a, ResourceKey<Faction>... b) {
        pair(FactionRelation.ENEMY, a, b);
    }

    @SafeVarargs
    private static void mortal(ResourceKey<Faction> a, ResourceKey<Faction>... b) {
        pair(FactionRelation.MORTAL_ENEMY, a, b);
    }

    /** Number of declared pairs not involving utumno or a pseudo-faction (spec: 152). */
    static final int REAL_PAIR_COUNT;
    /** Number of utumno pairs (spec: 24). */
    static final int UTUMNO_PAIR_COUNT;

    static {
        // Ally (22)
        ally(HOBBIT, BREE, DUNEDAIN_NORTH, BLUE_MOUNTAINS, DALE, DURINS_FOLK);
        ally(DUNEDAIN_NORTH, HIGH_ELF, LOTHLORIEN, GONDOR);
        ally(BLUE_MOUNTAINS, DURINS_FOLK);
        ally(HIGH_ELF, LOTHLORIEN, FANGORN);
        ally(ANGMAR, DOL_GULDUR, MORDOR);
        ally(WOOD_ELF, LOTHLORIEN, FANGORN, DORWINION);
        ally(DOL_GULDUR, MORDOR);
        ally(DALE, DURINS_FOLK);
        ally(LOTHLORIEN, FANGORN);
        ally(FANGORN, HARAD_JUNGLE);
        ally(ROHAN, GONDOR);
        ally(MORDOR, HALF_TROLL);
        // Friend (29)
        friend(HOBBIT, HIGH_ELF, WOOD_ELF, LOTHLORIEN, ROHAN, GONDOR);
        friend(BREE, DUNEDAIN_NORTH, BLUE_MOUNTAINS, HIGH_ELF, WOOD_ELF, DALE, DURINS_FOLK, LOTHLORIEN);
        friend(DUNEDAIN_NORTH, WOOD_ELF, ROHAN);
        friend(HIGH_ELF, WOOD_ELF, GONDOR);
        friend(GUNDABAD, ANGMAR, DOL_GULDUR, MORDOR);
        friend(DALE, ROHAN, GONDOR);
        friend(DURINS_FOLK, DUNLAND);
        friend(DUNLAND, ISENGARD);
        friend(ISENGARD, HALF_TROLL);
        friend(MORDOR, EASTERLING, NEAR_HARAD, FAR_HARAD);
        friend(NEAR_HARAD, FAR_HARAD, HALF_TROLL);
        // Enemy (16)
        enemy(DUNEDAIN_NORTH, DUNLAND, EASTERLING, NEAR_HARAD, FAR_HARAD);
        enemy(HIGH_ELF, EASTERLING, NEAR_HARAD);
        enemy(WOOD_ELF, EASTERLING, NEAR_HARAD);
        enemy(DALE, EASTERLING);
        enemy(LOTHLORIEN, EASTERLING, NEAR_HARAD);
        enemy(DUNLAND, GONDOR);
        enemy(ROHAN, NEAR_HARAD, FAR_HARAD);
        enemy(GONDOR, FAR_HARAD);
        enemy(NEAR_HARAD, HARAD_JUNGLE);
        // Mortal enemy: 13 free factions x 6 orc-side factions (78)
        List<ResourceKey<Faction>> freeSide = List.of(
                HOBBIT, BREE, DUNEDAIN_NORTH, BLUE_MOUNTAINS, HIGH_ELF, WOOD_ELF, DALE, DURINS_FOLK, LOTHLORIEN,
                FANGORN, ROHAN, GONDOR, DORWINION);
        List<ResourceKey<Faction>> orcSide = List.of(GUNDABAD, ANGMAR, DOL_GULDUR, ISENGARD, MORDOR, HALF_TROLL);
        for (ResourceKey<Faction> f : freeSide) {
            for (ResourceKey<Faction> o : orcSide) {
                mortal(f, o);
            }
        }
        // Plus 7 pairs
        mortal(DUNLAND, ROHAN);
        mortal(ROHAN, EASTERLING);
        mortal(GONDOR, EASTERLING);
        mortal(GONDOR, NEAR_HARAD);
        mortal(MORDOR, HARAD_JUNGLE);
        mortal(FAR_HARAD, HARAD_JUNGLE);
        mortal(HARAD_JUNGLE, HALF_TROLL);
        REAL_PAIR_COUNT = PAIRS.size();

        // Utumno is a mortal enemy of the 24 other real factions.
        for (ResourceKey<Faction> f : Factions.REAL) {
            if (!f.equals(UTUMNO)) {
                mortal(UTUMNO, f);
            }
        }
        UTUMNO_PAIR_COUNT = PAIRS.size() - REAL_PAIR_COUNT;

        // Pseudo-factions.
        // hostile: mortal enemy of every real faction (generic monsters).
        for (ResourceKey<Faction> f : Factions.REAL) {
            mortal(HOSTILE, f);
        }
        // hostile trees: mortal enemy of the hobbit, bree and dunedain_north.
        mortal(HOSTILE_TREES, HOBBIT, BREE, DUNEDAIN_NORTH);
        // ruffians: troublemakers, enemies of the Bree-land settlers.
        enemy(RUFFIANS, BREE, HOBBIT);

        validate();
    }

    private static void validate() {
        Set<ResourceKey<Faction>> known = new HashSet<>();
        for (Def d : DEFS) {
            if (!known.add(d.key())) {
                throw new IllegalStateException("Duplicate faction " + d.key().location());
            }
        }
        if (Factions.REAL.size() + Factions.PSEUDO.size() != DEFS.size()) {
            throw new IllegalStateException("Factions constants and datagen table disagree");
        }
        for (ResourceKey<Faction> k : Factions.REAL) {
            if (!known.contains(k)) {
                throw new IllegalStateException("Missing def for " + k.location());
            }
        }
        for (ResourceKey<Faction> k : Factions.PSEUDO) {
            if (!known.contains(k)) {
                throw new IllegalStateException("Missing def for " + k.location());
            }
        }
        for (Map.Entry<ResourceKey<Faction>, Map<ResourceKey<Faction>, FactionRelation>> e : RELATIONS.entrySet()) {
            if (!known.contains(e.getKey())) {
                throw new IllegalStateException("Relation for unknown faction " + e.getKey().location());
            }
            for (Map.Entry<ResourceKey<Faction>, FactionRelation> r : e.getValue().entrySet()) {
                if (!known.contains(r.getKey())) {
                    throw new IllegalStateException("Relation to unknown faction " + r.getKey().location());
                }
                FactionRelation back = RELATIONS.getOrDefault(r.getKey(), Map.of()).get(e.getKey());
                if (back != r.getValue()) {
                    throw new IllegalStateException("Asymmetric relation " + e.getKey().location() + " -> "
                            + r.getKey().location());
                }
            }
        }
        if (REAL_PAIR_COUNT != 152) {
            throw new IllegalStateException("Expected 152 relation pairs, found " + REAL_PAIR_COUNT);
        }
        if (UTUMNO_PAIR_COUNT != 24) {
            throw new IllegalStateException("Expected 24 utumno pairs, found " + UTUMNO_PAIR_COUNT);
        }
        EnumMap<FactionRelation, Integer> counts = new EnumMap<>(FactionRelation.class);
        for (Map.Entry<Set<ResourceKey<Faction>>, FactionRelation> e : PAIRS.entrySet()) {
            if (!e.getKey().contains(UTUMNO)
                    && Factions.PSEUDO.stream().noneMatch(e.getKey()::contains)) {
                counts.merge(e.getValue(), 1, Integer::sum);
            }
        }
        if (counts.getOrDefault(FactionRelation.ALLY, 0) != 22
                || counts.getOrDefault(FactionRelation.FRIEND, 0) != 29
                || counts.getOrDefault(FactionRelation.ENEMY, 0) != 16
                || counts.getOrDefault(FactionRelation.MORTAL_ENEMY, 0) != 85) {
            throw new IllegalStateException("Relation counts do not match the spec: " + counts);
        }
    }

    /** Builds the registry entry for a faction, with its relations written symmetrically. */
    static Faction build(Def d) {
        Map<ResourceKey<Faction>, FactionRelation> relations =
                new LinkedHashMap<>(RELATIONS.getOrDefault(d.key(), Map.of()));
        Optional<String> pledge = Optional.empty();
        if (d.fixedAlignment().isEmpty()) {
            pledge = d.ranks().stream()
                    .filter(r -> r.threshold() == 100.0f)
                    .map(FactionRank::id)
                    .findFirst();
        }
        return new Faction(
                d.region(),
                Integer.parseInt(d.color().substring(1), 16),
                SORT_ORDER.get(d.key()),
                d.hidden(),
                d.hostilityThreshold(),
                d.suspicion(),
                d.disapprovesCivilianKills(),
                d.fixedAlignment(),
                pledge,
                d.ranks(),
                relations,
                d.kinds());
    }
}

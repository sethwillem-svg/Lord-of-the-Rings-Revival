package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Faction registry: contents, symmetry, hidden pseudo-factions, tags. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionRegistryTest {
    private static Registry<Faction> registry(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
    }

    @GameTest(template = "empty")
    public static void registryHasAllFactions(GameTestHelper helper) {
        Registry<Faction> registry = registry(helper);
        int visible = 0;
        for (ResourceKey<Faction> key : registry.registryKeySet()) {
            if (!registry.getOrThrow(key).hidden()) {
                visible++;
            }
        }
        helper.assertTrue(visible == 24, "expected 24 visible factions, got " + visible);
        for (ResourceKey<Faction> key : Factions.REAL) {
            helper.assertTrue(registry.containsKey(key), "missing faction " + key.location());
        }
        for (ResourceKey<Faction> key : Factions.PSEUDO) {
            helper.assertTrue(registry.containsKey(key), "missing pseudo-faction " + key.location());
        }
        helper.assertTrue(
                registry.size() == Factions.REAL.size() + Factions.PSEUDO.size(),
                "unexpected registry size " + registry.size());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relationsAreSymmetricAndReferenceExistingFactions(GameTestHelper helper) {
        Registry<Faction> registry = registry(helper);
        for (ResourceKey<Faction> a : registry.registryKeySet()) {
            Faction fa = registry.getOrThrow(a);
            for (var entry : fa.relations().entrySet()) {
                ResourceKey<Faction> b = entry.getKey();
                helper.assertTrue(registry.containsKey(b), a.location() + " relates to unknown " + b.location());
                helper.assertTrue(!a.equals(b), a.location() + " relates to itself");
                helper.assertTrue(
                        registry.getOrThrow(b).relationTo(a) == entry.getValue(),
                        "asymmetric relation " + a.location() + " / " + b.location());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pseudoFactionsAreHiddenAndUnpledgeable(GameTestHelper helper) {
        Registry<Faction> registry = registry(helper);
        for (ResourceKey<Faction> key : Factions.PSEUDO) {
            Faction f = registry.getOrThrow(key);
            helper.assertTrue(f.hidden(), key.location() + " should be hidden");
            helper.assertTrue(f.ranks().isEmpty(), key.location() + " should have no ranks");
            helper.assertTrue(!f.isPledgeable(), key.location() + " should not be pledgeable");
            helper.assertTrue(f.fixedAlignment().isPresent(), key.location() + " should have fixed alignment");
        }
        helper.assertTrue(!registry.getOrThrow(Factions.UTUMNO).isPledgeable(), "utumno pledgeable");
        helper.assertTrue(registry.getOrThrow(Factions.UTUMNO).hidden(), "utumno not hidden");
        helper.assertTrue(registry.getOrThrow(Factions.GONDOR).isPledgeable(), "gondor not pledgeable");
        helper.assertTrue(
                "r3".equals(registry.getOrThrow(Factions.GONDOR).pledgeRank().orElse(null)), "gondor pledge rank");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sampleRelationsAndOverrides(GameTestHelper helper) {
        Registry<Faction> registry = registry(helper);
        helper.assertTrue(
                Factions.relation(registry, Factions.GONDOR, Factions.MORDOR) == FactionRelation.MORTAL_ENEMY,
                "gondor vs mordor");
        helper.assertTrue(
                Factions.relation(registry, Factions.MORDOR, Factions.GONDOR) == FactionRelation.MORTAL_ENEMY,
                "mordor vs gondor");
        helper.assertTrue(
                Factions.relation(registry, Factions.GONDOR, Factions.ROHAN) == FactionRelation.ALLY, "gondor rohan");
        helper.assertTrue(
                Factions.relation(registry, Factions.GONDOR, Factions.LOTHLORIEN) == FactionRelation.NEUTRAL,
                "unlisted pair should be neutral");
        helper.assertTrue(
                Factions.relation(registry, Factions.GONDOR, Factions.GONDOR) == FactionRelation.ALLY, "self ally");
        helper.assertTrue(
                Factions.relation(registry, Factions.HOSTILE_TREES, Factions.HOBBIT) == FactionRelation.MORTAL_ENEMY,
                "trees vs hobbit");
        helper.assertTrue(
                Factions.relation(registry, Factions.UNALIGNED, Factions.GONDOR) == FactionRelation.NEUTRAL,
                "unaligned neutral");
        Faction mordor = registry.getOrThrow(Factions.MORDOR);
        helper.assertTrue(
                mordor.suspicion().isPresent() && mordor.suspicion().get().ceiling() == 100.0f
                        && mordor.suspicion().get().factor() == 5.0f,
                "mordor suspicion");
        helper.assertTrue(registry.getOrThrow(Factions.WOOD_ELF).hostilityThreshold() == 50.0f, "wood elf threshold");
        helper.assertTrue(registry.getOrThrow(Factions.UTUMNO).fixedAlignment().orElse(0f) == -66666.0f, "utumno");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factionTagsLoaded(GameTestHelper helper) {
        Registry<Faction> registry = registry(helper);
        helper.assertTrue(
                registry.getHolderOrThrow(Factions.HOBBIT).is(ModTags.Factions.ISOLATIONIST),
                "hobbit not isolationist");
        helper.assertTrue(registry.getHolderOrThrow(Factions.UTUMNO).is(ModTags.Factions.HIDDEN), "utumno not hidden");
        helper.succeed();
    }
}

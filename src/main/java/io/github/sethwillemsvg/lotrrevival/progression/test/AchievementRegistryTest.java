package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.RankRequirement;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Relation;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Requirement;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementRow;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementTable;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Achievement registry, backing advancements and the datagen validation rules. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AchievementRegistryTest {
    private static final Function<ResourceKey<Faction>, Optional<List<FactionRank>>> LADDERS =
            faction -> Optional.of(FactionRank.LADDER_A);

    private static Set<String> tabIds() {
        Set<String> ids = new java.util.HashSet<>();
        for (AchievementTab tab : AchievementTab.values()) {
            ids.add(tab.id());
        }
        return ids;
    }

    @GameTest(template = "empty")
    public static void registryAndAdvancementsMatch(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Registry<AchievementEntry> registry = server.registryAccess().registryOrThrow(AchievementEntry.REGISTRY);
        helper.assertTrue(registry.size() >= 6, "first content present, got " + registry.size());
        for (ResourceLocation id : registry.keySet()) {
            AdvancementHolder holder = server.getAdvancements().get(id);
            helper.assertTrue(holder != null, "advancement for " + id);
            helper.assertTrue(holder.value().display().isEmpty(), "no display for " + id);
        }
        Set<String> tabs = tabIds();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            ResourceLocation id = holder.id();
            if (!id.getNamespace().equals(LOTRRevival.MOD_ID)) {
                continue;
            }
            int slash = id.getPath().indexOf('/');
            if (slash > 0 && tabs.contains(id.getPath().substring(0, slash))) {
                helper.assertTrue(registry.containsKey(id), "display entry for " + id);
            }
        }
        helper.assertTrue(
                registry.containsKey(LOTRRevival.id("general/enter_middle_earth")), "enter_middle_earth entry");
        helper.assertTrue(registry.containsKey(LOTRRevival.id("general/explorer_50")), "explorer_50 entry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entryCodecRoundTripsAndRejectsRequiresWithRank(GameTestHelper helper) {
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        for (AchievementRow row : AchievementTable.rows()) {
            JsonElement json = AchievementEntry.CODEC.encodeStart(ops, row.toEntry()).getOrThrow();
            AchievementEntry back = AchievementEntry.CODEC.parse(ops, json).getOrThrow();
            helper.assertTrue(back.tab() == row.tab(), "tab round trip " + row.id());
            helper.assertTrue(back.counted(), "counted defaults true " + row.id());
        }
        AchievementEntry both = new AchievementEntry(
                AchievementTab.GENERAL,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW),
                net.minecraft.network.chat.Component.literal("t"),
                net.minecraft.network.chat.Component.literal("d"),
                AchievementEntry.SortGroup.OTHER,
                List.of(new Requirement(Relation.ENEMY, List.of(Factions.GONDOR))),
                Optional.of(new RankRequirement(Factions.GONDOR, "r1")),
                true);
        helper.assertTrue(AchievementEntry.CODEC.encodeStart(ops, both).result().isEmpty(), "both rejected");
        helper.succeed();
    }

    private static AchievementRow good(GameTestHelper helper) {
        return AchievementTable.rows().get(0);
    }

    private static List<String> check(
            List<AchievementRow> rows,
            Map<ResourceLocation, AchievementEntry> entries,
            Map<ResourceLocation, Advancement> advancements) {
        return AchievementValidator.collect(rows, entries, advancements, LADDERS);
    }

    private static List<String> checkRows(List<AchievementRow> rows) {
        Map<ResourceLocation, AchievementEntry> entries = new LinkedHashMap<>();
        Map<ResourceLocation, Advancement> advancements = new LinkedHashMap<>();
        for (AchievementRow row : rows) {
            entries.put(row.id(), row.toEntry());
            advancements.put(row.id(), row.toAdvancement());
        }
        return check(rows, entries, advancements);
    }

    private static void rejects(GameTestHelper helper, String what, List<String> errors, String fragment) {
        helper.assertTrue(
                errors.stream().anyMatch(e -> e.contains(fragment)), what + " must report '" + fragment + "': " + errors);
    }

    @GameTest(template = "empty")
    public static void validatorAcceptsTable(GameTestHelper helper) {
        List<String> errors = checkRows(AchievementTable.rows());
        helper.assertTrue(errors.isEmpty(), "table is valid: " + errors);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validatorRejectsBadRows(GameTestHelper helper) {
        AchievementRow good = good(helper);

        rejects(helper, "bad id", checkRows(List.of(good.withId(LOTRRevival.id("general/bad-name")))), "snake_case");
        rejects(helper, "no slash", checkRows(List.of(good.withId(LOTRRevival.id("flat")))), "snake_case");
        rejects(
                helper,
                "tab mismatch",
                checkRows(List.of(good.withTab(AchievementTab.COMBAT))),
                "differs from the id prefix");

        List<AchievementRow> rows = List.of(good);
        Map<ResourceLocation, AchievementEntry> entries = new LinkedHashMap<>();
        entries.put(good.id(), good.toEntry());
        entries.put(LOTRRevival.id("general/orphan_entry"), good.toEntry());
        Map<ResourceLocation, Advancement> advancements = new LinkedHashMap<>();
        advancements.put(good.id(), good.toAdvancement());
        rejects(helper, "entry without advancement", check(rows, entries, advancements), "no backing advancement");

        entries.remove(LOTRRevival.id("general/orphan_entry"));
        advancements.put(LOTRRevival.id("general/orphan_advancement"), good.toAdvancement());
        rejects(helper, "advancement without entry", check(rows, entries, advancements), "no display entry");

        rejects(
                helper,
                "shared lang key",
                checkRows(List.of(good, good)),
                "is shared with another row");

        rejects(
                helper,
                "unknown faction in requires",
                checkRows(List.of(good.withRequires(List.of(new Requirement(
                        Relation.ALLY,
                        List.of(ResourceKey.create(ModDataRegistries.FACTION, LOTRRevival.id("nowhere")))))))),
                "unknown faction");

        rejects(
                helper,
                "rank not in ladder",
                checkRows(List.of(good.withRank(new RankRequirement(Factions.GONDOR, "r99")))),
                "not in the ladder");

        List<AchievementRow> single = List.of(good);
        Map<ResourceLocation, AchievementEntry> one = new LinkedHashMap<>();
        one.put(good.id(), good.toEntry());
        Map<ResourceLocation, Advancement> displayed = new LinkedHashMap<>();
        Advancement plain = good.toAdvancement();
        Advancement withDisplay = new Advancement(
                plain.parent(),
                Optional.of(new net.minecraft.advancements.DisplayInfo(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW),
                        net.minecraft.network.chat.Component.literal("t"),
                        net.minecraft.network.chat.Component.literal("d"),
                        Optional.empty(),
                        net.minecraft.advancements.AdvancementType.TASK,
                        true,
                        true,
                        false)),
                plain.rewards(),
                plain.criteria(),
                plain.requirements(),
                plain.sendsTelemetryEvent(),
                plain.name());
        displayed.put(good.id(), withDisplay);
        rejects(helper, "display block", check(single, one, displayed), "must not have a display");

        List<String> unused = new ArrayList<>();
        helper.assertTrue(unused.isEmpty(), "sanity");
        helper.succeed();
    }
}

package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.faction.Suspicion;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcGender;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcStanding;
import io.github.sethwillemsvg.lotrrevival.npc.name.NpcNames;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeechEvent;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeechLang;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Name banks, nameplate colour, speech and the interaction hooks. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcNamesSpeechTest {
    private NpcNamesSpeechTest() {}

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "npc-tester"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Spawns like a summon does: placed, then finalized (which rolls gender, role and name). */
    private static HobbitEntity spawnHobbit(GameTestHelper helper, int x) {
        HobbitEntity hobbit = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(x, 1, 2));
        hobbit.finalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(hobbit.blockPosition()),
                MobSpawnType.COMMAND,
                null);
        return hobbit;
    }

    private static HobbitEntity innkeeper(GameTestHelper helper) {
        HobbitEntity hobbit = spawnHobbit(helper, 2);
        hobbit.setNoAi(true);
        hobbit.setRole(NpcRole.INNKEEPER);
        return hobbit;
    }

    private static void click(HobbitEntity npc, ServerPlayer player) {
        npc.interact(player, InteractionHand.MAIN_HAND);
    }

    @SuppressWarnings("removal")
    private static <E extends net.neoforged.bus.api.Event> Consumer<E> listen(Class<E> type, Consumer<E> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
        return listener;
    }

    // ------------------------------------------------------------------ names

    @GameTest(template = "empty")
    public static void nameBanksAreLargeAndDistinct(GameTestHelper helper) {
        for (NpcRace race : NpcRace.values()) {
            for (NpcGender gender : NpcGender.values()) {
                List<String> first = NpcNames.bank(NpcNames.firstBankName(race, gender));
                helper.assertTrue(first.size() >= 40, race.id() + " " + gender + " first names: " + first.size());
                helper.assertTrue(new HashSet<>(first).size() == first.size(), "duplicate first names in " + race.id());
            }
            List<String> family = NpcNames.bank(NpcNames.familyBankName(race));
            helper.assertTrue(family.size() >= 30, race.id() + " family names: " + family.size());
            helper.assertTrue(new HashSet<>(family).size() == family.size(), "duplicate family names in " + race.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnRollsNameOnceAndItPersists(GameTestHelper helper) {
        HobbitEntity hobbit = spawnHobbit(helper, 2);
        hobbit.setNoAi(true);
        String name = hobbit.getNpcName();
        helper.assertTrue(name.split(" ").length == 2, "expected 'First Family', got '" + name + "'");

        hobbit.finalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(hobbit.blockPosition()),
                MobSpawnType.COMMAND,
                null);
        helper.assertTrue(name.equals(hobbit.getNpcName()), "name must not be re-rolled, now '" + hobbit.getNpcName() + "'");

        CompoundTag tag = hobbit.saveWithoutId(new CompoundTag());
        HobbitEntity loaded = NpcEntityTypes.HOBBIT.get().create(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(name.equals(loaded.getNpcName()), "name after load: " + loaded.getNpcName());
        helper.assertTrue(loaded.speakerName().getString().equals(name), "speaker name " + loaded.speakerName().getString());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void titledNameForRolesAndNoneForVillager(GameTestHelper helper) {
        HobbitEntity hobbit = spawnHobbit(helper, 2);
        hobbit.setNoAi(true);
        hobbit.setRole(NpcRole.VILLAGER);
        helper.assertTrue(hobbit.getName().getString().equals(hobbit.getNpcName()), "villager has no title");
        hobbit.setRole(NpcRole.FARMER);
        helper.assertTrue(
                hobbit.getName().getContents() instanceof TranslatableContents tc
                        && tc.getKey().equals("npc.lotrrevival.titled_name"),
                "farmer name is a titled template: " + hobbit.getName());
        hobbit.setCustomName(net.minecraft.network.chat.Component.literal("Tagged"));
        helper.assertTrue(hobbit.getName().getString().equals("Tagged"), "name tag overrides");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyNameBankShowsRaceNameAndWarnsOnce(GameTestHelper helper) {
        Map<String, List<String>> saved = NpcNames.snapshot();
        try {
            NpcNames.replaceBanks(Map.of("hobbit_male_first", List.of(), "hobbit_female_first", List.of()));
            int before = NpcNames.warningsLogged();
            HobbitEntity a = spawnHobbit(helper, 2);
            HobbitEntity b = spawnHobbit(helper, 3);
            for (HobbitEntity hobbit : List.of(a, b)) {
                hobbit.setNoAi(true);
                hobbit.setRole(NpcRole.VILLAGER);
                helper.assertTrue(hobbit.getNpcName().isEmpty(), "no generated name, got '" + hobbit.getNpcName() + "'");
                helper.assertTrue(
                        hobbit.getName().getContents() instanceof TranslatableContents tc
                                && tc.getKey().equals("npc.lotrrevival.race.hobbit"),
                        "displays the race name: " + hobbit.getName());
            }
            helper.assertTrue(NpcNames.warningsLogged() - before == 1, "one warning, got " + (NpcNames.warningsLogged() - before));
        } finally {
            NpcNames.replaceBanks(saved);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ nameplate colour

    @GameTest(template = "empty")
    public static void nameplateToneFunction(GameTestHelper helper) {
        Faction hobbit = helper.getLevel()
                .registryAccess()
                .registryOrThrow(ModDataRegistries.FACTION)
                .get(Factions.HOBBIT);
        helper.assertTrue(hobbit != null, "hobbit faction");
        helper.assertTrue(NpcStanding.tone(-1, hobbit) == NpcStanding.Tone.HOSTILE, "-1 red");
        helper.assertTrue(NpcStanding.tone(0, hobbit) == NpcStanding.Tone.NEUTRAL, "0 white");
        helper.assertTrue(NpcStanding.tone(5, hobbit) == NpcStanding.Tone.FRIENDLY, "+5 green");
        Optional<Suspicion> grey = Optional.of(new Suspicion(10.0f, 5.0f));
        helper.assertTrue(NpcStanding.tone(5, 0.0f, grey) == NpcStanding.Tone.NEUTRAL, "+5 inside the grey zone is white");
        helper.assertTrue(NpcStanding.tone(10, 0.0f, grey) == NpcStanding.Tone.FRIENDLY, "+10 at the ceiling is green");
        helper.assertTrue(NpcStanding.tone(-1, 0.0f, grey) == NpcStanding.Tone.HOSTILE, "-1 red with a grey zone");
        helper.succeed();
    }

    // ------------------------------------------------------------------ speech

    @GameTest(template = "empty")
    public static void everyCategoryHasAtLeastFourLines(GameTestHelper helper) {
        String[] categories = {
            "friendly", "neutral", "hostile", "night", "flee", "witness", "trader_none", "farmer_friendly",
            "innkeeper_friendly"
        };
        for (NpcRace race : NpcRace.values()) {
            for (String category : categories) {
                helper.assertTrue(NpcSpeechLang.count(race, category) >= 4, race.id() + " " + category);
            }
        }
        helper.assertTrue(NpcSpeechLang.count(NpcRace.HOBBIT, "shirriff_friendly") >= 4, "shirriff lines");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void innkeeperTradesAtZeroAndTalksHostileBelow(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        HobbitEntity npc = innkeeper(helper);
        List<NpcInteractEvent.Kind> kinds = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        Consumer<NpcInteractEvent> interact = listen(NpcInteractEvent.class, e -> {
            if (e.getNpc() == npc) {
                kinds.add(e.getKind());
            }
        });
        Consumer<NpcSpeechEvent> speech = listen(NpcSpeechEvent.class, e -> {
            if (e.getNpc() == npc) {
                categories.add(e.getCategory());
            }
        });
        try {
            Alignment.set(player, Factions.HOBBIT, 0.0f, AlignmentSource.command());
            click(npc, player);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TRADE)), "at 0: " + kinds);
            helper.assertTrue(categories.equals(List.of("trader_none")), "unhandled trade says trader_none: " + categories);

            kinds.clear();
            categories.clear();
            Alignment.set(player, Factions.HOBBIT, -1.0f, AlignmentSource.command());
            helper.runAfterDelay(NpcSpeech.COOLDOWN_TICKS + 1, () -> {
                try {
                    click(npc, player);
                    helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TALK)), "at -1: " + kinds);
                    helper.assertTrue(categories.equals(List.of("hostile")), "at -1 speech: " + categories);
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(interact);
                    NeoForge.EVENT_BUS.unregister(speech);
                }
            });
        } catch (RuntimeException e) {
            NeoForge.EVENT_BUS.unregister(interact);
            NeoForge.EVENT_BUS.unregister(speech);
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void cancelledTradeSaysNothingAndSneakAlwaysTalks(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        HobbitEntity npc = innkeeper(helper);
        List<NpcInteractEvent.Kind> kinds = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        Consumer<NpcInteractEvent> interact = listen(NpcInteractEvent.class, e -> {
            if (e.getNpc() != npc) {
                return;
            }
            kinds.add(e.getKind());
            if (e.getKind() == NpcInteractEvent.Kind.TRADE) {
                e.setCanceled(true);
            }
        });
        Consumer<NpcSpeechEvent> speech = listen(NpcSpeechEvent.class, e -> {
            if (e.getNpc() == npc) {
                categories.add(e.getCategory());
            }
        });
        try {
            Alignment.set(player, Factions.HOBBIT, 0.0f, AlignmentSource.command());
            click(npc, player);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TRADE)), "trade posted: " + kinds);
            helper.assertTrue(categories.isEmpty(), "a handled trade is silent: " + categories);

            player.setShiftKeyDown(true);
            click(npc, player);
            helper.assertTrue(
                    kinds.equals(List.of(NpcInteractEvent.Kind.TRADE, NpcInteractEvent.Kind.TALK)),
                    "sneak talks: " + kinds);
            helper.assertTrue(categories.size() == 1, "one line from sneak talk: " + categories);
        } finally {
            player.setShiftKeyDown(false);
            NeoForge.EVENT_BUS.unregister(interact);
            NeoForge.EVENT_BUS.unregister(speech);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void speechCooldownIsSixtyTicks(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        HobbitEntity npc = spawnHobbit(helper, 2);
        npc.setNoAi(true);
        npc.setRole(NpcRole.VILLAGER);
        List<String> keys = new ArrayList<>();
        Consumer<NpcSpeechEvent> speech = listen(NpcSpeechEvent.class, e -> {
            if (e.getNpc() == npc) {
                keys.add(e.getKey());
            }
        });
        try {
            click(npc, player);
            click(npc, player);
            helper.assertTrue(keys.size() == 1, "two clicks, one line: " + keys);
            helper.runAfterDelay(NpcSpeech.COOLDOWN_TICKS + 1, () -> {
                try {
                    click(npc, player);
                    helper.assertTrue(keys.size() == 2, "a line after the cooldown: " + keys);
                    helper.assertTrue(
                            keys.stream().allMatch(k -> k.startsWith("npc.lotrrevival.hobbit.")), "keys " + keys);
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(speech);
                }
            });
        } catch (RuntimeException e) {
            NeoForge.EVENT_BUS.unregister(speech);
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void creativePlayersAreNeverHostileForTalk(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        HobbitEntity npc = spawnHobbit(helper, 2);
        npc.setNoAi(true);
        Alignment.set(player, Factions.HOBBIT, -50.0f, AlignmentSource.command());
        helper.assertTrue(NpcStanding.isHostile(npc, player), "survival at -50 is hostile");
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(!NpcStanding.isHostile(npc, player), "creative is never hostile");
        player.setGameMode(GameType.SURVIVAL);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sayApiDeliversWitnessLine(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        HobbitEntity npc = spawnHobbit(helper, 2);
        npc.setNoAi(true);
        Optional<String> key = NpcSpeech.say(npc, player, NpcSpeech.WITNESS);
        helper.assertTrue(
                key.filter(k -> k.startsWith("npc.lotrrevival.hobbit.witness.")).isPresent(), "witness key " + key);
        helper.succeed();
    }
}

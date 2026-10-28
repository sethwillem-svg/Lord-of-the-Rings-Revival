package io.github.sethwillemsvg.lotrrevival.npc.name;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcGender;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reload listener for {@code data/<ns>/lotrrevival/npc_names/<bank>.json}, where each file is a JSON list of strings.
 * Banks are {@code <race>_<gender>_first} and {@code <race>_family}. Server side only.
 */
public final class NpcNames extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "lotrrevival/npc_names";

    private static final Codec<List<String>> LIST_CODEC = Codec.STRING.listOf();

    private static volatile Map<String, List<String>> banks = Map.of();
    private static final Set<NpcRace> WARNED = EnumSet.noneOf(NpcRace.class);
    private static int warningsLogged;

    public NpcNames() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<String>> loaded = new HashMap<>();
        files.forEach((id, json) -> LIST_CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Skipping NPC name bank {}: {}", id, error))
                .ifPresent(list -> loaded.put(id.getPath(), List.copyOf(list))));
        replaceBanks(loaded);
    }

    // ------------------------------------------------------------------ bank access

    public static String firstBankName(NpcRace race, NpcGender gender) {
        return race.id() + "_" + gender.getSerializedName() + "_first";
    }

    public static String familyBankName(NpcRace race) {
        return race.id() + "_family";
    }

    /** Names of a bank, empty when the bank is missing. */
    public static List<String> bank(String name) {
        return banks.getOrDefault(name, List.of());
    }

    /**
     * Rolls {@code First Family} deterministically from the seed. Empty when either bank is missing or empty; the
     * first such roll per race logs one warning (until the next reload).
     */
    public static Optional<String> roll(NpcRace race, NpcGender gender, long seed) {
        List<String> first = bank(firstBankName(race, gender));
        List<String> family = bank(familyBankName(race));
        if (first.isEmpty() || family.isEmpty()) {
            warnOnce(race);
            return Optional.empty();
        }
        RandomSource random = RandomSource.create(seed);
        return Optional.of(first.get(random.nextInt(first.size())) + " " + family.get(random.nextInt(family.size())));
    }

    private static void warnOnce(NpcRace race) {
        synchronized (WARNED) {
            if (WARNED.add(race)) {
                warningsLogged++;
                LOTRRevival.LOGGER.warn(
                        "NPC name bank for race '{}' is missing or empty; its NPCs will show the race name only",
                        race.id());
            }
        }
    }

    // ------------------------------------------------------------------ testing support

    /** Replaces all banks and re-arms the missing-bank warnings. Used by reloads and by tests. */
    public static void replaceBanks(Map<String, List<String>> newBanks) {
        banks = Map.copyOf(newBanks);
        synchronized (WARNED) {
            WARNED.clear();
        }
    }

    public static Map<String, List<String>> snapshot() {
        return banks;
    }

    /** Number of missing-bank warnings logged since startup. */
    public static int warningsLogged() {
        synchronized (WARNED) {
            return warningsLogged;
        }
    }
}

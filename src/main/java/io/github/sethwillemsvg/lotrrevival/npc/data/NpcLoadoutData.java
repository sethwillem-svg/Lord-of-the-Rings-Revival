package io.github.sethwillemsvg.lotrrevival.npc.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Reload listener for {@code data/<ns>/lotrrevival/npc_loadouts/<type>.json}. The file name is the entity type path.
 * The content maps {@code "default"} or a role name to a loadout; a loadout maps an equipment slot to weighted item
 * choices. Applying an NPC's loadout starts from {@code default}, then its role's loadout replaces whole slots.
 * Equipment never drops (every drop chance is set to 0). Server side only.
 */
public final class NpcLoadoutData extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "lotrrevival/npc_loadouts";
    public static final String DEFAULT_KEY = "default";

    /**
     * One weighted choice for a slot.
     *
     * @param item item id
     * @param weight relative weight, at least 1
     * @param dye leather dye colour (RGB), absent for undyed
     * @param label free text to record intent, for example "pike" for a placeholder; ignored when applying
     */
    public record Choice(ResourceLocation item, int weight, Optional<Integer> dye, Optional<String> label) {
        public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("item").forGetter(Choice::item),
                        Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(Choice::weight),
                        Codec.INT.optionalFieldOf("dye").forGetter(Choice::dye),
                        Codec.STRING.optionalFieldOf("label").forGetter(Choice::label))
                .apply(i, Choice::new));

        public static Choice of(Item item, int weight) {
            return new Choice(BuiltInRegistries.ITEM.getKey(item), weight, Optional.empty(), Optional.empty());
        }

        public static Choice dyed(Item item, int weight, int rgb) {
            return new Choice(BuiltInRegistries.ITEM.getKey(item), weight, Optional.of(rgb), Optional.empty());
        }

        public Choice labelled(String text) {
            return new Choice(item, weight, dye, Optional.of(text));
        }
    }

    public static final Codec<Map<EquipmentSlot, List<Choice>>> LOADOUT_CODEC =
            Codec.unboundedMap(EquipmentSlot.CODEC, Choice.CODEC.listOf());

    /** One file: {@code default} or role name to loadout. */
    public static final Codec<Map<String, Map<EquipmentSlot, List<Choice>>>> TABLE_CODEC =
            Codec.unboundedMap(Codec.STRING, LOADOUT_CODEC);

    private static volatile Map<ResourceLocation, Map<String, Map<EquipmentSlot, List<Choice>>>> tables = Map.of();

    public NpcLoadoutData() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        reloadFrom(files);
    }

    /** Replaces all loaded loadouts with the parsed files. Also the entry point for tests that swap data. */
    public static void reloadFrom(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, Map<String, Map<EquipmentSlot, List<Choice>>>> loaded = new HashMap<>();
        files.forEach((id, json) -> TABLE_CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Skipping NPC loadout file {}: {}", id, error))
                .ifPresent(table -> loaded.put(id, Map.copyOf(table))));
        tables = Map.copyOf(loaded);
    }

    /** The currently loaded tables, so a test can restore them afterwards. */
    public static Map<ResourceLocation, Map<String, Map<EquipmentSlot, List<Choice>>>> snapshot() {
        return tables;
    }

    public static void restore(Map<ResourceLocation, Map<String, Map<EquipmentSlot, List<Choice>>>> snapshot) {
        tables = snapshot;
    }

    /** Resolves the loadout of an NPC (default merged with its role) to a stack per slot, rolling weighted choices. */
    public static Map<EquipmentSlot, ItemStack> roll(EntityType<?> type, String role, RandomSource random) {
        Map<EquipmentSlot, ItemStack> result = new EnumMap<>(EquipmentSlot.class);
        Map<String, Map<EquipmentSlot, List<Choice>>> table = tables.get(EntityType.getKey(type));
        if (table == null) {
            return result;
        }
        Map<EquipmentSlot, List<Choice>> merged = new EnumMap<>(EquipmentSlot.class);
        merged.putAll(table.getOrDefault(DEFAULT_KEY, Map.of()));
        merged.putAll(table.getOrDefault(role, Map.of()));
        merged.forEach((slot, choices) -> pick(choices, random).ifPresent(stack -> result.put(slot, stack)));
        return result;
    }

    private static Optional<ItemStack> pick(List<Choice> choices, RandomSource random) {
        int total = choices.stream().mapToInt(Choice::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Choice choice : choices) {
            roll -= choice.weight();
            if (roll < 0) {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(choice.item());
                if (item.isEmpty()) {
                    LOTRRevival.LOGGER.warn("Unknown item {} in an NPC loadout", choice.item());
                    return Optional.empty();
                }
                ItemStack stack = new ItemStack(item.get());
                choice.dye().ifPresent(rgb -> stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false)));
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    /** Equips the NPC from its loadout and makes sure none of it can drop. Called from finalizeSpawn. */
    public static void apply(NpcEntity npc, RandomSource random) {
        Map<EquipmentSlot, ItemStack> stacks = roll(npc.getType(), npc.getRole().getSerializedName(), random);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            npc.setItemSlot(slot, stacks.getOrDefault(slot, ItemStack.EMPTY));
            npc.setDropChance(slot, 0.0f);
        }
    }
}

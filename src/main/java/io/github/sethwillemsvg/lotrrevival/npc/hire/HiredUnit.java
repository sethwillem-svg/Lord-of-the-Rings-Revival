package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a hired unit remembers (entity attachment {@code lotrrevival:hired_unit}). Immutable; update with the
 * {@code with} methods. {@link #NONE} (no owner) means the NPC is not hired.
 *
 * @param owner owner UUID, empty while unhired
 * @param ownerName owner's name when hired
 * @param requiredAlignment R: the owner's alignment with the unit's faction must stay at or above this
 * @param pledge pledge condition the owner must keep meeting
 * @param faction faction of the unit (fixed by its type, kept here so the check needs no lookup)
 * @param squadron free text group name, trimmed, up to {@value #SQUADRON_MAX} characters
 * @param task what the unit does
 * @param command command state, guard point and range, teleport, halt and sword-order flags
 * @param progress experience, level and kills
 * @param originalGear the unit's own gear for slots where the player put something else on it
 */
public record HiredUnit(
        Optional<UUID> owner,
        String ownerName,
        float requiredAlignment,
        PledgeCondition pledge,
        ResourceLocation faction,
        String squadron,
        UnitTask task,
        Command command,
        Progress progress,
        Map<EquipmentSlot, ItemStack> originalGear) {
    public static final int SQUADRON_MAX = 200;
    public static final int DEFAULT_GUARD_RANGE = 8;

    /** Not hired. */
    public static final HiredUnit NONE = new HiredUnit(
            Optional.empty(),
            "",
            0.0f,
            PledgeCondition.NONE,
            ResourceLocation.fromNamespaceAndPath("minecraft", "none"),
            "",
            UnitTask.WARRIOR,
            Command.DEFAULT,
            Progress.START,
            Map.of());

    /**
     * Orders and switches.
     *
     * @param state follow, guard or none
     * @param guardPos the guard point while guarding
     * @param guardRange guard range in blocks (1-64)
     * @param teleport whether the unit may teleport to its owner
     * @param halted set by a halt horn, cleared by ready
     * @param swordOrder set while the unit attacks on command-sword orders (drives the self-defence rule)
     */
    public record Command(
            Hireable.CommandState state,
            Optional<BlockPos> guardPos,
            int guardRange,
            boolean teleport,
            boolean halted,
            boolean swordOrder) {
        public static final Command DEFAULT =
                new Command(Hireable.CommandState.FOLLOW, Optional.empty(), DEFAULT_GUARD_RANGE, true, false, false);

        private static final Codec<Hireable.CommandState> STATE_CODEC = Codec.stringResolver(
                state -> state.name().toLowerCase(Locale.ROOT),
                name -> Hireable.CommandState.valueOf(name.toUpperCase(Locale.ROOT)));

        public static final Codec<Command> CODEC = RecordCodecBuilder.create(i -> i.group(
                        STATE_CODEC.optionalFieldOf("state", Hireable.CommandState.FOLLOW).forGetter(Command::state),
                        BlockPos.CODEC.optionalFieldOf("guard_pos").forGetter(Command::guardPos),
                        Codec.intRange(1, 64).optionalFieldOf("guard_range", DEFAULT_GUARD_RANGE).forGetter(Command::guardRange),
                        Codec.BOOL.optionalFieldOf("teleport", true).forGetter(Command::teleport),
                        Codec.BOOL.optionalFieldOf("halted", false).forGetter(Command::halted),
                        Codec.BOOL.optionalFieldOf("sword_order", false).forGetter(Command::swordOrder))
                .apply(i, Command::new));
    }

    /** Experience, level and kill count. */
    public record Progress(int xp, int level, int kills) {
        public static final Progress START = new Progress(0, 1, 0);

        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.INT.optionalFieldOf("xp", 0).forGetter(Progress::xp),
                        Codec.INT.optionalFieldOf("level", 1).forGetter(Progress::level),
                        Codec.INT.optionalFieldOf("kills", 0).forGetter(Progress::kills))
                .apply(i, Progress::new));
    }

    public static final Codec<HiredUnit> CODEC = RecordCodecBuilder.create(i -> i.group(
                    UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(HiredUnit::owner),
                    Codec.STRING.optionalFieldOf("owner_name", "").forGetter(HiredUnit::ownerName),
                    Codec.FLOAT.optionalFieldOf("required_alignment", 0.0f).forGetter(HiredUnit::requiredAlignment),
                    PledgeCondition.CODEC.optionalFieldOf("pledge", PledgeCondition.NONE).forGetter(HiredUnit::pledge),
                    ResourceLocation.CODEC.fieldOf("faction").forGetter(HiredUnit::faction),
                    Codec.STRING.optionalFieldOf("squadron", "").forGetter(HiredUnit::squadron),
                    UnitTask.CODEC.optionalFieldOf("task", UnitTask.WARRIOR).forGetter(HiredUnit::task),
                    Command.CODEC.optionalFieldOf("command", Command.DEFAULT).forGetter(HiredUnit::command),
                    Progress.CODEC.optionalFieldOf("progress", Progress.START).forGetter(HiredUnit::progress),
                    Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.OPTIONAL_CODEC)
                            .optionalFieldOf("original_gear", Map.of())
                            .forGetter(HiredUnit::originalGear))
            .apply(i, HiredUnit::new));

    public boolean isHired() {
        return owner.isPresent();
    }

    /** Trims and caps a squadron name. */
    public static String cleanSquadron(String squadron) {
        String trimmed = squadron == null ? "" : squadron.trim();
        return trimmed.length() > SQUADRON_MAX ? trimmed.substring(0, SQUADRON_MAX) : trimmed;
    }

    public HiredUnit withCommand(Command command) {
        return new HiredUnit(owner, ownerName, requiredAlignment, pledge, faction, squadron, task, command, progress, originalGear);
    }

    public HiredUnit withProgress(Progress progress) {
        return new HiredUnit(owner, ownerName, requiredAlignment, pledge, faction, squadron, task, command, progress, originalGear);
    }

    public HiredUnit withSquadron(String squadron) {
        return new HiredUnit(
                owner, ownerName, requiredAlignment, pledge, faction, cleanSquadron(squadron), task, command, progress, originalGear);
    }

    /** Remembers the unit's own piece for a slot; an existing record for the slot is kept (it is the true original). */
    public HiredUnit withOriginalGear(EquipmentSlot slot, ItemStack original) {
        if (originalGear.containsKey(slot)) {
            return this;
        }
        Map<EquipmentSlot, ItemStack> copy = new java.util.EnumMap<>(EquipmentSlot.class);
        copy.putAll(originalGear);
        copy.put(slot, original.copy());
        return new HiredUnit(
                owner, ownerName, requiredAlignment, pledge, faction, squadron, task, command, progress, Map.copyOf(copy));
    }
}

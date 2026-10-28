package io.github.sethwillemsvg.lotrrevival.progression.banner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;

/**
 * The protection settings of a banner: owner, mode, threshold, whitelist entries, default permissions,
 * self-protection, custom range and the structure flag.
 *
 * @param owner the owning player (slot 0 of the whitelist); empty only for structure banners
 * @param ownerName cached owner name for messages
 * @param threshold faction mode: the alignment a player needs, 1 to 10,000
 * @param entries whitelist entries after the owner (slot 0); at most {@code whitelistSize - 1}
 * @param defaults permissions for everyone not otherwise allowed, in both modes
 * @param selfProtection nobody but the owner can break the banner
 * @param customRange operator range 1 to 64 that overrides the base block, or 0 for none
 * @param structure placed by a world structure: protects against everyone, cannot be edited, drops without settings
 * @param whitelistSize number of whitelist slots including the owner's, 1 to 4,000
 */
public record BannerSettings(
        Optional<UUID> owner,
        String ownerName,
        Mode mode,
        int threshold,
        List<Entry> entries,
        Set<BannerPermission> defaults,
        boolean selfProtection,
        int customRange,
        boolean structure,
        int whitelistSize) {
    public static final int MIN_THRESHOLD = 1;
    public static final int MAX_THRESHOLD = 10_000;
    public static final int DEFAULT_WHITELIST_SIZE = 16;
    public static final int MAX_WHITELIST_SIZE = 4_000;

    /** Which rule decides who may act. */
    public enum Mode implements StringRepresentable {
        FACTION("faction"),
        WHITELIST("whitelist");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** Whom a whitelist entry names. */
    public enum Kind implements StringRepresentable {
        PLAYER("player"),
        FELLOWSHIP("fellowship");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** One whitelist entry: a player or a fellowship, with its own permission set. */
    public record Entry(Kind kind, UUID id, String name, Set<BannerPermission> permissions) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Kind.CODEC.fieldOf("kind").forGetter(Entry::kind),
                        UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                        Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
                        BannerPermission.SET_CODEC
                                .optionalFieldOf("permissions", Set.of(BannerPermission.FULL))
                                .forGetter(Entry::permissions))
                .apply(i, Entry::new));

        /** A new entry; new entries get Full. */
        public static Entry player(UUID id, String name) {
            return new Entry(Kind.PLAYER, id, name, BannerPermission.setOf(BannerPermission.FULL));
        }

        public static Entry fellowship(UUID id, String name) {
            return new Entry(Kind.FELLOWSHIP, id, name, BannerPermission.setOf(BannerPermission.FULL));
        }

        /** Whether this entry admits the player (by UUID, or as a current member of the listed fellowship). */
        public boolean matches(UUID player, FellowshipMembership fellowships) {
            return switch (kind) {
                case PLAYER -> id.equals(player);
                case FELLOWSHIP -> fellowships.isMember(id, player);
            };
        }
    }

    public static final Codec<BannerSettings> CODEC = RecordCodecBuilder.<BannerSettings>create(i -> i.group(
                    UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BannerSettings::owner),
                    Codec.STRING.optionalFieldOf("owner_name", "").forGetter(BannerSettings::ownerName),
                    Mode.CODEC.optionalFieldOf("mode", Mode.FACTION).forGetter(BannerSettings::mode),
                    Codec.INT.optionalFieldOf("threshold", MIN_THRESHOLD).forGetter(BannerSettings::threshold),
                    Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(BannerSettings::entries),
                    BannerPermission.SET_CODEC
                            .optionalFieldOf("defaults", Set.of())
                            .forGetter(BannerSettings::defaults),
                    Codec.BOOL.optionalFieldOf("self_protection", true).forGetter(BannerSettings::selfProtection),
                    Codec.INT.optionalFieldOf("custom_range", 0).forGetter(BannerSettings::customRange),
                    Codec.BOOL.optionalFieldOf("structure", false).forGetter(BannerSettings::structure),
                    Codec.INT
                            .optionalFieldOf("whitelist_size", DEFAULT_WHITELIST_SIZE)
                            .forGetter(BannerSettings::whitelistSize))
            .apply(i, BannerSettings::new))
            .xmap(BannerSettings::sanitised, BannerSettings::sanitised);

    /** Fresh settings owned by the player: faction mode, threshold +1, no entries, no defaults, self-protection on. */
    public static BannerSettings forOwner(Player owner) {
        return new BannerSettings(
                Optional.of(owner.getUUID()),
                owner.getGameProfile().getName(),
                Mode.FACTION,
                MIN_THRESHOLD,
                List.of(),
                Set.of(),
                true,
                0,
                false,
                DEFAULT_WHITELIST_SIZE);
    }

    /** Settings of a banner nobody has claimed yet (a banner that was just created, or one from a command). */
    public static BannerSettings unowned() {
        return new BannerSettings(
                Optional.empty(),
                "",
                Mode.FACTION,
                MIN_THRESHOLD,
                List.of(),
                Set.of(),
                true,
                0,
                false,
                DEFAULT_WHITELIST_SIZE);
    }

    /** Settings of a structure banner: protects against everyone, no owner. */
    public static BannerSettings forStructure() {
        return new BannerSettings(
                Optional.empty(),
                "",
                Mode.FACTION,
                MIN_THRESHOLD,
                List.of(),
                Set.of(),
                true,
                0,
                true,
                DEFAULT_WHITELIST_SIZE);
    }

    public boolean isOwner(UUID player) {
        return owner.isPresent() && owner.get().equals(player);
    }

    /** Clamps every number into its range and trims the entry list to the slots. */
    public BannerSettings sanitised() {
        int size = Math.max(1, Math.min(MAX_WHITELIST_SIZE, whitelistSize));
        List<Entry> kept = new ArrayList<>(entries);
        while (kept.size() > size - 1) {
            kept.remove(kept.size() - 1);
        }
        return new BannerSettings(
                owner,
                ownerName,
                mode,
                Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold)),
                List.copyOf(kept),
                BannerPermission.setOf(defaults),
                selfProtection,
                Math.max(0, Math.min(BannerBases.MAX_RANGE, customRange)),
                structure,
                size);
    }

    /** The same settings with a different owner (everything else kept). */
    public BannerSettings withOwner(UUID newOwner, String newName) {
        return new BannerSettings(
                Optional.of(newOwner),
                newName,
                mode,
                threshold,
                entries,
                defaults,
                selfProtection,
                customRange,
                structure,
                whitelistSize);
    }

    /** {@code range} 1 to 64 sets the operator range; 0 resets it. */
    public BannerSettings withCustomRange(int range) {
        return new BannerSettings(
                owner, ownerName, mode, threshold, entries, defaults, selfProtection, range, structure, whitelistSize)
                .sanitised();
    }

    public BannerSettings withWhitelistSize(int size) {
        return new BannerSettings(
                owner, ownerName, mode, threshold, entries, defaults, selfProtection, customRange, structure, size)
                .sanitised();
    }

    public BannerSettings withMode(Mode newMode) {
        return new BannerSettings(
                owner, ownerName, newMode, threshold, entries, defaults, selfProtection, customRange, structure,
                whitelistSize);
    }

    public BannerSettings withThreshold(int newThreshold) {
        return new BannerSettings(
                owner, ownerName, mode, newThreshold, entries, defaults, selfProtection, customRange, structure,
                whitelistSize)
                .sanitised();
    }

    public BannerSettings withEntries(List<Entry> newEntries) {
        return new BannerSettings(
                owner, ownerName, mode, threshold, newEntries, defaults, selfProtection, customRange, structure,
                whitelistSize)
                .sanitised();
    }

    public BannerSettings withDefaults(Set<BannerPermission> newDefaults) {
        return new BannerSettings(
                owner, ownerName, mode, threshold, entries, newDefaults, selfProtection, customRange, structure,
                whitelistSize)
                .sanitised();
    }

    public BannerSettings withSelfProtection(boolean value) {
        return new BannerSettings(
                owner, ownerName, mode, threshold, entries, defaults, value, customRange, structure, whitelistSize);
    }
}

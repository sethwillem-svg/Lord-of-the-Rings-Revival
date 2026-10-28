package io.github.sethwillemsvg.lotrrevival.world.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * The {@code lotrrevival:ring_portal} level attachment (kept on the overworld): the once-per-world flag and where
 * the two portals stand. Positions are the portal centres.
 */
public record RingPortalData(
        boolean made,
        Optional<BlockPos> overworldPortal,
        Optional<BlockPos> middleEarthPortal,
        Optional<UUID> overworldEntity,
        Optional<UUID> middleEarthEntity) {
    public static final RingPortalData EMPTY =
            new RingPortalData(false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<RingPortalData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.BOOL.optionalFieldOf("made", false).forGetter(RingPortalData::made),
                    BlockPos.CODEC.optionalFieldOf("overworld_portal").forGetter(RingPortalData::overworldPortal),
                    BlockPos.CODEC.optionalFieldOf("middle_earth_portal").forGetter(RingPortalData::middleEarthPortal),
                    UUIDUtil.CODEC.optionalFieldOf("overworld_entity").forGetter(RingPortalData::overworldEntity),
                    UUIDUtil.CODEC.optionalFieldOf("middle_earth_entity").forGetter(RingPortalData::middleEarthEntity))
            .apply(i, RingPortalData::new));

    /** A portal was made in the overworld at {@code centre}. */
    public RingPortalData withOverworld(BlockPos centre, UUID entity) {
        return new RingPortalData(true, Optional.of(centre), middleEarthPortal, Optional.of(entity), middleEarthEntity);
    }

    public RingPortalData withTwin(BlockPos centre, UUID entity) {
        return new RingPortalData(made, overworldPortal, Optional.of(centre), overworldEntity, Optional.of(entity));
    }

    /** The Middle-earth twin forgotten, so it is built again on the next arrival. */
    public RingPortalData withoutTwin() {
        return new RingPortalData(made, overworldPortal, Optional.empty(), overworldEntity, Optional.empty());
    }

    /** The once-per-world flag cleared; portal positions and entities are kept. */
    public RingPortalData reset() {
        return new RingPortalData(false, overworldPortal, middleEarthPortal, overworldEntity, middleEarthEntity);
    }
}

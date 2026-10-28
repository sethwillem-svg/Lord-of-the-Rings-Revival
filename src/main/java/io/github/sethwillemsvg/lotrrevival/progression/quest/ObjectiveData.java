package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Reserved per-objective data: NPCs already robbed (pickpocket) and the bounty target. Present on every
 * {@link QuestInstance} so later systems need no format change; empty for the first-wave objectives.
 */
public record ObjectiveData(List<UUID> robbed, Optional<UUID> bountyTarget, Optional<String> bountyTargetName) {
    public static final ObjectiveData EMPTY = new ObjectiveData(List.of(), Optional.empty(), Optional.empty());

    public static final Codec<ObjectiveData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    UUIDUtil.CODEC.listOf().optionalFieldOf("robbed", List.of()).forGetter(ObjectiveData::robbed),
                    UUIDUtil.CODEC.optionalFieldOf("bounty_target").forGetter(ObjectiveData::bountyTarget),
                    Codec.STRING.optionalFieldOf("bounty_target_name").forGetter(ObjectiveData::bountyTargetName))
            .apply(i, ObjectiveData::new));

    public ObjectiveData {
        robbed = List.copyOf(robbed);
    }
}

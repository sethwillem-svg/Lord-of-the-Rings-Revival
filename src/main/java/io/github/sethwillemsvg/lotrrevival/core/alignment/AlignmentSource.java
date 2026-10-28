package io.github.sethwillemsvg.lotrrevival.core.alignment;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Why an alignment change happened. {@code type} is one of the {@code lotrrevival:} source ids (kill, quest, event,
 * pledge_break, drain, command); {@code detail} names the specific event or victim; {@code pos} is where it happened.
 */
public record AlignmentSource(ResourceLocation type, Optional<ResourceLocation> detail, Optional<Vec3> pos) {
    public static final ResourceLocation KILL = LOTRRevival.id("kill");
    public static final ResourceLocation QUEST = LOTRRevival.id("quest");
    public static final ResourceLocation EVENT = LOTRRevival.id("event");
    public static final ResourceLocation PLEDGE_BREAK = LOTRRevival.id("pledge_break");
    public static final ResourceLocation DRAIN = LOTRRevival.id("drain");
    public static final ResourceLocation COMMAND = LOTRRevival.id("command");

    public static AlignmentSource of(ResourceLocation type) {
        return new AlignmentSource(type, Optional.empty(), Optional.empty());
    }

    public static AlignmentSource kill(ResourceLocation victimType, Vec3 pos) {
        return new AlignmentSource(KILL, Optional.of(victimType), Optional.ofNullable(pos));
    }

    public static AlignmentSource quest(ResourceLocation questId) {
        return new AlignmentSource(QUEST, Optional.of(questId), Optional.empty());
    }

    /** A one-off event; {@code detail} names it (for example {@code lotrrevival:npc_marriage}). */
    public static AlignmentSource event(ResourceLocation detail, Vec3 pos) {
        return new AlignmentSource(EVENT, Optional.of(detail), Optional.ofNullable(pos));
    }

    public static AlignmentSource command() {
        return of(COMMAND);
    }

    public static AlignmentSource pledgeBreak() {
        return of(PLEDGE_BREAK);
    }

    public static AlignmentSource drain() {
        return of(DRAIN);
    }

    public boolean is(ResourceLocation id) {
        return type.equals(id);
    }
}

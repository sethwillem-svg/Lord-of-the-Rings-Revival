package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/** Transient AI memory of a hired unit (attachment {@code lotrrevival:unit_runtime}). */
public final class UnitRuntime {
    /** The spot the unit guards while its owner is away; set when the owner leaves, cleared when they return. */
    @Nullable
    BlockPos awayAnchor;
}

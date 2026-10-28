package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.PledgeCondition;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitTask;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitAi;
import io.github.sethwillemsvg.lotrrevival.npc.hire.progress.UnitLevels;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * What the command screen shows and its switches, synced to the owner while the menu is open.
 *
 * @param levelStartXp total XP of the current level
 * @param nextLevelXp total XP of the next level
 * @param leveling whether the unit gains levels (Warrior task)
 */
public record UnitInfo(
        Component name,
        ResourceLocation faction,
        int level,
        int xp,
        int levelStartXp,
        int nextLevelXp,
        int kills,
        boolean leveling,
        UnitAi.Status status,
        float requiredAlignment,
        PledgeCondition pledge,
        boolean teleport,
        boolean guarding,
        int guardRange) {
    public static final StreamCodec<RegistryFriendlyByteBuf, UnitInfo> STREAM_CODEC = StreamCodec.of(
            (buf, info) -> {
                ComponentSerialization.STREAM_CODEC.encode(buf, info.name);
                buf.writeResourceLocation(info.faction);
                buf.writeVarInt(info.level);
                buf.writeVarInt(info.xp);
                buf.writeVarInt(info.levelStartXp);
                buf.writeVarInt(info.nextLevelXp);
                buf.writeVarInt(info.kills);
                buf.writeBoolean(info.leveling);
                buf.writeEnum(info.status);
                buf.writeFloat(info.requiredAlignment);
                buf.writeEnum(info.pledge);
                buf.writeBoolean(info.teleport);
                buf.writeBoolean(info.guarding);
                buf.writeVarInt(info.guardRange);
            },
            buf -> new UnitInfo(
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readResourceLocation(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readEnum(UnitAi.Status.class),
                    buf.readFloat(),
                    buf.readEnum(PledgeCondition.class),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt()));

    /** The current values of a hired unit, or null if it is not hired. */
    @Nullable
    public static UnitInfo of(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return null;
        }
        HiredUnit.Progress progress = state.progress();
        return new UnitInfo(
                unit.speakerName(),
                state.faction(),
                progress.level(),
                progress.xp(),
                UnitLevels.totalXpForLevel(progress.level()),
                UnitLevels.totalXpForLevel(progress.level() + 1),
                progress.kills(),
                state.task() == UnitTask.WARRIOR,
                UnitAi.status(unit),
                state.requiredAlignment(),
                state.pledge(),
                state.command().teleport(),
                state.command().state() == Hireable.CommandState.GUARD_POS,
                state.command().guardRange());
    }
}

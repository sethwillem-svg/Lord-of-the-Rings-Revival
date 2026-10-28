package io.github.sethwillemsvg.lotrrevival.core.alignment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The saved per-player alignment state. Keys are plain faction ids (not resolved holders) so entries for factions a
 * datapack removed survive untouched. Timer fields are remaining ticks of online time. Pods never touch this directly;
 * they use {@link Alignment}.
 */
public final class AlignmentData {
    public static final Codec<AlignmentData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                            .optionalFieldOf("values", Map.of())
                            .forGetter(d -> d.values),
                    ResourceLocation.CODEC.optionalFieldOf("pledge").forGetter(d -> Optional.ofNullable(d.pledge)),
                    Codec.INT.optionalFieldOf("pledge_warning_ticks", 0).forGetter(d -> d.pledgeWarningTicks),
                    Codec.INT.optionalFieldOf("pledge_cooldown_ticks", 0).forGetter(d -> d.pledgeCooldownTicks),
                    ResourceLocation.CODEC
                            .optionalFieldOf("pledge_cooldown_faction")
                            .forGetter(d -> Optional.ofNullable(d.pledgeCooldownFaction)),
                    Codec.INT.optionalFieldOf("drain_timer", 0).forGetter(d -> d.drainTimer),
                    Codec.BOOL.optionalFieldOf("drain_notice_shown", false).forGetter(d -> d.drainNoticeShown))
            .apply(i, AlignmentData::new));

    private final Map<ResourceLocation, Float> values = new HashMap<>();

    @Nullable
    private ResourceLocation pledge;

    private int pledgeWarningTicks;
    private int pledgeCooldownTicks;

    @Nullable
    private ResourceLocation pledgeCooldownFaction;

    private int drainTimer;
    private boolean drainNoticeShown;

    public AlignmentData() {}

    private AlignmentData(
            Map<ResourceLocation, Float> values,
            Optional<ResourceLocation> pledge,
            int pledgeWarningTicks,
            int pledgeCooldownTicks,
            Optional<ResourceLocation> pledgeCooldownFaction,
            int drainTimer,
            boolean drainNoticeShown) {
        values.forEach((k, v) -> {
            if (v != 0.0f) {
                this.values.put(k, v);
            }
        });
        this.pledge = pledge.orElse(null);
        this.pledgeWarningTicks = Math.max(0, pledgeWarningTicks);
        this.pledgeCooldownTicks = Math.max(0, pledgeCooldownTicks);
        this.pledgeCooldownFaction = pledgeCooldownFaction.orElse(null);
        this.drainTimer = Math.max(0, drainTimer);
        this.drainNoticeShown = drainNoticeShown;
    }

    public float value(ResourceLocation faction) {
        return values.getOrDefault(faction, 0.0f);
    }

    /** Stores a value; zero removes the entry. */
    public void setValue(ResourceLocation faction, float value) {
        if (value == 0.0f) {
            values.remove(faction);
        } else {
            values.put(faction, value);
        }
    }

    /** A live view of all non-zero values (including ones for factions that no longer exist). */
    public Map<ResourceLocation, Float> values() {
        return values;
    }

    public Optional<ResourceLocation> pledge() {
        return Optional.ofNullable(pledge);
    }

    public void setPledge(@Nullable ResourceLocation pledge) {
        this.pledge = pledge;
    }

    public int pledgeWarningTicks() {
        return pledgeWarningTicks;
    }

    public void setPledgeWarningTicks(int ticks) {
        this.pledgeWarningTicks = Math.max(0, ticks);
    }

    public int pledgeCooldownTicks() {
        return pledgeCooldownTicks;
    }

    public void setPledgeCooldownTicks(int ticks) {
        this.pledgeCooldownTicks = Math.max(0, ticks);
        if (this.pledgeCooldownTicks == 0) {
            this.pledgeCooldownFaction = null;
        }
    }

    public Optional<ResourceLocation> pledgeCooldownFaction() {
        return Optional.ofNullable(pledgeCooldownFaction);
    }

    public void setPledgeCooldownFaction(@Nullable ResourceLocation faction) {
        this.pledgeCooldownFaction = faction;
    }

    public int drainTimer() {
        return drainTimer;
    }

    public void setDrainTimer(int ticks) {
        this.drainTimer = Math.max(0, ticks);
    }

    public boolean drainNoticeShown() {
        return drainNoticeShown;
    }

    public void setDrainNoticeShown(boolean shown) {
        this.drainNoticeShown = shown;
    }
}

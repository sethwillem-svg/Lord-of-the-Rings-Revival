package io.github.sethwillemsvg.lotrrevival.npc.entity;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/** A job a civilian NPC does. Roles change equipment, title, speech and trading, never the AI class. */
public enum NpcRole implements StringRepresentable {
    VILLAGER("villager", false, 60, false),
    FARMER("farmer", true, 30, false),
    INNKEEPER("innkeeper", true, 0, true),
    TRAVELLER("traveller", true, 10, false),
    UNIT_TRADER("unit_trader", false, 0, true);

    public static final Codec<NpcRole> CODEC = StringRepresentable.fromEnum(NpcRole::values);

    private final String serializedName;
    private final boolean hasTitle;
    private final int spawnWeight;
    private final boolean trader;

    NpcRole(String serializedName, boolean hasTitle, int spawnWeight, boolean trader) {
        this.serializedName = serializedName;
        this.hasTitle = hasTitle;
        this.spawnWeight = spawnWeight;
        this.trader = trader;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Weight when a role is rolled at spawn. Zero means the role is never rolled. */
    public int spawnWeight() {
        return spawnWeight;
    }

    /** Goods or unit trader. */
    public boolean isTrader() {
        return trader;
    }

    /** Lang key of the title shown after the name; empty for roles without one (villager). */
    public Optional<String> titleKey() {
        return hasTitle ? Optional.of("npc.lotrrevival.role." + serializedName) : Optional.empty();
    }

    public static Optional<NpcRole> byName(String name) {
        for (NpcRole role : values()) {
            if (role.serializedName.equals(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    public static NpcRole byOrdinal(int ordinal) {
        NpcRole[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}

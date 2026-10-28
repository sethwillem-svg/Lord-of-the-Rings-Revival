package io.github.sethwillemsvg.lotrrevival.core.material;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** The metals this package adds. Copper and iron stay vanilla. */
public enum Metal {
    TIN("tin", "Tin", ToolTier.STONE, true, true),
    SILVER("silver", "Silver", ToolTier.IRON, true, true),
    BRONZE("bronze", "Bronze", ToolTier.STONE, false, true),
    MORGUL_IRON("morgul_iron", "Morgul Iron", ToolTier.STONE, true, false),
    ORC_STEEL("orc_steel", "Orc Steel", ToolTier.STONE, false, true),
    MITHRIL("mithril", "Mithril", ToolTier.DIAMOND, true, true);

    /** Minimum pickaxe tier for a metal's ore and blocks. */
    public enum ToolTier {
        STONE(BlockTags.NEEDS_STONE_TOOL),
        IRON(BlockTags.NEEDS_IRON_TOOL),
        DIAMOND(BlockTags.NEEDS_DIAMOND_TOOL);

        public final TagKey<Block> tag;

        ToolTier(TagKey<Block> tag) {
            this.tag = tag;
        }
    }

    private final String id;
    private final String displayName;
    private final ToolTier tier;
    private final boolean hasOre;
    private final boolean hasIngot;

    Metal(String id, String displayName, ToolTier tier, boolean hasOre, boolean hasIngot) {
        this.id = id;
        this.displayName = displayName;
        this.tier = tier;
        this.hasOre = hasOre;
        this.hasIngot = hasIngot;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ToolTier tier() {
        return tier;
    }

    /** Has ore, deepslate ore, raw item and raw block. */
    public boolean hasOre() {
        return hasOre;
    }

    /** Has ingot, nugget and storage block (false only for Morgul iron, whose smelt result is orc steel). */
    public boolean hasIngot() {
        return hasIngot;
    }
}

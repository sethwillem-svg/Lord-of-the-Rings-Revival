package io.github.sethwillemsvg.lotrrevival.core.material;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tag keys for the new metals: the common {@code c:} families and the {@code lotrrevival:ores/<m>} mirrors. */
public final class MaterialTags {
    private MaterialTags() {}

    private static ResourceLocation common(String path) {
        return ResourceLocation.fromNamespaceAndPath("c", path);
    }

    public static TagKey<Block> commonOreBlocks(Metal metal) {
        return TagKey.create(Registries.BLOCK, common("ores/" + metal.id()));
    }

    public static TagKey<Block> modOreBlocks(Metal metal) {
        return TagKey.create(Registries.BLOCK, LOTRRevival.id("ores/" + metal.id()));
    }

    public static TagKey<Block> storageBlocksBlock(Metal metal) {
        return TagKey.create(Registries.BLOCK, common("storage_blocks/" + metal.id()));
    }

    public static TagKey<Block> rawStorageBlocksBlock(Metal metal) {
        return TagKey.create(Registries.BLOCK, common("storage_blocks/raw_" + metal.id()));
    }

    public static TagKey<Item> commonOres(Metal metal) {
        return itemTag(common("ores/" + metal.id()));
    }

    public static TagKey<Item> modOres(Metal metal) {
        return itemTag(LOTRRevival.id("ores/" + metal.id()));
    }

    public static TagKey<Item> rawMaterials(Metal metal) {
        return itemTag(common("raw_materials/" + metal.id()));
    }

    public static TagKey<Item> ingots(Metal metal) {
        return itemTag(common("ingots/" + metal.id()));
    }

    public static TagKey<Item> nuggets(Metal metal) {
        return itemTag(common("nuggets/" + metal.id()));
    }

    public static TagKey<Item> storageBlocks(Metal metal) {
        return itemTag(common("storage_blocks/" + metal.id()));
    }

    public static TagKey<Item> rawStorageBlocks(Metal metal) {
        return itemTag(common("storage_blocks/raw_" + metal.id()));
    }

    private static TagKey<Item> itemTag(ResourceLocation id) {
        return TagKey.create(Registries.ITEM, id);
    }
}

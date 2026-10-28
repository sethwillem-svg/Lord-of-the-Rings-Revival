package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Core: the single writer of {@code #lotrrevival:road_surfaces}. Any other provider that needs blocks in this tag
 * (for example the Arnor brick set) must add them here, because two providers writing the same tag file collide.
 *
 * <p>Vanilla members: {@code dirt_path} and {@code coarse_dirt}. {@code gravel} is deliberately left out. Natural
 * gravel (riverbeds, ocean floors, mountain patches) is common, and listing it would make the tree and plant
 * filter and the NPC traveller check read natural patches as roads. Trees cannot grow on gravel anyway, so
 * excluding it costs the road filter nothing. Coarse dirt does occur naturally (badlands, savanna, old-growth
 * taiga patches); that small false-positive area is accepted because trees can grow on it and the path road
 * style needs it protected.
 */
final class RoadSurfaceTags extends BlockTagsProvider {
    RoadSurfaceTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
        super(output, lookup, LOTRRevival.MOD_ID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.Blocks.ROAD_SURFACES).add(Blocks.DIRT_PATH, Blocks.COARSE_DIRT);
    }

    @Override
    public String getName() {
        return "Road surface tags";
    }
}

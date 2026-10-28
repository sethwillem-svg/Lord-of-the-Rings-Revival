package io.github.sethwillemsvg.lotrrevival.world.road;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A bridge look ({@code specs/world/waypoints-travel.md}, Bridges): the deck layer, its half step, the rail on the
 * outermost columns and the pier block.
 *
 * @param id style id, for example {@code stone_and_wood}
 * @param deck full deck block
 * @param deckSlab bottom slab used when the deck height ends in a half
 * @param rail rail block placed on the deck of the outermost columns
 * @param pier block of the piers, from the deck down to the bed
 */
public record BridgeStyle(String id, BlockState deck, BlockState deckSlab, BlockState rail, BlockState pier) {}

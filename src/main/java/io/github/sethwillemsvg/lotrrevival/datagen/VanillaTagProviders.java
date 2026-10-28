package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The only providers that write {@code minecraft:} block and item tags (mineable/*, needs_*_tool, beacon_*, slabs,
 * stairs, swords, enchantable/* ...). A tag file is written whole by whichever provider touches it, so two providers
 * adding to the same vanilla tag silently drop each other's entries. Systems add their entries with
 * {@link #contributeBlocks} / {@link #contributeItems} from their datagen entry point, the same way
 * {@link ModLangProvider#contribute} works for en_us. Mod-namespace and {@code c:} tags stay in each system's own
 * provider (one provider per tag path). Both providers fail the run if a contributor writes a non-vanilla tag here.
 */
public final class VanillaTagProviders {
    private VanillaTagProviders() {}

    private static final List<Consumer<Function<TagKey<Block>, IntrinsicTagAppender<Block>>>> BLOCKS =
            new ArrayList<>();
    private static final List<Consumer<Function<TagKey<Item>, IntrinsicTagAppender<Item>>>> ITEMS = new ArrayList<>();

    /** Adds entries to {@code minecraft:} block tags. The function returns the appender for a tag. */
    public static synchronized void contributeBlocks(
            Consumer<Function<TagKey<Block>, IntrinsicTagAppender<Block>>> contributor) {
        BLOCKS.add(contributor);
    }

    /** Adds entries to {@code minecraft:} item tags. The function returns the appender for a tag. */
    public static synchronized void contributeItems(
            Consumer<Function<TagKey<Item>, IntrinsicTagAppender<Item>>> contributor) {
        ITEMS.add(contributor);
    }

    /** True for tags that must go through these providers. */
    public static boolean isVanilla(TagKey<?> tag) {
        return "minecraft".equals(tag.location().getNamespace());
    }

    static void gather(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        event.getGenerator()
                .addProvider(event.includeServer(), new Blocks(output, lookup, event.getExistingFileHelper()));
        event.getGenerator()
                .addProvider(event.includeServer(), new Items(output, lookup, event.getExistingFileHelper()));
    }

    private static <T> IntrinsicTagAppender<T> checked(TagKey<T> tag, Function<TagKey<T>, IntrinsicTagAppender<T>> f) {
        if (!isVanilla(tag)) {
            throw new IllegalStateException(
                    "VanillaTagProviders only writes minecraft: tags; put " + tag.location() + " in its owner's provider");
        }
        return f.apply(tag);
    }

    static final class Blocks extends BlockTagsProvider {
        Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
            super(output, lookup, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            List<Consumer<Function<TagKey<Block>, IntrinsicTagAppender<Block>>>> snapshot;
            synchronized (VanillaTagProviders.class) {
                snapshot = List.copyOf(BLOCKS);
            }
            snapshot.forEach(c -> c.accept(tag -> checked(tag, this::tag)));
        }

        @Override
        public String getName() {
            return "Vanilla block tags";
        }
    }

    static final class Items extends ItemTagsProvider {
        Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
            super(
                    output,
                    lookup,
                    CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()),
                    LOTRRevival.MOD_ID,
                    helper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            List<Consumer<Function<TagKey<Item>, IntrinsicTagAppender<Item>>>> snapshot;
            synchronized (VanillaTagProviders.class) {
                snapshot = List.copyOf(ITEMS);
            }
            snapshot.forEach(c -> c.accept(tag -> checked(tag, this::tag)));
        }

        @Override
        public String getName() {
            return "Vanilla item tags";
        }
    }
}

package io.github.sethwillemsvg.lotrrevival.core.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The {@code table} field of a faction recipe: a block id ({@code lotrrevival:gondor_crafting_table}) or a block tag
 * prefixed with {@code #} ({@code #lotrrevival:crafting_tables}).
 */
public record TableRef(ResourceLocation id, boolean tag) {
    public static final Codec<TableRef> CODEC = Codec.STRING.comapFlatMap(TableRef::parse, TableRef::toString);
    public static final StreamCodec<RegistryFriendlyByteBuf, TableRef> STREAM_CODEC = StreamCodec.of(
            (buf, ref) -> ByteBufCodecs.STRING_UTF8.encode(buf, ref.toString()),
            buf -> parse(ByteBufCodecs.STRING_UTF8.decode(buf)).getOrThrow());

    public static TableRef block(Block block) {
        return new TableRef(BuiltInRegistries.BLOCK.getKey(block), false);
    }

    public static TableRef tag(ResourceLocation id) {
        return new TableRef(id, true);
    }

    public static DataResult<TableRef> parse(String text) {
        boolean tag = text.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
        return id == null
                ? DataResult.error(() -> "Not a valid table reference: " + text)
                : DataResult.success(new TableRef(id, tag));
    }

    /** True if the given table block is this block, or a member of this tag. */
    public boolean matches(Block table) {
        if (tag) {
            return table.builtInRegistryHolder().is(TagKey.create(Registries.BLOCK, id));
        }
        return id.equals(BuiltInRegistries.BLOCK.getKey(table));
    }

    @Override
    public String toString() {
        return (tag ? "#" : "") + id;
    }
}

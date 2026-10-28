package io.github.sethwillemsvg.lotrrevival.progression.title;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

/**
 * The player's {@code lotrrevival:titles} attachment. Immutable; every change builds a new value.
 *
 * @param granted special titles only; unknown ids are kept on disk and ignored
 * @param selected the title shown in chat, none = no title
 * @param color one of the 16 chat colours
 * @param feminineRanks prefer the feminine text where a title has one
 * @param everUsable titles that were usable at some re-check, so {@code earn_title} fires once each
 */
public record TitleData(
        Set<ResourceLocation> granted,
        Optional<ResourceLocation> selected,
        ChatFormatting color,
        boolean feminineRanks,
        Set<ResourceLocation> everUsable) {

    public static final TitleData EMPTY = new TitleData(Set.of(), Optional.empty(), ChatFormatting.WHITE, false, Set.of());

    /** A chat colour name; only the 16 colours are accepted (no formatting codes, no reset). */
    public static final Codec<ChatFormatting> COLOR_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                ChatFormatting colour = parseColor(name);
                return colour != null ? DataResult.success(colour) : DataResult.error(() -> "Not a chat colour: " + name);
            },
            ChatFormatting::getName);

    private static final Codec<Set<ResourceLocation>> ID_SET = ResourceLocation.CODEC
            .listOf()
            .xmap(list -> Collections.unmodifiableSet(new LinkedHashSet<>(list)), set -> set.stream().toList());

    public static final Codec<TitleData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ID_SET.optionalFieldOf("granted", Set.of()).forGetter(TitleData::granted),
                    ResourceLocation.CODEC.optionalFieldOf("selected").forGetter(TitleData::selected),
                    COLOR_CODEC.optionalFieldOf("color", ChatFormatting.WHITE).forGetter(TitleData::color),
                    Codec.BOOL.optionalFieldOf("feminine_ranks", false).forGetter(TitleData::feminineRanks),
                    ID_SET.optionalFieldOf("ever_usable", Set.of()).forGetter(TitleData::everUsable))
            .apply(i, TitleData::new));

    /** The chat colour of that name, or null when it is not one of the 16. */
    @Nullable
    public static ChatFormatting parseColor(String name) {
        ChatFormatting colour = ChatFormatting.getByName(name);
        return colour != null && colour.isColor() ? colour : null;
    }

    public TitleData withGranted(Set<ResourceLocation> value) {
        return new TitleData(copy(value), selected, color, feminineRanks, everUsable);
    }

    public TitleData withSelected(Optional<ResourceLocation> value) {
        return new TitleData(granted, value, color, feminineRanks, everUsable);
    }

    public TitleData withChoice(Optional<ResourceLocation> title, ChatFormatting colour, boolean feminine) {
        return new TitleData(granted, title, colour, feminine, everUsable);
    }

    public TitleData withEverUsable(Set<ResourceLocation> value) {
        return new TitleData(granted, selected, color, feminineRanks, copy(value));
    }

    private static Set<ResourceLocation> copy(Set<ResourceLocation> set) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }
}

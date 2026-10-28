package io.github.sethwillemsvg.lotrrevival.progression.title;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;

/**
 * An entry of the synced datapack registry {@code lotrrevival:title}, files at
 * {@code data/<ns>/lotrrevival/title/<source>/<name>.json}. The colour is the player's choice, not part of the
 * definition.
 *
 * @param textFeminine optional variant shown when the player prefers feminine rank names
 * @param hiddenUntilUsable menu hint: do not list the title until the player can use it
 */
public record TitleDefinition(
        Component text, Optional<Component> textFeminine, TitleSource source, boolean hiddenUntilUsable) {

    public static final ResourceKey<Registry<TitleDefinition>> REGISTRY = ModDataRegistries.title();

    /** JSON codec; also the network codec of the synced registry. */
    public static final Codec<TitleDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ComponentSerialization.CODEC.fieldOf("text").forGetter(TitleDefinition::text),
                    ComponentSerialization.CODEC.optionalFieldOf("text_feminine").forGetter(TitleDefinition::textFeminine),
                    TitleSource.CODEC.fieldOf("source").forGetter(TitleDefinition::source),
                    Codec.BOOL.optionalFieldOf("hidden_until_usable", false).forGetter(TitleDefinition::hiddenUntilUsable))
            .apply(i, TitleDefinition::new));

    /** The text to show: the feminine variant when asked for and present, else the plain text. */
    public Component displayText(boolean feminine) {
        return feminine ? textFeminine.orElse(text) : text;
    }
}

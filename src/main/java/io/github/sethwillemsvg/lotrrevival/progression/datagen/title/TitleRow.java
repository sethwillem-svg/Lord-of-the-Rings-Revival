package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One row of the title table: the id path {@code <folder>/<name>}, the English text (and optional feminine
 * text) and the source. Folders: {@code starter}, {@code special}, {@code faction}, {@code achievement},
 * {@code rank}.
 */
public record TitleRow(
        String path, String text, String feminine, TitleSource source, boolean hiddenUntilUsable) {

    public ResourceLocation id() {
        return LOTRRevival.id(path);
    }

    /** {@code title.lotrrevival.<folder>.<name>} */
    public String textKey() {
        return "title." + LOTRRevival.MOD_ID + "." + path.replace('/', '.');
    }

    public String feminineKey() {
        return textKey() + ".feminine";
    }

    public TitleDefinition toDefinition() {
        return new TitleDefinition(
                Component.translatable(textKey()),
                feminine == null ? Optional.empty() : Optional.of(Component.translatable(feminineKey())),
                source,
                hiddenUntilUsable);
    }
}

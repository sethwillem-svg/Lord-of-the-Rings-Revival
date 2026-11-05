package io.github.sethwillemsvg.lotrrevival.nemesis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Entity attachment {@code lotr_revival:sergeant}: name parts and the one strength of a promoted hired unit. */
public record SergeantData(String firstName, String title, ResourceLocation trait) {
    public static final Codec<SergeantData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("first").forGetter(SergeantData::firstName),
            Codec.STRING.fieldOf("title").forGetter(SergeantData::title),
            ResourceLocation.CODEC.fieldOf("trait").forGetter(SergeantData::trait)).apply(i, SergeantData::new));
    public static final SergeantData NONE = new SergeantData("", "", ResourceLocation.fromNamespaceAndPath("lotr_revival", "none"));

    public String displayName() {
        return title.isEmpty() ? firstName : firstName + " " + title;
    }
}

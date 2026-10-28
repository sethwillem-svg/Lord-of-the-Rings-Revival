package io.github.sethwillemsvg.lotrrevival.core.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Grey-zone hostility parameters; see the alignment spec, Hostility. */
public record Suspicion(float ceiling, float factor) {
    public static final Codec<Suspicion> CODEC = RecordCodecBuilder.<Suspicion>create(i -> i.group(
                            Codec.FLOAT.fieldOf("ceiling").forGetter(Suspicion::ceiling),
                            Codec.FLOAT.fieldOf("factor").forGetter(Suspicion::factor))
                    .apply(i, Suspicion::new))
            .validate(s -> s.factor() > 0
                    ? DataResult.success(s)
                    : DataResult.error(() -> "suspicion factor must be > 0"));
}

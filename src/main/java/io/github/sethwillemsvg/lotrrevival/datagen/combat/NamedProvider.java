package io.github.sethwillemsvg.lotrrevival.datagen.combat;

import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

/** Gives a vanilla provider with a final {@code getName()} (recipes) a unique name. */
record NamedProvider(String name, DataProvider delegate) implements DataProvider {
    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return delegate.run(output);
    }

    @Override
    public String getName() {
        return name;
    }
}

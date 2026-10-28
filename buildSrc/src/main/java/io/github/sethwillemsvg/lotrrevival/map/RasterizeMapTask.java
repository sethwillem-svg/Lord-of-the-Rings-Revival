package io.github.sethwillemsvg.lotrrevival.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Rasterizes {@code src/main/map/} into the heightmap and biome map PNGs under {@code build/generated}. Runs
 * before processResources, so the images land in the jar and on the dev-run classpath. Nothing it writes is
 * committed.
 */
@CacheableTask
public abstract class RasterizeMapTask extends DefaultTask {
    /** {@code src/main/map/}: the vector source. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    /** {@code map_regions.json}: the painted windows. */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRegionsFile();

    /** {@code world/MiddleEarthBiomes.java}: the colour table. */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getBiomeTableSource();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void rasterize() throws IOException {
        long start = System.nanoTime();
        Path out = getOutputDir().get().getAsFile().toPath();
        deleteRecursively(out);
        Files.createDirectories(out);

        BiomeTable table = BiomeTable.parse(getBiomeTableSource().get().getAsFile().toPath());
        List<Regions.Window> windows = Regions.load(getRegionsFile().get().getAsFile().toPath());
        MapSource source = MapSource.load(getSourceDir().get().getAsFile().toPath());
        Rasterizer.Raster raster = Rasterizer.rasterize(source, table, windows);
        Rasterizer.writeImages(raster, out);
        TravelJson.writeAll(TravelSource.load(getSourceDir().get().getAsFile().toPath()), out);
        getLogger().lifecycle("Rasterized the map in {} ms", (System.nanoTime() - start) / 1_000_000);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}

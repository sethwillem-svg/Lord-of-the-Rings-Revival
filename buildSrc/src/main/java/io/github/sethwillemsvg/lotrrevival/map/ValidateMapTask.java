package io.github.sethwillemsvg.lotrrevival.map;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Runs the map validator over the source and the rasterized images. Part of {@code ./gradlew build}. */
public abstract class ValidateMapTask extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRegionsFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLandingFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getBiomeTableSource();

    /** The rasterizer's output directory. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getImagesDir();

    /** Expected generated-JSON files for the byte-for-byte golden test. Defaults to buildSrc/src/test/resources/golden. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getGoldenDir();

    /** Core's {@code Factions.java}, compared with the validator's copy of the faction ids. */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getFactionSource();

    public ValidateMapTask() {
        var project = getProject().getLayout().getProjectDirectory();
        getGoldenDir().convention(project.dir("buildSrc/src/test/resources/golden"));
        getFactionSource()
                .convention(project.file("src/main/java/io/github/sethwillemsvg/lotrrevival/core/faction/Factions.java"));
    }

    /** Marker so the task is up to date when nothing changed. */
    @OutputFile
    public abstract RegularFileProperty getMarker();

    @TaskAction
    public void validate() throws IOException {
        BiomeTable table = BiomeTable.parse(getBiomeTableSource().get().getAsFile().toPath());
        String selfTest = MapValidator.selfTest(table);
        if (selfTest != null) {
            throw new GradleException(selfTest);
        }

        selfTest = TravelValidator.selfTest(table);
        if (selfTest == null) {
            selfTest = TravelJson.goldenTest(getGoldenDir().get().getAsFile().toPath());
        }
        if (selfTest == null) {
            selfTest = FactionIds.compareWithCore(getFactionSource().get().getAsFile().toPath());
        }
        if (selfTest != null) {
            throw new GradleException(selfTest);
        }

        List<Regions.Window> windows = Regions.load(getRegionsFile().get().getAsFile().toPath());
        double[] landing = Regions.loadLanding(getLandingFile().get().getAsFile().toPath());
        MapSource source = MapSource.load(getSourceDir().get().getAsFile().toPath());

        List<String> problems = new ArrayList<>();
        problems.addAll(MapValidator.validateRegions(windows).lines());
        problems.addAll(MapValidator.validateSource(source, table, windows, landing).lines());

        Path images = getImagesDir().get().getAsFile().toPath();
        ImageIO.setUseCache(false);
        BufferedImage heightImage = readHeightmap(images.resolve("data/lotrrevival/novoatlas/heightmap/middle_earth.png"), problems);
        BufferedImage biomeImage = readBiomeMap(images.resolve("data/lotrrevival/novoatlas/biome_map/middle_earth.png"), problems);
        if (biomeImage != null) {
            TravelSource travel = TravelSource.load(getSourceDir().get().getAsFile().toPath());
            problems.addAll(TravelValidator.validate(
                            travel, table, windows, new TravelValidator.GridLookup(new BiomeImage(biomeImage), table))
                    .lines());
        }
        if (heightImage != null && biomeImage != null) {
            problems.addAll(MapValidator.validateImages(
                            new HeightImage(heightImage), new BiomeImage(biomeImage), table, windows, landing, source.landmarks)
                    .lines());
        }

        if (!problems.isEmpty()) {
            throw new GradleException("Map validation failed:\n  " + String.join("\n  ", problems));
        }
        Path marker = getMarker().get().getAsFile().toPath();
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "ok\n");
    }

    private static BufferedImage readHeightmap(Path file, List<String> problems) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            problems.add("heightmap is not a readable image: " + file);
            return null;
        }
        if (image.getRaster().getNumBands() != 1
                || image.getColorModel().getPixelSize() != 16
                || image.getRaster().getDataBuffer().getDataType() != DataBuffer.TYPE_USHORT) {
            problems.add("heightmap is not 16-bit grayscale (bands " + image.getRaster().getNumBands() + ", bits "
                    + image.getColorModel().getPixelSize() + ")");
            return null;
        }
        return image;
    }

    private static BufferedImage readBiomeMap(Path file, List<String> problems) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            problems.add("biome map is not a readable image: " + file);
            return null;
        }
        if (image.getColorModel().getNumColorComponents() != 3 || image.getColorModel().hasAlpha()) {
            problems.add("biome map is not 8-bit RGB without alpha");
            return null;
        }
        return image;
    }

    private record HeightImage(BufferedImage image) implements MapValidator.HeightGrid {
        @Override
        public int width() {
            return image.getWidth();
        }

        @Override
        public int height() {
            return image.getHeight();
        }

        @Override
        public void row(int y, int[] out) {
            image.getRaster().getSamples(0, y, image.getWidth(), 1, 0, out);
        }
    }

    private record BiomeImage(BufferedImage image) implements MapValidator.BiomeGrid {
        @Override
        public int width() {
            return image.getWidth();
        }

        @Override
        public int height() {
            return image.getHeight();
        }

        @Override
        public void row(int y, int[] out) {
            image.getRGB(0, y, image.getWidth(), 1, out, 0, image.getWidth());
        }
    }
}

package io.github.sethwillemsvg.lotrrevival.world.datagen;

import com.google.common.hash.Hashing;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Generates the relief image (detail strength per region) from the biome map and the colour table.
 *
 * <p>Each biome pixel gets its biome's amplitude, the field is blurred with a Gaussian of radius 3 biome
 * pixels so strength never steps at a border, then averaged down 2x2 to 256 blocks per pixel. Values are
 * {@code amplitude * 8} (vertical scale 0.125). Regenerated on every datagen run.
 */
public final class ReliefImageProvider implements DataProvider {
    private static final String BIOME_MAP_RESOURCE = "/data/lotrrevival/novoatlas/biome_map/middle_earth.png";
    private static final int RADIUS = 3;
    private static final float SIGMA = 1.5f;

    private final PackOutput output;

    public ReliefImageProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> {
            byte[] png = renderPng(readAmplitudes());
            Path file = output.getOutputFolder()
                    .resolve("data/" + LOTRRevival.MOD_ID + "/novoatlas/heightmap/middle_earth_relief.png");
            try {
                cache.writeIfNeeded(file, png, Hashing.sha1().hashBytes(png));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public String getName() {
        return "Middle-earth relief image";
    }

    private static float[] readAmplitudes() {
        int w = MiddleEarthMap.BIOME_WIDTH;
        int h = MiddleEarthMap.BIOME_HEIGHT;
        BufferedImage image;
        try (InputStream in = ReliefImageProvider.class.getResourceAsStream(BIOME_MAP_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + BIOME_MAP_RESOURCE);
            }
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (image.getWidth() != w || image.getHeight() != h) {
            throw new IllegalStateException("Biome map must be " + w + "x" + h);
        }
        float[] amplitudes = new float[w * h];
        int[] row = new int[w];
        for (int z = 0; z < h; z++) {
            image.getRGB(0, z, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                amplitudes[z * w + x] = amplitude(row[x] & 0xFFFFFF);
            }
        }
        return amplitudes;
    }

    private static float amplitude(int color) {
        for (MiddleEarthBiomes.Entry entry : MiddleEarthBiomes.TABLE) {
            if (entry.color() == color) {
                return entry.amplitude();
            }
        }
        throw new IllegalStateException(String.format("Biome map colour #%06X is not in the colour table", color));
    }

    private static byte[] renderPng(float[] amplitudes) {
        int w = MiddleEarthMap.BIOME_WIDTH;
        int h = MiddleEarthMap.BIOME_HEIGHT;
        float[] kernel = new float[RADIUS * 2 + 1];
        float sum = 0;
        for (int i = -RADIUS; i <= RADIUS; i++) {
            kernel[i + RADIUS] = (float) Math.exp(-(i * i) / (2 * SIGMA * SIGMA));
            sum += kernel[i + RADIUS];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        // Horizontal pass, then vertical pass, clamping at the edges.
        float[] tmp = new float[amplitudes.length];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                float v = 0;
                for (int k = -RADIUS; k <= RADIUS; k++) {
                    v += kernel[k + RADIUS] * amplitudes[z * w + Math.min(w - 1, Math.max(0, x + k))];
                }
                tmp[z * w + x] = v;
            }
        }
        int rw = MiddleEarthMap.RELIEF_WIDTH;
        int rh = MiddleEarthMap.RELIEF_HEIGHT;
        BufferedImage relief = new BufferedImage(rw, rh, BufferedImage.TYPE_BYTE_GRAY);
        float[] blurredRows = new float[w * 2];
        for (int rz = 0; rz < rh; rz++) {
            // Blur vertically only for the two source rows that feed this relief row.
            for (int dz = 0; dz < 2; dz++) {
                int z = rz * 2 + dz;
                for (int x = 0; x < w; x++) {
                    float v = 0;
                    for (int k = -RADIUS; k <= RADIUS; k++) {
                        v += kernel[k + RADIUS] * tmp[Math.min(h - 1, Math.max(0, z + k)) * w + x];
                    }
                    blurredRows[dz * w + x] = v;
                }
            }
            for (int rx = 0; rx < rw; rx++) {
                float avg = (blurredRows[rx * 2]
                                + blurredRows[rx * 2 + 1]
                                + blurredRows[w + rx * 2]
                                + blurredRows[w + rx * 2 + 1])
                        / 4f;
                int value = Math.min(255, Math.max(0, Math.round(avg / (float) MiddleEarthMap.RELIEF_VERTICAL_SCALE)));
                relief.getRaster().setSample(rx, rz, 0, value);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(relief, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}

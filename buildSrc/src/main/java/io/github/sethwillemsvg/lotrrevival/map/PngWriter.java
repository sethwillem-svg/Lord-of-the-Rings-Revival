package io.github.sethwillemsvg.lotrrevival.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimal streaming PNG encoder, so a 6,400 x 8,000 16-bit image never has to sit in memory as a raster.
 * Rows are produced one at a time and written with the Sub filter, which makes the flat ocean cost almost
 * nothing. Output is deterministic for a given JDK.
 */
final class PngWriter {
    interface RowProducer {
        /** Fills {@code raw} with the big-endian samples of row {@code y}. */
        void fill(int y, byte[] raw);
    }

    private PngWriter() {}

    static void writeGray16(Path file, int width, int height, RowProducer rows) throws IOException {
        write(file, width, height, 16, 0, 2, rows);
    }

    static void writeRgb8(Path file, int width, int height, RowProducer rows) throws IOException {
        write(file, width, height, 8, 2, 3, rows);
    }

    private static void write(Path file, int width, int height, int bitDepth, int colorType, int bytesPerPixel, RowProducer rows)
            throws IOException {
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(1 << 20);
        Deflater deflater = new Deflater(6);
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed, deflater, 1 << 16)) {
            int rowBytes = width * bytesPerPixel;
            byte[] raw = new byte[rowBytes];
            byte[] filtered = new byte[rowBytes + 1];
            filtered[0] = 1; // Sub
            for (int y = 0; y < height; y++) {
                rows.fill(y, raw);
                for (int i = 0; i < rowBytes; i++) {
                    filtered[i + 1] = (byte) (raw[i] - (i >= bytesPerPixel ? raw[i - bytesPerPixel] : 0));
                }
                out.write(filtered, 0, filtered.length);
            }
        } finally {
            deflater.end();
        }

        try (OutputStream os = Files.newOutputStream(file)) {
            os.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            byte[] ihdr = new byte[13];
            putInt(ihdr, 0, width);
            putInt(ihdr, 4, height);
            ihdr[8] = (byte) bitDepth;
            ihdr[9] = (byte) colorType;
            chunk(os, "IHDR", ihdr);
            chunk(os, "IDAT", compressed.toByteArray());
            chunk(os, "IEND", new byte[0]);
        }
    }

    private static void chunk(OutputStream os, String type, byte[] data) throws IOException {
        byte[] length = new byte[4];
        putInt(length, 0, data.length);
        os.write(length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        os.write(typeBytes);
        os.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        putInt(crcBytes, 0, (int) crc.getValue());
        os.write(crcBytes);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}

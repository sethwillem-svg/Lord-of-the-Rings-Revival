package io.github.sethwillemsvg.lotrrevival.structures.dev;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.StructureStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;

/**
 * Dev mode {@code -PstructureUpgrade=<work dir>} (system property {@code lotr_revival.structureUpgrade}): loads every
 * {@code <work dir>/<id>/raw.nbt} through vanilla's structure data fixer, writes {@code upgraded.nbt} next to it at the
 * current DataVersion, then the caller stops the server. tools/structure-import's build continues from upgraded.nbt.
 */
public final class StructureUpgrade {
    public static final String PROPERTY = "lotr_revival.structureUpgrade";
    static final String SYS = StructureStats.SYS;

    private StructureUpgrade() {}

    public static CompoundTag upgrade(CompoundTag raw) {
        int from = raw.contains("DataVersion") ? raw.getInt("DataVersion") : 500;
        int to = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        CompoundTag out = DataFixTypes.STRUCTURE.update(DataFixers.getDataFixer(), raw, from, to);
        out.putInt("DataVersion", to);
        return out;
    }

    public static boolean runIfAsked(MinecraftServer server) {
        String dir = System.getProperty(PROPERTY);
        if (dir == null || dir.isBlank()) return false;
        Path root = Path.of(dir).toAbsolutePath();
        int done = 0;
        try (Stream<Path> s = Files.list(root)) {
            List<Path> raws = s.map(p -> p.resolve("raw.nbt")).filter(Files::isRegularFile).toList();
            for (Path raw : raws) {
                try {
                    CompoundTag tag = NbtIo.readCompressed(raw, NbtAccounter.unlimitedHeap());
                    int from = tag.getInt("DataVersion");
                    CompoundTag up = upgrade(tag);
                    NbtIo.writeCompressed(up, raw.resolveSibling("upgraded.nbt"));
                    LotrLog.info(SYS, "structure upgrade {}: DataVersion {} -> {}", raw.getParent().getFileName(), from,
                            up.getInt("DataVersion"));
                    done++;
                } catch (Exception e) {
                    LotrLog.error(SYS, "structure upgrade failed for " + raw, e);
                }
            }
        } catch (Exception e) {
            LotrLog.error(SYS, "structure upgrade: cannot list " + root, e);
        }
        LotrLog.info(SYS, "structure upgrade: {} templates upgraded in {}", done, root);
        return true;
    }
}

package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPermission;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code lotrrevival:progression/update_banner}, serverbound: the edited settings of a banner. The server validates
 * everything (reach, ownership, ranges, slot count, fellowship entries) and ignores a request that fails.
 *
 * <p>Permission sets travel as bit masks over {@link BannerPermission#ordinal()}. A new player entry carries the
 * typed name and the nil UUID; the server resolves the name. Fellowship names are taken from the server.
 *
 * @param entries whitelist entries after the owner's slot, in order
 * @param customRange operator range 1 to 64, or 0 for none (only operators may change it)
 */
public record UpdateBannerPayload(
        BlockPos pos,
        BannerSettings.Mode mode,
        int threshold,
        List<EntryEdit> entries,
        int defaultsMask,
        boolean selfProtection,
        int customRange)
        implements CustomPacketPayload {
    public static final Type<UpdateBannerPayload> TYPE = new Type<>(LOTRRevival.id("progression/update_banner"));

    public static final int NAME_MAX = 32;
    public static final UUID NIL = new UUID(0L, 0L);

    /** One edited whitelist entry. */
    public record EntryEdit(BannerSettings.Kind kind, UUID id, String name, int permissionMask) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, EntryEdit> ENTRY = StreamCodec.composite(
            ByteBufCodecs.idMapper(
                    i -> BannerSettings.Kind.values()[Math.floorMod(i, BannerSettings.Kind.values().length)],
                    BannerSettings.Kind::ordinal),
            EntryEdit::kind,
            UUIDUtil.STREAM_CODEC,
            EntryEdit::id,
            ByteBufCodecs.stringUtf8(NAME_MAX),
            EntryEdit::name,
            ByteBufCodecs.VAR_INT,
            EntryEdit::permissionMask,
            EntryEdit::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBannerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                ByteBufCodecs.VAR_INT.encode(buf, payload.mode().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, payload.threshold());
                ByteBufCodecs.VAR_INT.encode(buf, payload.entries().size());
                for (EntryEdit entry : payload.entries()) {
                    ENTRY.encode(buf, entry);
                }
                ByteBufCodecs.VAR_INT.encode(buf, payload.defaultsMask());
                ByteBufCodecs.BOOL.encode(buf, payload.selfProtection());
                ByteBufCodecs.VAR_INT.encode(buf, payload.customRange());
            },
            buf -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                BannerSettings.Mode[] modes = BannerSettings.Mode.values();
                BannerSettings.Mode mode = modes[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), modes.length)];
                int threshold = ByteBufCodecs.VAR_INT.decode(buf);
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > BannerSettings.MAX_WHITELIST_SIZE) {
                    throw new io.netty.handler.codec.DecoderException("Too many banner entries: " + count);
                }
                List<EntryEdit> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(ENTRY.decode(buf));
                }
                return new UpdateBannerPayload(
                        pos,
                        mode,
                        threshold,
                        List.copyOf(entries),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf));
            });

    /** Packs a permission set into a bit mask. */
    public static int mask(Set<BannerPermission> permissions) {
        int mask = 0;
        for (BannerPermission permission : permissions) {
            mask |= 1 << permission.ordinal();
        }
        return mask;
    }

    /** Unpacks a bit mask; bits beyond the permissions are ignored. */
    public static Set<BannerPermission> permissions(int mask) {
        List<BannerPermission> set = new ArrayList<>();
        for (BannerPermission permission : BannerPermission.values()) {
            if ((mask & (1 << permission.ordinal())) != 0) {
                set.add(permission);
            }
        }
        return BannerPermission.setOf(set);
    }

    /** Whether the mask has bits that name no permission. */
    public static boolean hasUnknownBits(int mask) {
        return mask < 0 || (mask >> BannerPermission.values().length) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

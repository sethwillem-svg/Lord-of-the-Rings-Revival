package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code lotrrevival:progression/open_banner_editor}, clientbound: open the banner edit screen with the banner's full
 * settings (the whitelist size is part of the settings) and whether the viewer is an operator (who also sees the
 * custom range).
 */
public record OpenBannerEditorPayload(BlockPos pos, ResourceLocation design, BannerSettings settings, boolean op)
        implements CustomPacketPayload {
    public static final Type<OpenBannerEditorPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/open_banner_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBannerEditorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenBannerEditorPayload::pos,
            CoreNetwork.ID_CODEC,
            OpenBannerEditorPayload::design,
            ByteBufCodecs.fromCodecWithRegistries(BannerSettings.CODEC),
            OpenBannerEditorPayload::settings,
            ByteBufCodecs.BOOL,
            OpenBannerEditorPayload::op,
            OpenBannerEditorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

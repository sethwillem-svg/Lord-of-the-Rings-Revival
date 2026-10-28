package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of both banner blocks. It holds the design and the authoritative protection settings; the
 * per-dimension {@link BannerIndex} keeps a copy for queries.
 */
public class FactionBannerBlockEntity extends BlockEntity {
    /** Used when a banner has no design (should not happen for banners made from the creative tab). */
    public static final ResourceLocation FALLBACK_DESIGN = BannerDesign.idFor(Factions.GONDOR);

    private ResourceLocation design = FALLBACK_DESIGN;
    private BannerSettings settings = BannerSettings.unowned();
    /** Whether breaking this banner keeps its settings on the dropped item (false for non-owners). */
    private boolean dropSettings = true;

    public FactionBannerBlockEntity(BlockPos pos, BlockState state) {
        super(BannerBlocks.BLOCK_ENTITY.get(), pos, state);
    }

    public ResourceLocation design() {
        return design;
    }

    public BannerSettings settings() {
        return settings;
    }

    public void setDesign(ResourceLocation newDesign) {
        this.design = newDesign;
        setChanged();
        sync();
        reindex();
    }

    /** Replaces the settings (an edit, the placement, or a command) and updates the index. */
    public void setSettings(BannerSettings newSettings) {
        this.settings = newSettings;
        setChanged();
        reindex();
    }

    /** Sets design and settings together (placement). */
    public void initialise(ResourceLocation newDesign, BannerSettings newSettings) {
        this.design = newDesign;
        this.settings = newSettings;
        setChanged();
        sync();
        reindex();
    }

    public void setDropSettings(boolean value) {
        this.dropSettings = value;
    }

    /** The range this banner protects: custom range, else by base block; 0 if decorative or its design is gone. */
    public int effectiveRange() {
        if (level == null || !(getBlockState().getBlock() instanceof FactionBannerBlock)) {
            return 0;
        }
        if (level.registryAccess().registry(BannerDesign.REGISTRY).map(r -> !r.containsKey(design)).orElse(true)) {
            // A datapack removed the design: the banner is decorative until the design returns.
            return 0;
        }
        if (settings.customRange() > 0) {
            return settings.customRange();
        }
        return BannerBases.rangeOf(level.getBlockState(worldPosition.below()));
    }

    /** Brings the per-dimension index in line with this banner (server only). */
    public void reindex() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BannerIndex index = BannerIndex.get(serverLevel);
        int range = effectiveRange();
        if (range > 0) {
            index.put(new BannerEntry(worldPosition, design, range, settings));
        } else {
            index.remove(worldPosition);
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        reindex();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("design", design.toString());
        BannerSettings.CODEC.encodeStart(NbtOps.INSTANCE, settings).result().ifPresent(t -> tag.put("settings", t));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("design")) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("design"));
            if (parsed != null) {
                design = parsed;
            }
        }
        if (tag.contains("settings")) {
            Optional<BannerSettings> parsed = BannerSettings.CODEC
                    .parse(NbtOps.INSTANCE, tag.get("settings"))
                    .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Bad banner settings at {}: {}", worldPosition, error));
            parsed.ifPresent(value -> settings = value);
        }
    }

    /** Clients only need the design (for rendering); the settings stay on the server. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("design", design.toString());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        ResourceLocation fromItem = input.get(BannerComponents.DESIGN.get());
        if (fromItem != null) {
            design = fromItem;
        }
        BannerSettings carried = input.get(BannerComponents.PROTECTION.get());
        if (carried != null) {
            settings = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(BannerComponents.DESIGN.get(), design);
        if (dropSettings && !settings.structure() && settings.owner().isPresent()) {
            components.set(BannerComponents.PROTECTION.get(), settings);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("design");
        tag.remove("settings");
    }
}

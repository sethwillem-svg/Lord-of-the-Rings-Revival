package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** One fellowship. Mutated only by {@link Fellowships}, which marks the {@link FellowshipData} dirty. */
public final class Fellowship {
    private final UUID id;
    private String name;
    private UUID owner;
    private final Set<UUID> admins = new LinkedHashSet<>();
    private final Set<UUID> members = new LinkedHashSet<>();
    private ItemStack icon = ItemStack.EMPTY;
    private boolean preventPvp = true;
    private boolean preventUnitFriendlyFire = true;
    private boolean showMap = true;
    /** Pending invites: invitee to inviter. */
    private final Map<UUID, UUID> invites = new LinkedHashMap<>();
    private final long created;

    public Fellowship(UUID id, String name, UUID owner, long created) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.created = created;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> admins() {
        return admins;
    }

    /** Plain members only (not the owner, not admins). */
    public Set<UUID> members() {
        return members;
    }

    public Map<UUID, UUID> invites() {
        return invites;
    }

    public ItemStack icon() {
        return icon;
    }

    void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    public long created() {
        return created;
    }

    /** Owner + admins + members. */
    public int size() {
        return 1 + admins.size() + members.size();
    }

    public boolean isMember(UUID player) {
        return role(player).isPresent();
    }

    public Optional<FellowshipRole> role(UUID player) {
        if (player.equals(owner)) {
            return Optional.of(FellowshipRole.OWNER);
        }
        if (admins.contains(player)) {
            return Optional.of(FellowshipRole.ADMIN);
        }
        if (members.contains(player)) {
            return Optional.of(FellowshipRole.MEMBER);
        }
        return Optional.empty();
    }

    /** Owner first, then admins, then members. */
    public List<UUID> allMembers() {
        List<UUID> all = new ArrayList<>(size());
        all.add(owner);
        all.addAll(admins);
        all.addAll(members);
        return all;
    }

    public boolean option(FellowshipOption option) {
        return switch (option) {
            case PREVENT_PVP -> preventPvp;
            case PREVENT_UNIT_FRIENDLY_FIRE -> preventUnitFriendlyFire;
            case SHOW_MAP -> showMap;
        };
    }

    void setOption(FellowshipOption option, boolean value) {
        switch (option) {
            case PREVENT_PVP -> preventPvp = value;
            case PREVENT_UNIT_FRIENDLY_FIRE -> preventUnitFriendlyFire = value;
            case SHOW_MAP -> showMap = value;
        }
    }

    // ------------------------------------------------------------------ persistence

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("owner", owner);
        tag.put("admins", uuidList(admins));
        tag.put("members", uuidList(members));
        if (!icon.isEmpty()) {
            tag.put("icon", icon.save(registries));
        }
        tag.putBoolean("prevent_pvp", preventPvp);
        tag.putBoolean("prevent_unit_ff", preventUnitFriendlyFire);
        tag.putBoolean("show_map", showMap);
        ListTag inviteList = new ListTag();
        invites.forEach((invitee, inviter) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("invitee", invitee);
            row.putUUID("inviter", inviter);
            inviteList.add(row);
        });
        tag.put("invites", inviteList);
        tag.putLong("created", created);
        return tag;
    }

    static Fellowship load(CompoundTag tag, HolderLookup.Provider registries) {
        Fellowship fellowship =
                new Fellowship(tag.getUUID("id"), tag.getString("name"), tag.getUUID("owner"), tag.getLong("created"));
        readUuids(tag.getList("admins", Tag.TAG_INT_ARRAY), fellowship.admins);
        readUuids(tag.getList("members", Tag.TAG_INT_ARRAY), fellowship.members);
        fellowship.admins.remove(fellowship.owner);
        fellowship.members.remove(fellowship.owner);
        fellowship.members.removeAll(fellowship.admins);
        if (tag.contains("icon", Tag.TAG_COMPOUND)) {
            fellowship.icon = ItemStack.parse(registries, tag.getCompound("icon")).orElse(ItemStack.EMPTY);
        }
        fellowship.preventPvp = !tag.contains("prevent_pvp") || tag.getBoolean("prevent_pvp");
        fellowship.preventUnitFriendlyFire = !tag.contains("prevent_unit_ff") || tag.getBoolean("prevent_unit_ff");
        fellowship.showMap = !tag.contains("show_map") || tag.getBoolean("show_map");
        ListTag inviteList = tag.getList("invites", Tag.TAG_COMPOUND);
        for (int i = 0; i < inviteList.size(); i++) {
            CompoundTag row = inviteList.getCompound(i);
            if (row.hasUUID("invitee") && row.hasUUID("inviter")) {
                fellowship.invites.put(row.getUUID("invitee"), row.getUUID("inviter"));
            }
        }
        return fellowship;
    }

    private static ListTag uuidList(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID uuid : ids) {
            list.add(NbtUtils.createUUID(uuid));
        }
        return list;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            into.add(NbtUtils.loadUUID(list.get(i)));
        }
    }
}

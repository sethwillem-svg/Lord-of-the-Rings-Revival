package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** What the client is told about a fellowship it belongs to, and about an invite it holds. */
public final class FellowshipView {
    private FellowshipView() {}

    /** A member row: role, online marker and the member's selected title with its colour. */
    public record Member(
            UUID id,
            String name,
            FellowshipRole role,
            boolean online,
            Optional<ResourceLocation> title,
            String color,
            boolean feminine) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> CODEC = new StreamCodec<>() {
            @Override
            public Member decode(RegistryFriendlyByteBuf buf) {
                UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                String name = buf.readUtf(64);
                FellowshipRole role = buf.readEnum(FellowshipRole.class);
                boolean online = buf.readBoolean();
                Optional<ResourceLocation> title = ByteBufCodecs.optional(CoreNetwork.ID_CODEC).decode(buf);
                String color = buf.readUtf(32);
                boolean feminine = buf.readBoolean();
                return new Member(id, name, role, online, title, color, feminine);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Member value) {
                UUIDUtil.STREAM_CODEC.encode(buf, value.id);
                buf.writeUtf(value.name, 64);
                buf.writeEnum(value.role);
                buf.writeBoolean(value.online);
                ByteBufCodecs.optional(CoreNetwork.ID_CODEC).encode(buf, value.title);
                buf.writeUtf(value.color, 32);
                buf.writeBoolean(value.feminine);
            }
        };
    }

    /** One fellowship: icon, options and members, owner first. */
    public record Entry(
            UUID id,
            String name,
            ItemStack icon,
            boolean preventPvp,
            boolean preventUnitFriendlyFire,
            boolean showMap,
            List<Member> members) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = new StreamCodec<>() {
            @Override
            public Entry decode(RegistryFriendlyByteBuf buf) {
                UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                String name = buf.readUtf(Fellowships.NAME_MAX);
                ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                boolean pvp = buf.readBoolean();
                boolean unit = buf.readBoolean();
                boolean map = buf.readBoolean();
                int count = buf.readVarInt();
                if (count > CoreNetwork.MAX_COLLECTION) {
                    throw new io.netty.handler.codec.DecoderException("Too many fellowship members: " + count);
                }
                List<Member> members = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    members.add(Member.CODEC.decode(buf));
                }
                return new Entry(id, name, icon, pvp, unit, map, members);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Entry value) {
                UUIDUtil.STREAM_CODEC.encode(buf, value.id);
                buf.writeUtf(value.name, Fellowships.NAME_MAX);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, value.icon);
                buf.writeBoolean(value.preventPvp);
                buf.writeBoolean(value.preventUnitFriendlyFire);
                buf.writeBoolean(value.showMap);
                buf.writeVarInt(value.members.size());
                for (Member member : value.members) {
                    Member.CODEC.encode(buf, member);
                }
            }
        };

        /** The role the given player has here, if any. */
        public Optional<FellowshipRole> roleOf(UUID player) {
            for (Member member : members) {
                if (member.id.equals(player)) {
                    return Optional.of(member.role);
                }
            }
            return Optional.empty();
        }
    }

    /** A pending invite for the viewer. */
    public record Invite(UUID fellowship, String name, String inviter) {
        static final StreamCodec<RegistryFriendlyByteBuf, Invite> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                Invite::fellowship,
                ByteBufCodecs.stringUtf8(Fellowships.NAME_MAX),
                Invite::name,
                ByteBufCodecs.stringUtf8(64),
                Invite::inviter,
                Invite::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, List<Entry>> ENTRIES =
            Entry.CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION));
    public static final StreamCodec<RegistryFriendlyByteBuf, List<Invite>> INVITES =
            Invite.CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION));
}

package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.AlignmentCreditor;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Fellowship damage rules. Each side resolves to an owner: a player is themselves, a hired unit (or fighting mount) is
 * the player it is credited to, a projectile is whoever shot it. Different owners who share a fellowship cannot hurt
 * each other: two players by Prevent PvP, and when either side is a unit by Prevent hired-unit friendly fire. The
 * rules only cancel damage; they never touch alignment or retaliation.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class FellowshipDamage {
    /** An owner and whether the entity was a unit rather than the player in person. */
    public record Side(UUID owner, boolean unit) {}

    private FellowshipDamage() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) {
            return;
        }
        Optional<Side> victim = resolve(event.getEntity());
        Optional<Side> attacker = resolveAttacker(event.getSource());
        if (victim.isPresent() && attacker.isPresent() && blocks(server, attacker.get(), victim.get())) {
            event.setCanceled(true);
        }
    }

    /** True when the fellowship rules forbid {@code attacker} from hurting {@code victim}. */
    public static boolean blocks(MinecraftServer server, Side attacker, Side victim) {
        if (attacker.owner().equals(victim.owner())) {
            return false;
        }
        if (attacker.unit() || victim.unit()) {
            return FellowshipRules.protectsUnits(server, attacker.owner(), victim.owner());
        }
        return FellowshipRules.preventsPvp(server, attacker.owner(), victim.owner());
    }

    /** The attacker side of a damage source: the causing entity, falling back to the direct one. */
    public static Optional<Side> resolveAttacker(DamageSource source) {
        Entity cause = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        return cause == null ? Optional.empty() : resolve(cause);
    }

    /** Resolves an entity to its owner side, following projectiles to their shooter. */
    public static Optional<Side> resolve(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getOwner() != null) {
            return resolve(projectile.getOwner());
        }
        if (entity instanceof Player player) {
            return Optional.of(new Side(player.getUUID(), false));
        }
        if (entity instanceof AlignmentCreditor creditor) {
            return creditor.creditedPlayer().map(owner -> new Side(owner, true));
        }
        return Optional.empty();
    }
}

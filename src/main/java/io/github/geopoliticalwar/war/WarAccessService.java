package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.WarRecord;
import io.github.geopoliticalwar.domain.war.WarSide;
import io.github.geopoliticalwar.governance.NationMembershipService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Resolves whether a player may act as a formal war participant. */
public final class WarAccessService {
    private WarAccessService() {
    }

    public static boolean canDamageClaim(ServerPlayer player, ServerLevel level, Claim claim) {
        Optional<NationId> actorNation = NationMembershipService.nationFor(level, player.getUUID());
        Optional<NationId> targetNation = nationOwner(claim);
        if (actorNation.isEmpty() || targetNation.isEmpty() || actorNation.equals(targetNation)) {
            return false;
        }
        return WarService.activeForClaim(level, claim.id())
                .flatMap(war -> war.sideOf(actorNation.get()).map(side -> new WarDecision(war, side)))
                .map(decision -> decision.side() != decision.war().sideOf(targetNation.get()).orElse(decision.side()))
                .orElse(false);
    }

    public static boolean isParticipant(ServerPlayer player, ServerLevel level, WarRecord war) {
        return NationMembershipService.nationFor(level, player.getUUID())
                .map(war::includes)
                .orElse(false);
    }

    private static Optional<NationId> nationOwner(Claim claim) {
        String value = claim.owner().value();
        if (!value.startsWith("nation:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new NationId(java.util.UUID.fromString(value.substring("nation:".length()))));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private record WarDecision(WarRecord war, WarSide side) {
    }
}

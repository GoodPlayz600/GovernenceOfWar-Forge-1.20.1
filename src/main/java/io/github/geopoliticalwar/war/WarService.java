package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.WarGoal;
import io.github.geopoliticalwar.domain.war.WarId;
import io.github.geopoliticalwar.domain.war.WarRecord;
import io.github.geopoliticalwar.domain.war.WarSide;
import io.github.geopoliticalwar.persistence.CapturePointsSavedData;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/** Server-authoritative lifecycle for formal wars and their goals. */
public final class WarService {
    private WarService() {
    }

    public static WarRecord declare(ServerLevel level, NationId attacker, NationId defender, long now) {
        NationsSavedData nations = NationsSavedData.get(level);
        if (nations.get(attacker).isEmpty() || nations.get(defender).isEmpty()) {
            throw new IllegalArgumentException("Both war parties must be existing nations");
        }
        WarRecord war = WarRecord.declare(
                new DimensionId(level.dimension().location().toString()),
                attacker,
                defender,
                now
        );
        WarsSavedData.get(level).add(war);
        return war;
    }

    public static WarRecord addParticipant(ServerLevel level, WarId id, NationId nation, WarSide side, long now) {
        if (NationsSavedData.get(level).get(nation).isEmpty()) {
            throw new IllegalArgumentException("Unknown nation");
        }
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord updated = wars.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown war"))
                .addParticipant(nation, side, now);
        wars.replace(updated);
        return updated;
    }

    public static WarRecord addClaimGoal(
            ServerLevel level,
            WarId id,
            NationId beneficiary,
            ClaimId claimId,
            long now
    ) {
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        Claim claim = claims.get(claimId).orElseThrow(() -> new IllegalArgumentException("Unknown claim"));
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord war = wars.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown war"));
        if (war.status() != io.github.geopoliticalwar.domain.war.WarStatus.DECLARED) {
            throw new IllegalArgumentException("Goals cannot be added after activation");
        }
        if (!war.includes(beneficiary)) {
            throw new IllegalArgumentException("Goal beneficiary must participate in the war");
        }
        NationId targetOwner = nationOwner(claim)
                .orElseThrow(() -> new IllegalArgumentException("Conquest targets must be nation-owned claims"));
        if (war.sideOf(targetOwner).orElseThrow(() -> new IllegalArgumentException("Target owner is not a war participant"))
                != WarSide.DEFENDER || war.sideOf(beneficiary).orElseThrow() != WarSide.ATTACKER) {
            throw new IllegalArgumentException("Conquest goals must target a defender claim for an attacking nation");
        }
        if (wars.activeForClaim(claimId).filter(other -> !other.id().equals(id)).isPresent()) {
            throw new IllegalArgumentException("The claim is already a target in an active war");
        }
        if (war.goals().stream().anyMatch(goal -> goal.claim().filter(claimId::equals).isPresent())) {
            throw new IllegalArgumentException("The claim is already a goal in this war");
        }
        WarRecord updated = war.addDimension(new DimensionId(claim.dimension().value()))
                .addGoal(WarGoal.conquestClaim(UUID.randomUUID(), beneficiary, claimId));
        wars.replace(updated);
        return updated;
    }

    public static WarRecord addCaptureGoal(
            ServerLevel level,
            WarId id,
            NationId beneficiary,
            UUID pointId,
            long requiredScore
    ) {
        var point = CapturePointsSavedData.get(level).all().get(pointId);
        if (point == null) {
            throw new IllegalArgumentException("Unknown capture point");
        }
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord war = wars.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown war"));
        if (war.status() != io.github.geopoliticalwar.domain.war.WarStatus.DECLARED) {
            throw new IllegalArgumentException("Goals cannot be added after activation");
        }
        if (!war.includes(beneficiary)) {
            throw new IllegalArgumentException("Goal beneficiary must participate in the war");
        }
        if (war.sideOf(beneficiary).orElseThrow() != WarSide.ATTACKER) {
            throw new IllegalArgumentException("Capture goal beneficiary must be an attacker");
        }
        if (point.owner().isPresent()
                && war.sideOf(point.owner().get()).orElse(null) != WarSide.DEFENDER) {
            throw new IllegalArgumentException("Owned capture points must belong to a defender");
        }
        if (requiredScore <= 0) {
            throw new IllegalArgumentException("Capture goal hold time must be positive");
        }
        if (wars.activeForCapturePoint(pointId).filter(other -> !other.id().equals(id)).isPresent()) {
            throw new IllegalArgumentException("The capture point is already targeted by an active war");
        }
        if (war.goals().stream().anyMatch(goal -> goal.capturePoint().filter(pointId::equals).isPresent())) {
            throw new IllegalArgumentException("The capture point is already a goal in this war");
        }
        WarRecord updated = war.addDimension(new DimensionId(point.dimension().value()))
                .addGoal(WarGoal.capturePoint(UUID.randomUUID(), beneficiary, pointId, requiredScore));
        wars.replace(updated);
        return updated;
    }

    public static WarRecord activate(ServerLevel level, WarId id, long now) {
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord current = wars.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown war"));
        if (current.goals().isEmpty()) {
            throw new IllegalArgumentException("A war needs at least one goal before activation");
        }
        for (WarGoal goal : current.goals()) {
            if (goal.claim().isPresent() && ClaimsSavedData.get(level).get(goal.claim().get()).isEmpty()) {
                throw new IllegalArgumentException("War goal references a missing claim");
            }
            if (goal.capturePoint().isPresent()
                    && !CapturePointsSavedData.get(level).all().containsKey(goal.capturePoint().get())) {
                throw new IllegalArgumentException("War goal references a missing capture point");
            }
        }
        if (current.dimensions().stream().anyMatch(dimension -> wars.activeForDimension(dimension)
                .filter(other -> !other.id().equals(id))
                .isPresent())) {
            throw new IllegalArgumentException("Another active war already uses one of these dimensions");
        }
        WarRecord updated = current.activate(now);
        wars.replace(updated);
        return updated;
    }

    public static WarRecord markCapturePointCaptured(ServerLevel level, UUID pointId, NationId capturingNation) {
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord war = wars.activeForCapturePoint(pointId).orElseThrow(() -> new IllegalArgumentException("No active war goal for capture point"));
        if (!war.includes(capturingNation)) {
            throw new IllegalArgumentException("Capturing nation is not a war participant");
        }
        WarGoal goal = war.goals().stream()
                .filter(value -> value.capturePoint().filter(pointId::equals).isPresent() && !value.achieved())
                .filter(value -> value.beneficiary().equals(capturingNation))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Capture point is not a goal for this nation"));
        WarRecord updated = war.withGoal(goal.complete());
        wars.replace(updated);
        return updated;
    }

    public static WarRecord markClaimConquered(ServerLevel level, WarId id, ClaimId claimId, NationId capturingNation) {
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord war = wars.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown war"));
        if (!war.isActive() || !war.includes(capturingNation)) {
            throw new IllegalArgumentException("The war must be active and the nation must participate");
        }
        WarGoal goal = war.goals().stream()
                .filter(value -> value.claim().filter(claimId::equals).isPresent() && !value.achieved())
                .filter(value -> value.beneficiary().equals(capturingNation))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Claim is not an active goal for this nation"));
        WarRecord updated = war.withGoal(goal.complete());
        wars.replace(updated);
        return updated;
    }

    public static Optional<WarRecord> activeForDimension(ServerLevel level) {
        return WarsSavedData.get(level).activeForDimension(new DimensionId(level.dimension().location().toString()));
    }

    public static Optional<WarRecord> activeForClaim(ServerLevel level, ClaimId claim) {
        return WarsSavedData.get(level).activeForClaim(claim);
    }

    public static Optional<WarRecord> activeForCapturePoint(ServerLevel level, UUID point) {
        return WarsSavedData.get(level).activeForCapturePoint(point);
    }

    private static Optional<NationId> nationOwner(Claim claim) {
        String value = claim.owner().value();
        if (!value.startsWith("nation:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new NationId(UUID.fromString(value.substring("nation:".length()))));
        } catch (IllegalArgumentException invalidOwner) {
            return Optional.empty();
        }
    }
}

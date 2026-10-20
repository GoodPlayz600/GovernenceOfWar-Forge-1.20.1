package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WarGoal(
        UUID id,
        NationId beneficiary,
        WarGoalType type,
        Optional<ClaimId> claim,
        Optional<UUID> capturePoint,
        long requiredScore,
        long score,
        boolean achieved
) {
    public WarGoal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(beneficiary, "beneficiary");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(capturePoint, "capturePoint");
        if (requiredScore <= 0 || score < 0 || score > requiredScore) {
            throw new IllegalArgumentException("Invalid war goal score");
        }
        if (achieved && score < requiredScore) {
            throw new IllegalArgumentException("An achieved goal must have full score");
        }
        if (type == WarGoalType.CAPTURE_POINT && (capturePoint.isEmpty() || claim.isPresent())) {
            throw new IllegalArgumentException("Capture-point goals require only a capture point");
        }
        if (type == WarGoalType.CONQUEST_CLAIM && (claim.isEmpty() || capturePoint.isPresent())) {
            throw new IllegalArgumentException("Conquest goals require only a claim");
        }
    }

    public static WarGoal capturePoint(UUID id, NationId beneficiary, UUID point, long requiredScore) {
        return new WarGoal(id, beneficiary, WarGoalType.CAPTURE_POINT, Optional.empty(), Optional.of(point), requiredScore, 0L, false);
    }

    public static WarGoal conquestClaim(UUID id, NationId beneficiary, ClaimId claim) {
        return new WarGoal(id, beneficiary, WarGoalType.CONQUEST_CLAIM, Optional.of(claim), Optional.empty(), 1L, 0L, false);
    }

    public WarGoal advance(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Goal progress cannot be negative");
        }
        long updated = Math.min(requiredScore, Math.addExact(score, amount));
        return new WarGoal(id, beneficiary, type, claim, capturePoint, requiredScore, updated, updated >= requiredScore);
    }

    public WarGoal complete() {
        return new WarGoal(id, beneficiary, type, claim, capturePoint, requiredScore, requiredScore, true);
    }
}

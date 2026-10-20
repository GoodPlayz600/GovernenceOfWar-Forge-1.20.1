package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record OccupationRecord(
        ClaimId claim,
        WarId war,
        NationId occupyingNation,
        PrincipalId formerOwner,
        OccupationRights rights,
        long occupiedAtMillis,
        long settlementEligibleAtMillis,
        Optional<Long> settledAtMillis,
        Set<UUID> formerPopulation
) {
    public OccupationRecord {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(war, "war");
        Objects.requireNonNull(occupyingNation, "occupyingNation");
        Objects.requireNonNull(formerOwner, "formerOwner");
        Objects.requireNonNull(rights, "rights");
        Objects.requireNonNull(settledAtMillis, "settledAtMillis");
        Objects.requireNonNull(formerPopulation, "formerPopulation");
        if (occupiedAtMillis < 0 || settlementEligibleAtMillis < occupiedAtMillis
                || settledAtMillis.map(value -> value < settlementEligibleAtMillis).orElse(false)) {
            throw new IllegalArgumentException("Invalid occupation timestamps");
        }
        formerPopulation = Set.copyOf(formerPopulation);
    }

    public OccupationRecord(
            ClaimId claim,
            WarId war,
            NationId occupyingNation,
            PrincipalId formerOwner,
            OccupationRights rights,
            long occupiedAtMillis,
            long settlementEligibleAtMillis,
            Optional<Long> settledAtMillis
    ) {
        this(claim, war, occupyingNation, formerOwner, rights, occupiedAtMillis,
                settlementEligibleAtMillis, settledAtMillis, Set.of());
    }

    public boolean eligible(long now) {
        return now >= settlementEligibleAtMillis && settledAtMillis.isEmpty();
    }

    public OccupationRecord settle(long now) {
        if (!eligible(now)) {
            throw new IllegalArgumentException("Occupation is not ready to settle");
        }
        return new OccupationRecord(claim, war, occupyingNation, formerOwner, rights, occupiedAtMillis,
                settlementEligibleAtMillis, Optional.of(now), formerPopulation);
    }
}

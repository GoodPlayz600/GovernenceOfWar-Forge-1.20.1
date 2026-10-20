package io.github.geopoliticalwar.domain.economy;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;

import java.util.Objects;
import java.util.UUID;

/** Auditable ownership transition without replacing Claim's canonical current owner. */
public record OwnershipRecord(
        UUID id,
        ClaimId claim,
        PrincipalId previousOwner,
        PrincipalId newOwner,
        OwnershipChangeReason reason,
        UUID actor,
        long changedAtMillis
) {
    public OwnershipRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(previousOwner, "previousOwner");
        Objects.requireNonNull(newOwner, "newOwner");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (changedAtMillis < 0L || previousOwner.equals(newOwner)) {
            throw new IllegalArgumentException("Invalid ownership record");
        }
    }
}

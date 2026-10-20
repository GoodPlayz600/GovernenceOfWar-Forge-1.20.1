package io.github.geopoliticalwar.domain.claim;

public final class ClaimConflictException extends IllegalArgumentException {
    public ClaimConflictException(ClaimId existingClaim, ClaimId conflictingClaim) {
        super("Claim " + conflictingClaim + " intersects existing claim " + existingClaim);
    }
}

package io.github.geopoliticalwar.domain.claim;

/** Pure population-to-claim capacity arithmetic. */
public final class ClaimCapacityPolicy {
    private ClaimCapacityPolicy() {
    }

    public static int capacity(long population, int baseCapacity, int populationPerAdditionalClaim, int hardMaximum) {
        if (baseCapacity < 0 || populationPerAdditionalClaim <= 0 || hardMaximum < 0) {
            throw new IllegalArgumentException("Invalid claim capacity policy");
        }
        long additional = Math.max(0L, population) / populationPerAdditionalClaim;
        long result = Math.min((long) hardMaximum, (long) baseCapacity + additional);
        return (int) Math.max(0L, result);
    }
}

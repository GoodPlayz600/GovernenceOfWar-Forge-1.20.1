package io.github.geopoliticalwar.domain.war;

/** Scales capture effort from territory size, population, and active defenders. */
public final class CaptureDifficultyPolicy {
    private CaptureDifficultyPolicy() {
    }

    public static long requiredSeconds(
            long baseSeconds,
            long claimVolume,
            long defenderPopulation,
            int activeDefenders,
            int activeAttackers
    ) {
        if (baseSeconds <= 0 || claimVolume < 0 || defenderPopulation < 0
                || activeDefenders < 0 || activeAttackers < 0) {
            throw new IllegalArgumentException("Invalid capture difficulty inputs");
        }
        long sizeFactor = 1L + Math.min(4L, logarithmicFactor(claimVolume));
        long populationFactor = 1L + Math.min(4L, defenderPopulation / 25L);
        long defenderFactor = 1L + Math.min(3L, activeDefenders);
        long attackerRelief = 1L + Math.min(3L, Math.max(0, activeAttackers - 1));
        try {
            long scaled = Math.multiplyExact(baseSeconds, sizeFactor);
            scaled = Math.multiplyExact(scaled, populationFactor);
            scaled = Math.multiplyExact(scaled, defenderFactor);
            return Math.max(1L, scaled / attackerRelief);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long logarithmicFactor(long volume) {
        if (volume <= 1L) {
            return 0L;
        }
        long factor = 0L;
        while (volume >= 4_096L && factor < 4L) {
            volume /= 4_096L;
            factor++;
        }
        return factor;
    }
}

package io.github.geopoliticalwar.domain.claim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimCapacityPolicyTest {
    @Test
    void capacityGrowsWithPopulationAndStopsAtHardMaximum() {
        assertEquals(1, ClaimCapacityPolicy.capacity(1L, 1, 4, 256));
        assertEquals(1, ClaimCapacityPolicy.capacity(3L, 1, 4, 256));
        assertEquals(2, ClaimCapacityPolicy.capacity(4L, 1, 4, 256));
        assertEquals(126, ClaimCapacityPolicy.capacity(500L, 1, 4, 256));
        assertEquals(256, ClaimCapacityPolicy.capacity(Long.MAX_VALUE, 1, 4, 256));
    }

    @Test
    void invalidPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClaimCapacityPolicy.capacity(1L, 1, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ClaimCapacityPolicy.capacity(1L, -1, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> ClaimCapacityPolicy.capacity(1L, 1, 1, -1));
    }
}

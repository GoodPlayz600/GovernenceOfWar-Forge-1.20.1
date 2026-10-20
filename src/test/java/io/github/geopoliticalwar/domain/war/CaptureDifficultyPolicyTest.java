package io.github.geopoliticalwar.domain.war;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureDifficultyPolicyTest {
    @Test
    void smallUnoccupiedTerritoryStaysNearBase() {
        assertEquals(300L, CaptureDifficultyPolicy.requiredSeconds(300L, 100L, 1L, 0, 1));
    }

    @Test
    void populationSizeAndDefendersMakeCaptureHarder() {
        long rural = CaptureDifficultyPolicy.requiredSeconds(300L, 100L, 1L, 0, 1);
        long city = CaptureDifficultyPolicy.requiredSeconds(300L, 16_777_216L, 500L, 3, 1);
        assertEquals(true, city > rural);
    }

    @Test
    void additionalAttackersProvideBoundedRelief() {
        long one = CaptureDifficultyPolicy.requiredSeconds(300L, 100L, 100L, 1, 1);
        long many = CaptureDifficultyPolicy.requiredSeconds(300L, 100L, 100L, 1, 4);
        assertEquals(true, many < one);
        assertEquals(true, many >= one / 4);
    }
}

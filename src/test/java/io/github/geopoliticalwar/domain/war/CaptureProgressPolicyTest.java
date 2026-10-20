package io.github.geopoliticalwar.domain.war;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureProgressPolicyTest {
    @Test
    void captureProgressAdvancesAndCapsAtTheGoal() {
        assertEquals(10L, CaptureProgressPolicy.advance(8L, 5L, 10L));
    }

    @Test
    void contestedProgressDecaysWithoutGoingNegative() {
        assertEquals(4L, CaptureProgressPolicy.decay(10L, 3L, 2L));
        assertEquals(0L, CaptureProgressPolicy.decay(1L, 3L, 2L));
    }
}

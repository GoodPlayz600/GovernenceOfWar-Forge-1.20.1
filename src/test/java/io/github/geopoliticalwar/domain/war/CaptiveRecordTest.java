package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptiveRecordTest {
    @Test
    void captiveCanAccumulateWorkAndTransitionOnce() {
        long now = 100L;
        CaptiveRecord record = CaptiveRecord.active(
                UUID.randomUUID(), ClaimId.random(), NationId.random(), NationId.random(), now
        );
        CaptiveRecord worked = record.worked(60L, 2, 200L);
        CaptiveRecord liberated = worked.transition(CaptiveState.LIBERATED, 300L);

        assertEquals(60L, worked.workedSeconds());
        assertEquals(2, worked.unrest());
        assertEquals(CaptiveState.LIBERATED, liberated.state());
        assertThrows(IllegalArgumentException.class,
                () -> liberated.transition(CaptiveState.REBELLED, 400L));
    }
}

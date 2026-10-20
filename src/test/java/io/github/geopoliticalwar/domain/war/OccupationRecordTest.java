package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.governance.NationId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OccupationRecordTest {
    @Test
    void occupationCannotSettleBeforeItsCooldown() {
        OccupationRecord occupation = new OccupationRecord(
                ClaimId.random(),
                WarId.random(),
                NationId.random(),
                new PrincipalId("nation:former"),
                OccupationRights.SUBJECT,
                100L,
                200L,
                Optional.empty()
        );

        assertThrows(IllegalArgumentException.class, () -> occupation.settle(199L));
        assertEquals(250L, occupation.settle(250L).settledAtMillis().orElseThrow());
    }
}

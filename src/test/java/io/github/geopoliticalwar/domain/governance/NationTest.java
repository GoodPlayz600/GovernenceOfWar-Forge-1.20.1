package io.github.geopoliticalwar.domain.governance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NationTest {
    @Test
    void leaderStartsAsCitizenAndCannotLeaveWithoutTransfer() {
        UUID leader = UUID.randomUUID();
        Nation nation = Nation.create(
                NationId.random(),
                "Alpha",
                leader,
                RegimeType.DEMOCRACY,
                "#ffffff",
                1L,
                100L
        );

        assertEquals(1, nation.citizens().size());
        assertThrows(IllegalArgumentException.class, () -> nation.removeCitizen(leader));
    }
}

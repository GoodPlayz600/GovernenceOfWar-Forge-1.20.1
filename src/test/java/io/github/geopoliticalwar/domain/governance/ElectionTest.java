package io.github.geopoliticalwar.domain.governance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElectionTest {
    @Test
    void oneVotePerVoterCanBeUpdatedAndWinnerIsDeterministic() {
        NationId nation = NationId.random();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID voter = UUID.fromString("00000000-0000-0000-0000-000000000003");

        Election election = Election.open(nation, 0L, 100L)
                .addCandidate(first)
                .addCandidate(second)
                .castVote(voter, first);

        assertEquals(first, election.winner());
        Election changed = election.castVote(voter, second);
        assertEquals(second, changed.winner());
    }

    @Test
    void candidateMustBeRegistered() {
        Election election = Election.open(NationId.random(), 0L, 100L);
        assertThrows(IllegalArgumentException.class, () -> election.castVote(UUID.randomUUID(), UUID.randomUUID()));
    }
}

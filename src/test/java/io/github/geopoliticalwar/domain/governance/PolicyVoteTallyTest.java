package io.github.geopoliticalwar.domain.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyVoteTallyTest {
    @Test
    void quorumAndStrictMajorityAreRequired() {
        assertFalse(new PolicyVoteTally(10, 2, 0).passes(5_000, 5_000));
        assertFalse(new PolicyVoteTally(10, 5, 5).passes(5_000, 5_000));
        assertTrue(new PolicyVoteTally(10, 6, 4).passes(5_000, 5_000));
    }
}

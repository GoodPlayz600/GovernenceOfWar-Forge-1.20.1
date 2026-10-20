package io.github.geopoliticalwar.domain.governance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationMembershipTest {
    @Test
    void onlyCitizensMayVoteOrHoldOffice() {
        NationId nation = NationId.random();
        UUID player = UUID.randomUUID();
        NationMembership resident = new NationMembership(nation, player, MembershipStatus.RESIDENT, 1L, player);
        NationMembership citizen = new NationMembership(nation, player, MembershipStatus.CITIZEN, 1L, player);

        assertFalse(resident.status().mayVote());
        assertFalse(resident.status().mayHoldOffice());
        assertTrue(citizen.status().mayVote());
        assertTrue(citizen.status().mayHoldOffice());
    }

    @Test
    void requestsExpireAndDoNotRemainPendingAtTheirBoundary() {
        NationRequest request = new NationRequest(
                UUID.randomUUID(), NationId.random(), UUID.randomUUID(), NationRequestType.APPLICATION,
                MembershipStatus.CITIZEN, NationRequestState.PENDING, 10L, 20L, UUID.randomUUID()
        );

        assertTrue(request.pending(19L));
        assertFalse(request.pending(20L));
    }
}

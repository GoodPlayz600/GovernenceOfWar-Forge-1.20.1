package io.github.geopoliticalwar.domain.governance;

import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyChangeTest {
    @Test
    void policyPayloadRoundTripsAndValidates() {
        PolicyChange change = PolicyChange.fundedProject(
                "road", UUID.randomUUID(), new CurrencyId("test:credits"), 100L, 10L, 60L
        );
        assertEquals(change, PolicyChange.fromTag(change.toTag()));
        assertThrows(IllegalArgumentException.class, () -> new PolicyChange(
                PolicyCategory.TAXES, PolicyOperation.CLEAR,
                java.util.Optional.of(TaxPolicy.ledger(1L, new CurrencyId("test:credits"))),
                -1, -1, -1, -1, RevenueTarget.SYSTEM_TAX, java.util.Optional.empty(),
                0L, 0L, SubsidyScope.CITIZENS, "", java.util.Optional.empty(), 0L, 0L
        ));
    }

    @Test
    void democraticProposalCanExpireBeforeItsScheduledClose() {
        LawProposal proposal = new LawProposal(
                UUID.randomUUID(), NationId.random(), UUID.randomUUID(), PolicyChange.payroll(10_000),
                100L, 1_000L, Set.of(UUID.randomUUID()), Map.of(), ProposalStatus.OPEN,
                Optional.empty(), Optional.empty()
        );

        LawProposal expired = proposal.finalized(ProposalStatus.EXPIRED, Optional.empty(), 200L);

        assertEquals(ProposalStatus.EXPIRED, expired.status());
        assertEquals(Optional.of(200L), expired.finalizedAtMillis());
    }
}

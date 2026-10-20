package io.github.geopoliticalwar.domain.economy;

import io.github.geopoliticalwar.domain.claim.ClaimId;

import java.util.Objects;
import java.util.UUID;

public record Lease(
        UUID id,
        ClaimId claim,
        AccountId landlord,
        AccountId tenant,
        CurrencyId currency,
        long rentPerCycle,
        long nextDueAtMillis,
        int missedPayments
) {
    public Lease {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(landlord, "landlord");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(currency, "currency");
        if (landlord.equals(tenant) || rentPerCycle <= 0 || nextDueAtMillis < 0 || missedPayments < 0) {
            throw new IllegalArgumentException("Invalid lease");
        }
    }

    public Lease paid(long nextDue) {
        return new Lease(id, claim, landlord, tenant, currency, rentPerCycle, nextDue, 0);
    }

    public Lease missed() {
        return new Lease(id, claim, landlord, tenant, currency, rentPerCycle, nextDueAtMillis, missedPayments + 1);
    }
}

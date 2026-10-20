package io.github.geopoliticalwar.domain.economy;

import io.github.geopoliticalwar.domain.claim.ClaimId;

import java.util.Objects;

public record TaxObligation(
        ClaimId claim,
        AccountId debtor,
        CurrencyId currency,
        long amount,
        long assessedAtMillis,
        long dueAtMillis,
        int attempts,
        TaxType type,
        AccountId recipient
) {
    public TaxObligation {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(recipient, "recipient");
        if (amount <= 0 || assessedAtMillis < 0 || dueAtMillis < assessedAtMillis || attempts < 0) {
            throw new IllegalArgumentException("Invalid tax obligation");
        }
    }

    public TaxObligation retry() {
        return new TaxObligation(claim, debtor, currency, amount, assessedAtMillis, dueAtMillis, attempts + 1, type, recipient);
    }
}

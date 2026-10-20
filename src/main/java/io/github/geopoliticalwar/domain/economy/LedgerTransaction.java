package io.github.geopoliticalwar.domain.economy;

import java.util.Objects;
import java.util.UUID;

public record LedgerTransaction(
        UUID id,
        long timestampMillis,
        CurrencyId currency,
        AccountId from,
        AccountId to,
        long amount,
        String reason
) {
    public LedgerTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
    }
}

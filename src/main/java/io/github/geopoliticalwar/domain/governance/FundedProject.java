package io.github.geopoliticalwar.domain.governance;

import io.github.geopoliticalwar.domain.economy.CurrencyId;

import java.util.Objects;
import java.util.UUID;

public record FundedProject(
        UUID id,
        NationId nation,
        UUID law,
        String name,
        UUID recipient,
        CurrencyId currency,
        long budget,
        long installment,
        long intervalSeconds,
        long paid,
        long nextPaymentAtMillis,
        ProjectStatus status
) {
    public FundedProject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(law, "law");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        name = name.trim();
        if (name.isEmpty() || budget <= 0L || installment <= 0L || intervalSeconds <= 0L
                || paid < 0L || paid > budget || nextPaymentAtMillis < 0L) {
            throw new IllegalArgumentException("Invalid funded project");
        }
    }

    public FundedProject paid(long amount, long nextRun, ProjectStatus nextStatus) {
        long updated = Math.addExact(paid, amount);
        if (updated > budget) {
            throw new IllegalArgumentException("Project payment exceeds budget");
        }
        return new FundedProject(id, nation, law, name, recipient, currency, budget, installment,
                intervalSeconds, updated, nextRun, nextStatus);
    }
}

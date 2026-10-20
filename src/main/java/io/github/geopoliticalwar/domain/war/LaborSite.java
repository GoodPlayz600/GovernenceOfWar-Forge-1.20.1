package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.UUID;

/** A bounded production site to which fictional captive labor may be assigned. */
public record LaborSite(
        UUID id,
        ClaimId claim,
        NationId controller,
        String itemId,
        long outputPerCycle,
        long bufferedOutput,
        long lastOutputAtMillis
) {
    public LaborSite {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(itemId, "itemId");
        itemId = itemId.trim().toLowerCase(java.util.Locale.ROOT);
        if (itemId.isEmpty() || outputPerCycle <= 0L || bufferedOutput < 0L || lastOutputAtMillis < 0L) {
            throw new IllegalArgumentException("Invalid labor site");
        }
    }

    public LaborSite produce(long amount, long capacity, long now) {
        if (amount < 0L || capacity < 0L || now < lastOutputAtMillis) {
            throw new IllegalArgumentException("Invalid labor-site production update");
        }
        long updated = bufferedOutput > capacity - amount
                ? capacity
                : bufferedOutput + amount;
        return new LaborSite(id, claim, controller, itemId, outputPerCycle, updated, now);
    }

    public LaborSite withdraw(long amount) {
        if (amount <= 0L || amount > bufferedOutput) {
            throw new IllegalArgumentException("Insufficient labor-site output");
        }
        return new LaborSite(id, claim, controller, itemId, outputPerCycle,
                bufferedOutput - amount, lastOutputAtMillis);
    }
}

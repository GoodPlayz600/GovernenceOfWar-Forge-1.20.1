package io.github.geopoliticalwar.domain.economy;

import java.util.Objects;

public record CurrencyDefinition(
        CurrencyId id,
        String name,
        CurrencyMode mode,
        long supply,
        long purchasingPowerBps
) {
    public CurrencyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mode, "mode");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Currency name cannot be empty");
        }
        if (supply < 0) {
            throw new IllegalArgumentException("Currency supply cannot be negative");
        }
        if (purchasingPowerBps <= 0) {
            throw new IllegalArgumentException("Purchasing power must be positive");
        }
    }

    public CurrencyDefinition withSupply(long newSupply) {
        return new CurrencyDefinition(id, name, mode, newSupply, purchasingPowerBps);
    }

    public CurrencyDefinition withPurchasingPower(long newPowerBps) {
        return new CurrencyDefinition(id, name, mode, supply, newPowerBps);
    }
}

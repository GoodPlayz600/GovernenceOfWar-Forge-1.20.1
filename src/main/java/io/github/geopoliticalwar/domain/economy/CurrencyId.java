package io.github.geopoliticalwar.domain.economy;

import java.util.Locale;
import java.util.Objects;

public record CurrencyId(String value) {
    public CurrencyId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid currency ID: " + value);
        }
    }
}

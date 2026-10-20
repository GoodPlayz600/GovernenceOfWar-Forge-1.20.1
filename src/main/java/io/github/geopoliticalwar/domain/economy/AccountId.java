package io.github.geopoliticalwar.domain.economy;

import java.util.Objects;

public record AccountId(String value) {
    public AccountId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be empty");
        }
    }
}

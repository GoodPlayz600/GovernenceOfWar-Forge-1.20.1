package io.github.geopoliticalwar.domain.claim;

import java.util.Objects;

/** A stable, serialized dimension identifier such as {@code minecraft:overworld}. */
public record DimensionId(String value) {
    public DimensionId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Dimension ID cannot be empty or contain whitespace");
        }
    }
}

package io.github.geopoliticalwar.domain.war;

import java.util.Objects;
import java.util.UUID;

public record WarId(UUID value) {
    public WarId {
        Objects.requireNonNull(value, "value");
    }

    public static WarId random() {
        return new WarId(UUID.randomUUID());
    }
}

package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.UUID;

public record NationId(UUID value) {
    public NationId {
        Objects.requireNonNull(value, "value");
    }

    public static NationId random() {
        return new NationId(UUID.randomUUID());
    }
}

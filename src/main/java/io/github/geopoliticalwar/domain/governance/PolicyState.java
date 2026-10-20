package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.UUID;

public record PolicyState(
        UUID law,
        PolicyChange change,
        long enactedAtMillis,
        long nextRunAtMillis
) {
    public PolicyState {
        Objects.requireNonNull(law, "law");
        Objects.requireNonNull(change, "change");
        if (enactedAtMillis < 0L || nextRunAtMillis < 0L) {
            throw new IllegalArgumentException("Policy timestamps cannot be negative");
        }
    }
}

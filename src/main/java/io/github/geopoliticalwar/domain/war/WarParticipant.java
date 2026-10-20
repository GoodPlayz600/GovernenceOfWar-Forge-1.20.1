package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;

public record WarParticipant(NationId nation, WarSide side, long joinedAtMillis) {
    public WarParticipant {
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(side, "side");
        if (joinedAtMillis < 0) {
            throw new IllegalArgumentException("Participant timestamp cannot be negative");
        }
    }
}

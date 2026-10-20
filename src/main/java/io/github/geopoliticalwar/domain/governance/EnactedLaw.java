package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EnactedLaw(
        UUID id,
        NationId nation,
        LawOrigin origin,
        UUID actor,
        Optional<UUID> proposal,
        PolicyChange change,
        long enactedAtMillis
) {
    public EnactedLaw {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(change, "change");
        if (enactedAtMillis < 0L) {
            throw new IllegalArgumentException("Law timestamp cannot be negative");
        }
    }
}

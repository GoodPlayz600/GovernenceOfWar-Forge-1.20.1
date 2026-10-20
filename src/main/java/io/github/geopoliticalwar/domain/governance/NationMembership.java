package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.UUID;

public record NationMembership(
        NationId nation,
        UUID player,
        MembershipStatus status,
        long grantedAtMillis,
        UUID grantedBy
) {
    public NationMembership {
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(grantedBy, "grantedBy");
        if (grantedAtMillis < 0) {
            throw new IllegalArgumentException("Membership timestamp cannot be negative");
        }
    }
}

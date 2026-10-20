package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.UUID;

public record NationRequest(
        UUID id,
        NationId nation,
        UUID player,
        NationRequestType type,
        MembershipStatus requestedStatus,
        NationRequestState state,
        long createdAtMillis,
        long expiresAtMillis,
        UUID actor
) {
    public NationRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requestedStatus, "requestedStatus");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        if (requestedStatus == MembershipStatus.SUBJECT) {
            throw new IllegalArgumentException("Subjects are created by occupation, not admission requests");
        }
        if (createdAtMillis < 0 || expiresAtMillis < createdAtMillis) {
            throw new IllegalArgumentException("Invalid request timestamps");
        }
    }

    public boolean pending(long now) {
        return state == NationRequestState.PENDING && now < expiresAtMillis;
    }

    public NationRequest withState(NationRequestState next) {
        return new NationRequest(id, nation, player, type, requestedStatus, next, createdAtMillis, expiresAtMillis, actor);
    }
}

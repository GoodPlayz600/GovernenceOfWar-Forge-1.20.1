package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.UUID;

/** Server-persisted fictional player-captive state for occupied territory. */
public record CaptiveRecord(
        UUID player,
        ClaimId claim,
        NationId holdingNation,
        NationId formerNation,
        CaptiveState state,
        long capturedAtMillis,
        long lastWorkAtMillis,
        long workedSeconds,
        int unrest,
        long releasedAtMillis
) {
    public CaptiveRecord {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(holdingNation, "holdingNation");
        Objects.requireNonNull(formerNation, "formerNation");
        Objects.requireNonNull(state, "state");
        if (capturedAtMillis < 0 || lastWorkAtMillis < capturedAtMillis
                || workedSeconds < 0 || unrest < 0 || releasedAtMillis < 0) {
            throw new IllegalArgumentException("Invalid captive record");
        }
    }

    public static CaptiveRecord active(
            UUID player,
            ClaimId claim,
            NationId holdingNation,
            NationId formerNation,
            long now
    ) {
        return new CaptiveRecord(player, claim, holdingNation, formerNation, CaptiveState.ACTIVE,
                now, now, 0L, 0, 0L);
    }

    public boolean active() {
        return state == CaptiveState.ACTIVE;
    }

    public CaptiveRecord worked(long seconds, int unrestIncrease, long now) {
        if (!active() || seconds < 0 || unrestIncrease < 0) {
            throw new IllegalArgumentException("Invalid captive work update");
        }
        return new CaptiveRecord(player, claim, holdingNation, formerNation, state,
                capturedAtMillis, now, Math.addExact(workedSeconds, seconds),
                Math.addExact(unrest, unrestIncrease), releasedAtMillis);
    }

    public CaptiveRecord transition(CaptiveState next, long now) {
        if (!active() || next == CaptiveState.ACTIVE) {
            throw new IllegalArgumentException("Invalid captive state transition");
        }
        return new CaptiveRecord(player, claim, holdingNation, formerNation, next,
                capturedAtMillis, lastWorkAtMillis, workedSeconds, unrest, now);
    }
}

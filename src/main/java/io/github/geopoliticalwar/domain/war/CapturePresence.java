package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.Set;

public record CapturePresence(
        Set<NationId> attackerNations,
        Set<NationId> defenderNations,
        int neutralPlayers,
        boolean beneficiaryPresent
) {
    public CapturePresence {
        Objects.requireNonNull(attackerNations, "attackerNations");
        Objects.requireNonNull(defenderNations, "defenderNations");
        attackerNations = Set.copyOf(attackerNations);
        defenderNations = Set.copyOf(defenderNations);
        if (neutralPlayers < 0) {
            throw new IllegalArgumentException("Neutral presence cannot be negative");
        }
    }

    public boolean contested() {
        return !defenderNations.isEmpty() || neutralPlayers > 0;
    }
}

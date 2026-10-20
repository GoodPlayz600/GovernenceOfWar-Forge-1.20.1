package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RebellionRecord(
        NationId nation,
        int momentum,
        Set<UUID> rebelCells,
        long lastMomentumUpdateMillis,
        boolean civilWar
) {
    public RebellionRecord {
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(rebelCells, "rebelCells");
        if (momentum < 0 || lastMomentumUpdateMillis < 0) {
            throw new IllegalArgumentException("Rebellion values cannot be negative");
        }
        rebelCells = Set.copyOf(rebelCells);
    }

    public RebellionRecord addMomentum(int amount, long now, int threshold) {
        if (amount < 0) {
            throw new IllegalArgumentException("Momentum addition cannot be negative");
        }
        int updated = Math.addExact(momentum, amount);
        return new RebellionRecord(nation, updated, rebelCells, now, civilWar || updated >= threshold);
    }

    public RebellionRecord decay(int amount, long now) {
        return new RebellionRecord(nation, Math.max(0, momentum - amount), rebelCells, now, civilWar);
    }

    public RebellionRecord addCell(UUID player, long now) {
        java.util.HashSet<UUID> updated = new java.util.HashSet<>(rebelCells);
        updated.add(player);
        return new RebellionRecord(nation, momentum, updated, now, civilWar);
    }
}

package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.BlockPoint;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CapturePoint(
        UUID id,
        String name,
        DimensionId dimension,
        BlockPoint center,
        int radius,
        Optional<NationId> owner,
        long progressSeconds,
        long lastTickMillis
) {
    public CapturePoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(owner, "owner");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Capture point name cannot be empty");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("Capture point radius must be positive");
        }
        if (progressSeconds < 0 || lastTickMillis < 0) {
            throw new IllegalArgumentException("Capture timers cannot be negative");
        }
    }

    public boolean contains(double x, double y, double z) {
        double dx = x - center.x();
        double dy = y - center.y();
        double dz = z - center.z();
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    public CapturePoint withProgress(long progress, long tickMillis) {
        return new CapturePoint(id, name, dimension, center, radius, owner, progress, tickMillis);
    }

    public CapturePoint capturedBy(NationId nation, long tickMillis) {
        return new CapturePoint(id, name, dimension, center, radius, Optional.of(nation), 0L, tickMillis);
    }
}

package io.github.geopoliticalwar.domain.claim;

import java.util.Optional;
import java.util.Set;

public interface ClaimSpatialIndex {
    void add(Claim claim);

    void replace(Claim claim);

    boolean remove(ClaimId id);

    Optional<Claim> get(ClaimId id);

    Set<ClaimId> containing(DimensionId dimension, long x, long y, long z);

    Set<ClaimId> intersecting(DimensionId dimension, BlockBox area);
}

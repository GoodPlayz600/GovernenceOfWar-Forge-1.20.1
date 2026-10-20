package io.github.geopoliticalwar.domain.claim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Dimension-aware claim index backed by chunk columns and Y intervals.
 * Mutations are expected to run on the logical server thread.
 */
public final class ChunkColumnYClaimIndex implements ClaimSpatialIndex {
    private final ClaimOverlapPolicy overlapPolicy;
    private final Map<ClaimId, Claim> claims = new HashMap<>();
    private final Map<DimensionId, Map<ChunkColumn, YIntervalIndex<ClaimId>>> columns = new HashMap<>();

    public ChunkColumnYClaimIndex() {
        this(ClaimOverlapPolicy.REJECT);
    }

    public ChunkColumnYClaimIndex(ClaimOverlapPolicy overlapPolicy) {
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy, "overlapPolicy");
    }

    @Override
    public void add(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        if (claims.containsKey(claim.id())) {
            throw new IllegalArgumentException("Claim ID already exists: " + claim.id());
        }

        if (overlapPolicy == ClaimOverlapPolicy.REJECT) {
            for (ClaimId existing : intersecting(claim.dimension(), claim.volume())) {
                throw new ClaimConflictException(existing, claim.id());
            }
        }

        claims.put(claim.id(), claim);
        indexClaim(claim);
    }

    @Override
    public void replace(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        Claim previous = claims.get(claim.id());
        if (previous == null) {
            throw new IllegalArgumentException("Cannot replace unknown claim: " + claim.id());
        }

        remove(claim.id());
        try {
            add(claim);
        } catch (RuntimeException failure) {
            add(previous);
            throw failure;
        }
    }

    @Override
    public boolean remove(ClaimId id) {
        Claim previous = claims.remove(Objects.requireNonNull(id, "id"));
        if (previous == null) {
            return false;
        }

        unindexClaim(previous);
        return true;
    }

    @Override
    public Optional<Claim> get(ClaimId id) {
        return Optional.ofNullable(claims.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public Set<ClaimId> containing(DimensionId dimension, long x, long y, long z) {
        Objects.requireNonNull(dimension, "dimension");
        Map<ChunkColumn, YIntervalIndex<ClaimId>> dimensionColumns = columns.get(dimension);
        if (dimensionColumns == null) {
            return Set.of();
        }

        ChunkColumn column = new ChunkColumn(
                Math.floorDiv(x, BlockBox.CHUNK_SIZE),
                Math.floorDiv(z, BlockBox.CHUNK_SIZE)
        );
        YIntervalIndex<ClaimId> intervalIndex = dimensionColumns.get(column);
        if (intervalIndex == null) {
            return Set.of();
        }

        Set<ClaimId> result = new HashSet<>();
        for (ClaimId id : intervalIndex.containing(y)) {
            Claim claim = claims.get(id);
            if (claim != null && claim.volume().contains(x, y, z)) {
                result.add(id);
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public Set<ClaimId> intersecting(DimensionId dimension, BlockBox area) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(area, "area");
        Map<ChunkColumn, YIntervalIndex<ClaimId>> dimensionColumns = columns.get(dimension);
        if (dimensionColumns == null) {
            return Set.of();
        }

        Set<ClaimId> candidates = new HashSet<>();
        for (long chunkX = area.firstChunkX(); ; chunkX++) {
            for (long chunkZ = area.firstChunkZ(); ; chunkZ++) {
                YIntervalIndex<ClaimId> intervalIndex = dimensionColumns.get(new ChunkColumn(chunkX, chunkZ));
                if (intervalIndex != null) {
                    candidates.addAll(intervalIndex.intersecting(area.minY(), area.maxYExclusive()));
                }
                if (chunkZ == area.lastChunkZ()) {
                    break;
                }
            }
            if (chunkX == area.lastChunkX()) {
                break;
            }
        }

        Set<ClaimId> result = new HashSet<>();
        for (ClaimId candidate : candidates) {
            Claim claim = claims.get(candidate);
            if (claim != null && claim.dimension().equals(dimension) && claim.volume().intersects(area)) {
                result.add(candidate);
            }
        }
        return Set.copyOf(result);
    }

    public int size() {
        return claims.size();
    }

    private void indexClaim(Claim claim) {
        Map<ChunkColumn, YIntervalIndex<ClaimId>> dimensionColumns = columns.computeIfAbsent(
                claim.dimension(),
                ignored -> new HashMap<>()
        );

        for (long chunkX = claim.volume().firstChunkX(); ; chunkX++) {
            for (long chunkZ = claim.volume().firstChunkZ(); ; chunkZ++) {
                ChunkColumn column = new ChunkColumn(chunkX, chunkZ);
                dimensionColumns
                        .computeIfAbsent(column, ignored -> new SortedYIntervalIndex<>())
                        .add(claim.volume().minY(), claim.volume().maxYExclusive(), claim.id());
                if (chunkZ == claim.volume().lastChunkZ()) {
                    break;
                }
            }
            if (chunkX == claim.volume().lastChunkX()) {
                break;
            }
        }
    }

    private void unindexClaim(Claim claim) {
        Map<ChunkColumn, YIntervalIndex<ClaimId>> dimensionColumns = columns.get(claim.dimension());
        if (dimensionColumns == null) {
            return;
        }

        for (long chunkX = claim.volume().firstChunkX(); ; chunkX++) {
            for (long chunkZ = claim.volume().firstChunkZ(); ; chunkZ++) {
                ChunkColumn column = new ChunkColumn(chunkX, chunkZ);
                YIntervalIndex<ClaimId> intervalIndex = dimensionColumns.get(column);
                if (intervalIndex != null
                        && intervalIndex.remove(
                                claim.volume().minY(),
                                claim.volume().maxYExclusive(),
                                claim.id()
                        )
                        && intervalIndex.isEmpty()) {
                    dimensionColumns.remove(column);
                }
                if (chunkZ == claim.volume().lastChunkZ()) {
                    break;
                }
            }
            if (chunkX == claim.volume().lastChunkX()) {
                break;
            }
        }

        if (dimensionColumns.isEmpty()) {
            columns.remove(claim.dimension());
        }
    }
}

package io.github.geopoliticalwar.domain.claim;

import java.util.Objects;

/**
 * A normalized, half-open 3D block volume.
 *
 * <p>The maximum coordinates are exclusive. This makes adjacent volumes such
 * as {@code [0, 16)} and {@code [16, 32)} non-overlapping.</p>
 */
public record BlockBox(
        long minX,
        long minY,
        long minZ,
        long maxXExclusive,
        long maxYExclusive,
        long maxZExclusive
) {
    public static final long CHUNK_SIZE = 16L;

    public BlockBox {
        if (minX >= maxXExclusive || minY >= maxYExclusive || minZ >= maxZExclusive) {
            throw new IllegalArgumentException("BlockBox must have positive volume");
        }
    }

    public static BlockBox fromInclusiveCorners(BlockPoint first, BlockPoint second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        long minX = Math.min(first.x(), second.x());
        long minY = Math.min(first.y(), second.y());
        long minZ = Math.min(first.z(), second.z());
        long maxX = Math.max(first.x(), second.x());
        long maxY = Math.max(first.y(), second.y());
        long maxZ = Math.max(first.z(), second.z());

        return new BlockBox(
                minX,
                minY,
                minZ,
                Math.addExact(maxX, 1L),
                Math.addExact(maxY, 1L),
                Math.addExact(maxZ, 1L)
        );
    }

    public boolean contains(long x, long y, long z) {
        return x >= minX && x < maxXExclusive
                && y >= minY && y < maxYExclusive
                && z >= minZ && z < maxZExclusive;
    }

    public boolean intersects(BlockBox other) {
        Objects.requireNonNull(other, "other");
        return minX < other.maxXExclusive
                && other.minX < maxXExclusive
                && minY < other.maxYExclusive
                && other.minY < maxYExclusive
                && minZ < other.maxZExclusive
                && other.minZ < maxZExclusive;
    }

    public long volume() {
        long x = Math.subtractExact(maxXExclusive, minX);
        long y = Math.subtractExact(maxYExclusive, minY);
        long z = Math.subtractExact(maxZExclusive, minZ);
        return Math.multiplyExact(Math.multiplyExact(x, y), z);
    }

    public long firstChunkX() {
        return Math.floorDiv(minX, CHUNK_SIZE);
    }

    public long lastChunkX() {
        return Math.floorDiv(Math.subtractExact(maxXExclusive, 1L), CHUNK_SIZE);
    }

    public long firstChunkZ() {
        return Math.floorDiv(minZ, CHUNK_SIZE);
    }

    public long lastChunkZ() {
        return Math.floorDiv(Math.subtractExact(maxZExclusive, 1L), CHUNK_SIZE);
    }
}

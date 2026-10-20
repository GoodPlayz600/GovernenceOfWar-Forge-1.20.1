package io.github.geopoliticalwar.domain.claim;

import java.util.Set;

/** Index of half-open Y intervals for one chunk column. */
public interface YIntervalIndex<V> {
    void add(long minY, long maxYExclusive, V value);

    boolean remove(long minY, long maxYExclusive, V value);

    Set<V> containing(long y);

    Set<V> intersecting(long minY, long maxYExclusive);

    boolean isEmpty();
}

package io.github.geopoliticalwar.domain.claim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic Y interval index used by the first claim implementation.
 *
 * <p>Claim counts per chunk are bounded by server configuration. Keeping the
 * interval collection sorted makes insertion deterministic and leaves a clear
 * replacement point for an augmented tree if a server needs larger limits.</p>
 */
public final class SortedYIntervalIndex<V> implements YIntervalIndex<V> {
    private final List<Entry<V>> entries = new ArrayList<>();

    @Override
    public void add(long minY, long maxYExclusive, V value) {
        validateRange(minY, maxYExclusive);
        Objects.requireNonNull(value, "value");

        Entry<V> entry = new Entry<>(minY, maxYExclusive, value);
        if (entries.contains(entry)) {
            throw new IllegalArgumentException("Duplicate Y interval entry");
        }
        entries.add(entry);
        entries.sort(Comparator
                .comparingLong(Entry<V>::minY)
                .thenComparingLong(Entry<V>::maxYExclusive)
                .thenComparing(entryValue -> entryValue.value().toString()));
    }

    @Override
    public boolean remove(long minY, long maxYExclusive, V value) {
        validateRange(minY, maxYExclusive);
        return entries.remove(new Entry<>(minY, maxYExclusive, value));
    }

    @Override
    public Set<V> containing(long y) {
        Set<V> result = new HashSet<>();
        for (Entry<V> entry : entries) {
            if (entry.minY() > y) {
                break;
            }
            if (y < entry.maxYExclusive()) {
                result.add(entry.value());
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public Set<V> intersecting(long minY, long maxYExclusive) {
        validateRange(minY, maxYExclusive);
        Set<V> result = new HashSet<>();
        for (Entry<V> entry : entries) {
            if (entry.minY() >= maxYExclusive) {
                break;
            }
            if (entry.minY() < maxYExclusive && minY < entry.maxYExclusive()) {
                result.add(entry.value());
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static void validateRange(long minY, long maxYExclusive) {
        if (minY >= maxYExclusive) {
            throw new IllegalArgumentException("Y interval must have positive size");
        }
    }

    private record Entry<V>(long minY, long maxYExclusive, V value) {
        private Entry {
            Objects.requireNonNull(value, "value");
        }
    }
}

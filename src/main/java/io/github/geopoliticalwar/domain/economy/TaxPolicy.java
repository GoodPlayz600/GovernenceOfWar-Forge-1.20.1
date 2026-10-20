package io.github.geopoliticalwar.domain.economy;

import java.util.Objects;

/** A claim's immutable property-tax tender and rate. */
public record TaxPolicy(
        TaxMode mode,
        long unitsPerBlock,
        CurrencyId currency,
        String itemId
) {
    public TaxPolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(itemId, "itemId");
        itemId = itemId.trim().toLowerCase(java.util.Locale.ROOT);
        if (unitsPerBlock <= 0) {
            throw new IllegalArgumentException("Tax units per block must be positive");
        }
        if (mode == TaxMode.ITEM && itemId.isEmpty()) {
            throw new IllegalArgumentException("Item taxes require an item ID");
        }
        if (mode == TaxMode.DIAMONDS && !"minecraft:diamond".equals(itemId)) {
            throw new IllegalArgumentException("Diamond taxes must use minecraft:diamond");
        }
    }

    public static TaxPolicy ledger(long unitsPerBlock, CurrencyId currency) {
        return new TaxPolicy(TaxMode.LEDGER, unitsPerBlock, currency, "");
    }

    public static TaxPolicy diamonds(long diamondsPerBlock, CurrencyId currency) {
        return new TaxPolicy(TaxMode.DIAMONDS, diamondsPerBlock, currency, "minecraft:diamond");
    }

    public static TaxPolicy item(long itemsPerBlock, CurrencyId currency, String itemId) {
        return new TaxPolicy(TaxMode.ITEM, itemsPerBlock, currency, itemId);
    }
}

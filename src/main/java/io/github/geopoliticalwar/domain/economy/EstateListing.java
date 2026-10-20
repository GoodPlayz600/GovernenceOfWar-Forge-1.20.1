package io.github.geopoliticalwar.domain.economy;

import io.github.geopoliticalwar.domain.claim.ClaimId;

import java.util.Objects;
import java.util.UUID;

public record EstateListing(
        UUID id,
        ClaimId claim,
        AccountId seller,
        long salePrice,
        long rentPerCycle,
        long createdAtMillis
) {
    public EstateListing {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(seller, "seller");
        if (salePrice <= 0 && rentPerCycle <= 0) {
            throw new IllegalArgumentException("An estate listing needs a sale price or rent");
        }
        if (salePrice < 0 || rentPerCycle < 0 || createdAtMillis < 0) {
            throw new IllegalArgumentException("Estate values cannot be negative");
        }
    }

    public boolean forSale() {
        return salePrice > 0;
    }

    public boolean forRent() {
        return rentPerCycle > 0;
    }
}
